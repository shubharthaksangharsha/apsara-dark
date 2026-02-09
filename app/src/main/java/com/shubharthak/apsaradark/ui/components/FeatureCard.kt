package com.shubharthak.apsaradark.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.data.MainFeature
import com.shubharthak.apsaradark.ui.theme.*

@Composable
fun FeatureCard(
    feature: MainFeature,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalThemeManager.current.currentTheme

    Card(
        modifier = modifier
            .height(48.dp)
            .border(
                width = 0.5.dp,
                color = palette.surfaceContainerHighest,
                shape = RoundedCornerShape(24.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = feature.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )
        }
    }
}
