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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * A single message in the live conversation.
 */
data class LiveMessage(
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val thought: String? = null,
    // Tool call fields (only for TOOL_CALL role — kept for backward compat)
    val toolName: String? = null,
    val toolCallId: String? = null,
    val toolStatus: ToolStatus = ToolStatus.NONE,
    val toolMode: String? = null,        // "sync" or "async"
    val toolResult: String? = null,       // JSON result string, shown on tap
    // Embedded tool calls shown inside APSARA messages
    val toolCalls: List<EmbeddedToolCall> = emptyList()
) {
    enum class Role { USER, APSARA, TOOL_CALL }
    enum class ToolStatus { NONE, RUNNING, COMPLETED, ERROR }
}

/**
 * Represents a tool call embedded within an Apsara message.
 */
data class EmbeddedToolCall(
    val name: String,
    val id: String,
    val status: LiveMessage.ToolStatus = LiveMessage.ToolStatus.RUNNING,
    val mode: String = "sync",     // "sync" or "async"
    val result: String? = null,
    // Interpreter-specific: code and output shown inline as collapsible sections
    val codeBlock: String? = null,
    val codeOutput: String? = null,
    val codeImages: List<CodeImageInfo> = emptyList(),
    // Canvas-specific: streaming HTML and final render URL
    val canvasId: String? = null,
    val canvasRenderUrl: String? = null
)

/**
 * Image info from an interpreter code execution.
 */
data class CodeImageInfo(
    val index: Int,
    val mimeType: String = "image/png",
    val url: String = ""
)

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

    // Uses custom setter to push mute state to the notification bridge
    private var _isMuted by mutableStateOf(false)
    var isMuted: Boolean
        get() = _isMuted
        private set(value) {
            _isMuted = value
            LiveSessionBridge.updateMuted(value)
        }

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
    // Uses custom setter to also push state to the notification bridge
    private var _activeSpeaker by mutableStateOf(ActiveSpeaker.NONE)
    var activeSpeaker: ActiveSpeaker
        get() = _activeSpeaker
        private set(value) {
            _activeSpeaker = value
            LiveSessionBridge.updateSpeaker(value)
        }

    // ─── Video streaming state ─────────────────────────────
    var isVideoActive by mutableStateOf(false)
        private set

    fun startVideo() {
        isVideoActive = true
    }

    fun stopVideo() {
        isVideoActive = false
    }

    /** Send a JPEG video frame to the backend */
    fun sendVideoFrame(jpegData: ByteArray) {
        if (isVideoActive && liveState == LiveState.CONNECTED) {
            wsClient.sendVideo(jpegData)
        }
    }

    // Session resumption — tracks if current session was resumed from a previous one
    var sessionResumed by mutableStateOf(false)
        private set

    // Track if we've been connected before in this live session (for detecting reconnections)
    private var hasBeenConnected = false
    private var hasResumptionHandle = false
    private var shownResumptionReady = false
    // Prevent duplicate "Session resumed" messages during GoAway reconnect cycles
    private var pendingGoAwayReconnect = false
    private var goAwayMessageIndex = -1
    private var goAwayTimeoutJob: Job? = null

    // Canvas notification — emits the title of the canvas when it becomes ready
    data class CanvasNotification(val title: String, val status: String)
    private val _canvasNotification = MutableSharedFlow<CanvasNotification>(extraBufferCapacity = 8)
    val canvasNotification = _canvasNotification.asSharedFlow()

    // Accumulator for streaming output transcription
    private var currentOutputBuffer = StringBuilder()
    // Accumulator for streaming input transcription
    private var currentInputBuffer = StringBuilder()
    // Accumulator for thoughts (attached to next Apsara message)
    private var currentThoughtBuffer = StringBuilder()
    // Accumulator for tool calls (attached to next/current Apsara message)
    private val pendingToolCalls = mutableListOf<EmbeddedToolCall>()

    init {
        // Observe WebSocket state changes
        wsClient.state.onEach { wsState ->
            Log.d(TAG, "WS state: $wsState (pendingGoAway=$pendingGoAwayReconnect)")

            // During GoAway reconnect, keep the state as CONNECTED —
            // the intermediate disconnect is an internal detail
            if (pendingGoAwayReconnect &&
                (wsState == LiveWebSocketClient.ConnectionState.DISCONNECTED ||
                 wsState == LiveWebSocketClient.ConnectionState.IDLE)) {
                Log.d(TAG, "Ignoring DISCONNECTED/IDLE during GoAway reconnect")
                // Don't update liveState — stay CONNECTED
            } else {
                liveState = when (wsState) {
                    LiveWebSocketClient.ConnectionState.IDLE -> LiveState.IDLE
                    LiveWebSocketClient.ConnectionState.CONNECTING,
                    LiveWebSocketClient.ConnectionState.WS_OPEN -> LiveState.CONNECTING
                    LiveWebSocketClient.ConnectionState.LIVE_CONNECTED -> LiveState.CONNECTED
                    LiveWebSocketClient.ConnectionState.ERROR -> LiveState.ERROR
                    LiveWebSocketClient.ConnectionState.DISCONNECTED -> LiveState.IDLE
                }
            }

            // When Gemini Live is connected, start recording + playback
            if (wsState == LiveWebSocketClient.ConnectionState.LIVE_CONNECTED) {
                // Detect reconnection (connected again after being connected before)
                if (hasBeenConnected && hasResumptionHandle) {
                    sessionResumed = true
                    // Only show "Session resumed" once per GoAway event
                    if (pendingGoAwayReconnect) {
                        pendingGoAwayReconnect = false
                        goAwayTimeoutJob?.cancel()
                        goAwayTimeoutJob = null
                        // Update the GoAway placeholder message in-place
                        if (goAwayMessageIndex in messages.indices) {
                            messages[goAwayMessageIndex] = LiveMessage(
                                role = LiveMessage.Role.APSARA,
                                text = "Session resumed",
                                isStreaming = false
                            )
                        }
                        goAwayMessageIndex = -1
                    }
                }
                hasBeenConnected = true
                startAudio()
            }

            // When disconnected or errored, stop audio — but not during GoAway reconnects
            if (wsState == LiveWebSocketClient.ConnectionState.DISCONNECTED ||
                wsState == LiveWebSocketClient.ConnectionState.ERROR) {
                if (!pendingGoAwayReconnect) {
                    audioManager.stopRecording()
                    audioManager.stopPlayback()
                    activeSpeaker = ActiveSpeaker.NONE
                }
            }
        }.launchIn(viewModelScope)

        // ── Echo prevention is handled by hardware AEC in LiveAudioManager ──
        // (VOICE_COMMUNICATION source + USAGE_VOICE_COMMUNICATION playback + AcousticEchoCanceler)
        // Mic stays open at all times so interruptions work naturally.

        // Receive audio from Gemini → play it, mark Apsara as speaking
        wsClient.audioData.onEach { bytes ->
            audioManager.enqueueAudio(bytes)
            activeSpeaker = ActiveSpeaker.APSARA
        }.launchIn(viewModelScope)

        // Track mic input amplitude → set activeSpeaker to USER when voice detected
        // This makes the visualizer respond immediately to user speech, without
        // waiting for the backend transcription roundtrip.
        audioManager.inputAmplitude.onEach { amp ->
            if (amp > 0.05f && activeSpeaker != ActiveSpeaker.APSARA) {
                // User is speaking (and Apsara isn't currently outputting audio)
                activeSpeaker = ActiveSpeaker.USER
            } else if (amp < 0.02f && activeSpeaker == ActiveSpeaker.USER) {
                // User stopped speaking — only reset if no audio is playing
                if (audioManager.outputAmplitude.value < 0.02f) {
                    activeSpeaker = ActiveSpeaker.NONE
                }
            }
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
                val embeddedTools = pendingToolCalls.toList()
                val lastApsaraIdx = messages.indexOfLast { it.role == LiveMessage.Role.APSARA && it.isStreaming }
                if (lastApsaraIdx >= 0) {
                    messages[lastApsaraIdx] = messages[lastApsaraIdx].copy(
                        text = fullText,
                        thought = thoughtText,
                        toolCalls = embeddedTools
                    )
                } else {
                    messages.add(LiveMessage(LiveMessage.Role.APSARA, fullText, isStreaming = true, thought = thoughtText, toolCalls = embeddedTools))
                }
            }
        }.launchIn(viewModelScope)

        // Thoughts — model's reasoning process (accumulate, attach to next Apsara message)
        wsClient.thought.onEach { text ->
            currentThoughtBuffer.append(text)
        }.launchIn(viewModelScope)

        // Tool calls — accumulate as pending, attach to next/current Apsara message
        wsClient.toolCall.onEach { calls ->
            for (call in calls) {
                // Determine if this tool is async or sync from settings
                val isAsync = liveSettings.let {
                    val modes = it.buildConfigMap()["toolAsyncModes"]
                    (modes as? Map<*, *>)?.get(call.name) == true
                }
                pendingToolCalls.add(
                    EmbeddedToolCall(
                        name = call.name,
                        id = call.id,
                        status = LiveMessage.ToolStatus.RUNNING,
                        mode = if (isAsync) "async" else "sync"
                    )
                )
            }
            // Update the current streaming Apsara message with the new tool calls,
            // or create a placeholder Apsara message to hold them
            val lastApsaraIdx = messages.indexOfLast { it.role == LiveMessage.Role.APSARA && it.isStreaming }
            if (lastApsaraIdx >= 0) {
                messages[lastApsaraIdx] = messages[lastApsaraIdx].copy(
                    toolCalls = pendingToolCalls.toList()
                )
            } else {
                // No Apsara message yet — add a placeholder with empty text
                val thoughtText = currentThoughtBuffer.toString().trim().ifEmpty { null }
                messages.add(
                    LiveMessage(
                        LiveMessage.Role.APSARA,
                        text = "",
                        isStreaming = true,
                        thought = thoughtText,
                        toolCalls = pendingToolCalls.toList()
                    )
                )
            }
        }.launchIn(viewModelScope)

        // Tool results — update the matching embedded tool call to "completed"
        wsClient.toolResults.onEach { results ->
            for (result in results) {
                // Extract code/output from result JSON for run_code tools
                var extractedCode: String? = null
                var extractedOutput: String? = null
                var extractedImages: List<CodeImageInfo> = emptyList()
                if (result.name == "run_code" || result.name == "edit_code") {
                    try {
                        val json = org.json.JSONObject(result.result)
                        val resp = if (json.has("response")) json.getJSONObject("response") else json
                        extractedCode = resp.optString("code", "").ifEmpty { null }
                        extractedOutput = resp.optString("output", "").ifEmpty { null }
                        val imgsArr = resp.optJSONArray("images")
                        if (imgsArr != null) {
                            extractedImages = (0 until imgsArr.length()).map { i ->
                                val img = imgsArr.getJSONObject(i)
                                CodeImageInfo(
                                    index = img.optInt("index", i),
                                    mimeType = img.optString("mimeType", img.optString("mime_type", "image/png")),
                                    url = img.optString("url", "")
                                )
                            }
                        }
                    } catch (_: Exception) { /* ignore parse errors */ }
                } else if (result.name == "url_context") {
                    // Extract the text content and URL metadata from url_context result
                    try {
                        val json = org.json.JSONObject(result.result)
                        val resp = if (json.has("response")) json.getJSONObject("response") else json
                        val text = resp.optString("text", "").ifEmpty { null }
                        val urlsFetched = resp.optInt("urls_fetched", 0)
                        val urlMeta = resp.optJSONArray("url_metadata")
                        val metaLines = StringBuilder()
                        if (urlMeta != null && urlMeta.length() > 0) {
                            metaLines.append("URLs fetched ($urlsFetched):\n")
                            for (i in 0 until urlMeta.length()) {
                                val m = urlMeta.getJSONObject(i)
                                val url = m.optString("url", "")
                                val status = m.optString("status", "")
                                metaLines.append("  • $url — $status\n")
                            }
                        }
                        extractedOutput = (metaLines.toString() + "\n" + (text ?: "")).trim().ifEmpty { null }
                    } catch (_: Exception) { /* ignore parse errors */ }
                }

                // Update in pending buffer
                val pendingIdx = pendingToolCalls.indexOfFirst { it.id == result.id }
                if (pendingIdx >= 0) {
                    pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(
                        status = LiveMessage.ToolStatus.COMPLETED,
                        result = result.result,
                        mode = result.mode,
                        codeBlock = pendingToolCalls[pendingIdx].codeBlock ?: extractedCode,
                        codeOutput = pendingToolCalls[pendingIdx].codeOutput ?: extractedOutput,
                        codeImages = pendingToolCalls[pendingIdx].codeImages.ifEmpty { extractedImages }
                    )
                } else {
                    // Wasn't in pending — add it
                    pendingToolCalls.add(
                        EmbeddedToolCall(
                            name = result.name,
                            id = result.id,
                            status = LiveMessage.ToolStatus.COMPLETED,
                            mode = result.mode,
                            result = result.result,
                            codeBlock = extractedCode,
                            codeOutput = extractedOutput,
                            codeImages = extractedImages
                        )
                    )
                }
            }
            // Update the Apsara message that contains these tool calls
            val lastApsaraIdx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> results.any { r -> r.id == tc.id } }
            }
            if (lastApsaraIdx >= 0) {
                val updatedCalls = messages[lastApsaraIdx].toolCalls.map { tc ->
                    val matchingResult = results.find { it.id == tc.id }
                    if (matchingResult != null) {
                        // Extract code/output for run_code or url_context
                        var code: String? = null
                        var output: String? = null
                        var imgs: List<CodeImageInfo> = emptyList()
                        if (matchingResult.name == "run_code" || matchingResult.name == "edit_code") {
                            try {
                                val json = org.json.JSONObject(matchingResult.result)
                                val resp = if (json.has("response")) json.getJSONObject("response") else json
                                code = resp.optString("code", "").ifEmpty { null }
                                output = resp.optString("output", "").ifEmpty { null }
                                val imgsArr = resp.optJSONArray("images")
                                if (imgsArr != null) {
                                    imgs = (0 until imgsArr.length()).map { i ->
                                        val img = imgsArr.getJSONObject(i)
                                        CodeImageInfo(
                                            index = img.optInt("index", i),
                                            mimeType = img.optString("mime_type", "image/png"),
                                            url = img.optString("url", "")
                                        )
                                    }
                                }
                            } catch (_: Exception) { }
                        } else if (matchingResult.name == "url_context") {
                            try {
                                val json = org.json.JSONObject(matchingResult.result)
                                val resp = if (json.has("response")) json.getJSONObject("response") else json
                                val text = resp.optString("text", "").ifEmpty { null }
                                val urlsFetched = resp.optInt("urls_fetched", 0)
                                val urlMeta = resp.optJSONArray("url_metadata")
                                val metaLines = StringBuilder()
                                if (urlMeta != null && urlMeta.length() > 0) {
                                    metaLines.append("URLs fetched ($urlsFetched):\n")
                                    for (i in 0 until urlMeta.length()) {
                                        val m = urlMeta.getJSONObject(i)
                                        val url = m.optString("url", "")
                                        val status = m.optString("status", "")
                                        metaLines.append("  • $url — $status\n")
                                    }
                                }
                                output = (metaLines.toString() + "\n" + (text ?: "")).trim().ifEmpty { null }
                            } catch (_: Exception) { }
                        }
                        // Extract canvas_id and render_url for canvas tools
                        var extractedCanvasId: String? = null
                        var extractedCanvasRenderUrl: String? = null
                        if (matchingResult.name == "apsara_canvas" || matchingResult.name == "edit_canvas") {
                            try {
                                val json = org.json.JSONObject(matchingResult.result)
                                val resp = if (json.has("response")) json.getJSONObject("response") else json
                                extractedCanvasId = resp.optString("canvas_id", "").ifEmpty { null }
                                extractedCanvasRenderUrl = resp.optString("render_url", "").ifEmpty { null }
                            } catch (_: Exception) { }
                        }
                        tc.copy(
                            status = LiveMessage.ToolStatus.COMPLETED,
                            result = matchingResult.result,
                            mode = matchingResult.mode,
                            codeBlock = tc.codeBlock ?: code,
                            codeOutput = tc.codeOutput ?: output,
                            codeImages = tc.codeImages.ifEmpty { imgs },
                            canvasId = tc.canvasId ?: extractedCanvasId,
                            canvasRenderUrl = tc.canvasRenderUrl ?: extractedCanvasRenderUrl
                        )
                    } else tc
                }
                messages[lastApsaraIdx] = messages[lastApsaraIdx].copy(toolCalls = updatedCalls)
            }
        }.launchIn(viewModelScope)

        // Errors
        wsClient.error.onEach { msg ->
            lastError = msg
            Log.e(TAG, "Error: $msg")
        }.launchIn(viewModelScope)

        // Session resumption updates — Gemini is maintaining session state across connections
        wsClient.sessionResumptionUpdate.onEach { event ->
            Log.d(TAG, "Session resumption: resumable=${event.resumable}, hasHandle=${event.hasHandle}")
            if (event.resumable && event.hasHandle) {
                hasResumptionHandle = true
                // Show a one-time subtle message when first handle arrives
                if (!shownResumptionReady) {
                    shownResumptionReady = true
                    messages.add(
                        LiveMessage(
                            role = LiveMessage.Role.APSARA,
                            text = "✦ Session resumption active",
                            isStreaming = false
                        )
                    )
                }
            }
        }.launchIn(viewModelScope)

        // GoAway — Gemini connection will terminate soon, backend will auto-reconnect
        wsClient.goAway.onEach { event ->
            Log.w(TAG, "GoAway received: timeLeft=${event.timeLeft}")
            // Only add one message per GoAway; mark that reconnect is expected
            if (!pendingGoAwayReconnect) {
                pendingGoAwayReconnect = true
                messages.add(
                    LiveMessage(
                        role = LiveMessage.Role.APSARA,
                        text = "Reconnecting...",
                        isStreaming = false
                    )
                )
                goAwayMessageIndex = messages.lastIndex

                // Safety timeout: if reconnect doesn't complete within 15s, give up
                goAwayTimeoutJob?.cancel()
                goAwayTimeoutJob = viewModelScope.launch {
                    delay(15_000)
                    if (pendingGoAwayReconnect) {
                        Log.e(TAG, "GoAway reconnect timed out after 15s")
                        pendingGoAwayReconnect = false
                        goAwayMessageIndex = -1
                        if (liveState != LiveState.CONNECTED) {
                            // Reconnect failed — clean up properly
                            stopLive()
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)

        // Canvas progress — track canvas generation and update tool call + emit notification when ready
        wsClient.canvasProgress.onEach { event ->
            Log.d(TAG, "Canvas progress: status=${event.status}, message=${event.message}")
            // Update the matching canvas tool call's status text in messages
            val canvasTools = setOf("apsara_canvas", "edit_canvas")
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.id == event.toolCallId && tc.name in canvasTools }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if (tc.id == event.toolCallId && tc.name in canvasTools && tc.status == LiveMessage.ToolStatus.RUNNING) {
                        // Use codeBlock field to store the progress status text
                        tc.copy(codeBlock = event.message)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            // Also update pending buffer
            val pendingIdx = pendingToolCalls.indexOfFirst { it.id == event.toolCallId && it.name in canvasTools }
            if (pendingIdx >= 0 && pendingToolCalls[pendingIdx].status == LiveMessage.ToolStatus.RUNNING) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(codeBlock = event.message)
            }
            when (event.status) {
                "ready" -> {
                    _canvasNotification.tryEmit(CanvasNotification(event.message, "ready"))
                }
                "error" -> {
                    _canvasNotification.tryEmit(CanvasNotification(event.message, "error"))
                }
            }
        }.launchIn(viewModelScope)

        // Canvas HTML delta — accumulate streaming HTML into tool call's codeOutput
        wsClient.canvasHtmlDelta.onEach { event ->
            val canvasTools = setOf("apsara_canvas", "edit_canvas")
            // Update in messages
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.id == event.toolCallId && tc.name in canvasTools }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if (tc.id == event.toolCallId && tc.name in canvasTools) {
                        tc.copy(codeOutput = (tc.codeOutput ?: "") + event.delta)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            // Also update pending buffer
            val pendingIdx = pendingToolCalls.indexOfFirst { it.id == event.toolCallId && it.name in canvasTools }
            if (pendingIdx >= 0) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(
                    codeOutput = (pendingToolCalls[pendingIdx].codeOutput ?: "") + event.delta
                )
            }
        }.launchIn(viewModelScope)

        // URL Context progress — update the matching tool call with live progress messages
        wsClient.urlContextProgress.onEach { event ->
            Log.d(TAG, "URL context progress: status=${event.status}, message=${event.message}")
            // Find the url_context tool call and update its progress in codeOutput
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.name == "url_context" && tc.id == event.toolCallId }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if (tc.name == "url_context" && tc.id == event.toolCallId && tc.status == LiveMessage.ToolStatus.RUNNING) {
                        tc.copy(codeOutput = event.message)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            // Also update in pending buffer
            val pendingIdx = pendingToolCalls.indexOfFirst { it.name == "url_context" && it.id == event.toolCallId }
            if (pendingIdx >= 0 && pendingToolCalls[pendingIdx].status == LiveMessage.ToolStatus.RUNNING) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(codeOutput = event.message)
            }
        }.launchIn(viewModelScope)

        // ── Interpreter: executable code — attach to current run_code/edit_code tool call ──
        wsClient.executableCode.onEach { event ->
            Log.d(TAG, "Executable code received: ${event.code.take(80)}...")
            // Find the latest run_code or edit_code tool call and attach the code
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.name == "run_code" || tc.name == "edit_code" }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if ((tc.name == "run_code" || tc.name == "edit_code") && tc.codeBlock.isNullOrEmpty()) {
                        tc.copy(codeBlock = event.code)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            // Also update pending buffer
            val pendingIdx = pendingToolCalls.indexOfLast { it.name == "run_code" || it.name == "edit_code" }
            if (pendingIdx >= 0 && pendingToolCalls[pendingIdx].codeBlock.isNullOrEmpty()) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(codeBlock = event.code)
            }
        }.launchIn(viewModelScope)

        // ── Interpreter: code execution result — attach output to run_code/edit_code tool call ──
        wsClient.codeExecutionResult.onEach { event ->
            Log.d(TAG, "Code execution result: ${event.output.take(80)}...")
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.name == "run_code" || tc.name == "edit_code" }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if (tc.name == "run_code" || tc.name == "edit_code") {
                        tc.copy(codeOutput = (tc.codeOutput ?: "") + event.output)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            val pendingIdx = pendingToolCalls.indexOfLast { it.name == "run_code" || it.name == "edit_code" }
            if (pendingIdx >= 0) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(
                    codeOutput = (pendingToolCalls[pendingIdx].codeOutput ?: "") + event.output
                )
            }
        }.launchIn(viewModelScope)

        // ── Interpreter: images — attach image URLs to run_code/edit_code tool call ──
        wsClient.interpreterImages.onEach { event ->
            Log.d(TAG, "Interpreter images: sessionId=${event.sessionId}, count=${event.images.size}")
            val imageInfos = event.images.map { img ->
                CodeImageInfo(index = img.index, mimeType = img.mimeType, url = img.url)
            }
            val idx = messages.indexOfLast {
                it.role == LiveMessage.Role.APSARA &&
                it.toolCalls.any { tc -> tc.name == "run_code" || tc.name == "edit_code" }
            }
            if (idx >= 0) {
                val updatedCalls = messages[idx].toolCalls.map { tc ->
                    if (tc.name == "run_code" || tc.name == "edit_code") {
                        tc.copy(codeImages = tc.codeImages + imageInfos)
                    } else tc
                }
                messages[idx] = messages[idx].copy(toolCalls = updatedCalls)
            }
            val pendingIdx = pendingToolCalls.indexOfLast { it.name == "run_code" || it.name == "edit_code" }
            if (pendingIdx >= 0) {
                pendingToolCalls[pendingIdx] = pendingToolCalls[pendingIdx].copy(
                    codeImages = pendingToolCalls[pendingIdx].codeImages + imageInfos
                )
            }
        }.launchIn(viewModelScope)

        // Observe "stop live" requests from the foreground service notification
        LiveSessionBridge.stopRequested.onEach {
            Log.d(TAG, "Stop live requested via notification")
            stopLive()
        }.launchIn(viewModelScope)

        // Observe "mute toggle" requests from the foreground service notification
        LiveSessionBridge.muteToggleRequested.onEach {
            Log.d(TAG, "Mute toggle requested via notification")
            toggleMute()
        }.launchIn(viewModelScope)
    }

    private fun finalizeOutputMessage() {
        // Finalize the current streaming Apsara message and attach any thoughts + tool calls
        val idx = messages.indexOfLast { it.role == LiveMessage.Role.APSARA && it.isStreaming }
        if (idx >= 0) {
            val thoughtText = currentThoughtBuffer.toString().trim().ifEmpty { null }
            messages[idx] = messages[idx].copy(
                isStreaming = false,
                thought = thoughtText,
                toolCalls = pendingToolCalls.toList()
            )
        }
        currentOutputBuffer.clear()
        currentThoughtBuffer.clear()
        pendingToolCalls.clear()
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
        pendingToolCalls.clear()
        activeSpeaker = ActiveSpeaker.NONE
        sessionResumed = false
        hasBeenConnected = false
        hasResumptionHandle = false
        shownResumptionReady = false
        pendingGoAwayReconnect = false
        goAwayMessageIndex = -1
        goAwayTimeoutJob?.cancel()
        goAwayTimeoutJob = null
        val config = liveSettings.buildConfigMap()
        wsClient.connect(liveSettings.backendUrl, config)

        // Start foreground service to keep session alive in background
        try {
            LiveSessionBridge.updateLiveActive(true)
            LiveSessionService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun startAudio() {
        // IMPORTANT: Start recording FIRST — this sets MODE_IN_COMMUNICATION on the
        // AudioManager and obtains the shared audio session ID from AudioRecord.
        // Then start playback — the AudioTrack will be created with the shared session
        // while the AudioManager is already in communication mode, so isSpeakerphoneOn
        // routing (earpiece/loudspeaker/bluetooth) will take effect correctly.
        if (audioManager.hasPermission()) {
            audioManager.startRecording { pcmChunk ->
                wsClient.sendAudio(pcmChunk)
            }
        }
        audioManager.startPlayback()
    }

    fun stopLive() {
        audioManager.stopRecording()
        audioManager.stopPlayback()
        wsClient.disconnect()
        liveState = LiveState.IDLE
        isMuted = false
        isVideoActive = false
        activeSpeaker = ActiveSpeaker.NONE

        // Stop foreground service and reset bridge state
        try {
            LiveSessionBridge.reset()
            LiveSessionService.stop(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop foreground service: ${e.message}")
        }
    }

    fun toggleMute() {
        audioManager.toggleMute()
        isMuted = audioManager.isMuted.value

        // When muting: signal the backend that the audio stream has ended,
        // so Gemini processes whatever audio was captured so far.
        // When unmuting: the stream auto-reopens on the next audio chunk.
        if (isMuted) {
            wsClient.sendAudioStreamEnd()
        }
    }

    fun sendText(text: String) {
        // Immediately clear audio playback — text should interrupt Apsara's speech
        audioManager.clearPlaybackQueue()
        finalizeOutputMessage()
        activeSpeaker = ActiveSpeaker.USER

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
