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
        targetValue = if (isFocused) palette.accent.copy(alpha = 0.4f) else palette.surfaceContainerHighest,
        label = "borderColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Attach",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask Apsara anything…",
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
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.accentSubtle)
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice",
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
