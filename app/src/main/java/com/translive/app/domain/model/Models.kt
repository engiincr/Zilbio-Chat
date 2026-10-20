package com.translive.app.domain.model

enum class Language(val code: String, val displayName: String, val flag: String) {
    TURKISH("tr", "Türkçe", "🇹🇷"),
    ENGLISH("en", "English", "🇺🇸"),
    GERMAN("de", "Deutsch", "🇩🇪");

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class User(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val passwordHash: String = "",
    val language: String = "en",
    val avatarBase64: String? = null,
    val status: String = "offline",
    val createdAt: Long = System.currentTimeMillis()
)

data class Friend(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarBase64: String? = null,
    val status: String = "offline",
    val language: String = "en"
)

data class FriendRequest(
    val senderId: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderAvatarBase64: String? = null,
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val chatId: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val originalLanguage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: String = "text"
)

data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val callerName: String = "",
    val receiverName: String = "",
    val status: String = "ringing",
    val startTime: Long = System.currentTimeMillis()
)

data class CallTranscriptSegment(
    val speakerId: String = "",
    val speakerName: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val originalLanguage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val provider: String = "gemini"
)
