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

/**
 * Represents a code execution session from the Apsara Interpreter.
 */
data class CodeSession(
    val id: String,
    val title: String,
    val prompt: String = "",
    val status: String = "running", // running | completed | error
    val hasImages: Boolean = false,
    val imageCount: Int = 0,
    val createdAt: String = ""
)

/**
 * Full detail of a code session including code, output, and images.
 */
data class CodeSessionDetail(
    val id: String,
    val title: String,
    val prompt: String = "",
    val code: String = "",
    val output: String = "",
    val images: List<CodeSessionImage> = emptyList(),
    val status: String = "",
    val error: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class CodeSessionImage(
    val index: Int,
    val mimeType: String = "image/png",
    val url: String = ""
)

object MockData {

    val drawerItems = listOf(
        DrawerItem("My Canvas", Icons.Outlined.Dashboard),
        DrawerItem("My Code", Icons.Outlined.Code),
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
        PluginInfo(
            id = "apsara_interpreter",
            title = "Apsara Interpreter",
            description = "Execute Python code, create visualizations, and analyze data"
        ),
    )
}
