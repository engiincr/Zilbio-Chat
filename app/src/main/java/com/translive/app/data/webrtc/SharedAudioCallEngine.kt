package com.translive.app.data.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import com.translive.app.util.ApiConfig
import kotlinx.coroutines.channels.Channel
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

class SharedAudioCallEngine(private val context: Context) {

    companion object {
        private const val TAG = "SharedAudioCallEngine"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var dataChannel: DataChannel? = null
    private var audioManager: AudioManager? = null

    // PCM channel for VAD/transcription
    val pcmChannel = Channel<PcmChunk>(Channel.BUFFERED)

    // ICE candidate queue (drain after remote description set)
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    // Callbacks
    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onDataChannelMessage: ((String) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null

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
            state?.let { onConnectionStateChanged?.invoke(it) }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
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
            Log.d(TAG, "onAddStream: ${stream?.id}")
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "onRemoveStream: ${stream?.id}")
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
            Log.d(TAG, "onAddTrack")
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

    fun initialize() {
        Log.i(TAG, "Initializing SharedAudioCallEngine...")
        // 1. Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        // 2. Setup JavaAudioDeviceModule with echo cancellation and noise suppression
        val useHwAec = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
        val useHwNs = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()

        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(useHwAec)
            .setUseHardwareNoiseSuppressor(useHwNs)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
            .setSamplesReadyCallback { audioSamples ->
                val copy = audioSamples.data.copyOf()
                pcmChannel.trySend(PcmChunk(copy, audioSamples.sampleRate, audioSamples.channelCount))
            }
            .createAudioDeviceModule()

        // 3. Build PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        // 4. Setup AudioManager
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            isSpeakerphoneOn = true
            isMicrophoneMute = false
        }
        Log.i(TAG, "SharedAudioCallEngine initialized successfully")
    }

    fun createPeerConnection() {
        val iceServers = ApiConfig.ICE_SERVERS.map { config ->
            if (config.username != null && config.password != null) {
                PeerConnection.IceServer.builder(config.uri)
                    .setUsername(config.username)
                    .setPassword(config.password)
                    .createIceServer()
            } else {
                PeerConnection.IceServer.builder(config.uri).createIceServer()
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)

        // Create and add local audio track
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track_0", audioSource)
        localAudioTrack?.setEnabled(true)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track)
        }

        // Create DataChannel for transcript exchange
        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannel = peerConnection?.createDataChannel("transcripts", dcInit)
        dataChannel?.registerObserver(dataChannelObserver)
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "createOffer succeeded: type=${it.type}")
                    callback(it)
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
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "createAnswer succeeded: type=${it.type}")
                    callback(it)
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

    fun setSpeakerphoneOn(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    fun setMicrophoneMute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
        audioManager?.isMicrophoneMute = mute
    }

    fun dispose() {
        try {
            Log.i(TAG, "Disposing SharedAudioCallEngine...")
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

            audioManager?.mode = AudioManager.MODE_NORMAL
            pcmChannel.close()
            pendingIceCandidates.clear()
            isRemoteDescriptionSet = false
            Log.i(TAG, "SharedAudioCallEngine disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing SharedAudioCallEngine: ${e.message}", e)
        }
    }
}
