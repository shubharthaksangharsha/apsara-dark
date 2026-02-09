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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Records PCM audio from mic and plays PCM audio from Gemini.
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

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var onAudioChunk: ((ByteArray) -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording mic audio. Calls onChunk with PCM byte arrays.
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

        Log.d(TAG, "Recording started")
    }

    fun stopRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        onAudioChunk = null
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
     */
    fun startPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            .coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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

        Log.d(TAG, "Playback started")
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
