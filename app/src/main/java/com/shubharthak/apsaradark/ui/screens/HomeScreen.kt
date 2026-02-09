package com.shubharthak.apsaradark.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.R
import com.shubharthak.apsaradark.data.MockData
import com.shubharthak.apsaradark.ui.components.*
import com.shubharthak.apsaradark.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.surfaceContainer,
                drawerContentColor = palette.textPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                AppDrawerContent(
                    onItemClick = { item ->
                        scope.launch { drawerState.close() }
                        if (item.title == "Settings") {
                            showSettings = true
                        }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        gesturesEnabled = true,
        scrimColor = palette.surface.copy(alpha = 0.6f)
    ) {
        Scaffold(
            containerColor = palette.surface,
            topBar = {
                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                val shimmerOffset by infiniteTransition.animateFloat(
                    initialValue = -200f,
                    targetValue = 600f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmerOffset"
                )
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        palette.textPrimary,
                        palette.accent,
                        palette.textPrimary
                    ),
                    start = Offset(shimmerOffset, 0f),
                    end = Offset(shimmerOffset + 200f, 0f)
                )

                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.apsara_logo),
                                contentDescription = "Apsara Logo",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Apsara Dark",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(brush = shimmerBrush),
                                letterSpacing = 0.2.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "Menu",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.surface,
                        titleContentColor = palette.textPrimary
                    )
                )
            },
            bottomBar = {
                BottomInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = { inputText = "" },
                    onMicClick = { /* TODO: voice input */ },
                    onAttachClick = { /* TODO: attachments */ }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Good evening,",
                            fontSize = 14.sp,
                            color = palette.textTertiary,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = "Shubharthak",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MockData.mainFeatures.take(2).forEach { feature ->
                                FeatureCard(
                                    feature = feature,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MockData.mainFeatures.drop(2).forEach { feature ->
                                FeatureCard(
                                    feature = feature,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
