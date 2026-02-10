package com.shubharthak.apsaradark.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.data.CodeSession
import com.shubharthak.apsaradark.data.CodeSessionDetail
import com.shubharthak.apsaradark.data.CodeSessionImage
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
fun InterpreterScreen(
    onBack: () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<CodeSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSession by remember { mutableStateOf<CodeSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<CodeSession?>(null) }

    fun loadSessions() {
        scope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("$BACKEND_BASE/api/interpreter")
                    val connection = url.openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val response = connection.getInputStream().bufferedReader().readText()
                    val json = JSONObject(response)
                    val sessionsArray = json.getJSONArray("sessions")
                    val list = mutableListOf<CodeSession>()
                    for (i in 0 until sessionsArray.length()) {
                        val s = sessionsArray.getJSONObject(i)
                        list.add(
                            CodeSession(
                                id = s.getString("id"),
                                title = s.getString("title"),
                                prompt = s.optString("prompt", ""),
                                status = s.optString("status", "unknown"),
                                hasImages = s.optBoolean("has_images", false),
                                imageCount = s.optInt("image_count", 0),
                                createdAt = s.optString("created_at", "")
                            )
                        )
                    }
                    list
                }
                sessions = result
            } catch (_: Exception) {
                sessions = emptyList()
            }
            isLoading = false
        }
    }

    fun deleteSession(session: CodeSession) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL("$BACKEND_BASE/api/interpreter/${session.id}")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "DELETE"
                    connection.connectTimeout = 10000
                    connection.responseCode
                    connection.disconnect()
                }
                sessions = sessions.filter { it.id != session.id }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { loadSessions() }

    // If a session is selected, show the detail viewer
    if (selectedSession != null) {
        CodeSessionDetailViewer(
            session = selectedSession!!,
            palette = palette,
            onBack = { selectedSession = null }
        )
        return
    }

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Code",
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
                    IconButton(onClick = { loadSessions() }) {
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
                CircularProgressIndicator(color = palette.accent)
            }
        } else if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = palette.textTertiary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No code sessions yet",
                        color = palette.textTertiary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ask Apsara to run Python code or create visualizations",
                        color = palette.textTertiary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions) { session ->
                    CodeSessionCard(
                        session = session,
                        palette = palette,
                        onClick = { selectedSession = session },
                        onDelete = { showDeleteDialog = session }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Code Session?", color = palette.textPrimary) },
            text = { Text("\"${showDeleteDialog?.title}\" will be permanently deleted.", color = palette.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog?.let { deleteSession(it) }
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = palette.textTertiary)
                }
            },
            containerColor = palette.surfaceContainer,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun CodeSessionCard(
    session: CodeSession,
    palette: ApsaraColorPalette,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (session.status) {
        "completed" -> palette.accent
        "error" -> MaterialTheme.colorScheme.error
        else -> palette.textTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accentSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (session.hasImages) Icons.Outlined.Image else Icons.Outlined.Code,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = session.status.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = palette.textTertiary
                )
                if (session.imageCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${session.imageCount} image${if (session.imageCount > 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = palette.textTertiary
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = palette.textTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Detail Viewer ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSessionDetailViewer(
    session: CodeSession,
    palette: ApsaraColorPalette,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var detail by remember { mutableStateOf<CodeSessionDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedImageIndex by remember { mutableStateOf<Int?>(null) }

    // Fetch detail
    LaunchedEffect(session.id) {
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                val url = URL("$BACKEND_BASE/api/interpreter/${session.id}")
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                val response = connection.getInputStream().bufferedReader().readText()
                val json = JSONObject(response)
                val images = mutableListOf<CodeSessionImage>()
                val imagesArray = json.optJSONArray("images")
                if (imagesArray != null) {
                    for (i in 0 until imagesArray.length()) {
                        val img = imagesArray.getJSONObject(i)
                        images.add(
                            CodeSessionImage(
                                index = img.getInt("index"),
                                mimeType = img.optString("mime_type", "image/png"),
                                url = img.optString("url", "")
                            )
                        )
                    }
                }
                CodeSessionDetail(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    prompt = json.optString("prompt", ""),
                    originalPrompt = json.optString("original_prompt", json.optString("prompt", "")),
                    code = json.optString("code", ""),
                    output = json.optString("output", ""),
                    images = images,
                    previousCode = if (json.isNull("previous_code")) null else json.optString("previous_code", "").ifEmpty { null },
                    previousOutput = if (json.isNull("previous_output")) null else json.optString("previous_output", "").ifEmpty { null },
                    editInstructions = if (json.isNull("edit_instructions")) null else json.optString("edit_instructions", "").ifEmpty { null },
                    editCount = json.optInt("edit_count", 0),
                    status = json.optString("status", ""),
                    error = json.optString("error", ""),
                    createdAt = json.optString("created_at", ""),
                    updatedAt = json.optString("updated_at", "")
                )
            }
            detail = result
        } catch (_: Exception) { }
        isLoading = false
    }

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
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
                CircularProgressIndicator(color = palette.accent)
            }
        } else if (detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Failed to load session details", color = palette.textTertiary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prompt
                if (detail!!.prompt.isNotBlank()) {
                    item {
                        val hasBeenEdited = detail!!.editCount > 0 &&
                            detail!!.originalPrompt.isNotBlank() &&
                            detail!!.originalPrompt != detail!!.prompt
                        DetailSection(
                            title = if (hasBeenEdited) "Prompt (with edits)" else "Prompt",
                            palette = palette
                        ) {
                            Text(
                                text = detail!!.prompt,
                                fontSize = 13.sp,
                                color = palette.textSecondary,
                                lineHeight = 18.sp
                            )
                            // Show original prompt collapsible if edited
                            if (hasBeenEdited) {
                                var showOriginalPrompt by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showOriginalPrompt = !showOriginalPrompt }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showOriginalPrompt) "▾ Original Prompt" else "▸ Original Prompt",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent
                                    )
                                }
                                AnimatedVisibility(
                                    visible = showOriginalPrompt,
                                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Text(
                                        text = detail!!.originalPrompt,
                                        fontSize = 12.sp,
                                        color = palette.textTertiary,
                                        lineHeight = 17.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.surface)
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Code
                if (detail!!.code.isNotBlank()) {
                    item {
                        DetailSection(
                            title = "Python Code",
                            palette = palette,
                            trailing = {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(detail!!.code))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = palette.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = detail!!.code,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textPrimary,
                                    lineHeight = 18.sp
                                )
                            }

                            // Show previous code collapsible if session was edited
                            if (!detail!!.previousCode.isNullOrBlank()) {
                                var showPrevCode by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showPrevCode = !showPrevCode }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showPrevCode) "▾ Previous Code" else "▸ Previous Code",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(before edit)",
                                        fontSize = 11.sp,
                                        color = palette.textTertiary
                                    )
                                }
                                AnimatedVisibility(
                                    visible = showPrevCode,
                                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.surface)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = detail!!.previousCode ?: "",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.textTertiary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Output
                if (detail!!.output.isNotBlank()) {
                    item {
                        DetailSection(
                            title = "Output",
                            palette = palette,
                            trailing = {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(detail!!.output))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy output",
                                        tint = palette.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = detail!!.output,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.accent,
                                    lineHeight = 18.sp
                                )
                            }

                            // Show previous output collapsible if session was edited
                            if (!detail!!.previousOutput.isNullOrBlank()) {
                                var showPrevOutput by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showPrevOutput = !showPrevOutput }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showPrevOutput) "▾ Previous Output" else "▸ Previous Output",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent
                                    )
                                }
                                AnimatedVisibility(
                                    visible = showPrevOutput,
                                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.surface)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = detail!!.previousOutput ?: "",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.textTertiary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Images
                if (detail!!.images.isNotEmpty()) {
                    item {
                        DetailSection(
                            title = "Images (${detail!!.images.size})",
                            palette = palette
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail!!.images.forEachIndexed { index, img ->
                                    ImagePreviewCard(
                                        imageUrl = "$BACKEND_BASE${img.url}",
                                        index = index,
                                        isExpanded = expandedImageIndex == index,
                                        onToggle = {
                                            expandedImageIndex = if (expandedImageIndex == index) null else index
                                        },
                                        palette = palette
                                    )
                                }
                            }
                        }
                    }
                }

                // Error
                if (!detail!!.error.isNullOrBlank()) {
                    item {
                        DetailSection(title = "Error", palette = palette) {
                            Text(
                                text = detail!!.error ?: "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Metadata
                item {
                    DetailSection(title = "Info", palette = palette) {
                        Text("Status: ${detail!!.status}", fontSize = 12.sp, color = palette.textTertiary)
                        Text("Created: ${detail!!.createdAt}", fontSize = 12.sp, color = palette.textTertiary)
                        if (detail!!.updatedAt.isNotBlank()) {
                            Text("Updated: ${detail!!.updatedAt}", fontSize = 12.sp, color = palette.textTertiary)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    palette: ApsaraColorPalette,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textTertiary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ImagePreviewCard(
    imageUrl: String,
    index: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    palette: ApsaraColorPalette
) {
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var imageLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(
                width = 0.5.dp,
                color = palette.surfaceContainerHighest,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        // Header — collapsible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Image ${index + 1}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isExpanded) "Collapse" else "Expand",
                fontSize = 12.sp,
                color = palette.accent
            )
        }

        // Image content — expandable
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Generated image ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else if (imageLoading) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = "Tap to load image",
                        fontSize = 12.sp,
                        color = palette.textTertiary,
                        modifier = Modifier.clickable {
                            imageLoading = true
                            scope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        val url = URL(imageUrl)
                                        val stream = url.openStream()
                                        BitmapFactory.decodeStream(stream)
                                    }
                                    imageBitmap = bitmap?.asImageBitmap()
                                } catch (_: Exception) { }
                                imageLoading = false
                            }
                        }
                    )
                }
            }
        }

        // Auto-load when expanded
        LaunchedEffect(isExpanded) {
            if (isExpanded && imageBitmap == null && !imageLoading) {
                imageLoading = true
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val url = URL(imageUrl)
                        val stream = url.openStream()
                        BitmapFactory.decodeStream(stream)
                    }
                    imageBitmap = bitmap?.asImageBitmap()
                } catch (_: Exception) { }
                imageLoading = false
            }
        }
    }
}
