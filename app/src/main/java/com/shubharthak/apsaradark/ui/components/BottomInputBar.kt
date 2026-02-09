package com.shubharthak.apsaradark.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.live.ActiveSpeaker
import com.shubharthak.apsaradark.live.AudioOutputDevice
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.theme.*
import kotlin.math.sin

/**
 * BottomInputBar with two modes:
 * 1. Normal mode — text input + mic + live icon (default, when liveState == IDLE)
 * 2. Live mode  — ONLY when user taps the Live (GraphicEq) icon
 *                  Shows: + button, Type field, [visualizer] End button, mic mute/unmute
 */
@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onLiveClick: () -> Unit,
    onLiveEnd: () -> Unit,
    onLiveMuteToggle: () -> Unit,
    onLiveTextSend: (String) -> Unit,
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    activeSpeaker: ActiveSpeaker = ActiveSpeaker.NONE,
    currentAudioDevice: AudioOutputDevice = AudioOutputDevice.LOUDSPEAKER,
    onAudioDeviceChange: (AudioOutputDevice) -> Unit = {},
    hasBluetooth: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    // Live mode only when NOT idle
    val isLiveActive = liveState != LiveSessionViewModel.LiveState.IDLE

    AnimatedContent(
        targetState = isLiveActive,
        transitionSpec = {
            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
        },
        label = "inputBarMode"
    ) { live ->
        if (live) {
            LiveModeBar(
                liveState = liveState,
                isMuted = isMuted,
                activeSpeaker = activeSpeaker,
                currentAudioDevice = currentAudioDevice,
                onAudioDeviceChange = onAudioDeviceChange,
                hasBluetooth = hasBluetooth,
                onEnd = onLiveEnd,
                onMuteToggle = onLiveMuteToggle,
                onTextSend = onLiveTextSend,
                onAttachClick = onAttachClick,
                modifier = modifier
            )
        } else {
            NormalModeBar(
                value = value,
                onValueChange = onValueChange,
                onSend = onSend,
                onMicClick = onMicClick,
                onAttachClick = onAttachClick,
                onLiveClick = onLiveClick,
                focusRequester = focusRequester,
                modifier = modifier
            )
        }
    }
}

// ─── Normal mode — identical to old design, Live icon now wired ─────────────

@Composable
private fun NormalModeBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onLiveClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) palette.accent.copy(alpha = 0.3f) else palette.surfaceContainerHighest,
        label = "borderColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        // Input container
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceContainer)
                .border(0.5.dp, borderColor, RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask Apsara",
                        fontSize = 15.sp,
                        color = palette.textTertiary,
                        letterSpacing = 0.2.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = palette.textPrimary,
                        letterSpacing = 0.2.sp
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            if (value.isNotEmpty()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "Send",
                        tint = palette.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // Mic button (transcript — future)
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Live button — THIS is the only trigger for live mode
                IconButton(
                    onClick = onLiveClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceContainerHigh)
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Live",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─── Live mode — ChatGPT-style: + | Type | Mic | [Visualizer] End ───────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveModeBar(
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    activeSpeaker: ActiveSpeaker,
    currentAudioDevice: AudioOutputDevice,
    onAudioDeviceChange: (AudioOutputDevice) -> Unit,
    hasBluetooth: Boolean,
    onEnd: () -> Unit,
    onMuteToggle: () -> Unit,
    onTextSend: (String) -> Unit,
    onAttachClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme
    var liveText by remember { mutableStateOf("") }
    var showAudioDeviceMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
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
                if (liveText.isEmpty()) {
                    Text(
                        text = "Type",
                        fontSize = 14.sp,
                        color = palette.textTertiary
                    )
                }
                BasicTextField(
                    value = liveText,
                    onValueChange = { liveText = it },
                    textStyle = TextStyle(fontSize = 14.sp, color = palette.textPrimary),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = true,
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

// ─── Mini Visualizer — animated bars that change color by speaker ────────────

@Composable
private fun MiniVisualizer(
    activeSpeaker: ActiveSpeaker,
    isConnecting: Boolean,
    accentColor: Color,
    apsaraColor: Color,
    userColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vizBars")
    
    // 3 bars with different phase offsets
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f, // 2π
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bar3"
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
        val maxHeight = size.height * 0.85f
        val minHeight = size.height * 0.2f
        val centerY = size.height / 2f

        val bars = listOf(bar1, bar2, bar3)
        val totalBarsWidth = 3 * barWidth + 2 * gap
        val startX = (size.width - totalBarsWidth) / 2f

        bars.forEachIndexed { i, phase ->
            val height = if (isActive) {
                val normalized = (sin(phase + i * 1.2f) + 1f) / 2f // 0..1
                minHeight + normalized * (maxHeight - minHeight)
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
