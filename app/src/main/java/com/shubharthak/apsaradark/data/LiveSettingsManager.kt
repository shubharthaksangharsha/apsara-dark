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

    // Response modality
    var responseModality by mutableStateOf(prefs.getString("response_modality", "AUDIO") ?: "AUDIO")
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

    var googleSearch by mutableStateOf(prefs.getBoolean("google_search", true))
        private set

    var includeThoughts by mutableStateOf(prefs.getBoolean("include_thoughts", false))
        private set

    // Setters (named updateX to avoid JVM clash with private set)
    fun updateModel(v: String) { model = v; prefs.edit().putString("model", v).apply() }
    fun updateVoice(v: String) { voice = v; prefs.edit().putString("voice", v).apply() }
    fun updateTemperature(v: Float) { temperature = v; prefs.edit().putFloat("temperature", v).apply() }
    fun updateSystemInstruction(v: String) { systemInstruction = v; prefs.edit().putString("system_instruction", v).apply() }
    fun updateResponseModality(v: String) { responseModality = v; prefs.edit().putString("response_modality", v).apply() }
    fun updateAffectiveDialog(v: Boolean) { affectiveDialog = v; prefs.edit().putBoolean("affective_dialog", v).apply() }
    fun updateProactiveAudio(v: Boolean) { proactiveAudio = v; prefs.edit().putBoolean("proactive_audio", v).apply() }
    fun updateInputTranscription(v: Boolean) { inputTranscription = v; prefs.edit().putBoolean("input_transcription", v).apply() }
    fun updateOutputTranscription(v: Boolean) { outputTranscription = v; prefs.edit().putBoolean("output_transcription", v).apply() }
    fun updateContextCompression(v: Boolean) { contextCompression = v; prefs.edit().putBoolean("context_compression", v).apply() }
    fun updateGoogleSearch(v: Boolean) { googleSearch = v; prefs.edit().putBoolean("google_search", v).apply() }
    fun updateIncludeThoughts(v: Boolean) { includeThoughts = v; prefs.edit().putBoolean("include_thoughts", v).apply() }

    /** Build the config JSON map to send to the backend on connect. */
    fun buildConfigMap(): Map<String, Any?> {
        val config = mutableMapOf<String, Any?>()
        config["model"] = model
        // Only send voice config for AUDIO modality
        if (responseModality == "AUDIO") {
            config["voice"] = voice
        }
        config["temperature"] = temperature.toDouble()
        config["responseModalities"] = listOf(responseModality)
        config["enableAffectiveDialog"] = affectiveDialog
        config["proactiveAudio"] = proactiveAudio
        config["inputAudioTranscription"] = inputTranscription
        config["outputAudioTranscription"] = outputTranscription
        config["includeThoughts"] = includeThoughts
        if (systemInstruction.isNotBlank()) {
            config["systemInstruction"] = systemInstruction
        }
        if (contextCompression) {
            config["contextWindowCompression"] = mapOf("slidingWindow" to emptyMap<String, Any>())
        }
        config["tools"] = mapOf(
            "googleSearch" to googleSearch,
            "functionCalling" to false
        )
        return config
    }

    companion object {
        val availableVoices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
        val availableModels = listOf(
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )
    }
}

val LocalLiveSettings = compositionLocalOf<LiveSettingsManager> {
    error("LiveSettingsManager not provided")
}
