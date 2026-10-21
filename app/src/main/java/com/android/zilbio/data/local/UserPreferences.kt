package com.android.zilbio.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.zilbio.dataStore
import com.android.zilbio.domain.model.ChatCustomization
import com.android.zilbio.domain.model.PrivacySettings
import com.android.zilbio.domain.model.User
import com.android.zilbio.domain.model.UserPermissions
import com.android.zilbio.domain.model.isTugkancr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException

object UserPreferences {
    val KEY_USER_ID = stringPreferencesKey("user_id")
    val KEY_USERNAME = stringPreferencesKey("username")
    val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
    val KEY_LANGUAGE = stringPreferencesKey("language")
    val KEY_AVATAR_BASE64 = stringPreferencesKey("avatar_base64")
    val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

    // Personal Profile Info (Item #13)
    val KEY_BIO = stringPreferencesKey("bio")
    val KEY_AGE = intPreferencesKey("age")
    val KEY_BIRTH_DATE = stringPreferencesKey("birth_date")
    val KEY_PROFILE_VISIBILITY = stringPreferencesKey("profile_visibility")

    // Privacy keys
    val KEY_BLOCKED_USERS = stringPreferencesKey("blocked_user_ids")
    val KEY_ARCHIVE_UNKNOWN = booleanPreferencesKey("archive_unknown_chats")
    val KEY_SILENCE_NON_CONTACTS = booleanPreferencesKey("silence_non_contact_calls")
    val KEY_SHOW_AGE = booleanPreferencesKey("show_age")
    val KEY_SENSITIVE_FILTER = booleanPreferencesKey("sensitive_content_filter")

    // Power & Autostart Onboarding (Item #7)
    val KEY_HAS_SHOWN_POWER_ONBOARDING = booleanPreferencesKey("has_shown_power_onboarding")

    // Role & Permissions
    val KEY_ROLE = stringPreferencesKey("user_role")
    val KEY_IS_ADMIN = booleanPreferencesKey("is_admin")
    val KEY_CAN_EDIT_PROFILE = booleanPreferencesKey("can_edit_profile")
    val KEY_CAN_CHANGE_USERNAME = booleanPreferencesKey("can_change_username")

    // Theme & Customization (Item #9 & #19)
    val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode") // "SYSTEM", "DARK", "LIGHT"
    val KEY_PINNED_CHATS = stringPreferencesKey("pinned_chats") // Comma-separated chatIds
    val KEY_INSTALLED_COMMIT_SHA = stringPreferencesKey("installed_commit_sha")
    val KEY_LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
    val KEY_LAST_NOTIFIED_VERSION_CODE = intPreferencesKey("last_notified_version_code")
    val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_csv")

    fun getUser(context: Context): Flow<User?> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                val isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
                if (!isLoggedIn) {
                    null
                } else {
                    val userId = prefs[KEY_USER_ID] ?: return@map null
                    val username = prefs[KEY_USERNAME] ?: ""
                    val displayName = prefs[KEY_DISPLAY_NAME] ?: ""
                    val passwordHash = prefs[KEY_PASSWORD_HASH] ?: ""
                    val language = prefs[KEY_LANGUAGE] ?: "tr"
                    val avatarBase64 = prefs[KEY_AVATAR_BASE64]
                    val bio = prefs[KEY_BIO] ?: ""
                    val age = prefs[KEY_AGE] ?: 0
                    val birthDate = prefs[KEY_BIRTH_DATE] ?: ""
                    val profileVisibility = prefs[KEY_PROFILE_VISIBILITY] ?: "EVERYONE"
                    val role = prefs[KEY_ROLE] ?: if (isTugkancr(username)) "owner" else "user"
                    val isAdmin = prefs[KEY_IS_ADMIN] ?: (role.lowercase() in listOf("owner", "admin") || isTugkancr(username))
                    val canEditProfile = prefs[KEY_CAN_EDIT_PROFILE] ?: true
                    val canChangeUsername = prefs[KEY_CAN_CHANGE_USERNAME] ?: true

                    User(
                        userId = userId,
                        username = username,
                        displayName = displayName,
                        passwordHash = passwordHash,
                        language = language,
                        avatarBase64 = if (avatarBase64.isNullOrEmpty()) null else avatarBase64,
                        status = "online",
                        bio = bio,
                        age = age,
                        birthDate = birthDate,
                        profileVisibility = profileVisibility,
                        role = role,
                        permissions = UserPermissions(
                            isAdmin = isAdmin,
                            canEditProfile = canEditProfile,
                            canChangeUsername = canChangeUsername
                        )
                    )
                }
            }
    }

    suspend fun getUserSync(context: Context): User? {
        return getUser(context).firstOrNull()
    }

    suspend fun saveUser(context: Context, user: User) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.userId
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_DISPLAY_NAME] = user.displayName
            prefs[KEY_PASSWORD_HASH] = user.passwordHash
            prefs[KEY_LANGUAGE] = user.language
            prefs[KEY_AVATAR_BASE64] = user.avatarBase64 ?: ""
            prefs[KEY_BIO] = user.bio
            prefs[KEY_AGE] = user.age
            prefs[KEY_BIRTH_DATE] = user.birthDate
            prefs[KEY_PROFILE_VISIBILITY] = user.profileVisibility
            prefs[KEY_ROLE] = user.role
            prefs[KEY_IS_ADMIN] = user.permissions.isAdmin || user.role.lowercase() in listOf("owner", "admin") || isTugkancr(user.username)
            prefs[KEY_CAN_EDIT_PROFILE] = user.permissions.canEditProfile
            prefs[KEY_CAN_CHANGE_USERNAME] = user.permissions.canChangeUsername
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun updateDisplayName(context: Context, newDisplayName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = newDisplayName
        }
    }

    suspend fun updateAvatar(context: Context, avatarBase64: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR_BASE64] = avatarBase64
        }
    }

    suspend fun updateLanguage(context: Context, newLanguage: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = newLanguage
        }
    }

    suspend fun updateProfileDetails(
        context: Context,
        bio: String,
        age: Int,
        birthDate: String,
        profileVisibility: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIO] = bio
            prefs[KEY_AGE] = age
            prefs[KEY_BIRTH_DATE] = birthDate
            prefs[KEY_PROFILE_VISIBILITY] = profileVisibility
        }
    }

    // Power Onboarding
    fun hasShownPowerOnboarding(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[KEY_HAS_SHOWN_POWER_ONBOARDING] ?: false }
    }

    suspend fun setPowerOnboardingShown(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SHOWN_POWER_ONBOARDING] = true
        }
    }

    // Theme Mode
    fun getAppThemeMode(context: Context): Flow<String> {
        return context.dataStore.data.map { it[KEY_APP_THEME_MODE] ?: "DARK" }
    }

    suspend fun setAppThemeMode(context: Context, mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_MODE] = mode
        }
    }

    // Pinned Chats (Item #19)
    fun getPinnedChats(context: Context): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_PINNED_CHATS] ?: ""
            if (raw.isBlank()) emptySet() else raw.split(",").toSet()
        }
    }

    suspend fun togglePinnedChat(context: Context, chatId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_PINNED_CHATS] ?: ""
            val current = if (raw.isBlank()) mutableSetOf() else raw.split(",").toMutableSet()
            if (current.contains(chatId)) {
                current.remove(chatId)
            } else {
                current.add(chatId)
            }
            prefs[KEY_PINNED_CHATS] = current.joinToString(",")
        }
    }

    // Per-chat Theme & Brightness & Disappearing Messages (Item #9 & #E)
    fun getChatCustomization(context: Context, chatId: String): Flow<ChatCustomization> {
        val themeKey = stringPreferencesKey("chat_theme_$chatId")
        val brightnessKey = floatPreferencesKey("chat_brightness_$chatId")
        val disappearingKey = androidx.datastore.preferences.core.longPreferencesKey("chat_disappearing_$chatId")
        return context.dataStore.data.map { prefs ->
            val themeId = prefs[themeKey] ?: "midnight_navy"
            val brightness = prefs[brightnessKey] ?: 1.0f
            val disappearing = prefs[disappearingKey] ?: 0L
            ChatCustomization(themeId = themeId, brightness = brightness, disappearingDurationMs = disappearing)
        }
    }

    suspend fun saveChatCustomization(
        context: Context,
        chatId: String,
        themeId: String,
        brightness: Float,
        disappearingDurationMs: Long = 0L
    ) {
        val themeKey = stringPreferencesKey("chat_theme_$chatId")
        val brightnessKey = floatPreferencesKey("chat_brightness_$chatId")
        val disappearingKey = androidx.datastore.preferences.core.longPreferencesKey("chat_disappearing_$chatId")
        context.dataStore.edit { prefs ->
            prefs[themeKey] = themeId
            prefs[brightnessKey] = brightness.coerceIn(0.1f, 1.0f)
            prefs[disappearingKey] = disappearingDurationMs
        }
    }

    suspend fun setDisappearingMessagesDuration(context: Context, chatId: String, durationMs: Long) {
        val disappearingKey = androidx.datastore.preferences.core.longPreferencesKey("chat_disappearing_$chatId")
        context.dataStore.edit { prefs ->
            prefs[disappearingKey] = durationMs
        }
    }

    fun getInstalledCommitSha(context: Context): Flow<String> {
        return context.dataStore.data.map { it[KEY_INSTALLED_COMMIT_SHA] ?: "" }
    }

    suspend fun setInstalledCommitSha(context: Context, sha: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INSTALLED_COMMIT_SHA] = sha
        }
    }

    fun getLastNotifiedVersionCode(context: Context): Flow<Int> {
        return context.dataStore.data.map { it[KEY_LAST_NOTIFIED_VERSION_CODE] ?: 0 }
    }

    suspend fun setLastNotifiedVersionCode(context: Context, versionCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFIED_VERSION_CODE] = versionCode
        }
    }

    fun getLastUpdateCheckTime(context: Context): Flow<Long> {
        return context.dataStore.data.map { it[KEY_LAST_UPDATE_CHECK_TIME] ?: 0L }
    }

    suspend fun setLastUpdateCheckTime(context: Context, time: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK_TIME] = time
        }
    }

    fun getPrivacySettings(context: Context): Flow<PrivacySettings> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                val blockedStr = prefs[KEY_BLOCKED_USERS] ?: ""
                val blockedList = if (blockedStr.isBlank()) emptyList() else blockedStr.split(",")
                PrivacySettings(
                    blockedUserIds = blockedList,
                    archiveUnknownChats = prefs[KEY_ARCHIVE_UNKNOWN] ?: false,
                    silenceNonContactCalls = prefs[KEY_SILENCE_NON_CONTACTS] ?: false,
                    showAge = prefs[KEY_SHOW_AGE] ?: true,
                    sensitiveContentFilter = prefs[KEY_SENSITIVE_FILTER] ?: true
                )
            }
    }

    suspend fun savePrivacySettings(context: Context, settings: PrivacySettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_USERS] = settings.blockedUserIds.joinToString(",")
            prefs[KEY_ARCHIVE_UNKNOWN] = settings.archiveUnknownChats
            prefs[KEY_SILENCE_NON_CONTACTS] = settings.silenceNonContactCalls
            prefs[KEY_SHOW_AGE] = settings.showAge
            prefs[KEY_SENSITIVE_FILTER] = settings.sensitiveContentFilter
        }
    }

    fun getSearchHistory(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { prefs ->
            val csv = prefs[KEY_SEARCH_HISTORY] ?: ""
            if (csv.isBlank()) emptyList()
            else csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    suspend fun addSearchQuery(context: Context, query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_SEARCH_HISTORY] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
                .toMutableList()
            current.add(0, clean)
            val limited = current.take(15)
            prefs[KEY_SEARCH_HISTORY] = limited.joinToString(",")
        }
    }

    suspend fun removeSearchQuery(context: Context, query: String) {
        val clean = query.trim()
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_SEARCH_HISTORY] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
            prefs[KEY_SEARCH_HISTORY] = current.joinToString(",")
        }
    }

    suspend fun clearSearchHistory(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEARCH_HISTORY] = ""
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
