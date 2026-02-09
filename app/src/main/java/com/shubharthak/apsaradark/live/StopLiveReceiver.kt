package com.shubharthak.apsaradark.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the "stop live" broadcast from the notification action.
 * Posts an event that the ViewModel can observe to stop the session.
 */
class StopLiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_LIVE) {
            Log.d("StopLiveReceiver", "Stop live broadcast received")
            // Stop the foreground service
            LiveSessionService.stop(context)
            // Post to the global stop event — ViewModel observes this
            LiveSessionBridge.requestStopLive()
        }
    }
}
