package com.shubharthak.apsaradark.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Persists Gemini Live session settings to SharedPreferences.
 * Read by the WebSocket client when starting a live session.
 */
class LiveSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apsara_live_prefs", Context.MODE_PRIVATE)

    // Backend URL — fixed, not user-configurable
    val backendUrl = "wss://apsara-dark-backend.devshubh.me/live"

    // Model
    var model by mutableStateOf(prefs.getString("model", "gemini-2.5-flash-native-audio-preview-12-2025") ?: "gemini-2.5-flash-native-audio-preview-12-2025")
        private set

    // Voice
    var voice by mutableStateOf(prefs.getString("voice", "Kore") ?: "Kore")
        private set

    // Temperature
    var temperature by mutableFloatStateOf(prefs.getFloat("temperature", 0.7f))
        private set

    // System instruction
    var systemInstruction by mutableStateOf(prefs.getString("system_instruction", "") ?: "")
        private set

    // Toggles
    var affectiveDialog by mutableStateOf(prefs.getBoolean("affective_dialog", true))
        private set

    var proactiveAudio by mutableStateOf(prefs.getBoolean("proactive_audio", true))
        private set

    var inputTranscription by mutableStateOf(prefs.getBoolean("input_transcription", true))
        private set

    var outputTranscription by mutableStateOf(prefs.getBoolean("output_transcription", true))
        private set

    var contextCompression by mutableStateOf(prefs.getBoolean("context_compression", true))
        private set

    var sessionResumption by mutableStateOf(prefs.getBoolean("session_resumption", true))
        private set

    var googleSearch by mutableStateOf(prefs.getBoolean("google_search", true))
        private set

    var includeThoughts by mutableStateOf(prefs.getBoolean("include_thoughts", false))
        private set

    // Plugin/tool toggles — each tool can be independently enabled/disabled
    var toolServerInfo by mutableStateOf(prefs.getBoolean("tool_server_info", true))
        private set

    // Per-tool async/sync mode
    var toolServerInfoAsync by mutableStateOf(prefs.getBoolean("tool_server_info_async", false))
        private set

    // Apsara Canvas plugin
    var toolCanvas by mutableStateOf(prefs.getBoolean("tool_canvas", true))
        private set

    var toolCanvasAsync by mutableStateOf(prefs.getBoolean("tool_canvas_async", true))
        private set

    // Apsara Interpreter plugin
    var toolInterpreter by mutableStateOf(prefs.getBoolean("tool_interpreter", true))
        private set

    var toolInterpreterAsync by mutableStateOf(prefs.getBoolean("tool_interpreter_async", true))
        private set

    // ─── Interaction Settings (shared by Canvas, Interpreter, and future tools) ─────
    var interactionModel by mutableStateOf(prefs.getString("interaction_model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
        private set

    var interactionMaxOutputTokens by mutableStateOf(prefs.getInt("interaction_max_output_tokens", 65536))
        private set

    var interactionThinkingLevel by mutableStateOf(prefs.getString("interaction_thinking_level", "high") ?: "high")
        private set

    var interactionThinkingSummaries by mutableStateOf(prefs.getString("interaction_thinking_summaries", "auto") ?: "auto")
        private set

    var interactionTemperature by mutableFloatStateOf(prefs.getFloat("interaction_temperature", 0.7f))
        private set

    // Media resolution for video/image input (LOW, MEDIUM, HIGH)
    var mediaResolution by mutableStateOf(prefs.getString("media_resolution", "MEDIUM") ?: "MEDIUM")
        private set

    // General Settings
    var hapticFeedback by mutableStateOf(prefs.getBoolean("haptic_feedback", false))
        private set

    // Setters (named updateX to avoid JVM clash with private set)
    fun updateHapticFeedback(v: Boolean) { hapticFeedback = v; prefs.edit().putBoolean("haptic_feedback", v).apply() }
    fun updateModel(v: String) { model = v; prefs.edit().putString("model", v).apply() }
    fun updateVoice(v: String) { voice = v; prefs.edit().putString("voice", v).apply() }
    fun updateTemperature(v: Float) { temperature = v; prefs.edit().putFloat("temperature", v).apply() }
    fun updateSystemInstruction(v: String) { systemInstruction = v; prefs.edit().putString("system_instruction", v).apply() }
    fun clearSystemInstruction() { systemInstruction = ""; prefs.edit().putString("system_instruction", "").apply() }
    fun updateAffectiveDialog(v: Boolean) { affectiveDialog = v; prefs.edit().putBoolean("affective_dialog", v).apply() }
    fun updateProactiveAudio(v: Boolean) { proactiveAudio = v; prefs.edit().putBoolean("proactive_audio", v).apply() }
    fun updateInputTranscription(v: Boolean) { inputTranscription = v; prefs.edit().putBoolean("input_transcription", v).apply() }
    fun updateOutputTranscription(v: Boolean) { outputTranscription = v; prefs.edit().putBoolean("output_transcription", v).apply() }
    fun updateContextCompression(v: Boolean) { contextCompression = v; prefs.edit().putBoolean("context_compression", v).apply() }
    fun updateSessionResumption(v: Boolean) { sessionResumption = v; prefs.edit().putBoolean("session_resumption", v).apply() }
    fun updateGoogleSearch(v: Boolean) { googleSearch = v; prefs.edit().putBoolean("google_search", v).apply() }
    fun updateIncludeThoughts(v: Boolean) { includeThoughts = v; prefs.edit().putBoolean("include_thoughts", v).apply() }
    fun updateToolServerInfo(v: Boolean) { toolServerInfo = v; prefs.edit().putBoolean("tool_server_info", v).apply() }
    fun updateToolServerInfoAsync(v: Boolean) { toolServerInfoAsync = v; prefs.edit().putBoolean("tool_server_info_async", v).apply() }
    fun updateToolCanvas(v: Boolean) { toolCanvas = v; prefs.edit().putBoolean("tool_canvas", v).apply() }
    fun updateToolCanvasAsync(v: Boolean) { toolCanvasAsync = v; prefs.edit().putBoolean("tool_canvas_async", v).apply() }
    fun updateToolInterpreter(v: Boolean) { toolInterpreter = v; prefs.edit().putBoolean("tool_interpreter", v).apply() }
    fun updateToolInterpreterAsync(v: Boolean) { toolInterpreterAsync = v; prefs.edit().putBoolean("tool_interpreter_async", v).apply() }
    fun updateInteractionModel(v: String) { interactionModel = v; prefs.edit().putString("interaction_model", v).apply() }
    fun updateInteractionMaxOutputTokens(v: Int) { interactionMaxOutputTokens = v; prefs.edit().putInt("interaction_max_output_tokens", v).apply() }
    fun updateInteractionThinkingLevel(v: String) { interactionThinkingLevel = v; prefs.edit().putString("interaction_thinking_level", v).apply() }
    fun updateInteractionThinkingSummaries(v: String) { interactionThinkingSummaries = v; prefs.edit().putString("interaction_thinking_summaries", v).apply() }
    fun updateInteractionTemperature(v: Float) { interactionTemperature = v; prefs.edit().putFloat("interaction_temperature", v).apply() }
    fun updateMediaResolution(v: String) { mediaResolution = v; prefs.edit().putString("media_resolution", v).apply() }

    /** Build the config JSON map to send to the backend on connect. */
    fun buildConfigMap(): Map<String, Any?> {
        val config = mutableMapOf<String, Any?>()
        config["model"] = model
        config["voice"] = voice
        config["temperature"] = temperature.toDouble()
        config["responseModalities"] = listOf("AUDIO")
        config["enableAffectiveDialog"] = affectiveDialog
        config["proactiveAudio"] = proactiveAudio
        config["inputAudioTranscription"] = inputTranscription
        config["outputAudioTranscription"] = outputTranscription
        config["includeThoughts"] = includeThoughts
        config["mediaResolution"] = mediaResolution
        if (systemInstruction.isNotBlank()) {
            config["systemInstruction"] = systemInstruction
        }
        if (contextCompression) {
            config["contextWindowCompression"] = mapOf("slidingWindow" to emptyMap<String, Any>())
        }
        if (sessionResumption) {
            config["sessionResumption"] = emptyMap<String, Any>()
        }
        config["tools"] = mapOf(
            "googleSearch" to googleSearch,
            "functionCalling" to hasAnyToolEnabled()
        )

        // Send the list of enabled tool names and their async/sync mode to the backend
        val enabledTools = mutableListOf<String>()
        val toolAsyncModes = mutableMapOf<String, Boolean>()
        if (toolServerInfo) {
            enabledTools.add("get_server_info")
            toolAsyncModes["get_server_info"] = toolServerInfoAsync
        }
        if (toolCanvas) {
            enabledTools.add("apsara_canvas")
            enabledTools.add("list_canvases")
            enabledTools.add("get_canvas_detail")
            enabledTools.add("edit_canvas")
            toolAsyncModes["apsara_canvas"] = toolCanvasAsync
            toolAsyncModes["edit_canvas"] = toolCanvasAsync
        }
        if (toolInterpreter) {
            enabledTools.add("run_code")
            enabledTools.add("list_code_sessions")
            enabledTools.add("get_code_session")
            toolAsyncModes["run_code"] = toolInterpreterAsync
        }
        if (enabledTools.isNotEmpty()) {
            config["enabledTools"] = enabledTools
            config["toolAsyncModes"] = toolAsyncModes
        }

        // Interaction settings — shared by Canvas, Interpreter, and future tools
        config["interactionConfig"] = mapOf(
            "model" to interactionModel,
            "max_output_tokens" to interactionMaxOutputTokens,
            "thinking_level" to interactionThinkingLevel,
            "thinking_summaries" to interactionThinkingSummaries,
            "temperature" to interactionTemperature.toDouble()
        )

        return config
    }

    /** Check if any plugin tool is enabled */
    fun hasAnyToolEnabled(): Boolean = toolServerInfo || toolCanvas || toolInterpreter

    companion object {
        val availableVoices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
        val availableModels = listOf(
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )
        val availableMediaResolutions = listOf("LOW", "MEDIUM", "HIGH")
        val availableInteractionModels = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3-pro-preview",
            "gemini-3-flash-preview"
        )
        val availableThinkingLevels = listOf("minimal", "low", "medium", "high")
        val availableThinkingSummaries = listOf("auto", "none")
        val availableMaxOutputTokens = listOf(8192, 16384, 32768, 65536)
    }
}

val LocalLiveSettings = compositionLocalOf<LiveSettingsManager> {
    error("LiveSettingsManager not provided")
}
