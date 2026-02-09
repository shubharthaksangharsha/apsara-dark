package com.apsara.ai.data.api

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import com.apsara.ai.R
import com.apsara.ai.utils.HapticFeedbackManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Audio output device options
 */
enum class AudioOutputDevice {
    EARPIECE,      // Phone earpiece (default phone speaker at top)
    LOUDSPEAKER,   // Phone loudspeaker (bottom speaker, hands-free)
    BLUETOOTH      // Bluetooth headset/earbuds
}

/**
 * Data class for bounding box overlay on screen/camera
 */
data class BoundingBox(
    val label: String,
    val x: Float,      // 0-1 normalized
    val y: Float,      // 0-1 normalized
    val width: Float,  // 0-1 normalized  
    val height: Float, // 0-1 normalized
    val color: String? = null,
    val confidence: Float? = null
)

/**
 * Live API Service for real-time WebSocket communication with Apsara Backend
 * Fixed: Audio recording, playback queue, proper cleanup
 */
class LiveApiService(private val context: Context) {
    companion object {
        private const val WEBSOCKET_URL = "wss://apsara-backend.devshubh.me/live"
        private const val DEFAULT_MODEL = "gemini-2.5-flash-native-audio-preview-09-2025"
        private const val DEFAULT_VOICE = "Aoede"
        private const val DEFAULT_LANGUAGE = "en-US"
        
        // Debug flag - set to true to show debug logs in app UI
        var ENABLE_DEBUG_OVERLAY = false // Set to true for testing, false for production
    }
    
    private var webSocket: WebSocket? = null
    private var currentSessionId: String? = null
    private val isConnected = AtomicBoolean(false)
    
    // Haptic feedback manager
    private val hapticManager = HapticFeedbackManager.getInstance(context)
    
    // Track if AI is currently generating (between user input and first audio chunk)
    private val isAiGenerating = AtomicBoolean(false)
    
    // Thinking sound player - plays ambient music while AI is "thinking"
    private var thinkingMediaPlayer: MediaPlayer? = null
    private val isThinkingSoundPlaying = AtomicBoolean(false)
    
    // Debug logs for UI overlay
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()
    
    private fun addDebugLog(message: String) {
        if (ENABLE_DEBUG_OVERLAY) {
            Log.d("LiveApiService", message)
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            val logs = _debugLogs.value.toMutableList()
            logs.add("[$timestamp] $message")
            // Keep only last 20 logs
            if (logs.size > 20) logs.removeAt(0)
            _debugLogs.value = logs
        }
    }
    
    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }
    
    /**
     * Start playing thinking sound in loop
     * Called when AI is "thinking" before speaking
     */
    private fun startThinkingSound() {
        if (isThinkingSoundPlaying.get()) return // Already playing
        
        try {
            // Stop any existing player
            stopThinkingSound()
            
            thinkingMediaPlayer = MediaPlayer.create(context, R.raw.thinking_thought)?.apply {
                isLooping = true
                setVolume(0.3f, 0.3f) // Lower volume for ambient background
                setOnPreparedListener {
                    start()
                    isThinkingSoundPlaying.set(true)
                    addDebugLog("🎵 Thinking sound started")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("LiveApiService", "Thinking sound error: $what, $extra")
                    isThinkingSoundPlaying.set(false)
                    true
                }
            }
            
            // If MediaPlayer.create returns a prepared player, start it
            if (thinkingMediaPlayer?.isPlaying == false) {
                thinkingMediaPlayer?.start()
                isThinkingSoundPlaying.set(true)
                addDebugLog("🎵 Thinking sound started")
            }
        } catch (e: Exception) {
            Log.e("LiveApiService", "Failed to start thinking sound: ${e.message}")
        }
    }
    
    /**
     * Stop the thinking sound
     * Called when AI starts speaking or is interrupted
     */
    private fun stopThinkingSound() {
        if (!isThinkingSoundPlaying.get() && thinkingMediaPlayer == null) return
        
        try {
            thinkingMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            thinkingMediaPlayer = null
            isThinkingSoundPlaying.set(false)
            addDebugLog("🎵 Thinking sound stopped")
        } catch (e: Exception) {
            Log.e("LiveApiService", "Failed to stop thinking sound: ${e.message}")
        }
    }
    
    // ========== AUTOMATIC TITLE GENERATION ==========
    // Track message count and auto-generate title when > 1 messages
    private var messageCount = AtomicBoolean(false) // false = 0 messages, true = has messages
    private var titleUpdateTriggered = AtomicBoolean(false)
    private var currentConversationId: String? = null
    private var currentUserId: String? = null
    
    // ========== SHARED AUDIO SESSION FOR ECHO CANCELLATION ==========
    // CRITICAL: Both mic and speaker MUST share the same session for AEC to work
    private var sharedAudioSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var wasSpeakerphoneOn: Boolean = false
    
    // Audio output device selection (Earpiece, Loudspeaker, Bluetooth)
    private val _audioOutputDevice = MutableStateFlow(AudioOutputDevice.LOUDSPEAKER)
    val audioOutputDevice: StateFlow<AudioOutputDevice> = _audioOutputDevice.asStateFlow()
    
    // Available Bluetooth devices
    private val _availableBluetoothDevices = MutableStateFlow<List<String>>(emptyList())
    val availableBluetoothDevices: StateFlow<List<String>> = _availableBluetoothDevices.asStateFlow()
    
    // Legacy speaker state for backward compatibility
    private val isSpeakerOn = AtomicBoolean(true) // Speaker ON by default
    
    // ========== PIPELINE 1: MICROPHONE INPUT ==========
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    
    // Echo cancellation (linked to shared session)
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    
    // ========== PIPELINE 2: SPEAKER OUTPUT ==========
    private var playbackTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val playbackQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val isPlaybackRunning = AtomicBoolean(false)
    private var pendingPlaybackComplete = AtomicBoolean(false) // Track if we should emit PlaybackComplete
    private var playbackCompleteJob: Job? = null // Job to emit PlaybackComplete after delay
    private var lastAudioChunkTime = AtomicLong(0) // Track when last audio chunk was queued
    
    // Audio settings
    private val inputSampleRate = 16000
    private val outputSampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _sessionState = MutableStateFlow(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    private val _messages = MutableSharedFlow<LiveMessage>(replay = 1, extraBufferCapacity = 10)
    val messages: SharedFlow<LiveMessage> = _messages.asSharedFlow()
    
    // Audio amplitude for visualizer (0-1 range)
    private val _userAmplitude = MutableStateFlow(0f)
    val userAmplitude: StateFlow<Float> = _userAmplitude.asStateFlow()
    
    private val _aiAmplitude = MutableStateFlow(0f)
    val aiAmplitude: StateFlow<Float> = _aiAmplitude.asStateFlow()
    
    // Bounding boxes for AR overlay (from Gemini highlighter function)
    private val _boundingBoxes = MutableStateFlow<List<BoundingBox>>(emptyList())
    val boundingBoxes: StateFlow<List<BoundingBox>> = _boundingBoxes.asStateFlow()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
    enum class SessionState { Idle, Creating, Active, Error }
    
    sealed class LiveMessage {
        data class Connected(val clientId: String) : LiveMessage()
        data class SessionCreated(val sessionId: String, val conversationId: String?) : LiveMessage()
        data class SessionReady(val sessionId: String, val conversationId: String?) : LiveMessage()
        data class InputTranscription(val text: String) : LiveMessage()
        data class OutputTranscription(val text: String) : LiveMessage()
        data class AudioData(val data: String) : LiveMessage()
        data class TurnComplete(val sessionId: String) : LiveMessage()
        data class GenerationComplete(val sessionId: String) : LiveMessage()
        object PlaybackComplete : LiveMessage() // Audio playback has finished
        data class Interrupted(val sessionId: String) : LiveMessage()
        data class SessionClosed(val reason: String?) : LiveMessage()
        data class GoAway(val timeLeft: String?) : LiveMessage()
        data class Error(val message: String) : LiveMessage()
        object RecordingStarted : LiveMessage()
        object StartRecordingRequest : LiveMessage() // Signal to UI to start recording
        data class HighlightObjects(val boxes: List<BoundingBox>) : LiveMessage() // AR overlay bounding boxes
        data class RateLimitExceeded(
            val message: String,
            val retryAfterSeconds: Int,
            val limit: Int,
            val limitType: String // "daily" or "per_minute"
        ) : LiveMessage()
        data class ThinkingStarted(val thought: String?) : LiveMessage() // AI is thinking (before speaking)
        object ThinkingStopped : LiveMessage() // AI stopped thinking (started speaking or interrupted)
    }
    
    /**
     * Connect to WebSocket
     */
    suspend fun connect(authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) return@withContext true
            
            _connectionState.value = ConnectionState.CONNECTING
            
            val request = Request.Builder()
                .url(WEBSOCKET_URL)
                .header("User-Agent", "Apsara-AI-Android/2.0")
                .apply { authToken?.let { header("Authorization", "Bearer $it") } }
                .build()
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected.set(true)
                    _connectionState.value = ConnectionState.CONNECTED
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    cleanup()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = ConnectionState.ERROR
                    emitMessage(LiveMessage.Error("Connection failed: ${t.message}"))
                }
            })
            
            // Wait for connection
            var attempts = 0
            while (!isConnected.get() && attempts < 50) {
                delay(100)
                attempts++
            }
            
            isConnected.get()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            false
        }
    }
    
    /**
     * Create a Live session
     */
    suspend fun createSession(
        userId: String,
        conversationId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) return@withContext false
            
            _sessionState.value = SessionState.Creating
            
            // Store user and conversation info for auto-title generation
            currentUserId = userId
            currentConversationId = conversationId
            // Reset message tracking for new session
            messageCount.set(false)
            titleUpdateTriggered.set(false)
            
            val message = JSONObject().apply {
                put("type", "create_session")
                put("data", JSONObject().apply {
                    put("userId", userId)
                    // Only include conversationId if it's not null, not empty, and not a template placeholder
                    if (!conversationId.isNullOrEmpty() && conversationId != "{conversationId}") {
                        put("conversationId", conversationId)
                    }
                    put("model", DEFAULT_MODEL)
                    put("voice", DEFAULT_VOICE)
                    put("language", DEFAULT_LANGUAGE)
                })
            }
            
            webSocket?.send(message.toString()) == true
        } catch (e: Exception) {
            _sessionState.value = SessionState.Error
            false
        }
    }
    
    /**
     * Send text message
     * If AI is currently processing/responding, this will interrupt the response
     */
    suspend fun sendText(text: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) return@withContext false
        
        try {
            addDebugLog("📝 Sending text: ${text.take(50)}...")
            
            // Mark that messages were exchanged (for auto-title generation)
            messageCount.set(true)
            
            // If playback is running, stop it immediately to interrupt AI response
            // This allows text messages to interrupt audio playback
            if (isPlaybackRunning.get()) {
                addDebugLog("🛑 Interrupting playback for text message")
                stopPlayback()
                // Cancel any pending playback complete job
                playbackCompleteJob?.cancel()
                playbackCompleteJob = null
                pendingPlaybackComplete.set(false)
                // Reset AI amplitude for visualizer
                _aiAmplitude.value = 0f
            }
            
            // Clear bounding boxes when user sends a new text message
            clearBoundingBoxes()
            
            val message = JSONObject().apply {
                put("type", "send_text")
                put("data", JSONObject().apply {
                    put("text", text)
                })
            }
            webSocket?.send(message.toString()) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Send conversation history context to Gemini using sendClientContent
     * This allows continuing a previous conversation
     */
    suspend fun sendConversationContext(messages: List<Pair<String, String>>): Boolean = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) return@withContext false
        if (messages.isEmpty()) return@withContext true
        
        try {
            val turnsArray = org.json.JSONArray()
            
            for ((role, content) in messages) {
                val turn = JSONObject().apply {
                    put("role", if (role == "user") "user" else "model")
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().put("text", content))
                    })
                }
                turnsArray.put(turn)
            }
            
            val message = JSONObject().apply {
                put("type", "send_context")
                put("data", JSONObject().apply {
                    put("turns", turnsArray)
                    put("turnComplete", true)
                })
            }
            
            webSocket?.send(message.toString()) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Send audio data to backend
     */
    private suspend fun sendAudio(audioData: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) return@withContext false
        if (isMuted.get()) return@withContext true // Silently skip if muted
        
        try {
            val base64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            val message = JSONObject().apply {
                put("type", "send_audio")
                put("data", JSONObject().apply {
                    put("data", base64)
                    put("mimeType", "audio/pcm;rate=$inputSampleRate")
                })
            }
            webSocket?.send(message.toString()) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Send video frame to backend for real-time vision
     */
    suspend fun sendVideoFrame(jpegData: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) {
            android.util.Log.w("LiveApiService", "sendVideoFrame: No active session")
            return@withContext false
        }
        
        try {
            val base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP)
            val message = JSONObject().apply {
                put("type", "send_video")
                put("data", JSONObject().apply {
                    put("data", base64)
                    put("mimeType", "image/jpeg")
                })
            }
            val sent = webSocket?.send(message.toString()) == true
            android.util.Log.d("LiveApiService", "sendVideoFrame: sent=$sent, size=${jpegData.size} bytes, base64Len=${base64.length}")
            sent
        } catch (e: Exception) {
            android.util.Log.e("LiveApiService", "sendVideoFrame error: ${e.message}")
            false
        }
    }
    
    /**
     * Send document/file to backend for analysis
     * Supported: images (PNG, JPG, GIF, WEBP), text files (as text/plain)
     * NOT supported: PDF, DOCX (will fail with "invalid argument")
     * 
     * @param base64Data Base64 encoded file content
     * @param mimeType MIME type of the file (e.g., "image/png", "text/plain")
     * @param fileName Original file name for context
     */
    suspend fun sendDocument(base64Data: String, mimeType: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) {
            android.util.Log.w("LiveApiService", "sendDocument: No active session")
            return@withContext false
        }
        
        try {
            addDebugLog("📄 Sending document: $fileName ($mimeType)")
            
            // Mark that messages were exchanged (for auto-title generation)
            messageCount.set(true)
            
            val message = JSONObject().apply {
                put("type", "send_document")
                put("data", JSONObject().apply {
                    put("data", base64Data)
                    put("mimeType", mimeType)
                    put("fileName", fileName)
                })
            }
            
            val sent = webSocket?.send(message.toString()) == true
            android.util.Log.d("LiveApiService", "sendDocument: sent=$sent, file=$fileName, mimeType=$mimeType, base64Len=${base64Data.length}")
            
            if (sent) {
                addDebugLog("✅ Document sent: $fileName")
            } else {
                addDebugLog("❌ Document send failed: $fileName")
            }
            
            sent
        } catch (e: Exception) {
            android.util.Log.e("LiveApiService", "sendDocument error: ${e.message}")
            addDebugLog("❌ Error: ${e.message}")
            false
        }
    }
    
    /**
     * Video processing configuration
     */
    object VideoConfig {
        const val MAX_VIDEO_SIZE_BYTES = 100L * 1024 * 1024  // 100 MB limit
        const val MAX_FRAMES = 8                              // Max frames to extract (reduced for better quality)
        const val FRAME_INTERVAL_MS = 2000L                   // 1 frame per 2 seconds (better spread)
        const val FRAME_QUALITY = 85                          // JPEG quality (0-100)
        const val MAX_FRAME_WIDTH = 640                       // Max frame width for better quality
        const val FRAME_SEND_DELAY_MS = 500L                  // Delay between sending frames
    }
    
    /**
     * Video processing result
     */
    sealed class VideoProcessingResult {
        data class Success(val frameCount: Int) : VideoProcessingResult()
        data class Error(val message: String) : VideoProcessingResult()
        data class Progress(val current: Int, val total: Int, val status: String) : VideoProcessingResult()
    }
    
    /**
     * Process and send video file to Live API
     * Extracts frames from video and sends them via sendRealtimeInput for visual analysis.
     * 
     * @param context Android context for ContentResolver access
     * @param videoUri URI of the video file
     * @param fileName Original file name
     * @param fileSize File size in bytes
     * @param onProgress Callback for progress updates
     * @return VideoProcessingResult indicating success or error
     */
    suspend fun processAndSendVideo(
        context: Context,
        videoUri: android.net.Uri,
        fileName: String,
        fileSize: Long,
        onProgress: (VideoProcessingResult.Progress) -> Unit
    ): VideoProcessingResult = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) {
            return@withContext VideoProcessingResult.Error("No active session")
        }
        
        // Check file size
        if (fileSize > VideoConfig.MAX_VIDEO_SIZE_BYTES) {
            val maxMB = VideoConfig.MAX_VIDEO_SIZE_BYTES / (1024 * 1024)
            return@withContext VideoProcessingResult.Error("Video too large. Maximum size is ${maxMB}MB")
        }
        
        try {
            addDebugLog("🎬 Processing video: $fileName (${fileSize / 1024}KB)")
            onProgress(VideoProcessingResult.Progress(0, 0, "Analyzing video..."))
            
            // Use MediaMetadataRetriever to extract frames
            val retriever = android.media.MediaMetadataRetriever()
            
            try {
                retriever.setDataSource(context, videoUri)
                
                // Get video duration in milliseconds
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                
                if (durationMs <= 0) {
                    return@withContext VideoProcessingResult.Error("Could not determine video duration")
                }
                
                addDebugLog("📹 Video duration: ${durationMs}ms")
                
                // Calculate how many frames to extract spread across the video
                val totalFrames = minOf(
                    VideoConfig.MAX_FRAMES,
                    (durationMs / VideoConfig.FRAME_INTERVAL_MS).toInt().coerceAtLeast(1)
                )
                
                // Calculate interval to spread frames evenly across the video
                val actualInterval = if (totalFrames > 1) {
                    (durationMs - 500) / (totalFrames - 1) // Leave 500ms margin at end
                } else {
                    0L
                }
                
                addDebugLog("📹 Extracting $totalFrames frames, interval: ${actualInterval}ms")
                onProgress(VideoProcessingResult.Progress(0, totalFrames, "Extracting frames..."))
                
                var successfulFrames = 0
                val extractedHashes = mutableSetOf<Int>() // Track to avoid duplicate frames
                
                for (i in 0 until totalFrames) {
                    // Calculate timestamp for this frame (spread evenly across video)
                    val timeMs = if (totalFrames > 1) {
                        (i * actualInterval).coerceAtMost(durationMs - 100)
                    } else {
                        durationMs / 2 // Single frame: get middle of video
                    }
                    val timeUs = timeMs * 1000
                    
                    onProgress(VideoProcessingResult.Progress(i + 1, totalFrames, "Processing frame ${i + 1}/$totalFrames"))
                    addDebugLog("📷 Extracting frame ${i + 1} at ${timeMs}ms")
                    
                    // Extract frame at timestamp - use OPTION_CLOSEST for accurate frame
                    val bitmap = retriever.getFrameAtTime(
                        timeUs,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    
                    if (bitmap != null) {
                        // Resize if needed to reduce data size
                        val resizedBitmap = if (bitmap.width > VideoConfig.MAX_FRAME_WIDTH) {
                            val scale = VideoConfig.MAX_FRAME_WIDTH.toFloat() / bitmap.width
                            val newHeight = (bitmap.height * scale).toInt()
                            Bitmap.createScaledBitmap(bitmap, VideoConfig.MAX_FRAME_WIDTH, newHeight, true)
                        } else {
                            bitmap
                        }
                        
                        // Convert to JPEG bytes
                        val outputStream = java.io.ByteArrayOutputStream()
                        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, VideoConfig.FRAME_QUALITY, outputStream)
                        val jpegBytes = outputStream.toByteArray()
                        
                        // Simple hash to detect duplicate frames
                        val frameHash = jpegBytes.contentHashCode()
                        
                        if (!extractedHashes.contains(frameHash)) {
                            extractedHashes.add(frameHash)
                            
                            // Send frame via video channel
                            val sent = sendVideoFrame(jpegBytes)
                            if (sent) {
                                successfulFrames++
                                addDebugLog("📷 Frame ${i + 1}/$totalFrames sent (${jpegBytes.size / 1024}KB)")
                            }
                            
                            // Delay between frames - important for Gemini to process
                            delay(VideoConfig.FRAME_SEND_DELAY_MS)
                        } else {
                            addDebugLog("⏭️ Frame ${i + 1} skipped (duplicate)")
                        }
                        
                        // Clean up bitmap if we created a new one
                        if (resizedBitmap != bitmap) {
                            resizedBitmap.recycle()
                        }
                        bitmap.recycle()
                    }
                }
                
                if (successfulFrames == 0) {
                    return@withContext VideoProcessingResult.Error("Failed to extract any frames from video")
                }
                
                onProgress(VideoProcessingResult.Progress(totalFrames, totalFrames, "Analyzing video content..."))
                
                // Wait for Gemini to process all frames before asking
                delay(1500)
                
                // Send a text prompt to ask about the video
                sendText("I just shared a video clip with you. Please describe exactly what you see happening in these video frames - the people, objects, actions, and any text visible.")
                
                addDebugLog("✅ Video processed: $successfulFrames frames sent")
                
                // Mark that messages were exchanged
                messageCount.set(true)
                
                VideoProcessingResult.Success(successfulFrames)
                
            } finally {
                retriever.release()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("LiveApiService", "Video processing error: ${e.message}", e)
            addDebugLog("❌ Video error: ${e.message}")
            VideoProcessingResult.Error("Failed to process video: ${e.message}")
        }
    }
    
    /**
     * Start audio recording and streaming to backend
     * @return true if recording started successfully
     */
    fun startRecording(context: Context? = null): Boolean {
        if (isRecording.get()) return true
        if (!hasActiveSession()) return false
        
        try {
            // Set up AudioManager for audio routing with hardware AEC
            if (context != null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager?.let { am ->
                    // Save current state
                    previousAudioMode = am.mode
                    @Suppress("DEPRECATION")
                    wasSpeakerphoneOn = am.isSpeakerphoneOn
                    
                    // Detect available Bluetooth devices
                    detectAvailableBluetoothDevices(context)
                    
                    // Apply audio routing based on selected output device
                    applyAudioRouting(am, _audioOutputDevice.value)
                }
            }
            
            val minBufferSize = AudioRecord.getMinBufferSize(inputSampleRate, channelConfig, audioFormat)
            
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                emitMessage(LiveMessage.Error("Audio recording not supported on this device"))
                return false
            }
            
            val bufferSize = minBufferSize * 2
            
            // CRITICAL FOR ECHO CANCELLATION:
            // Use VOICE_COMMUNICATION which activates the phone's built-in 
            // echo cancellation hardware path (same as phone calls)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                inputSampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            val recordState = audioRecord?.state
            
            if (recordState != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                emitMessage(LiveMessage.Error("Microphone not available - please check permissions"))
                return false
            }
            
            sharedAudioSessionId = audioRecord?.audioSessionId ?: AudioManager.AUDIO_SESSION_ID_GENERATE
            
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(sharedAudioSessionId)
                    echoCanceler?.enabled = true
                } catch (e: Exception) { /* AEC not available */ }
            }
            
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sharedAudioSessionId)
                    noiseSuppressor?.enabled = true
                } catch (e: Exception) { /* NS not available */ }
            }
            
            audioRecord?.startRecording()
            
            val recordingState = audioRecord?.recordingState
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.release()
                audioRecord = null
                emitMessage(LiveMessage.Error("Failed to start microphone"))
                return false
            }
            
            isRecording.set(true)
            emitMessage(LiveMessage.RecordingStarted)
            
            // Start recording loop - optimized for low latency
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                
                while (isRecording.get() && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0 && !isMuted.get()) {
                        // Calculate amplitude for visualizer
                        _userAmplitude.value = calculateAmplitude(buffer, bytesRead)
                        sendAudio(buffer.copyOf(bytesRead))
                    } else if (bytesRead < 0) break
                    delay(30)
                }
                _userAmplitude.value = 0f
            }
            
            return true
            
        } catch (e: SecurityException) {
            emitMessage(LiveMessage.Error("Microphone permission denied"))
            return false
        } catch (e: Exception) {
            stopRecording()
            emitMessage(LiveMessage.Error("Failed to start recording: ${e.message}"))
            return false
        }
    }
    
    /**
     * Stop audio recording
     */
    fun stopRecording() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioManager?.let { am ->
                // Stop Bluetooth SCO if it was started
                try {
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                } catch (e: Exception) { /* ignore */ }
                
                // Restore previous audio settings
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = wasSpeakerphoneOn
                am.mode = previousAudioMode
                Log.d("LiveApiService", "🔊 Audio settings restored")
            }
            audioManager = null
        } catch (e: Exception) { /* cleanup error */ }
    }
    
    /**
     * Toggle mute
     */
    fun toggleMute(): Boolean {
        val newValue = !isMuted.get()
        isMuted.set(newValue)
        return newValue
    }
    
    /**
     * Check if muted
     */
    fun isMuted(): Boolean = isMuted.get()
    
    /**
     * Toggle speaker (earpiece vs loudspeaker) - Legacy method
     */
    fun toggleSpeaker(): Boolean {
        val newValue = !isSpeakerOn.get()
        isSpeakerOn.set(newValue)
        
        // Update audio output device based on toggle
        _audioOutputDevice.value = if (newValue) AudioOutputDevice.LOUDSPEAKER else AudioOutputDevice.EARPIECE
        
        // Apply speaker change if currently recording
        audioManager?.let { am ->
            applyAudioRouting(am, _audioOutputDevice.value)
        }
        
        return newValue
    }
    
    /**
     * Set audio output device (Earpiece, Loudspeaker, or Bluetooth)
     */
    fun setAudioOutputDevice(device: AudioOutputDevice) {
        _audioOutputDevice.value = device
        
        // Update legacy speaker state
        isSpeakerOn.set(device == AudioOutputDevice.LOUDSPEAKER)
        
        // Apply change if currently recording
        audioManager?.let { am ->
            applyAudioRouting(am, device)
        }
        
        Log.d("LiveApiService", "🔊 Audio output set to: $device")
        addDebugLog("🔊 Output: $device")
    }
    
    /**
     * Apply audio routing based on selected output device
     */
    private fun applyAudioRouting(am: AudioManager, device: AudioOutputDevice) {
        when (device) {
            AudioOutputDevice.EARPIECE -> {
                // Route to earpiece (phone speaker at top)
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                Log.d("LiveApiService", "🔊 Routing to EARPIECE + Hardware AEC")
                addDebugLog("🔊 EARPIECE + AEC")
            }
            
            AudioOutputDevice.LOUDSPEAKER -> {
                // Route to loudspeaker (bottom speaker, hands-free)
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                Log.d("LiveApiService", "🔊 Routing to LOUDSPEAKER + Hardware AEC")
                addDebugLog("🔊 LOUDSPEAKER + AEC")
            }
            
            AudioOutputDevice.BLUETOOTH -> {
                // Route to Bluetooth headset/earbuds
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                Log.d("LiveApiService", "🔊 Routing to BLUETOOTH + Hardware AEC")
                addDebugLog("🔊 BLUETOOTH + AEC")
            }
        }
    }
    
    /**
     * Detect available Bluetooth audio devices
     */
    fun detectAvailableBluetoothDevices(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = mutableListOf<String>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (deviceInfo in audioDevices) {
                    if (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            deviceInfo.productName.toString()
                        } else {
                            "Bluetooth Device"
                        }
                        devices.add(name)
                    }
                }
            }
            
            _availableBluetoothDevices.value = devices
            Log.d("LiveApiService", "🎧 Detected ${devices.size} Bluetooth devices: $devices")
        } catch (e: Exception) {
            Log.e("LiveApiService", "Error detecting Bluetooth devices: ${e.message}")
            _availableBluetoothDevices.value = emptyList()
        }
    }
    
    /**
     * Check if Bluetooth audio is available
     */
    fun isBluetoothAvailable(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                audioDevices.any { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO 
                }
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                am.isBluetoothA2dpOn || am.isBluetoothScoOn
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if speaker is on - Legacy method
     */
    fun isSpeakerOn(): Boolean = isSpeakerOn.get()
    
    /**
     * Start audio playback processor (single AudioTrack)
     */
    private fun startPlaybackProcessor() {
        if (isPlaybackRunning.get()) return
        
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            isPlaybackRunning.set(true)
            
            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    outputSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                val format = android.media.AudioFormat.Builder()
                    .setSampleRate(outputSampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                
                val sessionToUse = if (sharedAudioSessionId != AudioManager.AUDIO_SESSION_ID_GENERATE) {
                    sharedAudioSessionId
                } else {
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                }
                
                playbackTrack = AudioTrack(
                    attrs, format,
                    minBuffer * 2, // Reduced from 4x for lower latency
                    AudioTrack.MODE_STREAM,
                    sessionToUse
                )
                
                if (playbackTrack?.state != AudioTrack.STATE_INITIALIZED) return@launch
                
                playbackTrack?.play()
                
                for (audioData in playbackQueue) {
                    if (!isActive || !isPlaybackRunning.get()) break
                    try {
                        // Calculate amplitude from the audio being PLAYED (not queued!)
                        // This ensures visualizer spikes match actual speech rate
                        _aiAmplitude.value = calculateAmplitude(audioData, audioData.size)
                        
                        // Write to AudioTrack (this blocks until audio plays)
                        playbackTrack?.write(audioData, 0, audioData.size)
                    } catch (e: Exception) {
                        if (e !is CancellationException) { /* ignore */ }
                    }
                }
                
                // Reset amplitude when playback finishes
                _aiAmplitude.value = 0f
                
                // After processing all queued audio, check if we should emit PlaybackComplete
                if (pendingPlaybackComplete.get()) {
                    // Wait for AudioTrack buffer to drain
                    val bufferSize = playbackTrack?.bufferSizeInFrames ?: 0
                    val sampleRate = outputSampleRate
                    val estimatedPlaybackTimeMs = if (bufferSize > 0 && sampleRate > 0) {
                        (bufferSize * 1000L / sampleRate) + 300 // Add 300ms buffer for safety
                    } else {
                        500L // Default wait time
                    }
                    delay(estimatedPlaybackTimeMs)
                    
                    // Check if no new audio came in (by comparing timestamps)
                    val timeSinceLastAudio = System.currentTimeMillis() - lastAudioChunkTime.get()
                    if (timeSinceLastAudio > estimatedPlaybackTimeMs && pendingPlaybackComplete.get()) {
                        emitMessage(LiveMessage.PlaybackComplete)
                        pendingPlaybackComplete.set(false)
                    }
                }
            } catch (e: Exception) {
                // Playback error - silent
            } finally {
                playbackCompleteJob?.cancel()
                playbackCompleteJob = null
                try {
                    playbackTrack?.stop()
                    playbackTrack?.release()
                    playbackTrack = null
                } catch (e: Exception) { /* cleanup error */ }
                isPlaybackRunning.set(false)
                pendingPlaybackComplete.set(false)
            }
        }
    }
    
    /**
     * Queue audio for playback
     */
    private fun queueAudio(base64Data: String) {
        if (base64Data.isEmpty()) return
        
        try {
            val audioData = Base64.decode(base64Data, Base64.DEFAULT)
            if (audioData.size < 50) return
            
            // DON'T calculate amplitude here - chunks arrive fast during generation!
            // Amplitude is calculated in playback processor when audio actually plays
            
            // Start processor if not running
            if (!isPlaybackRunning.get()) {
                startPlaybackProcessor()
            }
            
            // Queue the audio
            playbackQueue.trySend(audioData)
            // Mark timestamp of last audio chunk
            lastAudioChunkTime.set(System.currentTimeMillis())
            // Mark that we should emit PlaybackComplete when playback finishes
            pendingPlaybackComplete.set(true)
            
        } catch (e: Exception) { /* queue error */ }
    }
    
    /**
     * Calculate audio amplitude (RMS) for visualizer - returns 0-1 range
     */
    private fun calculateAmplitude(buffer: ByteArray, size: Int): Float {
        if (size < 2) return 0f
        var sum = 0.0
        val samples = size / 2
        for (i in 0 until samples) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / samples).toFloat()
        // Normalize to 0-1 range (16-bit audio max is 32767)
        return (rms / 8000f).coerceIn(0f, 1f)
    }
    
    /**
     * Stop playback
     */
    fun stopPlayback() {
        isPlaybackRunning.set(false)
        _aiAmplitude.value = 0f
        playbackJob?.cancel()
        playbackJob = null
        
        try {
            playbackTrack?.stop()
            playbackTrack?.release()
            playbackTrack = null
        } catch (e: Exception) { /* cleanup error */ }
        
        while (playbackQueue.tryReceive().isSuccess) { }
    }
    
    /**
     * End session
     */
    /**
     * End session and auto-generate title if messages were exchanged
     */
    suspend fun endSession(): Boolean = withContext(Dispatchers.IO) {
        stopRecording()
        stopPlayback()
        
        if (!hasActiveSession()) return@withContext true
        
        // Auto-generate title before ending session if messages were exchanged
        if (messageCount.get() && 
            !titleUpdateTriggered.get() && 
            !currentConversationId.isNullOrEmpty() && 
            !currentUserId.isNullOrEmpty()) {
            
            titleUpdateTriggered.set(true)
            
            // Launch title update in background (don't block session end)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    delay(500) // Small delay to ensure last messages are saved
                    Log.d("LiveApiService", "🏷️ Auto-updating live conversation title: $currentConversationId")
                    
                    val response = ApiService.updateConversationTitle(currentUserId!!, currentConversationId!!)
                    when (response) {
                        is ApiResponse.Success -> {
                            Log.d("LiveApiService", "✅ Live title updated: ${response.data.newTitle}")
                        }
                        is ApiResponse.Error -> {
                            Log.w("LiveApiService", "⚠️ Title update failed: ${response.error}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LiveApiService", "❌ Title update error: ${e.message}")
                }
            }
        }
        
        try {
            val message = JSONObject().apply {
                put("type", "end_session")
            }
            
            val success = webSocket?.send(message.toString()) == true
            if (success) {
                currentSessionId = null
                _sessionState.value = SessionState.Idle
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Disconnect
     */
    fun disconnect() {
        stopRecording()
        stopPlayback()
        stopThinkingSound() // Stop thinking sound on disconnect
        webSocket?.close(1000, "Disconnect")
        cleanup()
    }
    
    private fun cleanup() {
        webSocket = null
        isConnected.set(false)
        currentSessionId = null
        stopThinkingSound() // Stop thinking sound on cleanup
        _connectionState.value = ConnectionState.DISCONNECTED
        _sessionState.value = SessionState.Idle
    }
    
    /**
     * Handle incoming messages
     */
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            
            addDebugLog("MSG: $type")
            
            when (type) {
                "connected" -> {
                    val clientId = json.optString("clientId")
                    addDebugLog("Connected: $clientId")
                    emitMessage(LiveMessage.Connected(clientId))
                }
                
                "session_created" -> {
                    val sessionId = json.optString("sessionId")
                    currentSessionId = sessionId
                    val rawConvoId = json.optString("conversationId", "")
                    val conversationId = if (rawConvoId.isNotEmpty()) rawConvoId else null
                    addDebugLog("Session created: $sessionId")
                    emitMessage(LiveMessage.SessionCreated(sessionId, conversationId))
                    if (_sessionState.value == SessionState.Active) {
                        emitMessage(LiveMessage.StartRecordingRequest)
                    }
                }
                
                "session_ready" -> {
                    val sessionId = json.optString("sessionId")
                    val rawConvoId = json.optString("conversationId", "")
                    val conversationId = if (rawConvoId.isNotEmpty()) rawConvoId else null
                    if (currentSessionId == null) currentSessionId = sessionId
                    _sessionState.value = SessionState.Active
                    addDebugLog("Session ready: $sessionId")
                    emitMessage(LiveMessage.SessionReady(sessionId, conversationId))
                    emitMessage(LiveMessage.StartRecordingRequest)
                }
                
                "input_transcription" -> {
                    val transcriptionText = json.optString("text")
                    if (transcriptionText.isNotEmpty()) {
                        addDebugLog("User: ${transcriptionText.take(50)}...")
                        // Clear bounding boxes when user starts speaking/sends new input
                        clearBoundingBoxes()
                        emitMessage(LiveMessage.InputTranscription(transcriptionText))
                        // Mark that messages were exchanged (for auto-title generation)
                        messageCount.set(true)
                    }
                }
                
                "output_transcription" -> {
                    val transcriptionText = json.optString("text")
                    if (transcriptionText.isNotEmpty()) {
                        addDebugLog("AI: ${transcriptionText.take(50)}...")
                        emitMessage(LiveMessage.OutputTranscription(transcriptionText))
                        // Mark that messages were exchanged (for auto-title generation)
                        messageCount.set(true)
                        
                        // Start haptic feedback when AI starts generating (first output transcription)
                        // This happens AFTER user stops speaking
                        if (!isAiGenerating.get()) {
                            isAiGenerating.set(true)
                            hapticManager.startContinuousHapticFeedback()
                        }
                    }
                }
                
                "audio_data" -> {
                    val audioData = json.optString("data")
                    if (audioData.isNotEmpty()) {
                        // Stop haptic feedback when AI starts speaking (first audio chunk)
                        if (isAiGenerating.get()) {
                            isAiGenerating.set(false)
                            hapticManager.stopContinuousHapticFeedback()
                        }
                        
                        emitMessage(LiveMessage.AudioData(audioData))
                        queueAudio(audioData)
                    }
                }
                
                "turn_complete" -> {
                    val sessionId = json.optString("sessionId")
                    emitMessage(LiveMessage.TurnComplete(sessionId))
                    // Schedule PlaybackComplete check after a delay
                    // This ensures we wait for all queued audio to finish playing
                    playbackCompleteJob?.cancel()
                    playbackCompleteJob = CoroutineScope(Dispatchers.IO).launch {
                        // Wait for any remaining audio chunks to be processed
                        delay(500) // Give time for queue to be processed
                        
                        // Wait for AudioTrack buffer to drain
                        val bufferSize = playbackTrack?.bufferSizeInFrames ?: 0
                        val sampleRate = outputSampleRate
                        val estimatedPlaybackTimeMs = if (bufferSize > 0 && sampleRate > 0) {
                            (bufferSize * 1000L / sampleRate) + 400 // Add 400ms buffer
                        } else {
                            800L // Default wait time
                        }
                        delay(estimatedPlaybackTimeMs)
                        
                        // Check if no new audio came in
                        val timeSinceLastAudio = System.currentTimeMillis() - lastAudioChunkTime.get()
                        if (timeSinceLastAudio > estimatedPlaybackTimeMs && pendingPlaybackComplete.get()) {
                            emitMessage(LiveMessage.PlaybackComplete)
                            pendingPlaybackComplete.set(false)
                        }
                    }
                }
                
                "generation_complete" -> {
                    val sessionId = json.optString("sessionId")
                    emitMessage(LiveMessage.GenerationComplete(sessionId))
                }
                
                "interrupted" -> {
                    val sessionId = json.optString("sessionId")
                    while (playbackQueue.tryReceive().isSuccess) { }
                    // Stop haptic feedback on interruption
                    isAiGenerating.set(false)
                    hapticManager.stopContinuousHapticFeedback()
                    emitMessage(LiveMessage.Interrupted(sessionId))
                }
                
                "session_closed", "session_ended" -> {
                    val reason = json.optString("reason")
                    // Stop haptic feedback when session ends
                    isAiGenerating.set(false)
                    hapticManager.stopContinuousHapticFeedback()
                    stopRecording()
                    stopPlayback()
                    emitMessage(LiveMessage.SessionClosed(reason))
                    _sessionState.value = SessionState.Idle
                }
                
                "go_away" -> {
                    val timeLeft = json.optString("timeLeft")
                    emitMessage(LiveMessage.GoAway(timeLeft))
                }
                
                "error" -> {
                    val error = json.optString("error")
                    emitMessage(LiveMessage.Error(error))
                    _sessionState.value = SessionState.Error
                }
                
                "rate_limit_exceeded" -> {
                    // Handle rate limit from backend
                    val errorMsg = json.optString("error", "Rate limit exceeded")
                    val retryAfter = json.optInt("retryAfter", 60)
                    val limit = json.optInt("limit", 0)
                    val limitType = json.optString("limitType", "daily")
                    
                    addDebugLog("⚠️ Rate limit: $errorMsg (retry: ${retryAfter}s)")
                    emitMessage(LiveMessage.RateLimitExceeded(errorMsg, retryAfter, limit, limitType))
                    _sessionState.value = SessionState.Error
                }
                
                "highlight_objects" -> {
                    // Parse bounding boxes from Gemini highlighter function
                    // Backend sends: { type: "highlight_objects", objects: [{label, box_2d: [y_min, x_min, y_max, x_max]}] }
                    // box_2d values are normalized 0-1000
                    try {
                        val objectsArray = json.optJSONArray("objects")
                        val boxes = mutableListOf<BoundingBox>()
                        
                        if (objectsArray != null) {
                            for (i in 0 until objectsArray.length()) {
                                val obj = objectsArray.getJSONObject(i)
                                val label = obj.optString("label", "Object")
                                val box2d = obj.optJSONArray("box_2d")
                                
                                if (box2d != null && box2d.length() >= 4) {
                                    // Convert from [y_min, x_min, y_max, x_max] (0-1000) to normalized 0-1 coordinates
                                    val yMin = box2d.optDouble(0, 0.0) / 1000.0
                                    val xMin = box2d.optDouble(1, 0.0) / 1000.0
                                    val yMax = box2d.optDouble(2, 1000.0) / 1000.0
                                    val xMax = box2d.optDouble(3, 1000.0) / 1000.0
                                    
                                    val x = xMin.toFloat()
                                    val y = yMin.toFloat()
                                    val width = (xMax - xMin).toFloat()
                                    val height = (yMax - yMin).toFloat()
                                    
                                    boxes.add(BoundingBox(
                                        label = label,
                                        x = x,
                                        y = y,
                                        width = width,
                                        height = height,
                                        color = null,
                                        confidence = null
                                    ))
                                    
                                    addDebugLog("Box: $label @ ($x, $y) ${width}x$height")
                                }
                            }
                        }
                        
                        addDebugLog("🎯 Highlight: ${boxes.size} objects - ${boxes.map { it.label }.joinToString(", ")}")
                        Log.d("LiveApiService", "Received ${boxes.size} bounding boxes")
                        _boundingBoxes.value = boxes
                        
                        emitMessage(LiveMessage.HighlightObjects(boxes))
                    } catch (e: Exception) {
                        addDebugLog("❌ Error parsing highlight: ${e.message}")
                        Log.e("LiveApiService", "Error parsing highlight_objects: ${e.message}")
                    }
                }
                
                "thinking_started" -> {
                    // AI is "thinking" - play ambient thinking sound
                    val thought = json.optString("thought", "")
                    addDebugLog("🧠 Thinking: ${thought.take(50)}...")
                    startThinkingSound()
                    emitMessage(LiveMessage.ThinkingStarted(thought.ifEmpty { null }))
                }
                
                "thinking_stopped" -> {
                    // AI stopped thinking - stop the sound
                    addDebugLog("🧠 Thinking stopped")
                    stopThinkingSound()
                    emitMessage(LiveMessage.ThinkingStopped)
                }
                
                "ping" -> webSocket?.send(JSONObject().put("type", "pong").toString())
                "pong" -> { }
            }
        } catch (e: Exception) { /* message parse error */ }
    }
    
    private fun emitMessage(msg: LiveMessage) {
        CoroutineScope(Dispatchers.Main).launch {
            _messages.emit(msg)
        }
    }
    
    fun hasActiveSession(): Boolean = isConnected.get() && currentSessionId != null
    fun isConnected(): Boolean = isConnected.get()
    fun isRecording(): Boolean = isRecording.get()
    
    /**
     * Clear bounding boxes overlay
     */
    fun clearBoundingBoxes() {
        if (_boundingBoxes.value.isNotEmpty()) {
            addDebugLog("🧹 Clearing bounding boxes")
        }
        _boundingBoxes.value = emptyList()
    }
}
