package com.shubharthak.apsaradark.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Records PCM audio from mic and plays PCM audio from Gemini.
 * Uses Android's built-in AEC (Acoustic Echo Cancellation) so the mic
 * doesn't pick up the speaker output — Apsara won't hear/interrupt herself.
 *
 * Input:  16kHz, mono, 16-bit PCM
 * Output: 24kHz, mono, 16-bit PCM
 */
class LiveAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveAudio"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordJob: Job? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // AEC and noise suppressor effects
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var onAudioChunk: ((ByteArray) -> Unit)? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording mic audio. Calls onChunk with PCM byte arrays.
     * Uses VOICE_COMMUNICATION source + AEC for echo cancellation.
     */
    fun startRecording(onChunk: (ByteArray) -> Unit) {
        if (!hasPermission()) {
            Log.e(TAG, "No RECORD_AUDIO permission")
            return
        }
        if (_isRecording.value) return

        onAudioChunk = onChunk
        val bufferSize = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, CHANNEL_IN, ENCODING)
            .coerceAtLeast(4096)

        // Set communication mode so Android's AEC pipeline can reference the speaker output
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException creating AudioRecord: ${e.message}")
            return
        }

        // Attach AEC (Acoustic Echo Canceler) if available
        val sessionId = audioRecord?.audioSessionId ?: 0
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(sessionId)
                echoCanceler?.enabled = true
                Log.d(TAG, "AEC enabled (session=$sessionId)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create AEC: ${e.message}")
            }
        } else {
            Log.w(TAG, "AEC not available on this device")
        }

        // Attach Noise Suppressor if available
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)
                noiseSuppressor?.enabled = true
                Log.d(TAG, "Noise suppressor enabled")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create noise suppressor: ${e.message}")
            }
        }

        audioRecord?.startRecording()
        _isRecording.value = true
        _isMuted.value = false

        recordJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && _isRecording.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0 && !_isMuted.value) {
                    onAudioChunk?.invoke(buffer.copyOf(read))
                }
            }
        }

        Log.d(TAG, "Recording started (AEC mode)")
    }

    fun stopRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        // Release audio effects
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        onAudioChunk = null

        // Reset audio mode
        audioManager.mode = AudioManager.MODE_NORMAL

        Log.d(TAG, "Recording stopped")
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        Log.d(TAG, "Mute: ${_isMuted.value}")
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    /**
     * Start the playback loop — drains the playbackQueue and writes to AudioTrack.
     * Uses USAGE_VOICE_COMMUNICATION so the AEC pipeline can reference this output
     * for echo cancellation.
     */
    fun startPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            .coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playbackJob = scope.launch {
            while (isActive) {
                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    delay(10)
                }
            }
        }

        Log.d(TAG, "Playback started (voice communication mode)")
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playbackQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        Log.d(TAG, "Playback stopped")
    }

    /** Enqueue received audio for playback */
    fun enqueueAudio(pcmBytes: ByteArray) {
        playbackQueue.add(pcmBytes)
    }

    /** Clear playback queue (on interruption) */
    fun clearPlaybackQueue() {
        playbackQueue.clear()
    }

    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
    }
}
