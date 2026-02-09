package com.shubharthak.apsaradark.live

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge for communication between the foreground service / broadcast receiver
 * and the LiveSessionViewModel. Uses SharedFlow for events and StateFlow for
 * notification state that the service reads when rebuilding the notification.
 */
object LiveSessionBridge {

    // ── Events (ViewModel observes these) ──────────────────────────

    private val _stopRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequested = _stopRequested.asSharedFlow()

    private val _muteToggleRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val muteToggleRequested = _muteToggleRequested.asSharedFlow()

    fun requestStopLive() {
        _stopRequested.tryEmit(Unit)
    }

    fun requestMuteToggle() {
        _muteToggleRequested.tryEmit(Unit)
    }

    // ── Notification state (ViewModel pushes, Service reads) ───────

    /** Who is currently speaking */
    private val _activeSpeaker = MutableStateFlow(ActiveSpeaker.NONE)
    val activeSpeaker = _activeSpeaker.asStateFlow()

    /** Whether mic is muted */
    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    /** Whether a live session is active at all */
    private val _isLiveActive = MutableStateFlow(false)
    val isLiveActive = _isLiveActive.asStateFlow()

    fun updateSpeaker(speaker: ActiveSpeaker) {
        _activeSpeaker.value = speaker
    }

    fun updateMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun updateLiveActive(active: Boolean) {
        _isLiveActive.value = active
    }

    /** Reset all state when session ends */
    fun reset() {
        _activeSpeaker.value = ActiveSpeaker.NONE
        _isMuted.value = false
        _isLiveActive.value = false
    }
}
