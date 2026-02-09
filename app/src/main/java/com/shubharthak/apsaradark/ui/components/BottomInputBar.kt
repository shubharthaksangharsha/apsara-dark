package com.shubharthak.apsaradark.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.theme.*

/**
 * BottomInputBar with two modes:
 * 1. Normal mode — text input + mic + live icon (default, when liveState == IDLE)
 * 2. Live mode  — ONLY when user taps the Live (GraphicEq) icon
 *                  Shows: + button, Type field, mic mute/unmute, End button (like ChatGPT)
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

// ─── Live mode — ChatGPT-style: + | Type | Mic | End ────────────────────────

@Composable
private fun LiveModeBar(
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    onEnd: () -> Unit,
    onMuteToggle: () -> Unit,
    onTextSend: (String) -> Unit,
    onAttachClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme
    var liveText by remember { mutableStateOf("") }

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

        // Mic mute/unmute — shows spinner while connecting
        IconButton(
            onClick = onMuteToggle,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(palette.surfaceContainerHigh)
        ) {
            if (liveState == LiveSessionViewModel.LiveState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) palette.textTertiary else palette.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // End button
        Button(
            onClick = onEnd,
            modifier = Modifier.height(42.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Outlined.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = palette.surface
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
