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
    val description: String = ""
)

/**
 * Represents a canvas app created by Apsara Canvas.
 */
data class CanvasApp(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String = "generating", // generating | testing | fixing | ready | error
    val createdAt: String = "",
    val renderUrl: String = ""
)

/**
 * Full detail of a canvas app including code, prompt, and generation log.
 */
data class CanvasAppDetail(
    val id: String,
    val title: String,
    val description: String = "",
    val prompt: String = "",
    val status: String = "",
    val error: String? = null,
    val attempts: Int = 0,
    val html: String? = null,
    val htmlLength: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val generationLog: List<CanvasLogEntry> = emptyList()
)

data class CanvasLogEntry(
    val step: String,
    val timestamp: String,
    val message: String = "",
    val attempts: Int = 0
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
            description = "Returns server time, uptime, and system info"
        ),
        PluginInfo(
            id = "apsara_canvas",
            title = "Apsara Canvas",
            description = "Create, list, view, and edit web apps with HTML, CSS, JS & React"
        ),
    )
}
