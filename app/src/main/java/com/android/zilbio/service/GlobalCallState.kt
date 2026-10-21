package com.android.zilbio.service

import com.android.zilbio.data.webrtc.SharedAudioCallEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalCallState {
    @Volatile
    var isInActiveVideoCall: Boolean = false

    @Volatile
    var activeAudioEngine: SharedAudioCallEngine? = null

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    /**
     * Smart camera lifecycle:
     * - Scenario A: App in floating PiP -> Keep camera active & transmit video.
     * - Scenario B: App completely minimized without PiP -> Pause camera stream to save battery and preserve privacy.
     */
    fun onAppBackgrounded(inPip: Boolean) {
        if (!inPip && isInActiveVideoCall) {
            activeAudioEngine?.pauseLocalCamera()
        }
    }

    /**
     * Scenario C: Resuming foreground / maximizing PiP -> Restart local camera and resume live video.
     */
    fun onAppForegrounded() {
        if (isInActiveVideoCall) {
            activeAudioEngine?.resumeLocalCamera()
        }
    }
}
