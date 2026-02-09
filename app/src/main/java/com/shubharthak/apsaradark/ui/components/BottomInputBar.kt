package com.shubharthak.apsaradark.ui.components

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shubharthak.apsaradark.live.ActiveSpeaker
import com.shubharthak.apsaradark.live.AudioOutputDevice
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.theme.*
import kotlin.math.sin

/**
 * BottomInputBar — Live mode only.
 * Shows the live input bar when a live session is active, nothing when idle.
 * Users start live mode via the "Talk" feature card on the home screen.
 */
@Composable
fun BottomInputBar(
    onAttachClick: () -> Unit,
    onLiveEnd: () -> Unit,
    onLiveMuteToggle: () -> Unit,
    onLiveTextSend: (String) -> Unit,
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    activeSpeaker: ActiveSpeaker = ActiveSpeaker.NONE,
    inputAmplitude: Float = 0f,
    outputAmplitude: Float = 0f,
    currentAudioDevice: AudioOutputDevice = AudioOutputDevice.LOUDSPEAKER,
    onAudioDeviceChange: (AudioOutputDevice) -> Unit = {},
    hasBluetooth: Boolean = false,
    pastedImages: List<Uri> = emptyList(),
    onImagePasted: (Uri) -> Unit = {},
    onImageRemoved: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLiveActive = liveState != LiveSessionViewModel.LiveState.IDLE

    AnimatedVisibility(
        visible = isLiveActive,
        enter = fadeIn(tween(200)) + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
    ) {
        LiveModeBar(
            liveState = liveState,
            isMuted = isMuted,
            activeSpeaker = activeSpeaker,
            inputAmplitude = inputAmplitude,
            outputAmplitude = outputAmplitude,
            currentAudioDevice = currentAudioDevice,
            onAudioDeviceChange = onAudioDeviceChange,
            hasBluetooth = hasBluetooth,
            onEnd = onLiveEnd,
            onMuteToggle = onLiveMuteToggle,
            onTextSend = onLiveTextSend,
            onAttachClick = onAttachClick,
            pastedImages = pastedImages,
            onImagePasted = onImagePasted,
            onImageRemoved = onImageRemoved,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveModeBar(
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    activeSpeaker: ActiveSpeaker,
    inputAmplitude: Float,
    outputAmplitude: Float,
    currentAudioDevice: AudioOutputDevice,
    onAudioDeviceChange: (AudioOutputDevice) -> Unit,
    hasBluetooth: Boolean,
    onEnd: () -> Unit,
    onMuteToggle: () -> Unit,
    onTextSend: (String) -> Unit,
    onAttachClick: () -> Unit,
    pastedImages: List<Uri>,
    onImagePasted: (Uri) -> Unit,
    onImageRemoved: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme
    var liveText by remember { mutableStateOf("") }
    var showAudioDeviceMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
    ) {
        // ─── Image preview strip for live mode ──────────────────────
        AnimatedVisibility(
            visible = pastedImages.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pastedImages.forEach { uri ->
                    PastedImageThumbnail(
                        uri = uri,
                        palette = palette,
                        onRemove = { onImageRemoved(uri) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // + button
        IconButton(
            onClick = onAttachClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(palette.surfaceContainerHigh)
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Attach",
                tint = palette.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Type field
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceContainer)
                .border(0.5.dp, palette.surfaceContainerHighest, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                // Rich content EditText — tells keyboard we accept images
                RichContentEditText(
                    value = liveText,
                    onValueChange = { liveText = it },
                    onImageReceived = onImagePasted,
                    onFocusChange = { /* no-op in live mode */ },
                    placeholder = "Type",
                    textColor = palette.textPrimary,
                    hintColor = palette.textTertiary,
                    cursorColor = palette.accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (liveText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onTextSend(liveText)
                        liveText = ""
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "Send",
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Mic mute/unmute — tap to mute, long-press to switch audio output device
        Box {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceContainerHigh)
                    .combinedClickable(
                        onClick = onMuteToggle,
                        onLongClick = { showAudioDeviceMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (liveState == LiveSessionViewModel.LiveState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = palette.accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    // Show icon based on current audio device + mute state
                    val icon = when {
                        isMuted -> Icons.Filled.MicOff
                        currentAudioDevice == AudioOutputDevice.BLUETOOTH -> Icons.Outlined.BluetoothAudio
                        currentAudioDevice == AudioOutputDevice.EARPIECE -> Icons.Outlined.PhoneInTalk
                        else -> Icons.Filled.Mic // Loudspeaker (default)
                    }
                    Icon(
                        icon,
                        contentDescription = if (isMuted) "Unmute" else "Audio: $currentAudioDevice",
                        tint = if (isMuted) palette.textTertiary else palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Audio output device picker dropdown — offset left so it doesn't clip off-screen
            DropdownMenu(
                expanded = showAudioDeviceMenu,
                onDismissRequest = { showAudioDeviceMenu = false },
                offset = DpOffset(x = (-120).dp, y = 0.dp),
                modifier = Modifier.background(palette.surfaceContainer)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Loudspeaker",
                            color = if (currentAudioDevice == AudioOutputDevice.LOUDSPEAKER) palette.accent else palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (currentAudioDevice == AudioOutputDevice.LOUDSPEAKER) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = if (currentAudioDevice == AudioOutputDevice.LOUDSPEAKER) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onAudioDeviceChange(AudioOutputDevice.LOUDSPEAKER)
                        showAudioDeviceMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Earpiece",
                            color = if (currentAudioDevice == AudioOutputDevice.EARPIECE) palette.accent else palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (currentAudioDevice == AudioOutputDevice.EARPIECE) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PhoneInTalk,
                            contentDescription = null,
                            tint = if (currentAudioDevice == AudioOutputDevice.EARPIECE) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onAudioDeviceChange(AudioOutputDevice.EARPIECE)
                        showAudioDeviceMenu = false
                    }
                )
                if (hasBluetooth) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Bluetooth",
                                color = if (currentAudioDevice == AudioOutputDevice.BLUETOOTH) palette.accent else palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (currentAudioDevice == AudioOutputDevice.BLUETOOTH) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.BluetoothAudio,
                                contentDescription = null,
                                tint = if (currentAudioDevice == AudioOutputDevice.BLUETOOTH) palette.accent else palette.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            onAudioDeviceChange(AudioOutputDevice.BLUETOOTH)
                            showAudioDeviceMenu = false
                        }
                    )
                }
            }
        }

        // Mini Visualizer + End button combined
        Button(
            onClick = onEnd,
            modifier = Modifier.height(42.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            contentPadding = PaddingValues(start = 10.dp, end = 16.dp)
        ) {
            // Mini live visualizer — left of "End"
            MiniVisualizer(
                activeSpeaker = activeSpeaker,
                isConnecting = liveState == LiveSessionViewModel.LiveState.CONNECTING,
                amplitude = when (activeSpeaker) {
                    ActiveSpeaker.APSARA -> outputAmplitude
                    ActiveSpeaker.USER -> inputAmplitude
                    ActiveSpeaker.NONE -> 0f
                },
                accentColor = palette.surface,
                apsaraColor = palette.surface,
                userColor = palette.surface.copy(alpha = 0.6f),
                modifier = Modifier.size(width = 24.dp, height = 20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "End",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.surface
            )
        }
    }
    }
}

// ─── Mini Visualizer — amplitude-driven bars that respond to actual audio ────

@Composable
private fun MiniVisualizer(
    activeSpeaker: ActiveSpeaker,
    isConnecting: Boolean,
    amplitude: Float, // 0.0..1.0 from real audio RMS
    accentColor: Color,
    apsaraColor: Color,
    userColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vizBars")

    // Phase offsets for organic movement — these keep the bars from moving in lockstep
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Smoothly animate the amplitude for visual smoothness
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "amplitude"
    )

    val isActive = activeSpeaker != ActiveSpeaker.NONE && !isConnecting
    val barColor = when (activeSpeaker) {
        ActiveSpeaker.APSARA -> apsaraColor
        ActiveSpeaker.USER -> userColor
        ActiveSpeaker.NONE -> accentColor.copy(alpha = 0.5f)
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / 5f
        val gap = barWidth * 0.5f
        val maxHeight = size.height * 0.95f
        val minHeight = size.height * 0.12f
        val centerY = size.height / 2f

        val phases = listOf(phase1, phase2, phase3)
        val totalBarsWidth = 3 * barWidth + 2 * gap
        val startX = (size.width - totalBarsWidth) / 2f

        phases.forEachIndexed { i, phase ->
            val height = if (isActive && animatedAmplitude > 0.01f) {
                // Amplitude drives the height, phase offset adds organic variation
                val variation = (sin(phase + i * 1.5f) + 1f) / 2f // 0..1
                // Boost amplitude: apply power curve to make movement more dramatic
                val boosted = (animatedAmplitude * 1.5f).coerceIn(0f, 1f)
                // Mix: amplitude controls overall scale, variation adds per-bar movement
                minHeight + boosted * (maxHeight - minHeight) * (0.3f + 0.7f * variation)
            } else {
                minHeight
            }
            val x = startX + i * (barWidth + gap)
            val top = centerY - height / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

// ─── Rich content EditText — enables keyboard image paste ───────────────────

/**
 * A Compose wrapper around an Android EditText that declares image MIME type support.
 * This tells the keyboard (Gboard, Samsung Keyboard, etc.) that we accept images,
 * so it shows "Paste" for copied images in the clipboard strip.
 */
@Composable
private fun RichContentEditText(
    value: String,
    onValueChange: (String) -> Unit,
    onImageReceived: (Uri) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    placeholder: String,
    textColor: Color,
    hintColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Keep a ref to sync text changes from Compose → EditText without looping
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnImageReceived = rememberUpdatedState(onImageReceived)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            object : EditText(ctx) {
                override fun onCreateInputConnection(outAttrs: EditorInfo): android.view.inputmethod.InputConnection? {
                    val ic = super.onCreateInputConnection(outAttrs) ?: return null
                    // Declare that we accept image content
                    EditorInfoCompat.setContentMimeTypes(outAttrs, arrayOf("image/png", "image/jpeg", "image/gif", "image/*"))
                    return InputConnectionCompat.createWrapper(ic, outAttrs) { inputContentInfo, flags, _ ->
                        try {
                            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                                try { inputContentInfo.requestPermission() } catch (_: Exception) { }
                            }
                            currentOnImageReceived.value(inputContentInfo.contentUri)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }.apply {
                // Style to match the app
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setTextColor(textColor.toArgb())
                setHintTextColor(hintColor.toArgb())
                hint = placeholder
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                letterSpacing = 0.012f
                maxLines = 4
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(0, 0, 0, 0)
                isSingleLine = false

                // Cursor color
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    textCursorDrawable?.setTint(cursorColor.toArgb())
                }

                // Text change listener
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val newText = s?.toString() ?: ""
                        if (newText != currentValue.value) {
                            currentOnValueChange.value(newText)
                        }
                    }
                })

                // Focus listener
                setOnFocusChangeListener { _, hasFocus ->
                    onFocusChange(hasFocus)
                }

                // Also set up ViewCompat receive content listener for Android 12+ drag & drop / paste
                ViewCompat.setOnReceiveContentListener(
                    this,
                    arrayOf("image/*")
                ) { _, payload ->
                    val clip = payload.clip
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { uri ->
                            currentOnImageReceived.value(uri)
                        }
                    }
                    null // consumed
                }
            }
        },
        update = { editText ->
            // Sync text from Compose state → EditText, avoid infinite loop
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
            // Update hint
            editText.hint = placeholder
            // Update colors
            editText.setTextColor(textColor.toArgb())
            editText.setHintTextColor(hintColor.toArgb())
        }
    )
}

// ─── Pasted image thumbnail with remove button ──────────────────────────────

@Composable
private fun PastedImageThumbnail(
    uri: Uri,
    palette: ApsaraColorPalette,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(72.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = "Pasted image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainerHigh)
        )
        // Remove button — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(palette.surface.copy(alpha = 0.85f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove image",
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

