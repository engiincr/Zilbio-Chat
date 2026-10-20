package com.translive.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
    private val uiHandler = Handler(Looper.getMainLooper())

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

    private fun showToast(msg: String) {
        uiHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

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

        CloudRealtimeService.initiateCall(session)

        val engine = SharedAudioCallEngine(context)
        engine.initialize()
        engine.createPeerConnection()
        audioEngine = engine

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

        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex ->
            if (cand.isNotBlank()) {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
                engine.addIceCandidate(candidate)
            }
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        val answerCall = CloudRealtimeService.listenForAnswer(callId) { sdp ->
            val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            engine.setRemoteDescription(desc)
            if (_callState.value?.status != "active") {
                _callState.value = _callState.value?.copy(status = "active")
                startDurationTimer()
            }
        }
        synchronized(sseListeners) { sseListeners.add(answerCall) }

        val statusCall = CloudRealtimeService.listenForCallStatus(callId) { status ->
            when (status) {
                "active" -> {
                    if (_callState.value?.status != "active") {
                        _callState.value = _callState.value?.copy(status = "active")
                        startDurationTimer()
                    }
                }
                "ended" -> {
                    endCallLocally()
                }
            }
        }
        synchronized(sseListeners) { sseListeners.add(statusCall) }

        engine.createOffer { offerDesc ->
            engine.setLocalDescription(offerDesc)
            scope.launch(Dispatchers.IO) {
                CloudRealtimeService.sendOffer(callId, offerDesc.description)
            }
        }

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
                val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                if (segment != null) {
                    appendTranscript(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transcript: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

        startForegroundService()
    }

    suspend fun answerCall(callSession: CallSession, myUser: User) {
        val callId = callSession.callId
        _callState.value = callSession.copy(status = "active")
        _transcripts.value = emptyList()

        Log.i(TAG, "Answering call: $callId from ${callSession.callerName}")

        // ADIM 1: getUserMedia / WebRTC Engine Başlatma
        val engine = try {
            val eng = SharedAudioCallEngine(context)
            eng.initialize()
            eng.createPeerConnection()
            audioEngine = eng
            showToast("Adım 1 başarılı (getUserMedia/Engine Başlatıldı)")
            eng
        } catch (e: Exception) {
            showToast("Hata Adım 1: ${e.message ?: "Bilinmeyen hata"}")
            return
        }

        try {
            CloudRealtimeService.answerCall(callId)
        } catch (e: Exception) {
            // Non-fatal
        }

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

        val iceCall = CloudRealtimeService.listenForIceCandidates(callId) { cand, sdpMid, sdpMLineIndex ->
            if (cand.isNotBlank()) {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, cand)
                engine.addIceCandidate(candidate)
            }
        }
        synchronized(sseListeners) { sseListeners.add(iceCall) }

        // ADIM 2: setRemoteDescription
        val offerCall = CloudRealtimeService.listenForOffer(callId) { sdp ->
            try {
                val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                engine.setRemoteDescription(desc)
                showToast("Adım 2 başarılı (setRemoteDescription)")

                // ADIM 3: createAnswer
                engine.createAnswer { answerDesc ->
                    try {
                        engine.setLocalDescription(answerDesc)
                        showToast("Adım 3 başarılı (createAnswer)")

                        // ADIM 4: answer'ı Firebase'e yazma
                        scope.launch(Dispatchers.IO) {
                            try {
                                CloudRealtimeService.sendAnswer(callId, answerDesc.description)
                                showToast("Adım 4 başarılı (Firebase'e Answer Yazıldı)")
                            } catch (e: Exception) {
                                showToast("Hata Adım 4: ${e.message ?: "Firebase yazma hatası"}")
                            }
                        }
                    } catch (e: Exception) {
                        showToast("Hata Adım 3: ${e.message ?: "createAnswer hatası"}")
                    }
                }
            } catch (e: Exception) {
                showToast("Hata Adım 2: ${e.message ?: "setRemoteDescription hatası"}")
            }
        }
        synchronized(sseListeners) { sseListeners.add(offerCall) }

        val statusCall = CloudRealtimeService.listenForCallStatus(callId) { status ->
            if (status == "ended") {
                endCallLocally()
            }
        }
        synchronized(sseListeners) { sseListeners.add(statusCall) }

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
                val segment = gson.fromJson(json, CallTranscriptSegment::class.java)
                if (segment != null) {
                    appendTranscript(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transcript: ${e.message}")
            }
        }

        val transcriptCall = CloudRealtimeService.listenForTranscripts(callId) { segment ->
            if (segment.speakerId != myUser.userId) {
                appendTranscript(segment)
            }
        }
        synchronized(sseListeners) { sseListeners.add(transcriptCall) }

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
