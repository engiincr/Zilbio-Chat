package com.android.zilbio.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
enum class Language(val code: String, val displayName: String, val flag: String) {
    TURKISH("tr", "Türkçe", "🇹🇷"),
    ENGLISH("en", "English", "🇺🇸"),
    GERMAN("de", "Deutsch", "🇩🇪");

    companion object {
        fun fromCode(code: String?): Language {
            val clean = code?.trim()?.lowercase() ?: "tr"
            return entries.firstOrNull { it.code == clean } ?: TURKISH
        }
    }
}

@Immutable
data class UserPermissions(
    val isAdmin: Boolean = false,
    val canEditProfile: Boolean = true,
    val canChangeUsername: Boolean = true,
    val canSendMessages: Boolean = true,
    val canSendMedia: Boolean = true,
    val canStartCalls: Boolean = true,
    val canCreateConference: Boolean = true,
    val unlimitedTranslationQuota: Boolean = false,
    val isVerifiedBadge: Boolean = false,
    val isAccountSuspended: Boolean = false,
    val customRoleLabel: String? = null
)

@Immutable
data class User(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val passwordHash: String = "",
    val language: String = "tr",
    val avatarBase64: String? = null,
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val bio: String = "",
    val age: Int = 0,
    val birthDate: String = "",
    val profileVisibility: String = "EVERYONE", // "FRIENDS", "EVERYONE"
    val role: String = "user", // "owner", "admin", "moderator", "vip", "user"
    val permissions: UserPermissions = UserPermissions(),
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class Friend(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarBase64: String? = null,
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val bio: String = "",
    val language: String = "tr",
    val role: String = "user",
    val permissions: UserPermissions = UserPermissions()
)

fun normalizeUsername(username: String): String =
    username.trim().removePrefix("@").lowercase()

fun isTugkancr(username: String): Boolean =
    normalizeUsername(username) == "tugkancr"

fun User.isOwnerOrAdmin(): Boolean =
    isTugkancr(username) || role.lowercase() in listOf("owner", "admin") || permissions.isAdmin

fun Friend.isOwnerOrAdmin(): Boolean =
    isTugkancr(username) || role.lowercase() in listOf("owner", "admin") || permissions.isAdmin


@Immutable
data class FriendRequest(
    val senderId: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderAvatarBase64: String? = null,
    val senderLanguage: String = "tr",
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
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
    val deliveryStatus: String = "SENT", // "SENDING", "SENT", "DELIVERED", "READ"
    val type: String = "text", // "text", "voice", "image", "document", "video", "contact", "audio", "location", "sticker", "call_log"

    // Pinning & Star
    val isPinned: Boolean = false,
    val isStarred: Boolean = false,

    // Edit message flag (Item H)
    val isEdited: Boolean = false,

    // Disappearing message expiration (Item E)
    val expiresAt: Long? = null,

    // Voice Note dual payload & Auto-transcript (Item F)
    val audioBase64: String? = null,
    val audioDurationMs: Long = 0L,
    val voiceTranscript: String? = null,

    // Rich Attachment payload
    val mediaUrlOrBase64: String? = null,
    val mediaFileName: String? = null,

    // Message Reactions (userId -> emoji string, e.g. "❤️")
    val reactions: Map<String, String> = emptyMap(),

    // Quoted Reply references (Item C)
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,

    // In-chat Call Log attributes
    val callStatus: String? = null, // "active", "ended", "missed", "declined"
    val callDurationSeconds: Long = 0L
)

@Immutable
data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val callerName: String = "",
    val receiverName: String = "",
    val status: String = "ringing", // "ringing", "active", "ended", "declined", "missed"
    val callType: String = "audio", // "audio", "video"
    val isVideo: Boolean = false,
    val isConference: Boolean = false,
    val participants: Map<String, Boolean> = emptyMap(),
    val participantNames: Map<String, String> = emptyMap(),
    val endedBy: String? = null,
    val batteryAlerts: Map<String, Int> = emptyMap(), // userId -> battery percentage (e.g. 10 or 5)
    val startTime: Long = System.currentTimeMillis()
)

@Immutable
data class CallTranscriptSegment(
    val speakerId: String = "",
    val speakerName: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val originalLanguage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val provider: String = "gemini"
)

@Immutable
data class PrivacySettings(
    val blockedUserIds: List<String> = emptyList(),
    val archiveUnknownChats: Boolean = false,
    val silenceNonContactCalls: Boolean = false,
    val showAge: Boolean = true,
    val sensitiveContentFilter: Boolean = true
)

@Immutable
data class CallHistoryItem(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val isIncoming: Boolean = false,
    val isVideo: Boolean = false,
    val isConference: Boolean = false,
    val callStatus: String = "ended", // "ended", "missed", "declined"
    val durationSeconds: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class ChatCustomization(
    val themeId: String = "midnight_navy",
    val brightness: Float = 1.0f,
    val disappearingDurationMs: Long = 0L // 0 = off, 24 * 3600 * 1000L = 24h, 7 * 24 * 3600 * 1000L = 7d (Item E)
)

@Stable
enum class AudioOutputRoute {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    @Deprecated("Use WIRED_HEADSET or BLUETOOTH")
    BLUETOOTH_OR_HEADSET
}

@Immutable
data class ConnectedAudioDeviceInfo(
    val route: AudioOutputRoute,
    val name: String,
    val isConnected: Boolean
)

@Stable
enum class NetworkQuality {
    EXCELLENT,
    GOOD,
    WEAK,
    CRITICAL
}

@Immutable
data class DeviceSession(
    val sessionId: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val lastActive: Long = 0L,
    val isCurrent: Boolean = false
)
