package com.android.zilbio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.android.zilbio.data.audio.VadAudioChunker
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.data.webrtc.SharedAudioCallEngine
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.domain.model.CallSession
import com.android.zilbio.domain.model.CallTranscriptSegment
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CallSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "CallSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()

    var audioEngine: SharedAudioCallEngine? = null
        private set
    var vadChunker: VadAudioChunker? = null
        private set

    private val _callState = MutableStateFlow<CallSession?>(null)
    val callState: StateFlow<CallSession?> = _callState.asStateFlow()

    private val _isCallEnded = MutableStateFlow(false)
    val isCallEnded: StateFlow<Boolean> = _isCallEnded.asStateFlow()

    private val _transcripts = MutableStateFlow<List<CallTranscriptSegment>>(emptyList())
    val transcripts: StateFlow<List<CallTranscriptSegment>> = _transcripts.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    // Mesh conference participant list (Item #5)
    private val _participants = MutableStateFlow<Map<String, String>>(emptyMap())
    val participants: StateFlow<Map<String, String>> = _participants.asStateFlow()

    // 3-second join notification banner (Item #5)
    private val _joinBanner = MutableStateFlow<String?>(null)
    val joinBanner: StateFlow<String?> = _joinBanner.asStateFlow()

    // Peer battery alert (Item #6)
    private val _peerBatteryAlert = MutableStateFlow<String?>(null)
    val peerBatteryAlert: StateFlow<String?> = _peerBatteryAlert.asStateFlow()

    // Live microphone audio levels for Caller & Callee UI
    private val _localMicLevel = MutableStateFlow(0f)
    val localMicLevel: StateFlow<Float> = _localMicLevel.asStateFlow()

    private val _remoteMicLevel = MutableStateFlow(0f)
    val remoteMicLevel: StateFlow<Float> = _remoteMicLevel.asStateFlow()

    private var durationJob: Job? = null
    private var outgoingTimeoutJob: Job? = null
    private var watchdogJob: Job? = null
    private var audioFocusRequest: Any? = null
    private val sseListeners = mutableListOf<Call>()

    private var hasBroadcast10Pct = false
    private var hasBroadcast5Pct = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100) / scale else level

            val session = _callState.value ?: return
            if (pct <= 10 && !hasBroadcast10Pct) {
                hasBroadcast10Pct = true
                com.android.zilbio.ui.components.ZilbioDynamicIslandManager.showBatteryAlert("", 10, isLocal = true)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.broadcastBatteryAlert(session.callId, session.callerId, 10)
                }
            } else if (pct <= 5 && !hasBroadcast5Pct) {
                hasBroadcast5Pct = true
                com.android.zilbio.ui.components.ZilbioDynamicIslandManager.showBatteryAlert("", 5, isLocal = true)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.broadcastBatteryAlert(session.callId, session.callerId, 5)
                }
            }
        }
    }

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    suspend fun initiateCall(myUser: User, friend: Friend, isVideo: Boolean = false) {
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            callerId = myUser.userId,
            receiverId = friend.userId,
            callerName = myUser.displayName.ifBlank { myUser.username },
            receiverName = friend.displayName.ifBlank { friend.username },
            status = "ringing",
            callType = if (isVideo) "video" else "audio",
            isVideo = isVideo,
            isConference = false,
            participants = mapOf(myUser.userId to true, friend.userId to true),
            participantNames = mapOf(
                myUser.userId to (myUser.displayName.ifBlank { myUser.username }),
                friend.userId to (friend.displayName.ifBlank { friend.username })
            ),
            startTime = System.currentTimeMillis()
        )
        _callState.value = session
        _isCallEnded.value = false
        _transcripts.value = emptyList()
        _participants.value = session.participantNames

        Log.i(TAG, "Initiating call: $callId (video=$isVideo) to ${friend.username} (${friend.userId})")

        // 1. Request VoIP audio focus
        requestCallAudioFocus()

        // 2. Register call in cloud database
        CloudRealtimeService.initiateCall(session)

        // 3. Initialize WebRTC audio/video engine
        val engine = SharedAudioCallEngine(context)
        engine.initialize(enableVideo = isVideo)
        engine.createPeerConnection(enableVideo = isVideo)
        audioEngine = engine

        // Collect local mic level and broadcast to peer via DataChannel
        scope.launch {
            engine.localMicLevel.collect { level ->
                _localMicLevel.value = level
                if (level > 0.04f) {
                    engine.sendTranscriptViaDataChannel("{\"type\":\"mic_level\",\"level\":$level}")
                }
            }
        }

        // 4. Handle local ICE candidates generated
        engine.onIceCandidateGenerated = { candidate ->
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendIceCandidate(
                    callId = callId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    senderId = myUser.userId
                )
            }
        }

        // 5. Listen for remote ICE candidates via SSE
        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex, senderId ->
            if (senderId == myUser.userId) return@listenForIceCandidates
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
            engine.addIceCandidate(candidate)
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        // 6. Dual-channel Answer detection
        val answerProcessed = AtomicBoolean(false)
        val onAnswerReceived: (String) -> Unit = { sdp ->
            if (answerProcessed.compareAndSet(false, true)) {
                outgoingTimeoutJob?.cancel()
                outgoingTimeoutJob = null
                Log.i(TAG, "Processing SDP answer for call $callId")
                val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                engine.setRemoteDescription(desc)
                _callState.value = _callState.value?.copy(status = "active")
                startDurationTimer()
                startVoiceDeliveryWatchdog()
            }
        }

        val answerCall = CloudRealtimeService.listenForAnswer(callId) { sdp ->
            onAnswerReceived(sdp)
        }
        synchronized(sseListeners) { sseListeners.add(answerCall) }

        scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (isActive && !answerProcessed.get() && attempts < 50) {
                delay(600L)
                attempts++
                try {
                    val sdp = CloudRealtimeService.getAnswer(callId)
                    if (!sdp.isNullOrBlank()) {
                        scope.launch(Dispatchers.Main) { onAnswerReceived(sdp) }
                        break
                    }
                } catch (e: Exception) {
                    // Retry
                }
            }
        }

        // 7. Listen for call session state changes (Item #4 Synchronized disconnect & mesh participants)
        val sessionCall = CloudRealtimeService.listenToCallSession(callId) { updatedSession ->
            if (updatedSession.status == "ended" || updatedSession.endedBy != null) {
                Log.i(TAG, "Remote call termination detected (endedBy=${updatedSession.endedBy})")
                _isCallEnded.value = true
                endCallLocally()
            } else {
                _callState.value = updatedSession
                if (updatedSession.participantNames.isNotEmpty()) {
                    val oldKeys = _participants.value.keys
                    val newKeys = updatedSession.participantNames.keys
                    val added = newKeys.filter { it !in oldKeys }
                    if (added.isNotEmpty()) {
                        val addedName = updatedSession.participantNames[added.first()] ?: "Yeni katılımcı"
                        showJoinBanner("$addedName görüşmeye katıldı")
                    }
                    _participants.value = updatedSession.participantNames
                }
                // Check battery alerts
                updatedSession.batteryAlerts.forEach { (peerId, pct) ->
                    if (peerId != myUser.userId) {
                        val peerName = updatedSession.participantNames[peerId] ?: friend.displayName
                        _peerBatteryAlert.value = "$peerName cihazının şarjı %$pct kaldı!"
                        com.android.zilbio.ui.components.ZilbioDynamicIslandManager.showBatteryAlert(peerName, pct, isLocal = false)
                    }
                }
            }
        }
        synchronized(sseListeners) { sseListeners.add(sessionCall) }

        // 8. Create local SDP offer and send via cloud
        engine.createOffer { offerDesc ->
            engine.setLocalDescription(offerDesc)
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendOffer(callId, offerDesc.description)
            }
        }

        // 9. Start VAD chunker
        val targetLang = friend.language.ifBlank { "en" }
        val chunker = VadAudioChunker(
            pcmChannel = engine.pcmChannel,
            targetLanguage = targetLang
        ).apply {
            speakerId = myUser.userId
            speakerName = myUser.displayName.ifBlank { myUser.username }
            speakerLanguage = myUser.language.ifBlank { "tr" }
        }
        vadChunker = chunker
        chunker.start()

        // 10. Collect and distribute transcripts
        scope.launch {
            chunker.transcriptSegments.collect { segment ->
                appendTranscript(segment)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.sendTranscriptSegment(callId, segment)
                }
                engine.sendTranscriptViaDataChannel(gson.toJson(segment))
            }
        }

        // 11. Listen for remote transcripts and mic levels via DataChannel
        engine.onDataChannelMessage = { json ->
            try {
                if (json.contains("\"type\":\"mic_level\"")) {
                    val jsonObj = org.json.JSONObject(json)
                    val level = jsonObj.optDouble("level", 0.0).toFloat()
                    _remoteMicLevel.value = level
                } else {
                    val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                    if (segment != null) {
                        appendTranscript(segment)
                        if (segment.speakerId != myUser.userId) {
                            ensureSegmentTranslated(segment, myUser.language.ifBlank { "tr" })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing DataChannel message: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
                ensureSegmentTranslated(segment, myUser.language.ifBlank { "tr" })
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

        // 12. Strict 30-second Outgoing Call Timeout (Item #2)
        outgoingTimeoutJob = scope.launch {
            delay(30_000L)
            if (_callState.value?.status == "ringing") {
                Log.i(TAG, "Outgoing call 30s timeout reached without answer. Terminating.")
                val missedHistory = CallHistoryItem(
                    callId = callId,
                    peerId = friend.userId,
                    peerName = friend.displayName.ifBlank { friend.username },
                    isIncoming = false,
                    isVideo = isVideo,
                    callStatus = "missed",
                    durationSeconds = 0L,
                    timestamp = System.currentTimeMillis()
                )
                CloudRealtimeService.saveCallHistoryItem(myUser.userId, missedHistory)
                val chatId = CloudRealtimeService.generateChatId(myUser.userId, friend.userId)
                CloudRealtimeService.logCallToChat(chatId, myUser.userId, friend.userId, isVideo, "missed", 0L)
                endCall(myUser.userId)
            }
        }

        startForegroundService(isVideo)
    }

    suspend fun answerCall(
        callId: String,
        callerId: String,
        callerName: String,
        myUser: User,
        isVideo: Boolean = false,
        targetLanguage: String = "en"
    ) {
        val session = CallSession(
            callId = callId,
            callerId = callerId,
            receiverId = myUser.userId,
            callerName = callerName,
            receiverName = myUser.displayName.ifBlank { myUser.username },
            status = "active",
            callType = if (isVideo) "video" else "audio",
            isVideo = isVideo,
            startTime = System.currentTimeMillis()
        )
        _callState.value = session
        _isCallEnded.value = false
        _transcripts.value = emptyList()

        Log.i(TAG, "Answering call: $callId (video=$isVideo) from $callerName ($callerId)")

        requestCallAudioFocus()
        CloudRealtimeService.answerCall(callId)

        val engine = SharedAudioCallEngine(context)
        engine.initialize(enableVideo = isVideo)
        engine.createPeerConnection(enableVideo = isVideo)
        audioEngine = engine

        scope.launch {
            engine.localMicLevel.collect { level ->
                _localMicLevel.value = level
                if (level > 0.04f) {
                    engine.sendTranscriptViaDataChannel("{\"type\":\"mic_level\",\"level\":$level}")
                }
            }
        }

        engine.onIceCandidateGenerated = { candidate ->
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendIceCandidate(
                    callId = callId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    senderId = myUser.userId
                )
            }
        }

        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex, senderId ->
            if (senderId == myUser.userId) return@listenForIceCandidates
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
            engine.addIceCandidate(candidate)
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        val offerProcessed = AtomicBoolean(false)
        val onOfferReceived: (String) -> Unit = { sdp ->
            if (offerProcessed.compareAndSet(false, true)) {
                Log.i(TAG, "Received remote SDP offer for call $callId, creating answer...")
                val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                engine.setRemoteDescription(desc)
                engine.createAnswer { answerDesc ->
                    engine.setLocalDescription(answerDesc)
                    scope.launch(Dispatchers.IO) {
                        CloudRealtimeService.sendAnswer(callId, answerDesc.description)
                    }
                }
            }
        }

        val offerCall = CloudRealtimeService.listenForOffer(callId) { sdp ->
            onOfferReceived(sdp)
        }
        synchronized(sseListeners) { sseListeners.add(offerCall) }

        scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (isActive && !offerProcessed.get() && attempts < 30) {
                try {
                    val sdp = CloudRealtimeService.getOffer(callId)
                    if (!sdp.isNullOrBlank()) {
                        scope.launch(Dispatchers.Main) { onOfferReceived(sdp) }
                        break
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                delay(400L)
                attempts++
            }
        }

        // Listen for call session state changes (Item #4 Synchronized disconnect)
        val sessionCall = CloudRealtimeService.listenToCallSession(callId) { updatedSession ->
            if (updatedSession.status == "ended" || updatedSession.endedBy != null) {
                Log.i(TAG, "Remote call termination detected (endedBy=${updatedSession.endedBy})")
                _isCallEnded.value = true
                endCallLocally()
            } else {
                _callState.value = updatedSession
                if (updatedSession.participantNames.isNotEmpty()) {
                    val oldKeys = _participants.value.keys
                    val newKeys = updatedSession.participantNames.keys
                    val added = newKeys.filter { it !in oldKeys }
                    if (added.isNotEmpty()) {
                        val addedName = updatedSession.participantNames[added.first()] ?: "Yeni katılımcı"
                        showJoinBanner("$addedName görüşmeye katıldı")
                    }
                    _participants.value = updatedSession.participantNames
                }
            }
        }
        synchronized(sseListeners) { sseListeners.add(sessionCall) }

        val chunker = VadAudioChunker(
            pcmChannel = engine.pcmChannel,
            targetLanguage = targetLanguage.ifBlank { "en" }
        ).apply {
            speakerId = myUser.userId
            speakerName = myUser.displayName.ifBlank { myUser.username }
            speakerLanguage = myUser.language.ifBlank { "tr" }
        }
        vadChunker = chunker
        chunker.start()

        scope.launch {
            chunker.transcriptSegments.collect { segment ->
                appendTranscript(segment)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.sendTranscriptSegment(callId, segment)
                }
                engine.sendTranscriptViaDataChannel(gson.toJson(segment))
            }
        }

        engine.onDataChannelMessage = { json ->
            try {
                if (json.contains("\"type\":\"mic_level\"")) {
                    val jsonObj = org.json.JSONObject(json)
                    val level = jsonObj.optDouble("level", 0.0).toFloat()
                    _remoteMicLevel.value = level
                } else {
                    val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                    if (segment != null) {
                        appendTranscript(segment)
                        if (segment.speakerId != myUser.userId) {
                            ensureSegmentTranslated(segment, myUser.language.ifBlank { "tr" })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing DataChannel message: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
                ensureSegmentTranslated(segment, myUser.language.ifBlank { "tr" })
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

        startDurationTimer()
        startVoiceDeliveryWatchdog()
        startForegroundService(isVideo)
    }

    suspend fun answerCall(callSession: CallSession, myUser: User) {
        answerCall(
            callId = callSession.callId,
            callerId = callSession.callerId,
            callerName = callSession.callerName,
            myUser = myUser,
            isVideo = callSession.isVideo,
            targetLanguage = "en"
        )
    }

    suspend fun addParticipantToCall(newUserId: String, displayName: String): Boolean {
        val session = _callState.value ?: return false
        val success = CloudRealtimeService.addConferenceParticipant(session.callId, newUserId, displayName)
        if (success) {
            val updatedMap = _participants.value.toMutableMap()
            updatedMap[newUserId] = displayName
            _participants.value = updatedMap
            showJoinBanner("$displayName görüşmeye eklendi")
        }
        return success
    }

    private fun showJoinBanner(message: String) {
        scope.launch {
            _joinBanner.value = message
            delay(3000L) // 3 seconds banner display (Item #5)
            _joinBanner.value = null
        }
    }

    fun endCall(endedByUserId: String = "") {
        outgoingTimeoutJob?.cancel()
        outgoingTimeoutJob = null

        val session = _callState.value
        val duration = _callDuration.value
        if (session != null) {
            val userId = endedByUserId.ifEmpty { session.callerId }
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.endCallWithSignal(session.callId, session.receiverId, userId)
                val chatId = CloudRealtimeService.generateChatId(session.callerId, session.receiverId)
                val callStatus = if (duration > 0L) "completed" else "missed"
                CloudRealtimeService.logCallToChat(chatId, session.callerId, session.receiverId, session.isVideo, callStatus, duration)
            }
        }
        _isCallEnded.value = true
        endCallLocally()
    }

    private fun endCallLocally() {
        Log.i(TAG, "Ending call session locally")
        val session = _callState.value
        val duration = _callDuration.value
        durationJob?.cancel()
        durationJob = null
        _callDuration.value = 0L

        if (session != null && duration > 0L) {
            scope.launch(Dispatchers.IO) {
                try {
                    val myUser = com.android.zilbio.data.local.UserPreferences.getUserSync(context)
                    if (myUser != null) {
                        com.android.zilbio.data.rewards.BadgeRewardManager.recordCallDuration(
                            context = context,
                            userId = myUser.userId,
                            displayName = myUser.displayName.ifBlank { myUser.username },
                            durationSeconds = duration,
                            isVideo = session.isVideo
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record call duration for badges: ${e.message}")
                }
            }
        }

        synchronized(sseListeners) {
            sseListeners.forEach { it.cancel() }
            sseListeners.clear()
        }

        vadChunker?.stop()
        vadChunker = null

        audioEngine?.dispose()
        audioEngine = null

        abandonCallAudioFocus()
        watchdogJob?.cancel()
        watchdogJob = null
        _localMicLevel.value = 0f
        _remoteMicLevel.value = 0f

        stopForegroundService()
        _callState.value = null
    }

    private fun requestCallAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Call AudioFocus changed: $focusChange")
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> Log.d(TAG, "Call AudioFocus legacy: $focusChange") },
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            Log.d(TAG, "VoIP AudioFocus acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request AudioFocus: ${e.message}")
        }
    }

    private fun abandonCallAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? android.media.AudioFocusRequest)?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
            Log.d(TAG, "VoIP AudioFocus abandoned")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon AudioFocus: ${e.message}")
        }
    }

    private fun startVoiceDeliveryWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Default) {
            var lastSentBytes = -1L
            var lastReceivedBytes = -1L
            var deadSeconds = 0
            var recoveryLevel = 0

            while (isActive) {
                delay(2500L)
                val engine = audioEngine ?: continue
                if (_callState.value?.status != "active") continue

                engine.getAudioRtpStats { stats ->
                    val sentProgress = if (lastSentBytes == -1L) true else stats.bytesSent > lastSentBytes
                    val recProgress = if (lastReceivedBytes == -1L) true else stats.bytesReceived > lastReceivedBytes

                    lastSentBytes = stats.bytesSent
                    lastReceivedBytes = stats.bytesReceived

                    if (!sentProgress || !recProgress) {
                        deadSeconds += 3
                        Log.w(TAG, "Watchdog: Audio RTP stagnation ($deadSeconds s). Sent: ${stats.bytesSent}, Rec: ${stats.bytesReceived}")

                        if (deadSeconds >= 5 && recoveryLevel < 1) {
                            recoveryLevel = 1
                            Log.i(TAG, "Watchdog Level 1: Re-enabling local audio track")
                            engine.reenableAudio()
                        } else if (deadSeconds >= 8 && recoveryLevel < 2) {
                            recoveryLevel = 2
                            Log.i(TAG, "Watchdog Level 2: Refreshing audio route")
                            engine.audioDeviceController?.let { controller ->
                                controller.setAudioRoute(controller.currentRoute.value)
                            }
                        } else if (deadSeconds >= 12 && recoveryLevel < 3) {
                            recoveryLevel = 3
                            Log.i(TAG, "Watchdog Level 3: Triggering WebRTC ICE restart")
                            engine.restartIce()
                        } else if (deadSeconds >= 18 && recoveryLevel < 4) {
                            recoveryLevel = 4
                            Log.i(TAG, "Watchdog Level 4: Full audio restart")
                            engine.reenableAudio()
                            engine.restartIce()
                        } else if (deadSeconds >= 25 && recoveryLevel < 5) {
                            recoveryLevel = 5
                            Log.e(TAG, "Watchdog Level 5: Stalled connection notice")
                            _joinBanner.value = "Ses bağlantısı yenileniyor..."
                        }
                    } else {
                        if (deadSeconds > 0) {
                            Log.i(TAG, "Watchdog: Audio RTP flow restored after $deadSeconds seconds.")
                        }
                        deadSeconds = 0
                        recoveryLevel = 0
                    }
                }
            }
        }
    }

    private fun appendTranscript(segment: CallTranscriptSegment) {
        val current = _transcripts.value.toMutableList()
        val index = current.indexOfFirst {
            it.speakerId == segment.speakerId &&
                    it.timestamp == segment.timestamp &&
                    it.originalText == segment.originalText
        }
        if (index >= 0) {
            if (segment.translatedText.isNotBlank() && current[index].translatedText != segment.translatedText) {
                current[index] = segment
                _transcripts.value = current
            }
        } else {
            current.add(segment)
            _transcripts.value = current
        }
    }

    private fun ensureSegmentTranslated(segment: CallTranscriptSegment, localLanguage: String) {
        val srcLang = segment.originalLanguage.ifBlank { "en" }
        if (localLanguage.equals(srcLang, ignoreCase = true)) return
        if (segment.translatedText.isNotBlank() && segment.translatedText != segment.originalText) return

        scope.launch(Dispatchers.IO) {
            try {
                val res = com.android.zilbio.data.remote.GeminiTranslationEngine.translateText(
                    text = segment.originalText,
                    sourceLanguage = srcLang,
                    targetLanguage = localLanguage
                )
                if (res.translatedText.isNotBlank() && res.translatedText != segment.originalText) {
                    val updated = segment.copy(translatedText = res.translatedText)
                    scope.launch(Dispatchers.Main) {
                        appendTranscript(updated)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to translate remote transcript segment: ${e.message}")
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds++
                _callDuration.value = seconds
            }
        }
    }

    private fun startForegroundService(isVideo: Boolean = false) {
        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START
                putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CallForegroundService: ${e.message}", e)
        }
    }

    private fun stopForegroundService() {
        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop CallForegroundService: ${e.message}", e)
        }
    }

    fun dispose() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        endCall()
        scope.cancel()
    }
}
