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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
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
import com.shubharthak.apsaradark.data.CanvasEditEntry
import com.shubharthak.apsaradark.data.CanvasLogEntry
import com.shubharthak.apsaradark.data.CanvasVersion
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
    canvasId: String? = null,
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
    var detailVersion by remember { mutableStateOf<Int?>(null) } // Version to show in detail view

    // Auto-open a specific canvas when canvasId is provided
    LaunchedEffect(canvasId) {
        if (canvasId != null && selectedApp == null) {
            try {
                val app = withContext(Dispatchers.IO) {
                    val url = URL("$BACKEND_BASE/api/canvas/$canvasId")
                    val conn = url.openConnection()
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val body = conn.getInputStream().bufferedReader().readText()
                    val json = JSONObject(body)
                    val a = json.getJSONObject("app")
                    CanvasApp(
                        id = a.getString("id"),
                        title = a.optString("title", ""),
                        description = a.optString("description", ""),
                        status = a.optString("status", "ready"),
                        createdAt = a.optString("created_at", ""),
                        renderUrl = "$BACKEND_BASE/api/canvas/${a.getString("id")}/render"
                    )
                }
                selectedApp = app
            } catch (_: Exception) {}
        }
    }

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
            initialVersion = detailVersion,
            onBack = { detailApp = null; detailVersion = null }
        )
        return
    }

    // If an app is selected, show the WebView viewer
    if (selectedApp != null) {
        CanvasViewer(
            app = selectedApp!!,
            palette = palette,
            onBack = { selectedApp = null },
            onViewCode = { version ->
                val app = selectedApp!!
                selectedApp = null
                detailVersion = version
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
    onViewCode: (Int?) -> Unit = {}
) {
    val context = LocalContext.current
    var showVersionSheet by remember { mutableStateOf(false) }
    var viewingVersion by remember { mutableStateOf<Int?>(null) }
    // Keep a reference to the WebView to update URL on version switch
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Fetch version list for the bottom sheet
    var versions by remember { mutableStateOf<List<CanvasVersion>>(emptyList()) }
    LaunchedEffect(app.id) {
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            kotlinx.coroutines.withContext(dispatcher) {
                try {
                    val url = java.net.URL("$BACKEND_BASE/api/canvas/${app.id}")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(body)
                        val appJson = json.optJSONObject("app") ?: return@withContext
                        val versionsArr = appJson.optJSONArray("versions")
                        val vList = mutableListOf<CanvasVersion>()
                        if (versionsArr != null) {
                            for (i in 0 until versionsArr.length()) {
                                val v = versionsArr.getJSONObject(i)
                                vList.add(
                                    CanvasVersion(
                                        version = v.optInt("version", 0),
                                        title = v.optString("title", ""),
                                        htmlLength = v.optInt("html_length", 0),
                                        timestamp = v.optString("timestamp", "")
                                    )
                                )
                            }
                        }
                        versions = vList
                    }
                } catch (_: Exception) {}
            }
        }
    }

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
                            if (viewingVersion != null) "Version $viewingVersion"
                            else "Apsara Canvas",
                            fontSize = 11.sp,
                            color = if (viewingVersion != null) palette.accent
                                    else palette.textTertiary
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
                    // History — version switcher
                    if (versions.isNotEmpty()) {
                        IconButton(onClick = { showVersionSheet = true }) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = "Version history",
                                tint = if (viewingVersion != null) palette.accent
                                        else palette.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // View code
                    IconButton(onClick = { onViewCode(viewingVersion) }) {
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
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // Load version-specific URL when version changes
                    val targetUrl = if (viewingVersion != null) {
                        "$BACKEND_BASE/api/canvas/${app.id}/render/$viewingVersion"
                    } else {
                        app.renderUrl
                    }
                    if (webView.url != targetUrl) {
                        webView.loadUrl(targetUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // ─── Version History Bottom Sheet ───
    if (showVersionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVersionSheet = false },
            containerColor = palette.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Version History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Current version
                val isCurrent = viewingVersion == null
                Surface(
                    color = if (isCurrent) palette.accent.copy(alpha = 0.1f)
                            else palette.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            viewingVersion = null
                            showVersionSheet = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "v${versions.size + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary, maxLines = 1)
                            Text("Current version", fontSize = 11.sp, color = palette.accent)
                        }
                        if (isCurrent) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Previous versions
                versions.reversed().forEach { ver ->
                    val isSelected = viewingVersion == ver.version
                    Surface(
                        color = if (isSelected) palette.accent.copy(alpha = 0.1f)
                                else palette.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                viewingVersion = ver.version
                                showVersionSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "v${ver.version}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) palette.accent else palette.textSecondary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    ver.title.ifBlank { "Version ${ver.version}" },
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = palette.textPrimary, maxLines = 1
                                )
                                Text(
                                    ver.htmlLength.formatFileSize(),
                                    fontSize = 11.sp, color = palette.textTertiary
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
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
    initialVersion: Int? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var detail by remember { mutableStateOf<CanvasAppDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Code, 1=Prompt, 2=Versions, 3=Log, 4=Info
    // null = current version, otherwise the version number from versions list
    var selectedVersion by remember { mutableStateOf(initialVersion) }

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

                // Parse edit history
                val editHistoryArray = a.optJSONArray("edit_history")
                val editEntries = mutableListOf<CanvasEditEntry>()
                if (editHistoryArray != null) {
                    for (i in 0 until editHistoryArray.length()) {
                        val entry = editHistoryArray.getJSONObject(i)
                        editEntries.add(
                            CanvasEditEntry(
                                instructions = entry.optString("instructions", ""),
                                timestamp = entry.optString("timestamp", ""),
                                configUsed = entry.optJSONObject("config_used")?.let { cfg ->
                                    val map = mutableMapOf<String, String>()
                                    cfg.keys().forEach { key -> map[key] = cfg.optString(key, "") }
                                    map.ifEmpty { null }
                                }
                            )
                        )
                    }
                }

                // Parse versions
                val versionsArray = a.optJSONArray("versions")
                val versionEntries = mutableListOf<CanvasVersion>()
                if (versionsArray != null) {
                    for (i in 0 until versionsArray.length()) {
                        val entry = versionsArray.getJSONObject(i)
                        versionEntries.add(
                            CanvasVersion(
                                version = entry.optInt("version", 0),
                                title = entry.optString("title", ""),
                                html = if (entry.isNull("html")) null else entry.optString("html", ""),
                                htmlLength = entry.optInt("html_length", 0),
                                timestamp = entry.optString("timestamp", "")
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
                    editHistory = editEntries,
                    versions = versionEntries,
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

    val tabTitles = listOf("Code", "Prompt", "Versions", "Log", "Info")

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
                // Show version indicator when viewing a previous version
                if (selectedVersion != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.accent.copy(alpha = 0.1f))
                            .clickable { selectedVersion = null }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Viewing Version ${selectedVersion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.accent
                        )
                        Text(
                            "✕ Back to current",
                            fontSize = 11.sp,
                            color = palette.accent.copy(alpha = 0.7f)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> CodeTabContent(detail!!, palette, selectedVersion)
                        1 -> PromptTabContent(detail!!, palette, selectedVersion)
                        2 -> VersionsTabContent(detail!!, app, palette, selectedVersion) { ver ->
                            selectedVersion = if (ver == null) null else ver
                            // Switch to Code tab to show the version's code
                            if (ver != null) selectedTab = 0
                        }
                        3 -> LogTabContent(detail!!, palette)
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
private fun CodeTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette, selectedVersion: Int? = null) {
    // Show version-specific code or current code
    val code = if (selectedVersion != null) {
        detail.versions.find { it.version == selectedVersion }?.html ?: detail.html
    } else detail.html

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
 * Prompt tab — shows original prompt and clean edit history.
 */
@Composable
private fun PromptTabContent(detail: CanvasAppDetail, palette: ApsaraColorPalette, selectedVersion: Int? = null) {
    val originalPrompt = detail.originalPrompt.ifBlank { detail.prompt }
    // Show edits up to selected version, or all if current
    val edits = if (selectedVersion != null) {
        detail.editHistory.take(selectedVersion.coerceAtMost(detail.editHistory.size))
    } else detail.editHistory

    if (originalPrompt.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No prompt recorded", fontSize = 14.sp, color = palette.textTertiary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Original prompt header
            Text(
                "Original Prompt",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Prompt content + config in a single styled card
            Surface(
                color = palette.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = originalPrompt,
                        fontSize = 14.sp,
                        color = palette.textPrimary,
                        lineHeight = 22.sp
                    )
                    // ── Collapsible config — inline inside the prompt card ──
                    if (!detail.configUsed.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            color = palette.textTertiary.copy(alpha = 0.1f),
                            thickness = 0.5.dp
                        )
                        var configExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { configExpanded = !configExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Config",
                                tint = palette.textTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (configExpanded) "▾ Generation Config" else "▸ Generation Config",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textTertiary
                            )
                        }
                        AnimatedVisibility(
                            visible = configExpanded,
                            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                        ) {
                            val configLabels = mapOf(
                                "model" to "Model",
                                "max_output_tokens" to "Max Tokens",
                                "thinking_level" to "Thinking",
                                "thinking_summaries" to "Summaries",
                                "temperature" to "Temperature"
                            )
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                detail.configUsed!!.forEach { (key, value) ->
                                    val label = configLabels[key] ?: key.replaceFirstChar { it.uppercase() }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(label, fontSize = 10.sp, color = palette.textTertiary)
                                        Text(
                                            value,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Edit history — each edit as a clean numbered card
            if (edits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Edit History",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                edits.forEachIndexed { index, edit ->
                    // Latest edit expanded by default, rest collapsed
                    var editExpanded by remember { mutableStateOf(index == edits.size - 1) }
                    Surface(
                        color = palette.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { editExpanded = !editExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accent
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Edit ${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textSecondary
                                    )
                                    if (edit.timestamp.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            formatTimestamp(edit.timestamp),
                                            fontSize = 10.sp,
                                            color = palette.textTertiary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Icon(
                                    if (editExpanded) Icons.Outlined.ExpandLess
                                    else Icons.Outlined.ExpandMore,
                                    contentDescription = if (editExpanded) "Collapse" else "Expand",
                                    tint = palette.textTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(
                                visible = editExpanded,
                                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = edit.instructions,
                                        fontSize = 14.sp,
                                        color = palette.textPrimary,
                                        lineHeight = 22.sp
                                    )
                                    // Per-edit config — collapsible
                                    if (!edit.configUsed.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(
                                            color = palette.textTertiary.copy(alpha = 0.1f),
                                            thickness = 0.5.dp
                                        )
                                        var editConfigExpanded by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { editConfigExpanded = !editConfigExpanded }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = "Config",
                                                tint = palette.textTertiary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (editConfigExpanded) "▾ Generation Config" else "▸ Generation Config",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = palette.textTertiary
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = editConfigExpanded,
                                            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                                        ) {
                                            val editConfigLabels = mapOf(
                                                "model" to "Model",
                                                "max_output_tokens" to "Max Tokens",
                                                "thinking_level" to "Thinking",
                                                "temperature" to "Temperature",
                                                "thinking_summaries" to "Summaries"
                                            )
                                            Column(
                                                modifier = Modifier.padding(top = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                edit.configUsed!!.forEach { (key, value) ->
                                                    val label = editConfigLabels[key] ?: key.replaceFirstChar { it.uppercase() }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(label, fontSize = 10.sp, color = palette.textTertiary)
                                                        Text(
                                                            value,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = palette.accent
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                    value = "${originalPrompt.split("\\s+".toRegex()).size}",
                    palette = palette
                )
                DetailChip(
                    label = "Characters",
                    value = "${originalPrompt.length}",
                    palette = palette
                )
                if (edits.isNotEmpty()) {
                    DetailChip(
                        label = "Edits",
                        value = "${edits.size}",
                        palette = palette
                    )
                }
            }
        }
    }
}

/**
 * Versions tab — shows version history timeline.
 */
@Composable
private fun VersionsTabContent(
    detail: CanvasAppDetail,
    app: CanvasApp,
    palette: ApsaraColorPalette,
    selectedVersion: Int? = null,
    onVersionSelected: (Int?) -> Unit = {}
) {
    val versions = detail.versions
    val currentVersion = versions.size + 1

    if (versions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = palette.textTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No version history yet", fontSize = 14.sp, color = palette.textTertiary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Versions are saved each time the canvas is edited",
                    fontSize = 12.sp,
                    color = palette.textTertiary.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Current version card (highlighted)
            val isCurrentSelected = selectedVersion == null
            Surface(
                color = palette.accent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isCurrentSelected) 1.5.dp else 0.5.dp,
                        color = palette.accent.copy(alpha = if (isCurrentSelected) 0.6f else 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onVersionSelected(null) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "v$currentVersion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            detail.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Current • ${detail.htmlLength.formatFileSize()}",
                            fontSize = 11.sp,
                            color = palette.accent
                        )
                    }
                    if (isCurrentSelected) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Selected",
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette.accent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider with label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = palette.textTertiary.copy(alpha = 0.15f)
                )
                Text(
                    "  Previous Versions  ",
                    fontSize = 10.sp,
                    color = palette.textTertiary.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = palette.textTertiary.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Previous versions (newest first)
            versions.reversed().forEach { ver ->
                val isSelected = selectedVersion == ver.version
                Surface(
                    color = if (isSelected) palette.accent.copy(alpha = 0.08f)
                            else palette.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .then(
                            if (isSelected) Modifier.border(
                                width = 1.dp,
                                color = palette.accent.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable { onVersionSelected(ver.version) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) palette.accent.copy(alpha = 0.2f)
                                    else palette.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "v${ver.version}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) palette.accent else palette.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ver.title.ifBlank { "Version ${ver.version}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                Text(
                                    ver.htmlLength.formatFileSize(),
                                    fontSize = 11.sp,
                                    color = palette.textTertiary
                                )
                                if (ver.timestamp.isNotBlank()) {
                                    Text(
                                        " • ${formatTimestamp(ver.timestamp)}",
                                        fontSize = 11.sp,
                                        color = palette.textTertiary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Selected",
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "View",
                                tint = palette.textTertiary.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailChip(
                    label = "Total Versions",
                    value = "$currentVersion",
                    palette = palette
                )
                DetailChip(
                    label = "Edits",
                    value = "${detail.editHistory.size}",
                    palette = palette
                )
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
