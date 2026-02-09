package com.shubharthak.apsaradark.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.ui.theme.*

@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
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
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 42.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Separate + button
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

        // Input container — takes remaining space, full curve on right
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text field
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
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            if (value.isNotEmpty()) {
                // Send button
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
                // Mic button
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

                // Live / waveform button
                IconButton(
                    onClick = { /* TODO: live mode */ },
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
