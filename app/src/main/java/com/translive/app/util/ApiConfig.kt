package com.translive.app.util

object ApiConfig {
    const val GEMINI_API_KEY = "AIzaSyAxKFKsqEusvrLYsG7d98_3ZyOr_Iox4bU"
    const val GEMINI_MODEL = "gemini-3.8-flash"
    const val GEMINI_FALLBACK_MODEL = "gemini-1.5-flash"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    const val TURN_HOST = "free.expressturn.com:3478"
    const val TURN_USERNAME = "000000002107783376"
    const val TURN_PASSWORD = "7sAV+w7hgytSej4wTIQOGYM2z90="
    const val CLOUD_DB_URL = "https://intent-realtime-global-default-rtdb.firebaseio.com"

    val ICE_SERVERS = listOf(
        IceServerConfig("stun:stun.l.google.com:19302"),
        IceServerConfig("turn:free.expressturn.com:3478?transport=udp", TURN_USERNAME, TURN_PASSWORD),
        IceServerConfig("turn:free.expressturn.com:3478?transport=tcp", TURN_USERNAME, TURN_PASSWORD)
    )
}

data class IceServerConfig(
    val uri: String,
    val username: String? = null,
    val password: String? = null
)
