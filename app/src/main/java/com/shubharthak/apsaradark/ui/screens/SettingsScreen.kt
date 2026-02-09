package com.shubharthak.apsaradark.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme
    var themesExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (themesExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "arrow"
    )

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Themes — expandable header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { themesExpanded = !themesExpanded }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Themes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (themesExpanded) "Collapse" else "Expand",
                        tint = palette.textTertiary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            // Themes grid — collapsible
            item {
                AnimatedVisibility(
                    visible = themesExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
                ) {
                    val themes = AppThemes.all
                    val rows = themes.chunked(2)

                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rows.forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowThemes.forEach { theme ->
                                    ThemeChip(
                                        name = theme.name,
                                        isSelected = theme.name == palette.name,
                                        onClick = { themeManager.setTheme(theme) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Fill empty space if odd number
                                if (rowThemes.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ThemeChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) palette.accentSubtle
                else palette.surfaceContainer
            )
            .border(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) palette.accent.copy(alpha = 0.5f)
                else palette.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) palette.accent else palette.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}
