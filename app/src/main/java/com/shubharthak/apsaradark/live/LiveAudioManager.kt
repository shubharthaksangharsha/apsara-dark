package com.shubharthak.apsaradark.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Audio output device options — which speaker to route audio to.
 */
enum class AudioOutputDevice {
    EARPIECE,      // Phone earpiece (top speaker)
    LOUDSPEAKER,   // Phone loudspeaker (bottom speaker, hands-free)
    BLUETOOTH      // Bluetooth headset/earbuds
}

/**
 * Records PCM audio from mic and plays PCM audio from Gemini.
 * Uses Android's built-in AEC (Acoustic Echo Cancellation) so the mic
 * doesn't pick up the speaker output — Apsara won't hear/interrupt herself.
 *
 * Supports routing audio to Earpiece, Loudspeaker, or Bluetooth,
 * all with hardware AEC active via MODE_IN_COMMUNICATION.
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
        // Amplitude smoothing factor (0..1, higher = faster response)
        private const val AMPLITUDE_SMOOTHING = 0.45f
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Current audio output device
    private val _audioOutputDevice = MutableStateFlow(AudioOutputDevice.LOUDSPEAKER)
    val audioOutputDevice: StateFlow<AudioOutputDevice> = _audioOutputDevice.asStateFlow()

    // Audio amplitude levels (0.0 .. 1.0) for visualizer
    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordJob: Job? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // AEC and noise suppressor effects
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    // Shared audio session ID — AudioRecord and AudioTrack MUST share the same session
    // for hardware AEC to correctly cancel the playback output from the mic input.
    private var sharedAudioSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var onAudioChunk: ((ByteArray) -> Unit)? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Save previous audio state for restoration
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    @Suppress("DEPRECATION")
    private var wasSpeakerphoneOn: Boolean = false

    // Wake lock + WiFi lock to prevent CPU/network sleep during live session
    private var wakeLock: PowerManager.WakeLock? = null
    @Suppress("DEPRECATION")
    private var wifiLock: WifiManager.WifiLock? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Apply audio routing based on selected output device.
     * All modes use MODE_IN_COMMUNICATION for hardware AEC.
     */
    private fun applyAudioRouting(device: AudioOutputDevice) {
        when (device) {
            AudioOutputDevice.EARPIECE -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                try {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                } catch (_: Exception) {}
                Log.d(TAG, "🔊 Routing to EARPIECE + Hardware AEC")
            }

            AudioOutputDevice.LOUDSPEAKER -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                try {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                } catch (_: Exception) {}
                Log.d(TAG, "🔊 Routing to LOUDSPEAKER + Hardware AEC")
            }

            AudioOutputDevice.BLUETOOTH -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "🔊 Routing to BLUETOOTH + Hardware AEC")
            }
        }
    }

    /**
     * Set audio output device. Can be called while recording to switch live.
     * Restarts the AudioTrack if playback is active so the new route takes effect.
     */
    fun setAudioOutputDevice(device: AudioOutputDevice) {
        _audioOutputDevice.value = device
        if (_isRecording.value) {
            applyAudioRouting(device)
            // Restart AudioTrack so it picks up the new routing
            if (audioTrack != null) {
                Log.d(TAG, "Restarting AudioTrack for route change to $device")
                restartPlayback()
            }
        }
        Log.d(TAG, "Audio output set to: $device")
    }

    /**
     * Cycle through available output devices: Loudspeaker → Earpiece → Bluetooth → Loudspeaker
     * Skips Bluetooth if not available.
     */
    fun cycleAudioOutputDevice(): AudioOutputDevice {
        val hasBluetooth = isBluetoothAvailable()
        val current = _audioOutputDevice.value
        val next = when (current) {
            AudioOutputDevice.LOUDSPEAKER -> AudioOutputDevice.EARPIECE
            AudioOutputDevice.EARPIECE -> if (hasBluetooth) AudioOutputDevice.BLUETOOTH else AudioOutputDevice.LOUDSPEAKER
            AudioOutputDevice.BLUETOOTH -> AudioOutputDevice.LOUDSPEAKER
        }
        setAudioOutputDevice(next)
        return next
    }

    /**
     * Check if Bluetooth audio output is available.
     */
    fun isBluetoothAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                audioDevices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (_: Exception) { false }
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

        // Save previous audio state
        previousAudioMode = audioManager.mode
        @Suppress("DEPRECATION")
        wasSpeakerphoneOn = audioManager.isSpeakerphoneOn

        // Apply routing for selected output device (sets MODE_IN_COMMUNICATION + speaker/bt)
        applyAudioRouting(_audioOutputDevice.value)

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
        val sessionId = audioRecord?.audioSessionId ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        sharedAudioSessionId = sessionId
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

        // Acquire wake lock + wifi lock to keep CPU/network alive in background
        acquireWakeLocks()

        recordJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && _isRecording.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    // Always compute input amplitude for visualizer (even when muted)
                    val amplitude = computeAmplitude(buffer, read)
                    _inputAmplitude.value = smoothAmplitude(_inputAmplitude.value, amplitude)

                    if (!_isMuted.value) {
                        onAudioChunk?.invoke(buffer.copyOf(read))
                    }
                }
            }
            // Reset amplitude when recording stops
            _inputAmplitude.value = 0f
        }

        Log.d(TAG, "Recording started (AEC mode, output=${_audioOutputDevice.value})")
    }

    fun stopRecording() {
        _isRecording.value = false
        _inputAmplitude.value = 0f
        recordJob?.cancel()
        recordJob = null

        // Release audio effects
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        onAudioChunk = null
        sharedAudioSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE

        // Restore previous audio state
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        } catch (_: Exception) {}
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = wasSpeakerphoneOn
        audioManager.mode = previousAudioMode

        // Release wake lock + wifi lock
        releaseWakeLocks()

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
     * for echo cancellation. Shares the audio session with AudioRecord for proper AEC.
     */
    fun startPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            .coerceAtLeast(4096)

        val sessionToUse = if (sharedAudioSessionId != AudioManager.AUDIO_SESSION_ID_GENERATE) {
            sharedAudioSessionId
        } else {
            AudioManager.AUDIO_SESSION_ID_GENERATE
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(OUTPUT_SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .setEncoding(ENCODING)
            .build()

        audioTrack = AudioTrack(
            attrs,
            format,
            bufferSize * 2,
            AudioTrack.MODE_STREAM,
            sessionToUse
        )

        audioTrack?.play()

        playbackJob = scope.launch {
            while (isActive) {
                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    // Update output amplitude level from audio data
                    val amplitude = computeAmplitude(chunk, chunk.size)
                    _outputAmplitude.value = smoothAmplitude(_outputAmplitude.value, amplitude)
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    // Decay output amplitude toward zero when no audio is playing
                    if (_outputAmplitude.value > 0.01f) {
                        _outputAmplitude.value = smoothAmplitude(_outputAmplitude.value, 0f)
                    } else {
                        _outputAmplitude.value = 0f
                    }
                    delay(10)
                }
            }
        }

        Log.d(TAG, "Playback started (voice communication mode, session=$sessionToUse)")
    }

    /**
     * Restart playback — stops and re-creates the AudioTrack so it picks up
     * any audio routing changes (e.g. switching from earpiece to loudspeaker).
     * Preserves any queued audio data.
     */
    private fun restartPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        // Don't clear the queue — keep any pending audio
        startPlayback()
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playbackQueue.clear()
        _outputAmplitude.value = 0f
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
        _outputAmplitude.value = 0f
    }

    fun release() {
        stopRecording()
        stopPlayback()
        releaseWakeLocks()
        scope.cancel()
    }

    /**
     * Acquire a partial wake lock (CPU) and WiFi lock to prevent
     * the system from sleeping the CPU or dropping WiFi while a
     * live session is active. This is critical for background operation.
     */
    @Suppress("DEPRECATION")
    private fun acquireWakeLocks() {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ApsaraDark:LiveSession"
                )
                wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
                Log.d(TAG, "Wake lock acquired")
            }
            if (wifiLock == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ApsaraDark:LiveSession")
                wifiLock?.acquire()
                Log.d(TAG, "WiFi lock acquired")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake/wifi lock: ${e.message}")
        }
    }

    /**
     * Release wake lock and WiFi lock.
     */
    private fun releaseWakeLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock: ${e.message}")
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "WiFi lock released")
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WiFi lock: ${e.message}")
        }
    }

    /**
     * Compute RMS amplitude from PCM 16-bit audio bytes, returns 0.0..1.0
     */
    private fun computeAmplitude(pcmBytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        val samples = length / 2
        var sumSquares = 0.0
        for (i in 0 until samples) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = kotlin.math.sqrt(sumSquares / samples)
        // Normalize: VOICE_COMMUNICATION source outputs quieter PCM than raw mic.
        // Typical speech RMS is ~500-4000 through AEC pipeline.
        // Use a lower divisor so normal speech maps to ~0.5-1.0 range.
        val normalized = (rms / 4000.0).coerceIn(0.0, 1.0).toFloat()
        // Apply a sqrt curve to boost quieter speech and make it more visible
        return kotlin.math.sqrt(normalized.toDouble()).toFloat()
    }

    /**
     * Smoothly update amplitude with exponential moving average
     */
    private fun smoothAmplitude(current: Float, target: Float): Float {
        return current + AMPLITUDE_SMOOTHING * (target - current)
    }
}
