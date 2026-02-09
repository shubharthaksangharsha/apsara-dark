package com.shubharthak.apsaradark.live

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Manages WebSocket connection to the Apsara Dark backend.
 * Sends/receives JSON messages per the backend protocol.
 */
class LiveWebSocketClient {

    companion object {
        private const val TAG = "LiveWS"
    }

    enum class ConnectionState { IDLE, CONNECTING, WS_OPEN, LIVE_CONNECTED, ERROR, DISCONNECTED }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Incoming events
    private val _audioData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val audioData = _audioData.asSharedFlow()

    private val _textData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val textData = _textData.asSharedFlow()

    private val _inputTranscription = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val inputTranscription = _inputTranscription.asSharedFlow()

    private val _outputTranscription = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val outputTranscription = _outputTranscription.asSharedFlow()

    private val _interrupted = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val interrupted = _interrupted.asSharedFlow()

    private val _turnComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val turnComplete = _turnComplete.asSharedFlow()

    private val _thought = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val thought = _thought.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val error = _error.asSharedFlow()

    // Tool call events: list of {name, id} when Gemini invokes tools
    data class ToolCallEvent(val name: String, val id: String)
    private val _toolCall = MutableSharedFlow<List<ToolCallEvent>>(extraBufferCapacity = 16)
    val toolCall = _toolCall.asSharedFlow()

    // Tool result events: list of {id, name, result JSON, mode}
    data class ToolResultEvent(val id: String, val name: String, val result: String, val mode: String)
    private val _toolResults = MutableSharedFlow<List<ToolResultEvent>>(extraBufferCapacity = 16)
    val toolResults = _toolResults.asSharedFlow()

    // Session resumption updates
    data class SessionResumptionEvent(val resumable: Boolean, val hasHandle: Boolean)
    private val _sessionResumptionUpdate = MutableSharedFlow<SessionResumptionEvent>(extraBufferCapacity = 16)
    val sessionResumptionUpdate = _sessionResumptionUpdate.asSharedFlow()

    /**
     * Connect WebSocket and immediately send "connect" with the live config.
     */
    fun connect(url: String, config: Map<String, Any?>) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.LIVE_CONNECTED) {
            Log.w(TAG, "Already connecting/connected")
            return
        }

        _state.value = ConnectionState.CONNECTING

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _state.value = ConnectionState.WS_OPEN
                // Send connect command with config
                val msg = JsonObject().apply {
                    addProperty("type", "connect")
                    add("config", gson.toJsonTree(config))
                }
                ws.send(msg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _state.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _state.value = ConnectionState.ERROR
                _error.tryEmit(t.message ?: "Connection failed")
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            when (json.get("type")?.asString) {
                "connected" -> {
                    Log.d(TAG, "Gemini Live connected")
                    _state.value = ConnectionState.LIVE_CONNECTED
                }
                "disconnected" -> {
                    Log.d(TAG, "Gemini Live disconnected")
                    _state.value = ConnectionState.DISCONNECTED
                }
                "audio" -> {
                    val b64 = json.get("data")?.asString ?: return
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    _audioData.tryEmit(bytes)
                }
                "text" -> {
                    val t = json.get("text")?.asString ?: return
                    _textData.tryEmit(t)
                }
                "input_transcription" -> {
                    val t = json.get("text")?.asString ?: return
                    _inputTranscription.tryEmit(t)
                }
                "output_transcription" -> {
                    val t = json.get("text")?.asString ?: return
                    _outputTranscription.tryEmit(t)
                }
                "interrupted" -> {
                    _interrupted.tryEmit(Unit)
                }
                "turn_complete" -> {
                    _turnComplete.tryEmit(Unit)
                }
                "thought" -> {
                    val t = json.get("text")?.asString ?: return
                    _thought.tryEmit(t)
                }
                "error" -> {
                    val msg = json.get("message")?.asString ?: "Unknown error"
                    Log.e(TAG, "Server error: $msg")
                    _error.tryEmit(msg)
                }
                "tool_call" -> {
                    // Backend sends: { type: "tool_call", functionCalls: [{name, id, args}] }
                    val calls = json.getAsJsonArray("functionCalls")
                    if (calls != null) {
                        val events = calls.map { el ->
                            val obj = el.asJsonObject
                            ToolCallEvent(
                                name = obj.get("name")?.asString ?: "unknown",
                                id = obj.get("id")?.asString ?: ""
                            )
                        }
                        Log.d(TAG, "Tool call: ${events.map { it.name }}")
                        _toolCall.tryEmit(events)
                    }
                }
                "tool_results" -> {
                    // Backend sends: { type: "tool_results", results: [{id, name, response: {...}}], mode: "sync"|"async" }
                    val mode = json.get("mode")?.asString ?: "sync"
                    val results = json.getAsJsonArray("results")
                    if (results != null) {
                        val events = results.map { el ->
                            val obj = el.asJsonObject
                            ToolResultEvent(
                                id = obj.get("id")?.asString ?: "",
                                name = obj.get("name")?.asString ?: "unknown",
                                result = obj.get("response")?.toString() ?: "{}",
                                mode = mode
                            )
                        }
                        Log.d(TAG, "Tool results ($mode): ${events.map { it.name }}")
                        _toolResults.tryEmit(events)
                    }
                }
                "session_resumption_update" -> {
                    val resumable = json.get("resumable")?.asBoolean ?: false
                    val hasHandle = json.get("hasHandle")?.asBoolean ?: false
                    Log.d(TAG, "Session resumption update: resumable=$resumable, hasHandle=$hasHandle")
                    _sessionResumptionUpdate.tryEmit(SessionResumptionEvent(resumable, hasHandle))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    /** Send base64-encoded PCM audio chunk */
    fun sendAudio(pcmData: ByteArray) {
        val b64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val msg = """{"type":"audio","data":"$b64"}"""
        webSocket?.send(msg)
    }

    /** Send text message */
    fun sendText(text: String) {
        val msg = JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        }
        webSocket?.send(msg.toString())
    }

    /** Signal mic pause */
    fun sendAudioStreamEnd() {
        webSocket?.send("""{"type":"audio_stream_end"}""")
    }

    /** Disconnect */
    fun disconnect() {
        webSocket?.send("""{"type":"disconnect"}""")
        webSocket?.close(1000, "User ended session")
        webSocket = null
        _state.value = ConnectionState.IDLE
    }

    fun isConnected(): Boolean = _state.value == ConnectionState.LIVE_CONNECTED
}
