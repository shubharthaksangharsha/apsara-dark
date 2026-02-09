package com.shubharthak.apsaradark.ui.components

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shubharthak.apsaradark.ui.theme.LocalThemeManager
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import android.graphics.BitmapFactory

/**
 * Full-width camera preview card with overlay icons for flash, flip, minimize, close.
 * - Tap anywhere to show a round focus ring animation + actually trigger CameraX autofocus.
 * - Draw mode: single-finger draws white annotation strokes on the camera preview.
 * - Annotations are composited onto captured frames sent to the backend.
 * - Icons auto-hide after 3 seconds of inactivity.
 *
 * Camera settings (useFrontCamera, flashEnabled) are hoisted so they survive minimize/restore.
 */
@Composable
fun CameraPreviewCard(
    useFrontCamera: Boolean,
    flashEnabled: Boolean,
    onUseFrontCameraChange: (Boolean) -> Unit,
    onFlashEnabledChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onFrameCaptured: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalThemeManager.current.currentTheme

    // Hold a reference to the bound Camera so we can control torch + focus
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Overlay visibility (auto-hide after 3s)
    var showOverlay by remember { mutableStateOf(true) }
    var overlayResetKey by remember { mutableIntStateOf(0) }

    // Focus animation state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Track the composable's pixel size for focus coordinate mapping
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Pinch-to-zoom state
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var showZoomIndicator by remember { mutableStateOf(false) }

    // ─── Annotation / Drawing state ─────────────────────────────────────────
    var isDrawMode by remember { mutableStateOf(false) }
    // Each stroke is a list of Offset points
    val annotationStrokes = remember { mutableStateListOf<List<Offset>>() }
    // Current stroke being drawn (live)
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Auto-hide overlay timer — not used in draw mode (overlay hidden)
    LaunchedEffect(overlayResetKey, isDrawMode) {
        if (!isDrawMode) {
            showOverlay = true
            delay(3000)
            showOverlay = false
        } else {
            showOverlay = false
        }
    }

    // Focus ring animation
    val focusScale by animateFloatAsState(
        targetValue = if (showFocusRing) 1f else 0.4f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "focusScale"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (showFocusRing) 1f else 0f,
        animationSpec = tween(if (showFocusRing) 200 else 600),
        label = "focusAlpha"
    )

    // Dismiss focus ring after animation
    LaunchedEffect(showFocusRing) {
        if (showFocusRing) {
            delay(800)
            showFocusRing = false
        }
    }

    // Auto-hide zoom indicator
    LaunchedEffect(showZoomIndicator, currentZoomRatio) {
        if (showZoomIndicator) {
            delay(1200)
            showZoomIndicator = false
        }
    }

    // Snapshot of annotation strokes for the frame capture thread
    val strokesSnapshot = remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    LaunchedEffect(annotationStrokes.size) {
        strokesSnapshot.value = annotationStrokes.toList()
    }

    // Frame throttle: send one frame every ~1 second
    val lastFrameTime = remember { mutableLongStateOf(0L) }
    val frameIntervalMs = 1000L

    // Camera setup
    val cameraSelector = remember(useFrontCamera) {
        if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
    }

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Bind camera when selector changes
    LaunchedEffect(cameraSelector) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastFrameTime.longValue >= frameIntervalMs) {
                    lastFrameTime.longValue = now
                    val jpegBytes = imageProxyToJpeg(imageProxy)
                    if (jpegBytes != null) {
                        // Composite annotations onto the frame if any exist
                        val strokes = strokesSnapshot.value
                        val currentViewSize = viewSize
                        val composited = if (strokes.isNotEmpty() && currentViewSize.width > 0 && currentViewSize.height > 0) {
                            compositeAnnotationsOnFrame(jpegBytes, strokes, currentViewSize, imageProxy.width, imageProxy.height)
                        } else {
                            jpegBytes
                        }
                        onFrameCaptured(composited)
                    }
                }
                imageProxy.close()
            }

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                activeCamera = camera
                camera.cameraControl.enableTorch(flashEnabled && !useFrontCamera)
                currentZoomRatio = 1f
                camera.cameraControl.setZoomRatio(1f)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera bind failed", e)
            }
        }, context.mainExecutor)
    }

    // Update torch when flash toggles
    LaunchedEffect(flashEnabled) {
        activeCamera?.let { camera ->
            try {
                camera.cameraControl.enableTorch(flashEnabled && !useFrontCamera)
            } catch (_: Exception) {}
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            activeCamera = null
        }
    }

    Column(modifier = modifier) {
        // ─── Draw toolbar — sits OUTSIDE the camera gesture area ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear annotations (only if annotations exist)
            AnimatedVisibility(
                visible = annotationStrokes.isNotEmpty(),
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                annotationStrokes.clear()
                            }
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Clear drawings",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Draw mode toggle pill
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isDrawMode) palette.accent.copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isDrawMode = !isDrawMode }
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isDrawMode) Icons.Outlined.Draw else Icons.Outlined.Edit,
                        contentDescription = if (isDrawMode) "Exit draw mode" else "Draw",
                        tint = if (isDrawMode) palette.accent else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ─── Camera preview box with gestures ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .onSizeChanged { viewSize = it }
                .pointerInput(isDrawMode) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val firstDownPos = firstDown.position
                        var isPinching = false
                        var isDragging = false

                        if (isDrawMode) {
                            // ── Drawing mode: single-finger draws strokes ──
                            currentStroke = listOf(firstDownPos)
                            isDragging = true

                            do {
                                val event = awaitPointerEvent()
                                val fingerCount = event.changes.count { it.pressed }

                                if (fingerCount >= 2) {
                                    isPinching = true
                                    isDragging = false
                                    currentStroke = emptyList()
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        activeCamera?.let { camera ->
                                            val zoomState = camera.cameraInfo.zoomState.value
                                            if (zoomState != null) {
                                                val newRatio = (currentZoomRatio * zoomChange)
                                                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                                currentZoomRatio = newRatio
                                                camera.cameraControl.setZoomRatio(newRatio)
                                                showZoomIndicator = true
                                            }
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (isDragging && fingerCount == 1) {
                                    val pos = event.changes.firstOrNull()?.position
                                    if (pos != null) {
                                        currentStroke = currentStroke + pos
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            if (isDragging && currentStroke.size >= 2) {
                                annotationStrokes.add(currentStroke.toList())
                            }
                            currentStroke = emptyList()
                        } else {
                            // ── Normal mode: tap-to-focus + pinch-to-zoom ──
                            do {
                                val event = awaitPointerEvent()
                                val fingerCount = event.changes.count { it.pressed }

                                if (fingerCount >= 2) {
                                    isPinching = true
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        activeCamera?.let { camera ->
                                            val zoomState = camera.cameraInfo.zoomState.value
                                            if (zoomState != null) {
                                                val newRatio = (currentZoomRatio * zoomChange)
                                                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                                currentZoomRatio = newRatio
                                                camera.cameraControl.setZoomRatio(newRatio)
                                                showZoomIndicator = true
                                                overlayResetKey++
                                            }
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isPinching) {
                                focusPoint = firstDownPos
                                showFocusRing = true
                                overlayResetKey++

                                activeCamera?.let { camera ->
                                    try {
                                        if (viewSize.width > 0 && viewSize.height > 0) {
                                            val factory = SurfaceOrientedMeteringPointFactory(
                                                viewSize.width.toFloat(),
                                                viewSize.height.toFloat()
                                            )
                                            val point = factory.createPoint(firstDownPos.x, firstDownPos.y)
                                            val action = FocusMeteringAction.Builder(point)
                                                .disableAutoCancel()
                                                .build()
                                            camera.cameraControl.startFocusAndMetering(action)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("CameraPreview", "Focus failed: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Camera preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // ─── Annotation strokes overlay ─────────────────────────────
            if (annotationStrokes.isNotEmpty() || currentStroke.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeStyle = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    for (stroke in annotationStrokes) {
                        if (stroke.size >= 2) {
                            val path = Path().apply {
                                moveTo(stroke[0].x, stroke[0].y)
                                for (i in 1 until stroke.size) {
                                    lineTo(stroke[i].x, stroke[i].y)
                                }
                            }
                            drawPath(path, Color.White, style = strokeStyle)
                        }
                    }
                    if (currentStroke.size >= 2) {
                        val path = Path().apply {
                            moveTo(currentStroke[0].x, currentStroke[0].y)
                            for (i in 1 until currentStroke.size) {
                                lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                        }
                        drawPath(path, Color.White.copy(alpha = 0.7f), style = strokeStyle)
                    }
                }
            }

            // Focus ring animation
            if (focusPoint != null && focusAlpha > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    focusPoint?.let { point ->
                        drawCircle(
                            color = Color.White.copy(alpha = focusAlpha * 0.8f),
                            radius = 36.dp.toPx() * focusScale,
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            // Zoom level indicator
            androidx.compose.animation.AnimatedVisibility(
                visible = showZoomIndicator,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "%.1f×".format(currentZoomRatio),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ─── Overlay icons (hidden in draw mode) ────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = showOverlay && !isDrawMode,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top-right: Close
                    OverlayIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close video",
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )

                    // Top-left: Flash toggle (back camera only)
                    if (!useFrontCamera) {
                        OverlayIconButton(
                            icon = if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = "Toggle flash",
                            onClick = {
                                onFlashEnabledChange(!flashEnabled)
                                overlayResetKey++
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        )
                    }

                    // Bottom-left: Flip camera
                    OverlayIconButton(
                        icon = Icons.Outlined.FlipCameraAndroid,
                        contentDescription = "Flip camera",
                        onClick = {
                            onFlashEnabledChange(false)
                            onUseFrontCameraChange(!useFrontCamera)
                            overlayResetKey++
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )

                    // Bottom-right: Minimize
                    OverlayIconButton(
                        icon = Icons.Outlined.PictureInPicture,
                        contentDescription = "Minimize video",
                        onClick = { onMinimize() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * A translucent round icon button used as camera overlay control.
 */
@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Minimized video indicator — a small sticky icon (bottom-right, above input bar).
 * Tap to restore the full camera preview.
 */
@Composable
fun MinimizedVideoIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme
    val infiniteTransition = rememberInfiniteTransition(label = "videoPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = pulseAlpha * 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Videocam,
            contentDescription = "Restore video preview",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Composite annotation strokes onto a camera JPEG frame.
 * Maps stroke coordinates from the Compose view space → the actual image pixel space.
 */
private fun compositeAnnotationsOnFrame(
    jpegBytes: ByteArray,
    strokes: List<List<Offset>>,
    viewSize: IntSize,
    imageWidth: Int,
    imageHeight: Int
): ByteArray {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return jpegBytes
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            strokeWidth = 6f * (mutableBitmap.width.toFloat() / viewSize.width)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val scaleX = mutableBitmap.width.toFloat() / viewSize.width
        val scaleY = mutableBitmap.height.toFloat() / viewSize.height

        for (stroke in strokes) {
            if (stroke.size >= 2) {
                val path = android.graphics.Path()
                path.moveTo(stroke[0].x * scaleX, stroke[0].y * scaleY)
                for (i in 1 until stroke.size) {
                    path.lineTo(stroke[i].x * scaleX, stroke[i].y * scaleY)
                }
                canvas.drawPath(path, paint)
            }
        }

        val out = ByteArrayOutputStream()
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        bitmap.recycle()
        mutableBitmap.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        Log.e("CameraPreview", "Annotation compositing failed", e)
        jpegBytes
    }
}

/**
 * Convert an ImageProxy (YUV_420_888) to a compressed JPEG byte array.
 * Returns null if conversion fails.
 */
private fun imageProxyToJpeg(imageProxy: ImageProxy, quality: Int = 60): ByteArray? {
    return try {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            quality,
            out
        )
        out.toByteArray()
    } catch (e: Exception) {
        Log.e("CameraPreview", "YUV→JPEG conversion failed", e)
        null
    }
}
