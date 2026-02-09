package com.shubharthak.apsaradark.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.data.MockData
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
                        inputTranscript = liveViewModel.inputTranscript,
                        outputTranscript = liveViewModel.outputTranscript,
                        isMuted = liveViewModel.isMuted,
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

// ─── Live Mode UI — ChatGPT Voice style ─────────────────────────────────────

@Composable
private fun LiveModeContent(
    liveState: LiveSessionViewModel.LiveState,
    inputTranscript: String,
    outputTranscript: String,
    isMuted: Boolean,
    palette: ApsaraColorPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Animated orb / status indicator
            LiveOrb(
                liveState = liveState,
                isMuted = isMuted,
                palette = palette
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = when (liveState) {
                    LiveSessionViewModel.LiveState.CONNECTING -> "Connecting..."
                    LiveSessionViewModel.LiveState.CONNECTED -> if (isMuted) "Muted" else "Listening..."
                    LiveSessionViewModel.LiveState.ERROR -> "Connection Error"
                    else -> ""
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Output transcript (what Apsara is saying)
            if (outputTranscript.isNotEmpty()) {
                Text(
                    text = outputTranscript,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Input transcript (what you're saying)
            if (inputTranscript.isNotEmpty()) {
                Text(
                    text = inputTranscript,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = palette.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveOrb(
    liveState: LiveSessionViewModel.LiveState,
    isMuted: Boolean,
    palette: ApsaraColorPalette
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    // Pulsing scale for the orb
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val isActive = liveState == LiveSessionViewModel.LiveState.CONNECTED && !isMuted
    val isConnecting = liveState == LiveSessionViewModel.LiveState.CONNECTING
    val currentScale = if (isActive) pulseScale else 1f
    val currentGlowAlpha = if (isActive) glowAlpha else if (isConnecting) 0.3f else 0.15f

    val orbColor = when {
        liveState == LiveSessionViewModel.LiveState.ERROR -> Color(0xFFEF5350)
        isMuted -> palette.textTertiary
        else -> palette.accent
    }

    Box(contentAlignment = Alignment.Center) {
        // Outer glow
        Box(
            modifier = Modifier
                .size((100 * currentScale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbColor.copy(alpha = currentGlowAlpha),
                            orbColor.copy(alpha = 0f)
                        )
                    )
                )
        )

        // Inner orb
        Box(
            modifier = Modifier
                .size((56 * currentScale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbColor.copy(alpha = 0.9f),
                            orbColor.copy(alpha = 0.5f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.surface,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
