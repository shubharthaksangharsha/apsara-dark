package com.shubharthak.apsaradark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.ui.theme.ApsaraColorPalette
import com.shubharthak.apsaradark.ui.theme.LocalThemeManager

/**
 * Attachment bottom sheet — shows Camera, Photos, Files options.
 * Minimalistic design matching the app's dark theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onFilesClick: () -> Unit
) {
    val palette = LocalThemeManager.current.currentTheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceContainer,
        contentColor = palette.textPrimary,
        dragHandle = {
            // Minimal drag handle
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.textTertiary.copy(alpha = 0.3f))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOption(
                icon = Icons.Outlined.PhotoCamera,
                label = "Camera",
                palette = palette,
                onClick = {
                    onCameraClick()
                    onDismiss()
                }
            )
            AttachmentOption(
                icon = Icons.Outlined.PhotoLibrary,
                label = "Photos",
                palette = palette,
                onClick = {
                    onPhotosClick()
                    onDismiss()
                }
            )
            AttachmentOption(
                icon = Icons.Outlined.AttachFile,
                label = "Files",
                palette = palette,
                onClick = {
                    onFilesClick()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    palette: ApsaraColorPalette,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = palette.textSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = palette.textSecondary
        )
    }
}
