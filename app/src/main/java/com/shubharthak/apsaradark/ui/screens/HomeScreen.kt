package com.shubharthak.apsaradark.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceContainer,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                AppDrawerContent(
                    onItemClick = {
                        scope.launch { drawerState.close() }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        gesturesEnabled = true,
        scrimColor = SurfaceDark.copy(alpha = 0.6f)
    ) {
        Scaffold(
            containerColor = SurfaceDark,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "A",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Purple80
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Apsara Dark",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    letterSpacing = 0.2.sp
                                )
                                Text(
                                    "Online · Ready",
                                    fontSize = 12.sp,
                                    color = StatusGreen,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "Menu",
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        titleContentColor = TextPrimary
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
                // Greeting section
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
                            color = TextTertiary,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = "Shubharthak",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Feature grid
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

                // Recent conversation
                item {
                    Text(
                        text = "Recent",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                items(MockData.recentChats) { message ->
                    ChatBubble(message = message)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
