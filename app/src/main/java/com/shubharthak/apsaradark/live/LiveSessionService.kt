package com.shubharthak.apsaradark.live

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shubharthak.apsaradark.MainActivity
import com.shubharthak.apsaradark.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/**
 * Foreground service that keeps the Apsara Dark live session alive
 * when the app is in the background or the screen is off.
 *
 * Shows a persistent notification with:
 * - Dynamic status text (who is speaking / listening / muted)
 * - Visual indicators using emoji waveforms
 * - Mute toggle action
 * - End session action
 */
class LiveSessionService : Service() {

    companion object {
        private const val TAG = "LiveService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "apsara_live_session"

        fun start(context: Context) {
            val intent = Intent(context, LiveSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveSessionService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationUpdateJob: Job? = null

    // Track last notification state to avoid unnecessary rebuilds
    private var lastSpeaker: ActiveSpeaker = ActiveSpeaker.NONE
    private var lastMuted: Boolean = false
    private var waveIndex: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        val notification = buildNotification(ActiveSpeaker.NONE, isMuted = false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start observing bridge state to auto-update notification
        startNotificationUpdates()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Observe LiveSessionBridge state flows and update notification
     * whenever speaker or mute state changes.
     */
    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            // Combine speaker + muted flows
            combine(
                LiveSessionBridge.activeSpeaker,
                LiveSessionBridge.isMuted
            ) { speaker, muted ->
                Pair(speaker, muted)
            }.collect { (speaker, muted) ->
                // Animate waveform: cycle through frames when someone is speaking
                if (speaker != ActiveSpeaker.NONE) {
                    waveIndex = (waveIndex + 1) % WAVE_FRAMES.size
                }
                // Only rebuild if state actually changed
                if (speaker != lastSpeaker || muted != lastMuted) {
                    lastSpeaker = speaker
                    lastMuted = muted
                    val notification = buildNotification(speaker, muted)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(speaker: ActiveSpeaker, isMuted: Boolean): Notification {
        // Tap notification → open the app
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Build status text and sub-text based on state ──

        val (title, contentText, subText) = when {
            isMuted -> Triple(
                "Apsara Dark — Muted",
                "🔇 Microphone muted · Tap to unmute",
                "Mic off"
            )
            speaker == ActiveSpeaker.APSARA -> {
                val wave = WAVE_FRAMES[waveIndex % WAVE_FRAMES.size]
                Triple(
                    "Apsara Dark — Speaking",
                    "$wave Apsara is speaking…",
                    "Apsara"
                )
            }
            speaker == ActiveSpeaker.USER -> {
                val wave = WAVE_FRAMES_USER[waveIndex % WAVE_FRAMES_USER.size]
                Triple(
                    "Apsara Dark — Listening",
                    "$wave You are speaking…",
                    "Listening"
                )
            }
            else -> Triple(
                "Apsara Dark — Live",
                "✦ Listening for your voice…",
                "Ready"
            )
        }

        // ── Mute/Unmute action ──
        val muteIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(ACTION_MUTE_TOGGLE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIcon = if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic
        val muteLabel = if (isMuted) "Unmute" else "Mute"

        // ── End session action ──
        val stopIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_STOP_LIVE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(muteIcon, muteLabel, muteIntent)
            .addAction(R.drawable.ic_notif_stop, "End", stopIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Apsara Dark has an active live voice session"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}

// ── Broadcast actions ───────────────────────────────────────────

const val ACTION_STOP_LIVE = "com.shubharthak.apsaradark.STOP_LIVE"
const val ACTION_MUTE_TOGGLE = "com.shubharthak.apsaradark.MUTE_TOGGLE"

// ── Animated waveform frames for notification text ──────────────
// These cycle through to give the impression of audio activity.

private val WAVE_FRAMES = listOf(
    "♪ ▁▃▅▇▅▃▁",
    "♪ ▂▅▇▅▃▁▂",
    "♪ ▃▇▅▃▁▂▃",
    "♪ ▅▅▃▁▂▃▅",
    "♪ ▇▃▁▂▃▅▇",
    "♪ ▅▁▂▃▅▇▅",
    "♪ ▃▂▃▅▇▅▃",
    "♪ ▁▃▅▇▅▃▁"
)

private val WAVE_FRAMES_USER = listOf(
    "🎤 ▁▃▅▇▅▃▁",
    "🎤 ▂▅▇▅▃▁▂",
    "🎤 ▃▇▅▃▁▂▃",
    "🎤 ▅▅▃▁▂▃▅",
    "🎤 ▇▃▁▂▃▅▇",
    "🎤 ▅▁▂▃▅▇▅",
    "🎤 ▃▂▃▅▇▅▃",
    "🎤 ▁▃▅▇▅▃▁"
)
