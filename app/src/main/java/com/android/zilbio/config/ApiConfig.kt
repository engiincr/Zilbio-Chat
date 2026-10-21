package com.android.zilbio.config

import com.android.zilbio.BuildConfig
import org.webrtc.PeerConnection

object ApiConfig {
    const val TURN_HOST = "free.expressturn.com:3478"
    const val GEMINI_MODEL = "gemini-3.5-flash-lite"
    const val GEMINI_FALLBACK_MODEL = "gemini-1.5-flash"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    val cloudDbUrl: String = BuildConfig.CLOUD_DB_URL
    val CLOUD_DB_URL: String get() = cloudDbUrl

    val geminiKeys: List<String> =
        listOf(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_API_KEY2).filter { it.isNotBlank() }

    val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:$TURN_HOST?transport=udp")
            .setUsername(BuildConfig.TURN_USERNAME)
            .setPassword(BuildConfig.TURN_PASSWORD)
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:$TURN_HOST?transport=tcp")
            .setUsername(BuildConfig.TURN_USERNAME)
            .setPassword(BuildConfig.TURN_PASSWORD)
            .createIceServer()
    )

    // Backward-compatibility alias
    val ICE_SERVERS get() = iceServers
}
