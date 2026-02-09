package com.shubharthak.apsaradark.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives broadcasts from notification actions (mute toggle, end session).
 * Posts events that the ViewModel observes.
 */
class StopLiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_LIVE -> {
                Log.d("StopLiveReceiver", "Stop live broadcast received")
                LiveSessionService.stop(context)
                LiveSessionBridge.requestStopLive()
            }
            ACTION_MUTE_TOGGLE -> {
                Log.d("StopLiveReceiver", "Mute toggle broadcast received")
                LiveSessionBridge.requestMuteToggle()
            }
        }
    }
}
