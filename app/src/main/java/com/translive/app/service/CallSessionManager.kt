package com.translive.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.translive.app.data.audio.VadAudioChunker
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.data.webrtc.SharedAudioCallEngine
import com.translive.app.domain.model.CallSession
import com.translive.app.domain.model.CallTranscriptSegment
import com.translive.app.domain.model.Friend
import com.translive.app.domain.model.User
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

    private val _transcripts = MutableStateFlow<List<CallTranscriptSegment>>(emptyList())
    val transcripts: StateFlow<List<CallTranscriptSegment>> = _transcripts.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private var durationJob: Job? = null
    private val sseListeners = mutableListOf<Call>()

    suspend fun initiateCall(myUser: User, friend: Friend) {
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            callerId = myUser.userId,
            receiverId = friend.userId,
            callerName = myUser.displayName.ifBlank { myUser.username },
            receiverName = friend.displayName.ifBlank { friend.username },
            status = "ringing",
            startTime = System.currentTimeMillis()
        )
        _callState.value = session
        _transcripts.value = emptyList()

        Log.i(TAG, "Initiating call: $callId to ${friend.username} (${friend.userId})")

        // 1. Register call in cloud database
        CloudRealtimeService.initiateCall(session)

        // 2. Initialize WebRTC audio engine
        val engine = SharedAudioCallEngine(context)
        engine.initialize()
        engine.createPeerConnection()
        audioEngine = engine

        // 3. Handle local ICE candidates generated
        engine.onIceCandidateGenerated = { candidate ->
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendIceCandidate(
                    callId = callId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }
        }

        // 4. Listen for remote ICE candidates via SSE
        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex ->
            Log.d(TAG, "Received remote ICE candidate: $sdpMid")
            if (cand.isNotBlank()) {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
                engine.addIceCandidate(candidate)
            }
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        // 5. Listen for remote SDP answer via SSE
        val answerCall = CloudRealtimeService.listenForAnswer(callId) { sdp ->
            Log.i(TAG, "Received remote SDP answer for call $callId")
            val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            engine.setRemoteDescription(desc)
            _callState.value = _callState.value?.copy(status = "active")
            startDurationTimer()
        }
        synchronized(sseListeners) { sseListeners.add(answerCall) }

        // 6. Listen for call status changes via SSE
        val statusCall = CloudRealtimeService.listenForCallStatus(callId) { status ->
            Log.i(TAG, "Call status updated: $status")
            when (status) {
                "active" -> {
                    _callState.value = _callState.value?.copy(status = "active")
                }
                "ended" -> {
                    endCallLocally()
                }
            }
        }
        synchronized(sseListeners) { sseListeners.add(statusCall) }

        // 7. Create local SDP offer and send via cloud
        engine.createOffer { offerDesc ->
            engine.setLocalDescription(offerDesc)
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendOffer(callId, offerDesc.description)
            }
        }

        // 8. Start VAD chunker and translate into friend's preferred language
        val targetLang = friend.language.ifBlank { "en" }
        val chunker = VadAudioChunker(
            pcmChannel = engine.pcmChannel,
            targetLanguage = targetLang
        ).apply {
            speakerId = myUser.userId
            speakerName = myUser.displayName.ifBlank { myUser.username }
        }
        vadChunker = chunker
        chunker.start()

        // 9. Collect and distribute local transcripts
        scope.launch {
            chunker.transcriptSegments.collect { segment ->
                appendTranscript(segment)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.sendTranscriptSegment(callId, segment)
                }
                engine.sendTranscriptViaDataChannel(gson.toJson(segment))
            }
        }

        // 10. Listen for remote transcripts via DataChannel & SSE
        engine.onDataChannelMessage = { json ->
            try {
                val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                if (segment != null) {
                    appendTranscript(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transcript from DataChannel: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

        // 11. Start Android foreground service to keep call alive
        startForegroundService()
    }

    suspend fun answerCall(callSession: CallSession, myUser: User) {
        val callId = callSession.callId
        _callState.value = callSession.copy(status = "active")
        _transcripts.value = emptyList()

        Log.i(TAG, "Answering call: $callId from ${callSession.callerName}")

        // 1. Notify cloud that call is accepted
        CloudRealtimeService.answerCall(callId)

        // 2. Initialize WebRTC audio engine
        val engine = SharedAudioCallEngine(context)
        engine.initialize()
        engine.createPeerConnection()
        audioEngine = engine

        // 3. Handle local ICE candidate emission
        engine.onIceCandidateGenerated = { candidate ->
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendIceCandidate(
                    callId = callId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }
        }

        // 4. Listen for remote ICE candidates via SSE
        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex ->
            Log.d(TAG, "Received remote ICE candidate: $sdpMid")
            if (cand.isNotBlank()) {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
                engine.addIceCandidate(candidate)
            }
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        // 5. Listen for remote SDP offer via SSE
        val offerCall = CloudRealtimeService.listenForOffer(callId) { sdp ->
            Log.i(TAG, "Received remote SDP offer for call $callId")
            val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
            engine.setRemoteDescription(desc)
            engine.createAnswer { answerDesc ->
                engine.setLocalDescription(answerDesc)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.sendAnswer(callId, answerDesc.description)
                }
            }
        }
        synchronized(sseListeners) { sseListeners.add(offerCall) }

        // 6. Listen for call status changes via SSE
        val statusCall = CloudRealtimeService.listenForCallStatus(callId) { status ->
            Log.i(TAG, "Call status updated: $status")
            if (status == "ended") {
                endCallLocally()
            }
        }
        synchronized(sseListeners) { sseListeners.add(statusCall) }

        // 7. Start VAD chunker and translate into caller's expected language
        val targetLang = "en"
        val chunker = VadAudioChunker(
            pcmChannel = engine.pcmChannel,
            targetLanguage = targetLang
        ).apply {
            speakerId = myUser.userId
            speakerName = myUser.displayName.ifBlank { myUser.username }
        }
        vadChunker = chunker
        chunker.start()

        // 8. Collect and distribute local transcripts
        scope.launch {
            chunker.transcriptSegments.collect { segment ->
                appendTranscript(segment)
                scope.launch(Dispatchers.IO) {
                    CloudRealtimeService.sendTranscriptSegment(callId, segment)
                }
                engine.sendTranscriptViaDataChannel(gson.toJson(segment))
            }
        }

        // 9. Listen for remote transcripts via DataChannel & SSE
        engine.onDataChannelMessage = { json ->
            try {
                val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                if (segment != null) {
                    appendTranscript(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transcript from DataChannel: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

        // 10. Start duration timer and foreground service
        startDurationTimer()
        startForegroundService()
    }

    fun endCall() {
        val session = _callState.value
        if (session != null) {
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.endCall(session.callId, session.receiverId)
            }
        }
        endCallLocally()
    }

    private fun endCallLocally() {
        Log.i(TAG, "Ending call session locally")
        durationJob?.cancel()
        durationJob = null
        _callDuration.value = 0L

        synchronized(sseListeners) {
            sseListeners.forEach { it.cancel() }
            sseListeners.clear()
        }

        vadChunker?.stop()
        vadChunker = null

        audioEngine?.dispose()
        audioEngine = null

        stopForegroundService()
        _callState.value = null
    }

    private fun appendTranscript(segment: CallTranscriptSegment) {
        val current = _transcripts.value.toMutableList()
        val isDuplicate = current.any {
            it.speakerId == segment.speakerId &&
                    it.timestamp == segment.timestamp &&
                    it.originalText == segment.originalText
        }
        if (!isDuplicate) {
            current.add(segment)
            _transcripts.value = current
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

    private fun startForegroundService() {
        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START
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
        endCall()
        scope.cancel()
    }
}
