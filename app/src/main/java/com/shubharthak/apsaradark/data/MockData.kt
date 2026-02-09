package com.shubharthak.apsaradark.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

data class DrawerItem(
    val title: String,
    val icon: ImageVector
)

data class MainFeature(
    val title: String
)

data class PluginInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

object MockData {

    val drawerItems = listOf(
        DrawerItem("My Canvas", Icons.Outlined.Dashboard),
        DrawerItem("My Plugins", Icons.Outlined.Extension),
        DrawerItem("Laptop Control", Icons.Outlined.Computer),
        DrawerItem("Settings", Icons.Outlined.Settings),
    )

    val mainFeatures = listOf(
        MainFeature("Talk"),
        MainFeature("Design"),
        MainFeature("Control"),
        MainFeature("Reminders"),
    )

    val availablePlugins = listOf(
        PluginInfo(
            id = "get_server_info",
            title = "Server Info",
            description = "Get current server time, uptime, and system info",
            icon = Icons.Outlined.Info
        ),
        PluginInfo(
            id = "calculate",
            title = "Calculator",
            description = "Perform arithmetic calculations (ask Apsara to calculate something)",
            icon = Icons.Outlined.Calculate
        ),
        PluginInfo(
            id = "get_random_fact",
            title = "Fun Facts",
            description = "Get a random fun fact or trivia",
            icon = Icons.Outlined.Lightbulb
        ),
    )
}
