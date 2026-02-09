package com.shubharthak.apsaradark.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.R
import com.shubharthak.apsaradark.data.DrawerItem
import com.shubharthak.apsaradark.data.MockData
import com.shubharthak.apsaradark.ui.theme.*

@Composable
fun AppDrawerContent(
    onItemClick: (DrawerItem) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalThemeManager.current.currentTheme

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(palette.surfaceContainer)
            .padding(top = 48.dp)
    ) {
        // Logo at top
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.apsara_logo),
                contentDescription = "Apsara",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        MockData.drawerItems.forEach { item ->
            DrawerMenuItem(item = item, onClick = { onItemClick(item) })
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    onClick: () -> Unit
) {
    val palette = LocalThemeManager.current.currentTheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = palette.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = palette.textPrimary
        )
    }
}
