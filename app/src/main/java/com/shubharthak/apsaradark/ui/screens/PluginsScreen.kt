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
            // Tool cards — each has enable toggle + async/sync toggle
            items(MockData.availablePlugins) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isEnabled = when (plugin.id) {
                        "get_server_info" -> liveSettings.toolServerInfo
                        "apsara_canvas" -> liveSettings.toolCanvas
                        "apsara_interpreter" -> liveSettings.toolInterpreter
                        else -> false
                    },
                    isAsync = when (plugin.id) {
                        "get_server_info" -> liveSettings.toolServerInfoAsync
                        "apsara_canvas" -> liveSettings.toolCanvasAsync
                        "apsara_interpreter" -> liveSettings.toolInterpreterAsync
                        else -> false
                    },
                    onToggle = { enabled ->
                        when (plugin.id) {
                            "get_server_info" -> liveSettings.updateToolServerInfo(enabled)
                            "apsara_canvas" -> liveSettings.updateToolCanvas(enabled)
                            "apsara_interpreter" -> liveSettings.updateToolInterpreter(enabled)
                        }
                    },
                    onAsyncToggle = { async ->
                        when (plugin.id) {
                            "get_server_info" -> liveSettings.updateToolServerInfoAsync(async)
                            "apsara_canvas" -> liveSettings.updateToolCanvasAsync(async)
                            "apsara_interpreter" -> liveSettings.updateToolInterpreterAsync(async)
                        }
                    },
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
    isAsync: Boolean,
    onToggle: (Boolean) -> Unit,
    onAsyncToggle: (Boolean) -> Unit,
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
            // Row 1: Tool name + enable/disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary
                    )
                    if (plugin.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = plugin.description,
                            fontSize = 12.sp,
                            color = palette.textTertiary
                        )
                    }
                }

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

            // Row 2: Sync / Async toggle — only show when tool is enabled
            if (isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = isAsync,
                        onCheckedChange = onAsyncToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.surface,
                            checkedTrackColor = palette.accent,
                            uncheckedThumbColor = palette.surface,
                            uncheckedTrackColor = palette.accent.copy(alpha = 0.5f),
                            uncheckedBorderColor = palette.accent.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

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
}
