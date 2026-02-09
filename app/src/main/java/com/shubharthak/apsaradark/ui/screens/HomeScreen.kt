package com.shubharthak.apsaradark.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                TopAppBar(
                    title = { },
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
                        containerColor = palette.surface
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
            // Center content vertically
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Headline
                Text(
                    text = "Apsara is here for you!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    letterSpacing = (-0.2).sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Feature grid — 2x2
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
            }
        }
    }
}
