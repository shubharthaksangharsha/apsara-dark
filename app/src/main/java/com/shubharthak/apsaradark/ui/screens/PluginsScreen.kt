package com.shubharthak.apsaradark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
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
            // Header
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tools & Plugins",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                    Text(
                        text = "Enable tools below to let Apsara use them during live sessions. When a tool is enabled, Apsara can automatically invoke it when relevant.",
                        fontSize = 13.sp,
                        color = palette.textTertiary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Plugin cards
            items(MockData.availablePlugins) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isEnabled = when (plugin.id) {
                        "get_server_info" -> liveSettings.toolServerInfo
                        "calculate" -> liveSettings.toolCalculate
                        "get_random_fact" -> liveSettings.toolRandomFact
                        else -> false
                    },
                    onToggle = { enabled ->
                        when (plugin.id) {
                            "get_server_info" -> liveSettings.updateToolServerInfo(enabled)
                            "calculate" -> liveSettings.updateToolCalculate(enabled)
                            "get_random_fact" -> liveSettings.updateToolRandomFact(enabled)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isEnabled) palette.accent.copy(alpha = 0.12f)
                        else palette.textTertiary.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = plugin.icon,
                    contentDescription = plugin.title,
                    tint = if (isEnabled) palette.accent else palette.textTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = plugin.description,
                    fontSize = 12.sp,
                    color = palette.textTertiary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle switch
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
