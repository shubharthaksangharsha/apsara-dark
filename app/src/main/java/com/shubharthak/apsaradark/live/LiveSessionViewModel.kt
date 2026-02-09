package com.shubharthak.apsaradark.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shubharthak.apsaradark.data.LiveSettingsManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A single message in the live conversation.
 */
data class LiveMessage(
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val thought: String? = null  // Collapsible thought text, if any
) {
    enum class Role { USER, APSARA }
}

/**
 * Who is currently "talking" in the live session.
 */
enum class ActiveSpeaker { NONE, USER, APSARA }

/**
 * ViewModel that orchestrates the live session:
 * WebSocket ↔ Audio recording ↔ Audio playback.
 */
class LiveSessionViewModel(
    private val context: Context,
    private val liveSettings: LiveSettingsManager
) : ViewModel() {

    companion object {
        private const val TAG = "LiveSession"
    }

    enum class LiveState { IDLE, CONNECTING, CONNECTED, ERROR }

    val wsClient = LiveWebSocketClient()
    val audioManager = LiveAudioManager(context)

    var liveState by mutableStateOf(LiveState.IDLE)
        private set

    var isMuted by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    // Latest partial transcripts (for streaming display)
    var inputTranscript by mutableStateOf("")
        private set

    var outputTranscript by mutableStateOf("")
        private set

    // Conversation history for chat bubbles
    val messages = mutableStateListOf<LiveMessage>()

    // Who is currently speaking (for visualizer color)
    var activeSpeaker by mutableStateOf(ActiveSpeaker.NONE)
        private set

    // Accumulator for streaming output transcription
    private var currentOutputBuffer = StringBuilder()
    // Accumulator for streaming input transcription
    private var currentInputBuffer = StringBuilder()
    // Accumulator for thoughts (attached to next Apsara message)
    private var currentThoughtBuffer = StringBuilder()

    init {
        // Observe WebSocket state changes
        wsClient.state.onEach { wsState ->
            liveState = when (wsState) {
                LiveWebSocketClient.ConnectionState.IDLE -> LiveState.IDLE
                LiveWebSocketClient.ConnectionState.CONNECTING,
                LiveWebSocketClient.ConnectionState.WS_OPEN -> LiveState.CONNECTING
                LiveWebSocketClient.ConnectionState.LIVE_CONNECTED -> LiveState.CONNECTED
                LiveWebSocketClient.ConnectionState.ERROR -> LiveState.ERROR
                LiveWebSocketClient.ConnectionState.DISCONNECTED -> LiveState.IDLE
            }

            // When Gemini Live is connected, start recording + playback
            if (wsState == LiveWebSocketClient.ConnectionState.LIVE_CONNECTED) {
                startAudio()
            }

            // When disconnected or errored, stop audio immediately
            if (wsState == LiveWebSocketClient.ConnectionState.DISCONNECTED ||
                wsState == LiveWebSocketClient.ConnectionState.ERROR) {
                audioManager.stopRecording()
                audioManager.stopPlayback()
                activeSpeaker = ActiveSpeaker.NONE
            }
        }.launchIn(viewModelScope)

        // Receive audio from Gemini → play it, mark Apsara as speaking
        wsClient.audioData.onEach { bytes ->
            audioManager.enqueueAudio(bytes)
            activeSpeaker = ActiveSpeaker.APSARA
        }.launchIn(viewModelScope)

        // On interruption clear playback, user is speaking
        wsClient.interrupted.onEach {
            audioManager.clearPlaybackQueue()
            // Finalize any in-progress output message
            finalizeOutputMessage()
            activeSpeaker = ActiveSpeaker.USER
        }.launchIn(viewModelScope)

        // Turn complete → finalize output message
        wsClient.turnComplete.onEach {
            finalizeOutputMessage()
            activeSpeaker = ActiveSpeaker.NONE
        }.launchIn(viewModelScope)

        // Input transcriptions — user speech
        wsClient.inputTranscription.onEach { text ->
            inputTranscript = text
            activeSpeaker = ActiveSpeaker.USER

            // Accumulate and add/update as user message
            currentInputBuffer.append(text)
            val fullText = currentInputBuffer.toString().trim()
            if (fullText.isNotEmpty()) {
                val lastUserIdx = messages.indexOfLast { it.role == LiveMessage.Role.USER && it.isStreaming }
                if (lastUserIdx >= 0) {
                    messages[lastUserIdx] = LiveMessage(LiveMessage.Role.USER, fullText, isStreaming = true)
                } else {
                    messages.add(LiveMessage(LiveMessage.Role.USER, fullText, isStreaming = true))
                }
            }
        }.launchIn(viewModelScope)

        // Output transcriptions — Apsara speech
        wsClient.outputTranscription.onEach { text ->
            outputTranscript = text
            activeSpeaker = ActiveSpeaker.APSARA

            // Accumulate and add/update as Apsara message
            currentOutputBuffer.append(text)
            val fullText = currentOutputBuffer.toString().trim()
            if (fullText.isNotEmpty()) {
                val thoughtText = currentThoughtBuffer.toString().trim().ifEmpty { null }
                val lastApsaraIdx = messages.indexOfLast { it.role == LiveMessage.Role.APSARA && it.isStreaming }
                if (lastApsaraIdx >= 0) {
                    messages[lastApsaraIdx] = LiveMessage(LiveMessage.Role.APSARA, fullText, isStreaming = true, thought = thoughtText)
                } else {
                    messages.add(LiveMessage(LiveMessage.Role.APSARA, fullText, isStreaming = true, thought = thoughtText))
                }
            }
        }.launchIn(viewModelScope)

        // Thoughts — model's reasoning process (accumulate, attach to next Apsara message)
        wsClient.thought.onEach { text ->
            currentThoughtBuffer.append(text)
        }.launchIn(viewModelScope)

        // Errors
        wsClient.error.onEach { msg ->
            lastError = msg
            Log.e(TAG, "Error: $msg")
        }.launchIn(viewModelScope)
    }

    private fun finalizeOutputMessage() {
        // Finalize the current streaming Apsara message and attach any thoughts
        val idx = messages.indexOfLast { it.role == LiveMessage.Role.APSARA && it.isStreaming }
        if (idx >= 0) {
            val thoughtText = currentThoughtBuffer.toString().trim().ifEmpty { null }
            messages[idx] = messages[idx].copy(isStreaming = false, thought = thoughtText)
        }
        currentOutputBuffer.clear()
        currentThoughtBuffer.clear()
        outputTranscript = ""

        // Also finalize any streaming user message
        finalizeInputMessage()
    }

    private fun finalizeInputMessage() {
        val idx = messages.indexOfLast { it.role == LiveMessage.Role.USER && it.isStreaming }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(isStreaming = false)
        }
        currentInputBuffer.clear()
        inputTranscript = ""
    }

    fun startLive() {
        if (liveState == LiveState.CONNECTING || liveState == LiveState.CONNECTED) return

        lastError = null
        inputTranscript = ""
        outputTranscript = ""
        messages.clear()
        currentInputBuffer.clear()
        currentOutputBuffer.clear()
        currentThoughtBuffer.clear()
        activeSpeaker = ActiveSpeaker.NONE
        val config = liveSettings.buildConfigMap()
        wsClient.connect(liveSettings.backendUrl, config)
    }

    private fun startAudio() {
        audioManager.startPlayback()
        if (audioManager.hasPermission()) {
            audioManager.startRecording { pcmChunk ->
                wsClient.sendAudio(pcmChunk)
            }
        }
    }

    fun stopLive() {
        audioManager.stopRecording()
        audioManager.stopPlayback()
        wsClient.disconnect()
        liveState = LiveState.IDLE
        isMuted = false
        activeSpeaker = ActiveSpeaker.NONE
    }

    fun toggleMute() {
        audioManager.toggleMute()
        isMuted = audioManager.isMuted.value
    }

    fun sendText(text: String) {
        wsClient.sendText(text)
        // Add as user message immediately
        messages.add(LiveMessage(LiveMessage.Role.USER, text, isStreaming = false))
    }

    override fun onCleared() {
        super.onCleared()
        stopLive()
        audioManager.release()
    }

    class Factory(
        private val context: Context,
        private val liveSettings: LiveSettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LiveSessionViewModel(context.applicationContext, liveSettings) as T
        }
    }
}
