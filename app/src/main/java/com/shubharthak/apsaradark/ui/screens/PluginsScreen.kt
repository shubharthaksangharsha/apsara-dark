package com.shubharthak.apsaradark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.data.MockData
import com.shubharthak.apsaradark.data.PluginInfo
import com.shubharthak.apsaradark.ui.theme.ApsaraColorPalette
import com.shubharthak.apsaradark.ui.theme.LocalThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onBack: () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme
    val liveSettings = LocalLiveSettings.current

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Plugins",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Tool toggles — simple rows, no icons, no descriptions
            items(MockData.availablePlugins) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isEnabled = when (plugin.id) {
                        "get_server_info" -> liveSettings.toolServerInfo
                        else -> false
                    },
                    onToggle = { enabled ->
                        when (plugin.id) {
                            "get_server_info" -> liveSettings.updateToolServerInfo(enabled)
                        }
                    },
                    palette = palette
                )
            }

            // Async / Sync slider
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FunctionCallModeCard(
                    isAsync = liveSettings.asyncFunctionCalls,
                    onToggle = { liveSettings.updateAsyncFunctionCalls(it) },
                    palette = palette
                )
            }

            // Footer note
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Changes take effect on the next live session.",
                    fontSize = 12.sp,
                    color = palette.textTertiary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginInfo,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    palette: ApsaraColorPalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plugin.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.surface,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.textTertiary,
                    uncheckedTrackColor = palette.surfaceContainer,
                    uncheckedBorderColor = palette.textTertiary.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun FunctionCallModeCard(
    isAsync: Boolean,
    onToggle: (Boolean) -> Unit,
    palette: ApsaraColorPalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Function Call Mode",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync",
                    fontSize = 13.sp,
                    fontWeight = if (!isAsync) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!isAsync) palette.accent else palette.textTertiary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isAsync,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.surface,
                        checkedTrackColor = palette.accent,
                        uncheckedThumbColor = palette.surface,
                        uncheckedTrackColor = palette.accent.copy(alpha = 0.5f),
                        uncheckedBorderColor = palette.accent.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Async",
                    fontSize = 13.sp,
                    fontWeight = if (isAsync) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isAsync) palette.accent else palette.textTertiary
                )
            }
        }
    }
}
