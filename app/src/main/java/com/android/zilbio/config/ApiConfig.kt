package com.android.zilbio.config

import com.android.zilbio.BuildConfig
import org.webrtc.PeerConnection

object ApiConfig {
    const val TURN_HOST = "free.expressturn.com:3478"
    const val GEMINI_MODEL = "gemini-1.5-flash"
    const val GEMINI_FALLBACK_MODEL = "gemini-2.0-flash"
    const val ENABLE_MODEL_FALLBACK = true
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    val cloudDbUrl: String = BuildConfig.CLOUD_DB_URL
    val CLOUD_DB_URL: String get() = cloudDbUrl

    val geminiKeys: List<String> =
        listOf(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_API_KEY2).filter { it.isNotBlank() }

    val iceServers: List<PeerConnection.IceServer>
        get() {
            val list = mutableListOf<PeerConnection.IceServer>()
            // Enforce primary Google STUN servers
            list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            list.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())

            val turnUser = BuildConfig.TURN_USERNAME
            val turnPass = BuildConfig.TURN_PASSWORD

            // Validate ExpressTURN credentials over UDP and TCP
            if (turnUser.isNotBlank() && turnPass.isNotBlank()) {
                list.add(
                    PeerConnection.IceServer.builder("turn:$TURN_HOST?transport=udp")
                        .setUsername(turnUser)
                        .setPassword(turnPass)
                        .createIceServer()
                )
                list.add(
                    PeerConnection.IceServer.builder("turn:$TURN_HOST?transport=tcp")
                        .setUsername(turnUser)
                        .setPassword(turnPass)
                        .createIceServer()
                )
            } else {
                // Failover to secondary open STUN nodes without crashing if credentials are blank
                list.add(PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer())
            }
            return list
        }

    // Backward-compatibility alias
    val ICE_SERVERS get() = iceServers
}
