package com.shubharthak.apsaradark.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

data class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

data class MainFeature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: String
)

object MockData {

    val drawerItems = listOf(
        DrawerItem("My Canvas", Icons.Outlined.Dashboard),
        DrawerItem("Latest Videos", Icons.Outlined.VideoLibrary),
        DrawerItem("My Plugins", Icons.Outlined.Extension, badge = "3"),
        DrawerItem("Laptop Control", Icons.Outlined.Computer),
        DrawerItem("Settings", Icons.Outlined.Settings),
    )

    val mainFeatures = listOf(
        MainFeature("Talk", "Voice conversations with Apsara", Icons.Outlined.Mic),
        MainFeature("Design", "Generate & edit visuals", Icons.Outlined.Palette),
        MainFeature("Control", "Manage your devices", Icons.Outlined.Tune),
        MainFeature("Reminders", "Never miss a thing", Icons.Outlined.NotificationsActive),
    )

    val recentChats = listOf(
        ChatMessage(
            "Hey Apsara, remind me to push the new build at 6 PM.",
            isUser = true,
            timestamp = "2 min ago"
        ),
        ChatMessage(
            "Sure! I've set a reminder for 6:00 PM — \"Push the new build\". I'll nudge you.",
            isUser = false,
            timestamp = "2 min ago"
        ),
        ChatMessage(
            "Also, can you dim my laptop screen to 40%?",
            isUser = true,
            timestamp = "1 min ago"
        ),
        ChatMessage(
            "Done. Laptop brightness set to 40%. Anything else?",
            isUser = false,
            timestamp = "just now"
        ),
    )
}
