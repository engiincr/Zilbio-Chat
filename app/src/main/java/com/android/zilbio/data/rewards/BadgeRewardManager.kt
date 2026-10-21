package com.android.zilbio.data.rewards

import android.content.Context
import android.util.Log
import com.android.zilbio.data.remote.CloudRealtimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

enum class BadgeCategory {
    MESSAGING,
    VOICE_CALL,
    VIDEO_CALL
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val category: BadgeCategory,
    val requirementText: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

data class CelebrationEvent(
    val badge: Badge,
    val userName: String
)

object BadgeRewardManager {
    private const val TAG = "BadgeRewardManager"
    private const val PREFS_NAME = "zilbio_badges_prefs"

    // Badge IDs
    const val BADGE_FIRST_MESSAGE = "first_message"
    const val BADGE_CHAT_100 = "chat_enthusiast_100"
    const val BADGE_CHAT_500 = "legendary_chatter_500"

    const val BADGE_VOICE_10M = "voice_starter_10m"
    const val BADGE_VOICE_100M = "voice_pro_100m"
    const val BADGE_VOICE_500M = "voice_legend_500m"

    const val BADGE_VIDEO_10M = "video_starter_10m"
    const val BADGE_VIDEO_100M = "video_pro_100m"
    const val BADGE_VIDEO_500M = "video_legend_500m"

    val ALL_BADGES = listOf(
        // Mesajlaşma Rozetleri
        Badge(
            id = BADGE_FIRST_MESSAGE,
            title = "İlk Adım 💬",
            description = "İlk mesajını başarıyla gönderdin!",
            iconEmoji = "💬",
            category = BadgeCategory.MESSAGING,
            requirementText = "1 Mesaj Gönder"
        ),
        Badge(
            id = BADGE_CHAT_100,
            title = "Sohbet Ustası ⚡",
            description = "100 mesaj barajını aştın!",
            iconEmoji = "⚡",
            category = BadgeCategory.MESSAGING,
            requirementText = "100 Mesaj Gönder"
        ),
        Badge(
            id = BADGE_CHAT_500,
            title = "Efsanevi Hatip 👑",
            description = "500 mesaj barajını aştın, tam bir sohbet liderisin!",
            iconEmoji = "👑",
            category = BadgeCategory.MESSAGING,
            requirementText = "500 Mesaj Gönder"
        ),

        // Sesli Görüşme Rozetleri (Strictly isVideo == false)
        Badge(
            id = BADGE_VOICE_10M,
            title = "Sesli Kaşif 📞",
            description = "Toplam 10 dakika sesli görüşme gerçekleştirdin!",
            iconEmoji = "📞",
            category = BadgeCategory.VOICE_CALL,
            requirementText = "10 Dk Sesli Görüşme"
        ),
        Badge(
            id = BADGE_VOICE_100M,
            title = "Sesli Usta 🎙️",
            description = "Toplam 100 dakika sesli görüşme gerçekleştirdin!",
            iconEmoji = "🎙️",
            category = BadgeCategory.VOICE_CALL,
            requirementText = "100 Dk Sesli Görüşme"
        ),
        Badge(
            id = BADGE_VOICE_500M,
            title = "Seslerin Efendisi 🌟",
            description = "Toplam 500 dakika sesli görüşme tamamladın!",
            iconEmoji = "🌟",
            category = BadgeCategory.VOICE_CALL,
            requirementText = "500 Dk Sesli Görüşme"
        ),

        // Görüntülü Görüşme Rozetleri (Strictly isVideo == true)
        Badge(
            id = BADGE_VIDEO_10M,
            title = "Kamera Dostu 📹",
            description = "Toplam 10 dakika görüntülü görüşme gerçekleştirdin!",
            iconEmoji = "📹",
            category = BadgeCategory.VIDEO_CALL,
            requirementText = "10 Dk Görüntülü Görüşme"
        ),
        Badge(
            id = BADGE_VIDEO_100M,
            title = "Yayıncı Yıldız ✨",
            description = "Toplam 100 dakika görüntülü görüşme gerçekleştirdin!",
            iconEmoji = "✨",
            category = BadgeCategory.VIDEO_CALL,
            requirementText = "100 Dk Görüntülü Görüşme"
        ),
        Badge(
            id = BADGE_VIDEO_500M,
            title = "Görüntü Virtüözü 🏆",
            description = "Toplam 500 dakika görüntülü görüşme tamamladın!",
            iconEmoji = "🏆",
            category = BadgeCategory.VIDEO_CALL,
            requirementText = "500 Dk Görüntülü Görüşme"
        )
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _celebrationFlow = MutableStateFlow<CelebrationEvent?>(null)
    val celebrationFlow: StateFlow<CelebrationEvent?> = _celebrationFlow.asStateFlow()

    private val _unlockedBadgesFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unlockedBadgesFlow: StateFlow<Map<String, Long>> = _unlockedBadgesFlow.asStateFlow()

    fun dismissCelebration() {
        _celebrationFlow.value = null
    }

    fun init(context: Context, userId: String) {
        scope.launch {
            loadLocalBadges(context, userId)
            syncFromFirebase(userId)
        }
    }

    private fun loadLocalBadges(context: Context, userId: String) {
        val sp = context.getSharedPreferences("${PREFS_NAME}_$userId", Context.MODE_PRIVATE)
        val map = mutableMapOf<String, Long>()
        ALL_BADGES.forEach { badge ->
            val timestamp = sp.getLong("badge_${badge.id}", 0L)
            if (timestamp > 0L) {
                map[badge.id] = timestamp
            }
        }
        _unlockedBadgesFlow.value = map
    }

    private suspend fun syncFromFirebase(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = CloudRealtimeService.getRawData("users/$userId/badges.json")
                if (!response.isNullOrBlank() && response != "null") {
                    val json = JSONObject(response)
                    val map = _unlockedBadgesFlow.value.toMutableMap()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val badgeId = keys.next()
                        val obj = json.optJSONObject(badgeId)
                        val unlockedAt = obj?.optLong("unlockedAt", System.currentTimeMillis())
                            ?: System.currentTimeMillis()
                        map[badgeId] = unlockedAt
                    }
                    _unlockedBadgesFlow.value = map
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync badges from Firebase: ${e.message}")
            }
        }
    }

    fun recordMessageSent(context: Context, userId: String, displayName: String) {
        if (userId.isBlank()) return
        scope.launch {
            val sp = context.getSharedPreferences("${PREFS_NAME}_$userId", Context.MODE_PRIVATE)
            val currentCount = sp.getInt("stat_messages_sent", 0) + 1
            sp.edit().putInt("stat_messages_sent", currentCount).apply()

            // Firebase update
            CloudRealtimeService.updateUserStats(userId, mapOf("messagesSent" to currentCount))

            // Check Milestones
            if (currentCount >= 1) checkAndUnlock(context, userId, displayName, BADGE_FIRST_MESSAGE)
            if (currentCount >= 100) checkAndUnlock(context, userId, displayName, BADGE_CHAT_100)
            if (currentCount >= 500) checkAndUnlock(context, userId, displayName, BADGE_CHAT_500)
        }
    }

    fun recordCallDuration(
        context: Context,
        userId: String,
        displayName: String,
        durationSeconds: Long,
        isVideo: Boolean
    ) {
        if (userId.isBlank() || durationSeconds <= 0) return
        scope.launch {
            val sp = context.getSharedPreferences("${PREFS_NAME}_$userId", Context.MODE_PRIVATE)
            if (isVideo) {
                val totalVideoSec = sp.getLong("stat_video_sec", 0L) + durationSeconds
                sp.edit().putLong("stat_video_sec", totalVideoSec).apply()
                val totalVideoMinutes = totalVideoSec / 60
                CloudRealtimeService.updateUserStats(userId, mapOf("videoCallSeconds" to totalVideoSec))

                if (totalVideoMinutes >= 10) checkAndUnlock(context, userId, displayName, BADGE_VIDEO_10M)
                if (totalVideoMinutes >= 100) checkAndUnlock(context, userId, displayName, BADGE_VIDEO_100M)
                if (totalVideoMinutes >= 500) checkAndUnlock(context, userId, displayName, BADGE_VIDEO_500M)
            } else {
                val totalVoiceSec = sp.getLong("stat_voice_sec", 0L) + durationSeconds
                sp.edit().putLong("stat_voice_sec", totalVoiceSec).apply()
                val totalVoiceMinutes = totalVoiceSec / 60
                CloudRealtimeService.updateUserStats(userId, mapOf("voiceCallSeconds" to totalVoiceSec))

                if (totalVoiceMinutes >= 10) checkAndUnlock(context, userId, displayName, BADGE_VOICE_10M)
                if (totalVoiceMinutes >= 100) checkAndUnlock(context, userId, displayName, BADGE_VOICE_100M)
                if (totalVoiceMinutes >= 500) checkAndUnlock(context, userId, displayName, BADGE_VOICE_500M)
            }
        }
    }

    private suspend fun checkAndUnlock(
        context: Context,
        userId: String,
        displayName: String,
        badgeId: String
    ) {
        val sp = context.getSharedPreferences("${PREFS_NAME}_$userId", Context.MODE_PRIVATE)
        if (sp.contains("badge_$badgeId")) {
            return // Already unlocked
        }

        val timestamp = System.currentTimeMillis()
        sp.edit().putLong("badge_$badgeId", timestamp).apply()

        val updatedMap = _unlockedBadgesFlow.value.toMutableMap()
        updatedMap[badgeId] = timestamp
        _unlockedBadgesFlow.value = updatedMap

        // Sync to Firebase
        CloudRealtimeService.saveUserBadge(userId, badgeId, timestamp)

        // Find badge definition
        val badge = ALL_BADGES.firstOrNull { it.id == badgeId } ?: return
        val unlockedBadge = badge.copy(isUnlocked = true, unlockedAt = timestamp)

        // Trigger celebratory dialog with uppercase user name
        val formattedName = displayName.trim().ifBlank { "KULLANICI" }.uppercase(Locale.getDefault())
        _celebrationFlow.value = CelebrationEvent(unlockedBadge, formattedName)

        // Trigger sleek Dynamic Island notification overlay
        com.android.zilbio.ui.components.ZilbioDynamicIslandManager.showBadgeUnlocked(
            userName = formattedName,
            badgeTitle = badge.title,
            iconEmoji = badge.iconEmoji
        )
        Log.i(TAG, "Unlocked badge $badgeId for $formattedName!")
    }

    fun getBadgesWithStatus(unlockedMap: Map<String, Long>): List<Badge> {
        return ALL_BADGES.map { b ->
            val ts = unlockedMap[b.id]
            b.copy(isUnlocked = ts != null && ts > 0L, unlockedAt = ts)
        }
    }

    suspend fun fetchContactBadges(contactUserId: String): List<Badge> = withContext(Dispatchers.IO) {
        try {
            val response = CloudRealtimeService.getRawData("users/$contactUserId/badges.json")
            val unlockedMap = mutableMapOf<String, Long>()
            if (!response.isNullOrBlank() && response != "null") {
                val json = JSONObject(response)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = json.optJSONObject(k)
                    val ts = obj?.optLong("unlockedAt", 1L) ?: 1L
                    unlockedMap[k] = ts
                }
            }
            getBadgesWithStatus(unlockedMap)
        } catch (e: Exception) {
            getBadgesWithStatus(emptyMap())
        }
    }
}
