package com.shubharthak.apsaradark.ui.components

import android.graphics.ImageFormat
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

/**
 * Full-width camera preview card with overlay icons for flash, flip, minimize, close.
 * - Tap anywhere to show a round focus ring animation + actually trigger CameraX autofocus.
 * - Icons auto-hide after 3 seconds of inactivity.
 * - Sends JPEG frames to the backend at a throttled interval.
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

    // Auto-hide overlay timer
    LaunchedEffect(overlayResetKey) {
        showOverlay = true
        delay(3000)
        showOverlay = false
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
                        onFrameCaptured(jpegBytes)
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
                // Apply current flash state
                camera.cameraControl.enableTorch(flashEnabled && !useFrontCamera)
                // Reset zoom to 1x on camera switch
                currentZoomRatio = 1f
                camera.cameraControl.setZoomRatio(1f)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera bind failed", e)
            }
        }, context.mainExecutor)
    }

    // Update torch when flash toggles — direct control via camera reference
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { viewSize = it }
            // Combined tap-to-focus + pinch-to-zoom
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val firstDownPos = firstDown.position
                    var isPinching = false

                    do {
                        val event = awaitPointerEvent()
                        val fingerCount = event.changes.count { it.pressed }

                        if (fingerCount >= 2) {
                            // Multi-finger → pinch-to-zoom
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

                    // If it was a simple single-finger tap (not a pinch), trigger focus
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
    ) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Focus ring animation
        if (focusPoint != null && focusAlpha > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                focusPoint?.let { point ->
                    drawCircle(
                        color = Color.White.copy(alpha = focusAlpha * 0.8f),
                        radius = 36.dp.toPx() * focusScale,
                        center = point,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx()
                        )
                    )
                }
            }
        }

        // Zoom level indicator (center-top pill)
        AnimatedVisibility(
            visible = showZoomIndicator,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp)
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

        // Overlay icons — animated visibility
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-right: Close button
                OverlayIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Close video",
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

                // Top-left: Flash toggle (only for back camera)
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
                        onFlashEnabledChange(false) // Reset flash on flip
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
                    onClick = {
                        onMinimize()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                )
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
    modifier: Modifier = Modifier
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
            tint = Color.White,
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
