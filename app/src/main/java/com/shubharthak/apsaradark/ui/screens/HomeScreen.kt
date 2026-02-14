                                                                                                                                                                                                                package com.shubharthak.apsaradark.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shubharthak.apsaradark.MainActivity
import com.shubharthak.apsaradark.R
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.data.MockData
import com.shubharthak.apsaradark.live.ActiveSpeaker
import com.shubharthak.apsaradark.live.CodeImageInfo
import com.shubharthak.apsaradark.live.EmbeddedToolCall
import com.shubharthak.apsaradark.live.LiveMessage
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.components.*
import com.shubharthak.apsaradark.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    liveViewModel: LiveSessionViewModel,
    openDrawerOnReturn: Boolean = false,
    startInLiveMode: Boolean = false,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToCanvas: () -> Unit = {},
    onNavigateToCanvasApp: (String) -> Unit = {},
    onNavigateToInterpreter: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showLiveAttachmentSheet by remember { mutableStateOf(false) }
    var pastedImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    // Video preview state: expanded, minimized, or hidden
    var videoMinimized by remember { mutableStateOf(false) }
    // Whether we should show the camera (tracks ViewModel's isVideoActive)
    val isVideoActive = liveViewModel.isVideoActive

    // Hoisted camera settings — survive minimize/restore cycles
    var cameraUseFront by remember { mutableStateOf(true) }
    var cameraFlashEnabled by remember { mutableStateOf(false) }

    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme

    val context = LocalContext.current
    val liveSettings = LocalLiveSettings.current

    // LiveSessionViewModel — now hoisted to Activity scope via Navigation.kt
    // Survives navigation between Home, Settings, and Plugins screens.

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

    // Camera permission launcher — for live video feature
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            videoMinimized = false
            liveViewModel.startVideo()
        } else {
            Toast.makeText(context, "Camera permission is required for Video", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to start video with permission check
    fun startVideoWithPermission() {
        val hasCamPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            videoMinimized = false
            liveViewModel.startVideo()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Notification permission launcher (Android 13+) — for foreground service notification
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or not, proceed with starting live
        // The foreground service will work without notification permission,
        // the notification just won't be visible
    }

    // Helper to start live with permission check
    fun startLiveWithPermission() {
        // Request notification permission on Android 13+ (non-blocking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

    // Auto-start live mode when launched via ACTION_ASSIST (power button long-press)
    LaunchedEffect(startInLiveMode) {
        if (startInLiveMode && liveViewModel.liveState == LiveSessionViewModel.LiveState.IDLE) {
            startLiveWithPermission()
        }
    }

    // Show error toast
    LaunchedEffect(liveViewModel.lastError) {
        liveViewModel.lastError?.let { error ->
            Toast.makeText(context, "Live error: $error", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Haptic feedback synced with Apsara's speech transcription ─────
    val hapticEnabled = liveSettings.hapticFeedback
    val outputAmplitude by liveViewModel.audioManager.outputAmplitude.collectAsState()
    val currentActiveSpeaker = liveViewModel.activeSpeaker
    val outputTranscript = liveViewModel.outputTranscript

    // Remember the vibrator instance once
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) { null }
    }

    // Track previous transcript length to detect new words arriving
    val prevTranscriptLength = remember { mutableStateOf(0) }

    // When output transcript changes (new words from Apsara), pulse vibration
    LaunchedEffect(outputTranscript) {
        if (!hapticEnabled) return@LaunchedEffect
        if (vibrator == null || !vibrator.hasVibrator()) return@LaunchedEffect

        val newLen = outputTranscript.length
        val prevLen = prevTranscriptLength.value
        prevTranscriptLength.value = newLen

        // Only vibrate when new text has actually been added
        val addedText = if (newLen > prevLen) outputTranscript.substring(prevLen) else ""
        if (addedText.isBlank()) return@LaunchedEffect

        // Count how many new words arrived in this chunk
        val newWordCount = addedText.trim().split("\\s+".toRegex()).size

        // Pulse intensity: moderate for short chunks, stronger for longer ones
        // Duration scales with word count: 1 word = 45ms, 2+ words = up to 100ms
        val durationMs = (45L + (newWordCount - 1).coerceIn(0, 4) * 15L)
        val intensity = (100 + (newWordCount - 1).coerceIn(0, 4) * 30).coerceIn(80, 220)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, intensity)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Ignore — don't crash for haptic issues
        }
    }

    // Reset transcript tracking when Apsara stops speaking
    LaunchedEffect(currentActiveSpeaker) {
        if (currentActiveSpeaker != ActiveSpeaker.APSARA) {
            prevTranscriptLength.value = 0
        }
    }

    // ─── Haptic feedback for tool/function calls ──────────────────────
    // MOVED TO LiveSessionViewModel to support background haptics
    // The ViewModel now triggers haptics directly based on tool state transitions.

    val isLiveActive = liveViewModel.liveState != LiveSessionViewModel.LiveState.IDLE

    // ─── Canvas notification: Snackbar + system notification ─────────
    val snackbarHostState = remember { SnackbarHostState() }

    // Create notification channel for canvas (once)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "apsara_canvas",
                "Canvas Apps",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when an Apsara Canvas app has been created"
            }
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // Observe canvas notifications from the ViewModel
    LaunchedEffect(Unit) {
        liveViewModel.canvasNotification.collect { notification ->
            if (notification.status == "ready") {
                // Show in-app Snackbar
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "✨ ${notification.title}",
                        actionLabel = "Open Canvas",
                        duration = SnackbarDuration.Long
                    ).let { result ->
                        if (result == SnackbarResult.ActionPerformed) {
                            onNavigateToCanvas()
                        }
                    }
                }
                // Also show a system notification
                try {
                    val tapIntent = PendingIntent.getActivity(
                        context,
                        100,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navigate_to", "canvas")
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val notif = NotificationCompat.Builder(context, "apsara_canvas")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Canvas App Ready")
                        .setContentText(notification.title)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(tapIntent)
                        .build()
                    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(2000 + System.currentTimeMillis().toInt() % 1000, notif)
                } catch (e: Exception) {
                    // Ignore notification errors (permission not granted, etc.)
                }
            } else if (notification.status == "error") {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "❌ Canvas failed: ${notification.title}",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
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
                        when (item.title) {
                            "Settings" -> onNavigateToSettings()
                            "My Plugins" -> onNavigateToPlugins()
                            "My Canvas" -> onNavigateToCanvas()
                            "My Code" -> onNavigateToInterpreter()
                        }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        gesturesEnabled = true, // Always allow swipe gesture, even during live mode
        scrimColor = palette.surface.copy(alpha = 0.6f)
    ) {
        Scaffold(
            containerColor = palette.surface,
            modifier = Modifier.imePadding(),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = palette.surfaceContainer,
                        contentColor = palette.textPrimary,
                        actionColor = palette.accent,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
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
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ─── Minimized video indicator (bottom-right, above input bar) ──
                    AnimatedVisibility(
                        visible = isVideoActive && videoMinimized && isLiveActive,
                        enter = scaleIn(tween(250)) + fadeIn(tween(200)),
                        exit = scaleOut(tween(200)) + fadeOut(tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp, bottom = 8.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            MinimizedVideoIndicator(
                                onClick = { videoMinimized = false }
                            )
                        }
                    }

                    // ─── Full camera preview (above input bar) ──────────────────
                    AnimatedVisibility(
                        visible = isVideoActive && !videoMinimized && isLiveActive,
                        enter = expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(250)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(200))
                    ) {
                        CameraPreviewCard(
                            useFrontCamera = cameraUseFront,
                            flashEnabled = cameraFlashEnabled,
                            onUseFrontCameraChange = { cameraUseFront = it },
                            onFlashEnabledChange = { cameraFlashEnabled = it },
                            onClose = {
                                liveViewModel.stopVideo()
                                videoMinimized = false
                                cameraUseFront = true
                                cameraFlashEnabled = false
                            },
                            onMinimize = {
                                videoMinimized = true
                            },
                            onFrameCaptured = { jpegBytes ->
                                liveViewModel.sendVideoFrame(jpegBytes)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    // ─── Input bar ──────────────────────────────────────────────
                    BottomInputBar(
                    onAttachClick = { showLiveAttachmentSheet = true },
                    onLiveEnd = { liveViewModel.stopLive() },
                    onLiveMuteToggle = { liveViewModel.toggleMute() },
                    onLiveTextSend = { text -> liveViewModel.sendText(text) },
                    liveState = liveViewModel.liveState,
                    isMuted = liveViewModel.isMuted,
                    activeSpeaker = liveViewModel.activeSpeaker,
                    inputAmplitude = liveViewModel.audioManager.inputAmplitude.collectAsState().value,
                    outputAmplitude = liveViewModel.audioManager.outputAmplitude.collectAsState().value,
                    currentAudioDevice = liveViewModel.audioManager.audioOutputDevice.collectAsState().value,
                    onAudioDeviceChange = { device -> liveViewModel.audioManager.setAudioOutputDevice(device) },
                    hasBluetooth = liveViewModel.audioManager.isBluetoothAvailable(),
                    pastedImages = pastedImages,
                    onImagePasted = { uri -> pastedImages = pastedImages + uri },
                    onImageRemoved = { uri -> pastedImages = pastedImages - uri }
                )
                }
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
                        onNavigateToCanvasApp = onNavigateToCanvasApp,
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

    // Live mode attachment bottom sheet
    if (showLiveAttachmentSheet) {
        AttachmentBottomSheet(
            onDismiss = { showLiveAttachmentSheet = false },
            onCameraClick = { /* TODO: open camera for live video */ },
            onPhotosClick = { /* TODO: pick photo to send */ },
            onFilesClick = { /* TODO: pick file to send */ },
            onVideoClick = { startVideoWithPermission() },
            onScreenshareClick = { /* TODO: start screen share */ }
        )
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
    onNavigateToCanvasApp: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Filter messages based on transcription settings
    val visibleMessages = messages.filter { msg ->
        when (msg.role) {
            LiveMessage.Role.USER -> showInput
            LiveMessage.Role.APSARA -> showOutput || msg.toolCalls.isNotEmpty()
            LiveMessage.Role.TOOL_CALL -> false  // No longer shown as separate items
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
                            thought = message.thought,
                            toolCalls = message.toolCalls,
                            palette = palette,
                            onNavigateToCanvasApp = onNavigateToCanvasApp
                        )
                        else -> { /* TOOL_CALL no longer rendered as separate items */ }
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

// ─── Apsara output — plain text, no bubble, collapsible thoughts ────────────

@Composable
private fun ApsaraBubble(
    text: String,
    isStreaming: Boolean = false,
    thought: String? = null,
    toolCalls: List<EmbeddedToolCall> = emptyList(),
    palette: ApsaraColorPalette,
    onNavigateToCanvasApp: (String) -> Unit = {}
) {
    var thoughtsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        // Collapsible "Thoughts" section — shown ABOVE tool calls and main text
        if (!thought.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { thoughtsExpanded = !thoughtsExpanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (thoughtsExpanded) "▾ Thoughts" else "▸ Thoughts",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textTertiary
                )
            }
            AnimatedVisibility(
                visible = thoughtsExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
            ) {
                Text(
                    text = buildAnnotatedString {
                        val raw = thought ?: ""
                        val boldPattern = Regex("""\*\*(.+?)\*\*""")
                        var lastEnd = 0
                        for (match in boldPattern.findAll(raw)) {
                            // append text before the match
                            append(raw.substring(lastEnd, match.range.first))
                            // append the matched text as bold
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.textSecondary)) {
                                append(match.groupValues[1])
                            }
                            lastEnd = match.range.last + 1
                        }
                        // append remaining text
                        if (lastEnd < raw.length) {
                            append(raw.substring(lastEnd))
                        }
                    },
                    fontSize = 13.sp,
                    color = palette.textTertiary,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Embedded tool call cards — shown after thoughts, before main text
        if (toolCalls.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                toolCalls.forEach { tc ->
                    if (tc.name == "run_code" || tc.name == "edit_code") {
                        CodeExecutionCard(
                            toolCall = tc,
                            palette = palette
                        )
                    } else if (tc.name == "url_context") {
                        UrlContextCard(
                            toolCall = tc,
                            palette = palette
                        )
                    } else if (tc.name == "apsara_canvas" || tc.name == "edit_canvas") {
                        CanvasStreamCard(
                            toolCall = tc,
                            palette = palette,
                            onNavigateToCanvasApp = onNavigateToCanvasApp
                        )
                    } else {
                        ToolCallCard(
                            toolName = tc.name,
                            toolStatus = tc.status,
                            toolMode = tc.mode,
                            toolResult = tc.result,
                            palette = palette
                        )
                    }
                }
            }
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Main text — plain, no bubble
        if (text.isNotBlank()) {
            Text(
                text = text,
                fontSize = 15.sp,
                color = palette.textPrimary,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Tool Call card — shown inline inside ApsaraBubble ──────────────────────

@Composable
private fun ToolCallCard(
    toolName: String,
    toolStatus: LiveMessage.ToolStatus,
    toolMode: String,
    toolResult: String?,
    palette: ApsaraColorPalette
) {
    var expanded by remember { mutableStateOf(false) }

    val displayName = toolName
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    val isCompleted = toolStatus == LiveMessage.ToolStatus.COMPLETED
    val isRunning = toolStatus == LiveMessage.ToolStatus.RUNNING

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 0.5.dp,
                    color = if (isCompleted) palette.accent.copy(alpha = 0.3f)
                            else palette.textTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(enabled = isCompleted && !toolResult.isNullOrBlank()) {
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status icon
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            } else if (isCompleted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Completed",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Tool name + mode
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Text(
                    text = if (isRunning) {
                        if (toolMode == "async") "Running (async)…" else "Running…"
                    } else {
                        if (toolMode == "async") "Completed · async" else "Completed · sync"
                    },
                    fontSize = 11.sp,
                    color = palette.textTertiary
                )
            }

            // Expand hint when result available
            if (isCompleted && !toolResult.isNullOrBlank()) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
            }
        }

        // Expandable result JSON
        AnimatedVisibility(
            visible = expanded && isCompleted && !toolResult.isNullOrBlank(),
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
        ) {
            val formattedResult = try {
                toolResult
                    ?.replace(",\"", ",\n  \"")
                    ?.replace("{\"", "{\n  \"")
                    ?.replace("}", "\n}")
                    ?.replace("\\\"", "\"")
                    ?: ""
            } catch (_: Exception) { toolResult ?: "" }

            Text(
                text = formattedResult,
                fontSize = 11.sp,
                color = palette.textTertiary,
                lineHeight = 16.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(
                        0.5.dp,
                        palette.textTertiary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )
        }
    }
}

// ─── Canvas Stream card — shown inline for apsara_canvas / edit_canvas tool calls ──

@Composable
private fun CanvasStreamCard(
    toolCall: EmbeddedToolCall,
    palette: ApsaraColorPalette,
    onNavigateToCanvasApp: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    val isCompleted = toolCall.status == LiveMessage.ToolStatus.COMPLETED
    val isRunning = toolCall.status == LiveMessage.ToolStatus.RUNNING
    val isEdit = toolCall.name == "edit_canvas"
    val displayName = if (isEdit) "Canvas Edit" else "Canvas"
    val htmlContent = toolCall.codeOutput ?: ""
    val progressText = toolCall.codeBlock ?: ""
    val charCount = htmlContent.length
    val thoughts = toolCall.canvasThoughts
    val hasThoughts = thoughts.isNotEmpty()

    // Fallback: extract canvasId from result JSON if not set on the toolCall directly
    val canvasId = toolCall.canvasId ?: run {
        if (isCompleted && toolCall.result != null) {
            try {
                val json = org.json.JSONObject(toolCall.result!!)
                val resp = if (json.has("response")) json.getJSONObject("response") else json
                resp.optString("canvas_id", "").ifEmpty { null }
            } catch (_: Exception) { null }
        } else null
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 0.5.dp,
                    color = if (isCompleted) palette.accent.copy(alpha = 0.3f)
                            else palette.textTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    if (isCompleted && charCount > 0) {
                        expanded = !expanded
                    } else if (isCompleted && canvasId != null) {
                        onNavigateToCanvasApp(canvasId)
                    } else if (isRunning) {
                        expanded = !expanded
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status icon
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            } else if (isCompleted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Completed",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Title + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                // Subtitle: show latest thought title (bold) while running, or status text
                val subtitleText = if (isRunning) {
                    if (charCount > 0) {
                        "Generating… ${charCount.formatChars()}"
                    } else if (hasThoughts) {
                        thoughts.last().title.replace("**", "").ifBlank { "Thinking…" }
                    } else if (progressText.isNotBlank()) {
                        progressText
                    } else {
                        "Starting…"
                    }
                } else {
                    buildString {
                        append("Ready")
                        if (charCount > 0) append(" · ${charCount.formatChars()}")
                    }
                }
                Text(
                    text = subtitleText,
                    fontSize = 11.sp,
                    fontWeight = if (isRunning && hasThoughts && charCount == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isRunning && hasThoughts && charCount == 0) palette.textSecondary else palette.textTertiary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Hint arrow
            if (isCompleted && canvasId != null && charCount == 0) {
                Text(
                    text = "›",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            } else if (isRunning || charCount > 0) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
            }
        }

        // ── Expandable section: thoughts and/or code ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show sub-tool call mini cards (url_context etc.)
                val subTools = toolCall.canvasSubToolCalls
                if (subTools.isNotEmpty()) {
                    subTools.forEach { subTool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surface)
                                .border(0.5.dp, palette.textTertiary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (subTool.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = palette.accent,
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "Done",
                                    tint = palette.accent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = subTool.toolType.replace("_", " ").replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (subTool.isRunning) palette.textSecondary else palette.textTertiary
                            )
                        }
                    }
                }

                // Show thought summaries if available
                if (hasThoughts) {
                    val thoughtScrollState = rememberScrollState()
                    LaunchedEffect(thoughts.size, thoughts.lastOrNull()?.body?.length) {
                        thoughtScrollState.animateScrollTo(thoughtScrollState.maxValue)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .verticalScroll(thoughtScrollState)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        thoughts.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .heightIn(min = 16.dp)
                                        .background(palette.accent.copy(alpha = 0.5f))
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    if (entry.title.isNotBlank()) {
                                        Text(
                                            text = entry.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textPrimary,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    if (entry.body.isNotBlank()) {
                                        Text(
                                            text = entry.body.trim(),
                                            fontSize = 10.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = palette.textTertiary,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Show code view if streaming / completed with code
                if (charCount > 0) {
                    val codeScrollState = rememberScrollState()
                    LaunchedEffect(charCount) {
                        codeScrollState.animateScrollTo(codeScrollState.maxValue)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .verticalScroll(codeScrollState)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = htmlContent,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = palette.textPrimary.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/** Format character count for display (e.g., "12.3k chars") */
private fun Int.formatChars(): String {
    return when {
        this >= 1000 -> "${String.format("%.1f", this / 1000.0)}k chars"
        else -> "$this chars"
    }
}

// ─── Code Execution card — shown inline for run_code tool calls ─────────────

@Composable
private fun CodeExecutionCard(
    toolCall: EmbeddedToolCall,
    palette: ApsaraColorPalette
) {
    var expanded by remember { mutableStateOf(false) }

    val isCompleted = toolCall.status == LiveMessage.ToolStatus.COMPLETED
    val isRunning = toolCall.status == LiveMessage.ToolStatus.RUNNING
    val hasCode = !toolCall.codeBlock.isNullOrBlank()
    val hasOutput = !toolCall.codeOutput.isNullOrBlank()
    val hasImages = toolCall.codeImages.isNotEmpty()
    val hasContent = hasCode || hasOutput || hasImages

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header row — tap to expand/collapse (same pattern as ToolCallCard)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 0.5.dp,
                    color = if (isCompleted) palette.accent.copy(alpha = 0.3f)
                            else palette.textTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(enabled = isCompleted && hasContent) {
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status icon
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            } else if (isCompleted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Completed",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Title + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Code Execution",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Text(
                    text = if (isRunning) {
                        if (toolCall.mode == "async") "Running (async)…" else "Running…"
                    } else {
                        buildString {
                            append(if (toolCall.mode == "async") "Completed · async" else "Completed · sync")
                            if (hasImages) append(" · ${toolCall.codeImages.size} image(s)")
                        }
                    },
                    fontSize = 11.sp,
                    color = palette.textTertiary
                )
            }

            // Expand hint when completed
            if (isCompleted && hasContent) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
            }
        }

        // ── Expandable content — all shown at once on tap, matching ToolCallCard ──
        AnimatedVisibility(
            visible = expanded && isCompleted,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ── Code ──
                if (hasCode) {
                    Text(
                        text = "Code",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = toolCall.codeBlock ?: "",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        lineHeight = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }

                // ── Output ──
                if (hasOutput) {
                    Text(
                        text = "Output",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textTertiary,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = toolCall.codeOutput ?: "",
                        fontSize = 11.sp,
                        color = palette.textTertiary,
                        lineHeight = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.textTertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }

                // ── Images ──
                if (hasImages) {
                    Text(
                        text = "Images (${toolCall.codeImages.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    toolCall.codeImages.forEach { imgInfo ->
                        CodeImageInline(
                            imageUrl = "https://apsara-dark-backend.devshubh.me${imgInfo.url}",
                            palette = palette
                        )
                    }
                }

                // ── Fallback: Result JSON ──
                if (!hasCode && !hasOutput && !toolCall.result.isNullOrBlank()) {
                    val formattedResult = try {
                        toolCall.result
                            ?.replace(",\"", ",\n  \"")
                            ?.replace("{\"", "{\n  \"")
                            ?.replace("}", "\n}")
                            ?.replace("\\\"", "\"")
                            ?: ""
                    } catch (_: Exception) { toolCall.result ?: "" }

                    Text(
                        text = formattedResult,
                        fontSize = 11.sp,
                        color = palette.textTertiary,
                        lineHeight = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.textTertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

// ─── URL Context card — shown inline for url_context tool calls ─────────────

@Composable
private fun UrlContextCard(
    toolCall: EmbeddedToolCall,
    palette: ApsaraColorPalette
) {
    var expanded by remember { mutableStateOf(false) }

    val isCompleted = toolCall.status == LiveMessage.ToolStatus.COMPLETED
    val isRunning = toolCall.status == LiveMessage.ToolStatus.RUNNING
    val hasOutput = !toolCall.codeOutput.isNullOrBlank()
    val hasResult = !toolCall.result.isNullOrBlank()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainer)
                .border(
                    width = 0.5.dp,
                    color = if (isCompleted) palette.accent.copy(alpha = 0.3f)
                            else palette.textTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(enabled = isCompleted && (hasOutput || hasResult)) {
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status icon
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            } else if (isCompleted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Completed",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Title + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "URL Context",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Text(
                    text = if (isRunning) {
                        // Show live progress message while running
                        val progress = toolCall.codeOutput
                        if (!progress.isNullOrBlank()) progress
                        else if (toolCall.mode == "async") "Fetching URLs (async)…" else "Fetching URLs…"
                    } else {
                        if (toolCall.mode == "async") "Completed · async" else "Completed · sync"
                    },
                    fontSize = 11.sp,
                    color = palette.textTertiary
                )
            }

            // Expand hint
            if (isCompleted && (hasOutput || hasResult)) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
            }
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded && isCompleted,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Output — URL metadata + extracted text
                if (hasOutput) {
                    Text(
                        text = "Content",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = toolCall.codeOutput ?: "",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }

                // Fallback: raw result JSON
                if (!hasOutput && hasResult) {
                    val formattedResult = try {
                        toolCall.result
                            ?.replace(",\"", ",\n  \"")
                            ?.replace("{\"", "{\n  \"")
                            ?.replace("}", "\n}")
                            ?.replace("\\\"", "\"")
                            ?: ""
                    } catch (_: Exception) { toolCall.result ?: "" }

                    Text(
                        text = formattedResult,
                        fontSize = 11.sp,
                        color = palette.textTertiary,
                        lineHeight = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(0.5.dp, palette.textTertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

// ─── Inline image loader for Code Execution images in live mode ─────────────

@Composable
private fun CodeImageInline(
    imageUrl: String,
    palette: ApsaraColorPalette
) {
    var imageBitmap by remember(imageUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }

    LaunchedEffect(imageUrl) {
        isLoading = true
        try {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val url = java.net.URL(imageUrl)
                val stream = url.openStream()
                android.graphics.BitmapFactory.decodeStream(stream)
            }
            imageBitmap = bitmap?.asImageBitmap()
        } catch (_: Exception) { }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surface)
            .border(0.5.dp, palette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = imageBitmap!!,
                contentDescription = "Code output image",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
            )
        } else if (isLoading) {
            CircularProgressIndicator(
                color = palette.accent,
                modifier = Modifier.size(24.dp).padding(4.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("Failed to load image", fontSize = 11.sp, color = palette.textTertiary)
        }
    }
}