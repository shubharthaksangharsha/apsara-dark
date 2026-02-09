package com.shubharthak.apsaradark.live

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple bridge for communication between the foreground service / broadcast receiver
 * and the LiveSessionViewModel. Uses SharedFlow so the ViewModel can observe
 * stop requests from the notification action.
 */
object LiveSessionBridge {

    private val _stopRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequested = _stopRequested.asSharedFlow()

    fun requestStopLive() {
        _stopRequested.tryEmit(Unit)
    }
}
