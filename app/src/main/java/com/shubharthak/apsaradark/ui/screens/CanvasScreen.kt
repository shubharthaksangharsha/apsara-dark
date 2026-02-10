package com.shubharthak.apsaradark.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shubharthak.apsaradark.data.CanvasApp
import com.shubharthak.apsaradark.data.CanvasAppDetail
import com.shubharthak.apsaradark.data.CanvasLogEntry
import com.shubharthak.apsaradark.ui.theme.ApsaraColorPalette
import com.shubharthak.apsaradark.ui.theme.LocalThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private const val BACKEND_BASE = "https://apsara-dark-backend.devshubh.me"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    onBack: () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme
    val scope = rememberCoroutineScope()

    var canvasApps by remember { mutableStateOf<List<CanvasApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<CanvasApp?>(null) }
    var showDeleteDialog by remember { mutableStateOf<CanvasApp?>(null) }
    var detailApp by remember { mutableStateOf<CanvasApp?>(null) } // For "View Code" detail screen

    // Fetch canvas apps from backend
    fun loadApps() {
        scope.launch {
            isLoading = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val url = URL("$BACKEND_BASE/api/canvas")
                    val connection = url.openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val response = connection.getInputStream().bufferedReader().readText()
                    val json = JSONObject(response)
                    val appsArray = json.getJSONArray("apps")
                    val result = mutableListOf<CanvasApp>()
                    for (i in 0 until appsArray.length()) {
                        val app = appsArray.getJSONObject(i)
                        result.add(
                            CanvasApp(
                                id = app.getString("id"),
                                title = app.getString("title"),
                                description = app.optString("description", ""),
                                status = app.optString("status", "unknown"),
                                createdAt = app.optString("created_at", ""),
                                renderUrl = "$BACKEND_BASE/api/canvas/${app.getString("id")}/render"
                            )
                        )
                    }
                    result
                }
                canvasApps = apps
            } catch (e: Exception) {
                // Silently fail — show empty state
                canvasApps = emptyList()
            }
            isLoading = false
        }
    }

    fun deleteApp(app: CanvasApp) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL("$BACKEND_BASE/api/canvas/${app.id}")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "DELETE"
                    connection.connectTimeout = 10000
                    connection.responseCode // Execute the request
                    connection.disconnect()
                }
                canvasApps = canvasApps.filter { it.id != app.id }
            } catch (_: Exception) {
                // Ignore delete errors
            }
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    // If viewing code detail
    if (detailApp != null) {
        CanvasDetailViewer(
            app = detailApp!!,
            palette = palette,
            onBack = { detailApp = null }
        )
        return
    }

    // If an app is selected, show the WebView viewer
    if (selectedApp != null) {
        CanvasViewer(
            app = selectedApp!!,
            palette = palette,
            onBack = { selectedApp = null },
            onViewCode = {
                val app = selectedApp!!
                selectedApp = null
                detailApp = app
            }
        )
        return
    }

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Canvas",
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
                actions = {
                    IconButton(onClick = { loadApps() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Loading canvas apps…",
                        fontSize = 14.sp,
                        color = palette.textSecondary
                    )
                }
            }
        } else if (canvasApps.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 48.dp)
                ) {
                    Icon(
                        Icons.Outlined.Dashboard,
                        contentDescription = null,
                        tint = palette.textTertiary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No canvas apps yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ask Apsara to create an app for you during a live session. Say something like \"Create a calculator app\" or \"Build me a todo list\".",
                        fontSize = 13.sp,
                        color = palette.textTertiary,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(canvasApps) { app ->
                    CanvasAppCard(
                        app = app,
                        palette = palette,
                        onClick = { selectedApp = app },
                        onViewCode = { detailApp = app },
                        onDelete = { showDeleteDialog = app }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${canvasApps.size} app${if (canvasApps.size != 1) "s" else ""} created by Apsara Canvas",
                        fontSize = 12.sp,
                        color = palette.textTertiary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog?.let { deleteApp(it) }
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            title = {
                Text(
                    "Delete Canvas?",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "\"${showDeleteDialog?.title}\" will be permanently deleted.",
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            },
            containerColor = palette.surfaceContainer,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun CanvasAppCard(
    app: CanvasApp,
    palette: ApsaraColorPalette,
    onClick: () -> Unit,
    onViewCode: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (app.status) {
        "ready" -> palette.accent
        "generating", "testing", "fixing" -> palette.textTertiary
        "error" -> palette.textTertiary.copy(alpha = 0.6f)
        else -> palette.textTertiary
    }

    val statusText = when (app.status) {
        "ready" -> "Ready"
        "generating" -> "Generating…"
        "testing" -> "Testing…"
        "fixing" -> "Fixing…"
        "error" -> "Error"
        else -> app.status
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .border(
                width = 0.5.dp,
                color = if (app.status == "ready") palette.accent.copy(alpha = 0.2f)
                else palette.textTertiary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = app.status == "ready") { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // App info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (app.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.description,
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            color = statusColor
                        )

                        if (app.createdAt.isNotBlank()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = formatTimestamp(app.createdAt),
                                fontSize = 11.sp,
                                color = palette.textTertiary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = palette.textTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Action buttons row — only for ready apps
            if (app.status == "ready") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View Code button
                    OutlinedButton(
                        onClick = onViewCode,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            width = 0.5.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = palette.textSecondary
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Code", fontSize = 11.sp)
                    }

                    // Open App button
                    OutlinedButton(
                        onClick = onClick,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            width = 0.5.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = palette.accent
                        )
                    ) {
                        Icon(
                            Icons.Outlined.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open App", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Full-screen WebView viewer for a canvas app.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasViewer(
    app: CanvasApp,
    palette: ApsaraColorPalette,
    onBack: () -> Unit,
    onViewCode: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            app.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Apsara Canvas",
                            fontSize = 11.sp,
                            color = palette.textTertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    // View code
                    IconButton(onClick = onViewCode) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = "View code",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Open in external browser
                    IconButton(onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(app.renderUrl)
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            Icons.Outlined.OpenInBrowser,
                            contentDescription = "Open in browser",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // Critical: useWideViewPort + loadWithOverviewMode together
                            // make the WebView respect <meta viewport> properly.
                            // If the page has viewport meta → renders at device width.
                            // If the page is desktop-only → scales down to fit (overview mode).
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            // Enable zoom so users can pinch-zoom desktop-designed apps
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            // Force mobile-friendly text size
                            textZoom = 100
                            // Allow file access for inline data
                            @Suppress("DEPRECATION")
                            allowFileAccess = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Inject viewport meta tag + mobile CSS fixes if missing.
                                // The backend also injects these server-side, but this is
                                // a safety net for cached pages or direct URL loading.
                                view?.evaluateJavascript("""
                                    (function() {
                                        // 1. Ensure viewport meta tag exists
                                        if (!document.querySelector('meta[name="viewport"]')) {
                                            var meta = document.createElement('meta');
                                            meta.name = 'viewport';
                                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                                            document.head.appendChild(meta);
                                        }
                                        // 2. Inject mobile CSS reset to prevent horizontal overflow
                                        var style = document.createElement('style');
                                        style.textContent = '*, *::before, *::after { box-sizing: border-box !important; } body { margin: 0; overflow-x: hidden; max-width: 100vw; } img, video, canvas, svg { max-width: 100%; height: auto; }';
                                        document.head.appendChild(style);
                                    })();
                                """.trimIndent(), null)
                            }
                        }
                        setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
                        loadUrl(app.renderUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Canvas Detail Viewer — shows code, prompt, metadata, and generation log.
 * Fetches full detail from /api/canvas/:id on mount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasDetailViewer(
    app: CanvasApp,
    palette: ApsaraColorPalette,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var detail by remember { mutableStateOf<CanvasAppDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Code, 1=Prompt, 2=Log, 3=Config, 4=Info

    // Fetch detail from backend
    LaunchedEffect(app.id) {
        isLoading = true
        errorMsg = null
        try {
            val result = withContext(Dispatchers.IO) {
                val url = URL("$BACKEND_BASE/api/canvas/${app.id}")
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val response = connection.getInputStream().bufferedReader().readText()
                val json = JSONObject(response)
                val a = json.getJSONObject("app")
                val logArray = a.optJSONArray("generation_log")
                val logEntries = mutableListOf<CanvasLogEntry>()
                if (logArray != null) {
                    for (i in 0 until logArray.length()) {
                        val entry = logArray.getJSONObject(i)
                        logEntries.add(
                            CanvasLogEntry(
                                step = entry.optString("step", ""),
                                timestamp = entry.optString("timestamp", ""),
                                message = entry.optString("message", ""),
                                attempts = entry.optInt("attempts", 0)
                            )
                        )
                    }
                }
                CanvasAppDetail(
                    id = a.getString("id"),
                    title = a.optString("title", ""),
                    description = a.optString("description", ""),
                    prompt = a.optString("prompt", ""),
                    originalPrompt = a.optString("original_prompt", a.optString("prompt", "")),
                    status = a.optString("status", ""),
                    error = if (a.isNull("error")) null else a.optString("error"),
                    attempts = a.optInt("attempts", 0),
                    html = if (a.isNull("html")) null else a.optString("html"),
                    htmlLength = a.optInt("html_length", 0),
                    createdAt = a.optString("created_at", ""),
                    updatedAt = a.optString("updated_at", ""),
                    generationLog = logEntries,
                    editCount = a.optInt("edit_count", 0),
                    configUsed = if (a.isNull("config_used")) null else {
                        val cfg = a.optJSONObject("config_used")
                        if (cfg != null) {
                            val map = mutableMapOf<String, String>()
                            cfg.keys().forEach { key -> map[key] = cfg.opt(key)?.toString() ?: "" }
                            map
                        } else null
                    }
                )
            }
            detail = result
        } catch (e: Exception) {
            errorMsg = e.message ?: "Failed to load detail"
        }
        isLoading = false
    }

    val tabTitles = listOf("Code", "Prompt", "Log", "Config", "Info")

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            app.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Canvas Detail",
                            fontSize = 11.sp,
                            color = palette.textTertiary
                        )
                    }
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
                actions = {
                    // Copy current tab content
                    if (!isLoading && detail != null) {
                        IconButton(onClick = {
                            val textToCopy = when (selectedTab) {
                                0 -> detail?.html ?: "(No code)"
                                1 -> detail?.prompt ?: "(No prompt)"
                                else -> null
                            }
                            if (textToCopy != null) {
                                clipboardManager.setText(AnnotatedString(textToCopy))
                            }
                        }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ─── Tab Row ────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = palette.surfaceContainer,
                contentColor = palette.textPrimary,
                edgePadding = 16.dp,
                divider = {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = palette.textTertiary.copy(alpha = 0.15f)
                    )
                },
                indicator = { /* use default accent-colored indicator */ }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == index) palette.accent else palette.textSecondary
                            )
                        }
                    )
                }
            }

            // ─── Content ────────────────────────────────────────────────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = palette.accent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Fetching detail…", fontSize = 13.sp, color = palette.textSecondary)
                    }
                }
            } else if (errorMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Failed to load",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            errorMsg ?: "",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (detail != null) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> CodeTabContent(detail!!, palette)
                        1 -> PromptTabContent(detail!!, palette)
                        2 -> LogTabContent(detail!!, palette)
                        3 -> ConfigTabContent(detail!!, palette)
                        4 -> InfoTabContent(detail!!, palette)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tab content composables
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Code tab — shows HTML source with syntax-styled mono font.
 */
@Composable
private fun CodeTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette) {
    val code = detail.html

    if (code.isNullOrBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = palette.textTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No code available", fontSize = 14.sp, color = palette.textTertiary)
                if (detail.status != "ready") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Status: ${detail.status}",
                        fontSize = 12.sp,
                        color = palette.textTertiary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Code stats bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HTML • ${detail.htmlLength.formatFileSize()}",
                    fontSize = 11.sp,
                    color = palette.textTertiary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${code.lines().size} lines",
                    fontSize = 11.sp,
                    color = palette.textTertiary,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = palette.textTertiary.copy(alpha = 0.1f)
            )

            // Scrollable code area with line numbers
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                // Line numbers gutter
                val lines = code.lines()
                Column(
                    modifier = Modifier
                        .background(palette.surfaceContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.forEachIndexed { index, _ ->
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textTertiary.copy(alpha = 0.35f),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Code content
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    lines.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorForCodeLine(line, palette),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Very basic syntax "highlighting" by line type — keeps it fast, no regex per token.
 */
private fun colorForCodeLine(line: String, palette: ApsaraColorPalette): androidx.compose.ui.graphics.Color {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") ->
            palette.textTertiary.copy(alpha = 0.5f)
        trimmed.startsWith("<") && trimmed.contains(">") ->
            palette.accent.copy(alpha = 0.85f)
        trimmed.startsWith("import ") || trimmed.startsWith("export ") ||
            trimmed.startsWith("function ") || trimmed.startsWith("const ") ||
            trimmed.startsWith("let ") || trimmed.startsWith("var ") ||
            trimmed.startsWith("class ") || trimmed.startsWith("return ") ->
            palette.accentSubtle.let { palette.accent.copy(alpha = 0.7f) }
        trimmed.startsWith(".") || trimmed.startsWith("#") ->
            palette.textSecondary
        else -> palette.textPrimary.copy(alpha = 0.9f)
    }
}

/**
 * Prompt tab — shows the original user prompt.
 */
@Composable
private fun PromptTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette) {
    val prompt = detail.prompt
    val originalPrompt = detail.originalPrompt
    val hasBeenEdited = detail.editCount > 0 && originalPrompt.isNotBlank() && originalPrompt != prompt

    if (prompt.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No prompt recorded", fontSize = 14.sp, color = palette.textTertiary)
        }
    } else {
        var showOriginal by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Current prompt header
            Text(
                if (hasBeenEdited) "Current Prompt (with edits)" else "Original Prompt",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Prompt content in a styled card
            Surface(
                color = palette.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = prompt,
                    fontSize = 14.sp,
                    color = palette.textPrimary,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Show original prompt (collapsible) if edited
            if (hasBeenEdited) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showOriginal = !showOriginal }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showOriginal) "▾ Original Prompt" else "▸ Original Prompt",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${detail.editCount} edit${if (detail.editCount > 1) "s" else ""} applied)",
                        fontSize = 11.sp,
                        color = palette.textTertiary
                    )
                }
                AnimatedVisibility(
                    visible = showOriginal,
                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                ) {
                    Surface(
                        color = palette.surfaceContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = originalPrompt,
                            fontSize = 14.sp,
                            color = palette.textSecondary,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailChip(
                    label = "Words",
                    value = "${prompt.split("\\s+".toRegex()).size}",
                    palette = palette
                )
                DetailChip(
                    label = "Characters",
                    value = "${prompt.length}",
                    palette = palette
                )
                if (hasBeenEdited) {
                    DetailChip(
                        label = "Edits",
                        value = "${detail.editCount}",
                        palette = palette
                    )
                }
            }
        }
    }
}

/**
 * Log tab — shows the generation timeline.
 */
@Composable
private fun LogTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette) {
    val log = detail.generationLog

    if (log.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No generation log", fontSize = 14.sp, color = palette.textTertiary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    "Generation Timeline",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(log) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Timeline indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp)
                    ) {
                        val dotColor = when (entry.step) {
                            "created" -> palette.textTertiary
                            "ready" -> palette.accent
                            "error" -> palette.textTertiary.copy(alpha = 0.5f)
                            else -> palette.accentSubtle.let { palette.accent.copy(alpha = 0.5f) }
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(dotColor)
                        )
                        if (entry != log.last()) {
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(40.dp)
                                    .background(palette.textTertiary.copy(alpha = 0.15f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Entry content
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.step.replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary
                            )
                            if (entry.attempts > 0) {
                                Text(
                                    text = "Attempt ${entry.attempts}",
                                    fontSize = 10.sp,
                                    color = palette.textTertiary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (entry.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.message,
                                fontSize = 12.sp,
                                color = palette.textTertiary,
                                lineHeight = 17.sp
                            )
                        }

                        if (entry.timestamp.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatTimestamp(entry.timestamp),
                                fontSize = 10.sp,
                                color = palette.textTertiary.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Config tab — shows the interaction config used for generation/edit.
 */
@Composable
private fun ConfigTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette) {
    val config = detail.configUsed

    if (config.isNullOrEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = palette.textTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No config data available", fontSize = 14.sp, color = palette.textTertiary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Config tracking is available for new generations",
                    fontSize = 12.sp,
                    color = palette.textTertiary.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        val configLabels = mapOf(
            "model" to "Model",
            "max_output_tokens" to "Max Output Tokens",
            "thinking_level" to "Thinking Level",
            "thinking_summaries" to "Thinking Summaries",
            "temperature" to "Temperature"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Interaction Config",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary,
                letterSpacing = 0.5.sp
            )

            Text(
                if (detail.editCount > 0) "Config used for the last edit" else "Config used for generation",
                fontSize = 12.sp,
                color = palette.textTertiary
            )

            config.forEach { (key, value) ->
                val label = configLabels[key] ?: key.replaceFirstChar { it.uppercase() }
                Surface(
                    color = palette.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = palette.textTertiary
                        )
                        Text(
                            text = value,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.accent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info tab — shows metadata about the canvas app.
 */
@Composable
private fun InfoTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "App Information",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
            letterSpacing = 0.5.sp
        )

        // Info rows
        InfoRow("Title", detail.title, palette)
        if (detail.description.isNotBlank()) {
            InfoRow("Description", detail.description, palette)
        }
        InfoRow("Status", detail.status.replaceFirstChar { it.uppercase() }, palette)
        if (detail.error != null && detail.error.isNotBlank()) {
            InfoRow("Error", detail.error, palette)
        }
        InfoRow("Attempts", "${detail.attempts}", palette)
        InfoRow("Code Size", if (detail.htmlLength > 0) detail.htmlLength.formatFileSize() else "—", palette)
        InfoRow("Lines of Code", if (detail.html != null) "${detail.html.lines().size}" else "—", palette)
        if (detail.createdAt.isNotBlank()) {
            InfoRow("Created", formatTimestamp(detail.createdAt), palette)
        }
        if (detail.updatedAt.isNotBlank()) {
            InfoRow("Last Updated", formatTimestamp(detail.updatedAt), palette)
        }
        InfoRow("ID", detail.id, palette)

        Spacer(modifier = Modifier.height(8.dp))

        // Generation stats summary
        if (detail.generationLog.isNotEmpty()) {
            Surface(
                color = palette.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Generation Summary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailChip("Steps", "${detail.generationLog.size}", palette)
                        DetailChip("Attempts", "${detail.attempts}", palette)
                        DetailChip(
                            "Status",
                            detail.status.replaceFirstChar { it.uppercase() },
                            palette
                        )
                    }
                }
            }
        }
    }
}

/**
 * A key-value info row for the Info tab.
 */
@Composable
private fun InfoRow(label: String, value: String, palette: ApsaraColorPalette) {
    Surface(
        color = palette.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = palette.textTertiary,
                modifier = Modifier.width(90.dp)
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
                fontFamily = if (label == "ID") FontFamily.Monospace else FontFamily.Default,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Small detail chip used in summary sections.
 */
@Composable
private fun DetailChip(label: String, value: String, palette: ApsaraColorPalette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.accent
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = palette.textTertiary
        )
    }
}

/**
 * Format byte count to human-readable file size.
 */
private fun Int.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        else -> "${"%.1f".format(this / (1024.0 * 1024.0))} MB"
    }
}

/**
 * Format ISO timestamp to a human-readable short format.
 */
private fun formatTimestamp(iso: String): String {
    return try {
        val instant = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(iso)
        } else {
            return iso.take(10)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern("MMM d, h:mm a")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } else {
            iso.take(10)
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}
