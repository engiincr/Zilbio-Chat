package com.android.zilbio.data.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Log
import com.android.zilbio.domain.model.AudioOutputRoute
import com.android.zilbio.domain.model.NetworkQuality
import com.android.zilbio.util.ApiConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class SharedAudioCallEngine(private val context: Context) {

    companion object {
        private const val TAG = "SharedAudioCallEngine"
    }

    var audioDeviceController: AudioDeviceController? = null
        private set

    var rootEglBase: EglBase? = null
        private set

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var dataChannel: DataChannel? = null

    val remoteVideoTracks = ConcurrentHashMap<String, VideoTrack>()

    // PCM channel for VAD/transcription
    val pcmChannel = Channel<PcmChunk>(Channel.BUFFERED)

    // Network stability indicator (Item #6)
    private val _networkQuality = MutableStateFlow(NetworkQuality.EXCELLENT)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()

    // Live microphone audio level (RMS) for UI level meter
    private val _localMicLevel = MutableStateFlow(0f)
    val localMicLevel: StateFlow<Float> = _localMicLevel.asStateFlow()

    private var audioSender: org.webrtc.RtpSender? = null

    // ICE candidate queue (drain after remote description set)
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    // Callbacks
    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onDataChannelMessage: ((String) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onRemoteVideoTrackAdded: ((VideoTrack) -> Unit)? = null

    data class PcmChunk(
        val data: ByteArray,
        val sampleRate: Int,
        val channelCount: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PcmChunk
            if (!data.contentEquals(other.data)) return false
            if (sampleRate != other.sampleRate) return false
            if (channelCount != other.channelCount) return false
            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + channelCount
            return result
        }
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "onSignalingChange: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "onIceConnectionChange: $state")
            state?.let {
                when (it) {
                    PeerConnection.IceConnectionState.CONNECTED -> _networkQuality.value = NetworkQuality.EXCELLENT
                    PeerConnection.IceConnectionState.COMPLETED -> _networkQuality.value = NetworkQuality.GOOD
                    PeerConnection.IceConnectionState.DISCONNECTED -> _networkQuality.value = NetworkQuality.WEAK
                    PeerConnection.IceConnectionState.FAILED -> _networkQuality.value = NetworkQuality.CRITICAL
                    else -> {}
                }
                onConnectionStateChanged?.invoke(it)
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
            if (!receiving) {
                _networkQuality.value = NetworkQuality.WEAK
            }
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "onIceGatheringChange: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            Log.d(TAG, "onIceCandidate generated: sdpMid=${candidate?.sdpMid}, index=${candidate?.sdpMLineIndex}")
            candidate?.let { onIceCandidateGenerated?.invoke(it) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "onIceCandidatesRemoved: count=${candidates?.size}")
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.d(TAG, "onAddStream: ${stream?.id}, audioTracks=${stream?.audioTracks?.size}, videoTracks=${stream?.videoTracks?.size}")
            stream?.audioTracks?.forEach { track ->
                track.setEnabled(true)
                track.setVolume(1.0)
                Log.i(TAG, "Remote AudioTrack enabled via onAddStream: id=${track.id()}")
            }
            stream?.videoTracks?.forEach { track ->
                track.setEnabled(true)
                remoteVideoTracks[track.id()] = track
                onRemoteVideoTrackAdded?.invoke(track)
            }
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "onRemoveStream: ${stream?.id}")
            stream?.videoTracks?.forEach { track ->
                remoteVideoTracks.remove(track.id())
            }
        }

        override fun onDataChannel(dc: DataChannel?) {
            Log.d(TAG, "onDataChannel received: ${dc?.label()}")
            dc?.let {
                dataChannel = it
                it.registerObserver(dataChannelObserver)
            }
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded")
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            Log.d(TAG, "onAddTrack: trackId=${track?.id()}, kind=${track?.kind()}")
            if (track is AudioTrack) {
                track.setEnabled(true)
                track.setVolume(1.0)
                Log.i(TAG, "Remote AudioTrack enabled via onAddTrack: id=${track.id()}")
            } else if (track is VideoTrack) {
                track.setEnabled(true)
                remoteVideoTracks[track.id()] = track
                onRemoteVideoTrackAdded?.invoke(track)
            }
        }
    }

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(amount: Long) {
            Log.d(TAG, "DataChannel buffered amount: $amount")
        }

        override fun onStateChange() {
            Log.d(TAG, "DataChannel state changed: ${dataChannel?.state()}")
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            try {
                val byteBuffer = buffer.data
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                val text = String(bytes, Charsets.UTF_8)
                Log.d(TAG, "DataChannel received message: $text")
                onDataChannelMessage?.invoke(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading DataChannel message: ${e.message}", e)
            }
        }
    }

    fun initialize(enableVideo: Boolean = false) {
        Log.i(TAG, "Initializing SharedAudioCallEngine (video=$enableVideo)...")

        // 1. Initialize EGL Base for hardware-accelerated video
        rootEglBase = EglBase.create()

        // 2. Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        // 3. Audio Device Module: Enforce software AEC3 & NS for guaranteed two-way voice across fragmented OEM audio HALs
        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
            .setSamplesReadyCallback { audioSamples ->
                val copy = audioSamples.data.copyOf()
                pcmChannel.trySend(PcmChunk(copy, audioSamples.sampleRate, audioSamples.channelCount))
                calculateMicLevel(copy)
            }
            .createAudioDeviceModule()

        // 4. Build PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        // 5. Initialize AudioDeviceController (Speaker default, earpiece & BT ready)
        audioDeviceController = AudioDeviceController(context)
        audioDeviceController?.setAudioRoute(AudioOutputRoute.SPEAKER)

        Log.i(TAG, "SharedAudioCallEngine initialized successfully with software AEC/NS")
    }

    private fun calculateMicLevel(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        var sumSquares = 0.0
        val shortCount = pcmData.size / 2
        for (i in 0 until shortCount) {
            val sample = ((pcmData[i * 2 + 1].toInt() shl 8) or (pcmData[i * 2].toInt() and 0xFF)).toShort()
            sumSquares += sample * sample
        }
        val rms = kotlin.math.sqrt(sumSquares / shortCount)
        val normalized = (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
        _localMicLevel.value = normalized
    }

    fun createPeerConnection(enableVideo: Boolean = false) {
        val iceServers = ApiConfig.iceServers

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            keyType = PeerConnection.KeyType.ECDSA
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)

        // 1. Create and add local audio track
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track_0", audioSource)
        localAudioTrack?.setEnabled(true)
        localAudioTrack?.let { track ->
            audioSender = peerConnection?.addTrack(track, listOf("ARDAMS"))
        }

        // 2. Setup video track if video call enabled
        if (enableVideo) {
            setupVideoCapturer()
        }

        // 3. Create DataChannel for transcript exchange
        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannel = peerConnection?.createDataChannel("transcripts", dcInit)
        dataChannel?.registerObserver(dataChannelObserver)
    }

    private fun setupVideoCapturer() {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()

            if (frontCamera != null) {
                videoCapturer = enumerator.createCapturer(frontCamera, null)
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase?.eglBaseContext)
                videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)

                videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                videoCapturer?.startCapture(640, 480, 30)

                localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track_0", videoSource)
                localVideoTrack?.setEnabled(true)
                localVideoTrack?.let { vTrack ->
                    peerConnection?.addTrack(vTrack)
                }
                Log.i(TAG, "Video capturer started successfully on camera: $frontCamera")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video capturer: ${e.message}", e)
        }
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun setVideoEnabled(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
    }

    fun pauseLocalCamera() {
        try {
            localVideoTrack?.setEnabled(false)
            videoCapturer?.stopCapture()
            Log.i(TAG, "Local camera paused for background privacy/battery")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing local camera: ${e.message}")
        }
    }

    fun resumeLocalCamera() {
        try {
            videoCapturer?.startCapture(640, 480, 30)
            localVideoTrack?.setEnabled(true)
            Log.i(TAG, "Local camera resumed for foreground/PiP")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming local camera: ${e.message}")
        }
    }

    fun getLocalVideoTrack(): VideoTrack? = localVideoTrack

    fun tuneOpusSdp(sdpDescription: SessionDescription): SessionDescription {
        val sdp = sdpDescription.description
        val lines = sdp.split("\r\n").toMutableList()
        var opusPayloadType: String? = null

        for (line in lines) {
            if (line.startsWith("a=rtpmap:") && line.contains("opus/48000", ignoreCase = true)) {
                val parts = line.substringAfter("a=rtpmap:").split(" ")
                if (parts.isNotEmpty()) {
                    opusPayloadType = parts[0]
                    break
                }
            }
        }

        if (opusPayloadType != null) {
            val fmtpPrefix = "a=fmtp:$opusPayloadType "
            val tunedFmtp = "$fmtpPrefix minptime=10;useinbandfec=1;usedtx=0;stereo=0;sprop-stereo=0;maxaveragebitrate=32000"
            var fmtpFound = false
            for (i in lines.indices) {
                if (lines[i].startsWith(fmtpPrefix)) {
                    lines[i] = tunedFmtp
                    fmtpFound = true
                    break
                }
            }
            if (!fmtpFound) {
                val rtpmapIndex = lines.indexOfFirst { it.startsWith("a=rtpmap:$opusPayloadType ") }
                if (rtpmapIndex != -1) {
                    lines.add(rtpmapIndex + 1, tunedFmtp)
                }
            }
        }

        val tunedSdp = lines.joinToString("\r\n")
        return SessionDescription(sdpDescription.type, tunedSdp)
    }

    data class AudioRtpStats(
        val bytesSent: Long = 0,
        val bytesReceived: Long = 0,
        val packetsSent: Long = 0,
        val packetsReceived: Long = 0,
        val packetsLost: Long = 0
    )

    fun getAudioRtpStats(callback: (AudioRtpStats) -> Unit) {
        peerConnection?.getStats { report ->
            var sent = 0L
            var rec = 0L
            var pSent = 0L
            var pRec = 0L
            var pLost = 0L
            for (stat in report.statsMap.values) {
                if (stat.type == "outbound-rtp" && stat.members["mediaType"] == "audio") {
                    sent = (stat.members["bytesSent"] as? Number)?.toLong() ?: sent
                    pSent = (stat.members["packetsSent"] as? Number)?.toLong() ?: pSent
                } else if (stat.type == "inbound-rtp" && stat.members["mediaType"] == "audio") {
                    rec = (stat.members["bytesReceived"] as? Number)?.toLong() ?: rec
                    pRec = (stat.members["packetsReceived"] as? Number)?.toLong() ?: pRec
                    pLost = (stat.members["packetsLost"] as? Number)?.toLong() ?: pLost
                }
            }
            callback(AudioRtpStats(sent, rec, pSent, pRec, pLost))
        }
    }

    fun restartIce() {
        Log.i(TAG, "Restarting ICE connection on PeerConnection...")
        peerConnection?.restartIce()
    }

    fun reenableAudio() {
        Log.i(TAG, "Re-enabling local audio track...")
        localAudioTrack?.setEnabled(false)
        localAudioTrack?.setEnabled(true)
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    val tuned = tuneOpusSdp(it)
                    Log.d(TAG, "createOffer succeeded (Opus tuned): type=${tuned.type}")
                    callback(tuned)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
            }

            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun createAnswer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    val tuned = tuneOpusSdp(it)
                    Log.d(TAG, "createAnswer succeeded (Opus tuned): type=${tuned.type}")
                    callback(tuned)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
            }

            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun setLocalDescription(sdp: SessionDescription) {
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "setLocalDescription succeeded: type=${sdp.type}")
            }

            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setLocalDescription failed: $error")
            }
        }, sdp)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "setRemoteDescription succeeded: type=${sdp.type}")
                isRemoteDescriptionSet = true
                synchronized(pendingIceCandidates) {
                    pendingIceCandidates.forEach { candidate ->
                        peerConnection?.addIceCandidate(candidate)
                    }
                    Log.d(TAG, "Drained ${pendingIceCandidates.size} pending ICE candidates")
                    pendingIceCandidates.clear()
                }
            }

            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription failed: $error")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        synchronized(pendingIceCandidates) {
            if (isRemoteDescriptionSet) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                Log.d(TAG, "Queued ICE candidate (remote description not set yet)")
                pendingIceCandidates.add(candidate)
            }
        }
    }

    fun sendTranscriptViaDataChannel(json: String) {
        try {
            val dc = dataChannel
            if (dc != null && dc.state() == DataChannel.State.OPEN) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                val byteBuffer = ByteBuffer.wrap(bytes)
                val buffer = DataChannel.Buffer(byteBuffer, false)
                dc.send(buffer)
            } else {
                Log.w(TAG, "DataChannel not open, cannot send message. State: ${dc?.state()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send transcript via DataChannel: ${e.message}", e)
        }
    }

    fun setMicrophoneMute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    fun dispose() {
        try {
            Log.i(TAG, "Disposing SharedAudioCallEngine...")
            audioDeviceController?.release()
            audioDeviceController = null

            try {
                videoCapturer?.stopCapture()
                videoCapturer?.dispose()
            } catch (e: Exception) {
                // Ignore
            }
            surfaceTextureHelper?.dispose()
            videoSource?.dispose()
            localVideoTrack?.dispose()

            dataChannel?.unregisterObserver()
            dataChannel?.close()
            dataChannel = null

            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            audioDeviceModule?.release()
            audioDeviceModule = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            rootEglBase?.release()
            rootEglBase = null

            pcmChannel.close()
            pendingIceCandidates.clear()
            remoteVideoTracks.clear()
            isRemoteDescriptionSet = false
            Log.i(TAG, "SharedAudioCallEngine disposed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing SharedAudioCallEngine: ${e.message}", e)
        }
    }
}
