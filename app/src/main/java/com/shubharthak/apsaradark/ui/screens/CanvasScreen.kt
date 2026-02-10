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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shubharthak.apsaradark.data.CanvasApp
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

    // If an app is selected, show the WebView viewer
    if (selectedApp != null) {
        CanvasViewer(
            app = selectedApp!!,
            palette = palette,
            onBack = { selectedApp = null }
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
    onBack: () -> Unit
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
                        webViewClient = WebViewClient()
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
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
