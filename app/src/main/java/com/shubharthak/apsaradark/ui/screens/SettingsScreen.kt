package com.shubharthak.apsaradark.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubharthak.apsaradark.data.LiveSettingsManager
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val palette = themeManager.currentTheme
    val liveSettings = LocalLiveSettings.current

    var generalExpanded by remember { mutableStateOf(false) }
    var themesExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var interactionExpanded by remember { mutableStateOf(false) }

    val generalArrowRotation by animateFloatAsState(
        targetValue = if (generalExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "generalArrow"
    )
    val themeArrowRotation by animateFloatAsState(
        targetValue = if (themesExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "themeArrow"
    )
    val liveArrowRotation by animateFloatAsState(
        targetValue = if (liveExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "liveArrow"
    )
    val interactionArrowRotation by animateFloatAsState(
        targetValue = if (interactionExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "interactionArrow"
    )

    Scaffold(
        containerColor = palette.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ─── General Settings — expandable header ──────────────────
            item {
                SectionHeader(
                    title = "General Settings",
                    isExpanded = generalExpanded,
                    rotation = generalArrowRotation,
                    onClick = { generalExpanded = !generalExpanded },
                    palette = palette
                )
            }

            // General Settings panel — collapsible
            item {
                AnimatedVisibility(
                    visible = generalExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsToggle(
                            label = "Haptic Feedback",
                            description = "Make sure you turn on Output Transcriptions",
                            checked = liveSettings.hapticFeedback,
                            onCheckedChange = { liveSettings.updateHapticFeedback(it) },
                            palette = palette
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ─── Themes — expandable header ─────────────────────────
            item {
                SectionHeader(
                    title = "Themes",
                    isExpanded = themesExpanded,
                    rotation = themeArrowRotation,
                    onClick = { themesExpanded = !themesExpanded },
                    palette = palette
                )
            }

            // Themes grid — collapsible
            item {
                AnimatedVisibility(
                    visible = themesExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
                ) {
                    val themes = AppThemes.all
                    val rows = themes.chunked(2)

                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rows.forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowThemes.forEach { theme ->
                                    ThemeChip(
                                        name = theme.name,
                                        isSelected = theme.name == palette.name,
                                        onClick = { themeManager.setTheme(theme) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowThemes.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ─── Live Settings — expandable header ──────────────────
            item {
                SectionHeader(
                    title = "Live Settings",
                    isExpanded = liveExpanded,
                    rotation = liveArrowRotation,
                    onClick = { liveExpanded = !liveExpanded },
                    palette = palette
                )
            }

            // Live Settings panel — collapsible
            item {
                AnimatedVisibility(
                    visible = liveExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
                ) {
                    LiveSettingsPanel(liveSettings = liveSettings, palette = palette)
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ─── Interaction Settings — expandable header ───────────
            item {
                SectionHeader(
                    title = "Interaction Settings",
                    isExpanded = interactionExpanded,
                    rotation = interactionArrowRotation,
                    onClick = { interactionExpanded = !interactionExpanded },
                    palette = palette
                )
            }

            // Interaction Settings panel — collapsible
            item {
                AnimatedVisibility(
                    visible = interactionExpanded,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(150))
                ) {
                    InteractionSettingsPanel(liveSettings = liveSettings, palette = palette)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ─── Section Header ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    rotation: Float,
    onClick: () -> Unit,
    palette: ApsaraColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = palette.textTertiary,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}

// ─── Live Settings Panel ────────────────────────────────────────────────────

@Composable
private fun LiveSettingsPanel(
    liveSettings: LiveSettingsManager,
    palette: ApsaraColorPalette
) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Model selector
        SettingsDropdown(
            label = "Model",
            value = liveSettings.model,
            options = LiveSettingsManager.availableModels,
            onSelect = { liveSettings.updateModel(it) },
            palette = palette
        )

        // Voice selector
        SettingsDropdown(
            label = "Voice",
            value = liveSettings.voice,
            options = LiveSettingsManager.availableVoices,
            onSelect = { liveSettings.updateVoice(it) },
            palette = palette
        )

        // Temperature slider
        SettingsSlider(
            label = "Temperature",
            value = liveSettings.temperature,
            onValueChange = { liveSettings.updateTemperature(it) },
            valueRange = 0f..2f,
            palette = palette
        )

        // System instruction with Clear button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Instruction",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textTertiary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (liveSettings.systemInstruction.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { liveSettings.clearSystemInstruction() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box {
                if (liveSettings.systemInstruction.isEmpty()) {
                    Text(
                        text = "Custom personality or instructions...",
                        fontSize = 14.sp,
                        color = palette.textTertiary.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = liveSettings.systemInstruction,
                    onValueChange = { liveSettings.updateSystemInstruction(it) },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Toggles
        SettingsToggle(
            label = "Affective Dialog",
            description = "Emotion-aware responses",
            checked = liveSettings.affectiveDialog,
            onCheckedChange = { liveSettings.updateAffectiveDialog(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Proactive Audio",
            description = "Model decides when to respond",
            checked = liveSettings.proactiveAudio,
            onCheckedChange = { liveSettings.updateProactiveAudio(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Input Transcription",
            description = "Transcribe your speech",
            checked = liveSettings.inputTranscription,
            onCheckedChange = { liveSettings.updateInputTranscription(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Output Transcription",
            description = "Transcribe Apsara's speech",
            checked = liveSettings.outputTranscription,
            onCheckedChange = { liveSettings.updateOutputTranscription(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Context Compression",
            description = "Unlimited session length via sliding window",
            checked = liveSettings.contextCompression,
            onCheckedChange = { liveSettings.updateContextCompression(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Session Resumption",
            description = "Auto-reconnect without losing context",
            checked = liveSettings.sessionResumption,
            onCheckedChange = { liveSettings.updateSessionResumption(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Google Search",
            description = "Allow model to search the web",
            checked = liveSettings.googleSearch,
            onCheckedChange = { liveSettings.updateGoogleSearch(it) },
            palette = palette
        )

        SettingsToggle(
            label = "Include Thoughts",
            description = "Show model's thinking process",
            checked = liveSettings.includeThoughts,
            onCheckedChange = { liveSettings.updateIncludeThoughts(it) },
            palette = palette
        )

        // Media Resolution
        SettingsDropdown(
            label = "Media Resolution",
            value = liveSettings.mediaResolution,
            options = LiveSettingsManager.availableMediaResolutions,
            onSelect = { liveSettings.updateMediaResolution(it) },
            palette = palette
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Interaction Settings Panel (Canvas, Interpreter, etc.) ─────────────────

@Composable
private fun InteractionSettingsPanel(
    liveSettings: LiveSettingsManager,
    palette: ApsaraColorPalette
) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Description
        Text(
            text = "Shared settings for Canvas, Interpreter, and other Interactions API tools.",
            fontSize = 12.sp,
            color = palette.textTertiary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Model selector
        SettingsDropdown(
            label = "Interaction Model",
            value = liveSettings.interactionModel,
            options = LiveSettingsManager.availableInteractionModels,
            onSelect = { liveSettings.updateInteractionModel(it) },
            palette = palette
        )

        // Max output tokens
        SettingsDropdown(
            label = "Max Output Tokens",
            value = liveSettings.interactionMaxOutputTokens.toString(),
            options = LiveSettingsManager.availableMaxOutputTokens.map { it.toString() },
            onSelect = { liveSettings.updateInteractionMaxOutputTokens(it.toIntOrNull() ?: 65536) },
            palette = palette
        )

        // Temperature slider
        SettingsSlider(
            label = "Interaction Temperature",
            value = liveSettings.interactionTemperature,
            onValueChange = { liveSettings.updateInteractionTemperature(it) },
            valueRange = 0f..2f,
            palette = palette
        )

        // Thinking level
        SettingsDropdown(
            label = "Thinking Level",
            value = liveSettings.interactionThinkingLevel,
            options = LiveSettingsManager.availableThinkingLevels,
            onSelect = { liveSettings.updateInteractionThinkingLevel(it) },
            palette = palette
        )

        // Thinking summaries
        SettingsDropdown(
            label = "Thinking Summaries",
            value = liveSettings.interactionThinkingSummaries,
            options = LiveSettingsManager.availableThinkingSummaries,
            onSelect = { liveSettings.updateInteractionThinkingSummaries(it) },
            palette = palette
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Settings Components ────────────────────────────────────────────────────

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    palette: ApsaraColorPalette,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    placeholder: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textTertiary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = palette.textTertiary.copy(alpha = 0.5f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = palette.textPrimary
                ),
                cursorBrush = SolidColor(palette.accent),
                singleLine = singleLine,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    palette: ApsaraColorPalette,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val contentAlpha = if (enabled) 1f else 0.4f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceContainer)
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textTertiary.copy(alpha = contentAlpha),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = palette.textPrimary.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Select",
                tint = palette.textTertiary.copy(alpha = contentAlpha),
                modifier = Modifier.size(18.dp)
            )
        }

        if (enabled) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(palette.surfaceContainerHigh)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                fontSize = 14.sp,
                                color = if (option == value) palette.accent else palette.textPrimary,
                                fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    palette: ApsaraColorPalette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textTertiary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%.1f", value),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: ApsaraColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceContainer)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = palette.textPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = palette.textTertiary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.accent,
                checkedTrackColor = palette.accentSubtle,
                uncheckedThumbColor = palette.textTertiary,
                uncheckedTrackColor = palette.surfaceContainerHigh,
                uncheckedBorderColor = palette.surfaceContainerHighest
            )
        )
    }
}

// ─── Theme Chip ─────────────────────────────────────────────────────────────

@Composable
private fun ThemeChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalThemeManager.current.currentTheme

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) palette.accentSubtle
                else palette.surfaceContainer
            )
            .border(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) palette.accent.copy(alpha = 0.5f)
                else palette.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) palette.accent else palette.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}
