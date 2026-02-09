package com.shubharthak.apsaradark.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.data.MockData
import com.shubharthak.apsaradark.live.ActiveSpeaker
import com.shubharthak.apsaradark.live.LiveMessage
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.components.*
import com.shubharthak.apsaradark.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    openDrawerOnReturn: Boolean = false,
    onNavigateToSettings: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme

    val context = LocalContext.current
    val liveSettings = LocalLiveSettings.current

    // LiveSessionViewModel — scoped to this screen
    val liveViewModel: LiveSessionViewModel = viewModel(
        factory = LiveSessionViewModel.Factory(context, liveSettings)
    )

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            liveViewModel.startLive()
        } else {
            Toast.makeText(context, "Microphone permission is required for Live mode", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to start live with permission check
    fun startLiveWithPermission() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            liveViewModel.startLive()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Open drawer when returning from Settings
    LaunchedEffect(openDrawerOnReturn) {
        if (openDrawerOnReturn) {
            drawerState.open()
        }
    }

    // Auto-focus the input field when the screen appears (only in normal mode)
    LaunchedEffect(liveViewModel.liveState) {
        if (liveViewModel.liveState == LiveSessionViewModel.LiveState.IDLE) {
            delay(300)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Show error toast
    LaunchedEffect(liveViewModel.lastError) {
        liveViewModel.lastError?.let { error ->
            Toast.makeText(context, "Live error: $error", Toast.LENGTH_LONG).show()
        }
    }

    val isLiveActive = liveViewModel.liveState != LiveSessionViewModel.LiveState.IDLE

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
                            onNavigateToSettings()
                        }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        gesturesEnabled = !isLiveActive, // Disable swipe gestures during live mode
        scrimColor = palette.surface.copy(alpha = 0.6f)
    ) {
        Scaffold(
            containerColor = palette.surface,
            modifier = Modifier.imePadding(),
            topBar = {
                AnimatedVisibility(
                    visible = !isLiveActive,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
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
                }
            },
            bottomBar = {
                BottomInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = { inputText = "" },
                    onMicClick = { /* TODO: voice-to-text input */ },
                    onAttachClick = { /* TODO: attach menu */ },
                    onLiveClick = { startLiveWithPermission() },
                    onLiveEnd = { liveViewModel.stopLive() },
                    onLiveMuteToggle = { liveViewModel.toggleMute() },
                    onLiveTextSend = { text -> liveViewModel.sendText(text) },
                    liveState = liveViewModel.liveState,
                    isMuted = liveViewModel.isMuted,
                    activeSpeaker = liveViewModel.activeSpeaker,
                    focusRequester = focusRequester
                )
            }
        ) { paddingValues ->
            // Content switches between normal (feature cards) and live mode (waveform/transcript)
            AnimatedContent(
                targetState = isLiveActive,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "homeContent"
            ) { live ->
                if (live) {
                    // ─── Live Mode Content ──────────────────────────────────
                    LiveModeContent(
                        liveState = liveViewModel.liveState,
                        messages = liveViewModel.messages,
                        showInput = liveSettings.inputTranscription,
                        showOutput = liveSettings.outputTranscription,
                        palette = palette,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                } else {
                    // ─── Normal Mode Content ────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

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
                                        modifier = Modifier.weight(1f),
                                        onClick = if (feature.title == "Talk") {
                                            { startLiveWithPermission() }
                                        } else null
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

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── Live Mode UI — Chat-style with transcription bubbles ───────────────────

@Composable
private fun LiveModeContent(
    liveState: LiveSessionViewModel.LiveState,
    messages: List<LiveMessage>,
    showInput: Boolean,
    showOutput: Boolean,
    palette: ApsaraColorPalette,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Filter messages based on transcription settings
    val visibleMessages = messages.filter { msg ->
        when (msg.role) {
            LiveMessage.Role.USER -> showInput
            LiveMessage.Role.APSARA -> showOutput
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.text) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    Box(modifier = modifier) {
        if (liveState == LiveSessionViewModel.LiveState.CONNECTING) {
            // Connecting state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting…",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textSecondary
                    )
                }
            }
        } else if (visibleMessages.isEmpty()) {
            // No messages yet — show "Start talking" placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Start talking",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textTertiary,
                        letterSpacing = (-0.2).sp
                    )
                    if (!showInput && !showOutput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transcription is off",
                            fontSize = 13.sp,
                            color = palette.textTertiary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            // Chat bubbles
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                items(visibleMessages, key = null) { message ->
                    when (message.role) {
                        LiveMessage.Role.USER -> UserBubble(
                            text = message.text,
                            palette = palette
                        )
                        LiveMessage.Role.APSARA -> ApsaraBubble(
                            text = message.text,
                            isStreaming = message.isStreaming,
                            palette = palette
                        )
                    }
                }
            }
        }
    }
}

// ─── User chat bubble — right-aligned ───────────────────────────────────────

@Composable
private fun UserBubble(
    text: String,
    palette: ApsaraColorPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(palette.accent.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                color = palette.textPrimary,
                lineHeight = 21.sp
            )
        }
    }
}

// ─── Apsara output — plain text, no bubble, no animation ────────────────────

@Composable
private fun ApsaraBubble(
    text: String,
    isStreaming: Boolean,
    palette: ApsaraColorPalette
) {
    // Plain text — no bubble, no border, no streaming cursor
    // Text is shown as soon as received (async), not waiting for turn complete
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = palette.textPrimary,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}
