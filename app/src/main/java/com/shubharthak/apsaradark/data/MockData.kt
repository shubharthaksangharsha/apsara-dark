package com.shubharthak.apsaradark.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

data class DrawerItem(
    val title: String,
    val icon: ImageVector
)

data class MainFeature(
    val title: String,
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
        MainFeature("Talk", Icons.Outlined.Mic),
        MainFeature("Design", Icons.Outlined.Palette),
        MainFeature("Control", Icons.Outlined.Tune),
        MainFeature("Reminders", Icons.Outlined.NotificationsActive),
    )
}
