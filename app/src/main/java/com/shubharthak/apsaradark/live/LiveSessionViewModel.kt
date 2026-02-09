package com.shubharthak.apsaradark.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shubharthak.apsaradark.data.LiveSettingsManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

    var inputTranscript by mutableStateOf("")
        private set

    var outputTranscript by mutableStateOf("")
        private set

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
            }
        }.launchIn(viewModelScope)

        // Receive audio from Gemini → play it
        wsClient.audioData.onEach { bytes ->
            audioManager.enqueueAudio(bytes)
        }.launchIn(viewModelScope)

        // On interruption clear playback
        wsClient.interrupted.onEach {
            audioManager.clearPlaybackQueue()
        }.launchIn(viewModelScope)

        // Transcriptions
        wsClient.inputTranscription.onEach { text ->
            inputTranscript = text
        }.launchIn(viewModelScope)

        wsClient.outputTranscription.onEach { text ->
            outputTranscript = text
        }.launchIn(viewModelScope)

        // Errors
        wsClient.error.onEach { msg ->
            lastError = msg
            Log.e(TAG, "Error: $msg")
        }.launchIn(viewModelScope)
    }

    fun startLive() {
        if (liveState == LiveState.CONNECTING || liveState == LiveState.CONNECTED) return

        lastError = null
        inputTranscript = ""
        outputTranscript = ""
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
    }

    fun toggleMute() {
        audioManager.toggleMute()
        isMuted = audioManager.isMuted.value
    }

    fun sendText(text: String) {
        wsClient.sendText(text)
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
