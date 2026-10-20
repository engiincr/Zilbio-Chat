package com.translive.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.translive.app.dataStore
import com.translive.app.domain.model.User
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
                    val language = prefs[KEY_LANGUAGE] ?: "en"
                    val avatarBase64 = prefs[KEY_AVATAR_BASE64]
                    User(
                        userId = userId,
                        username = username,
                        displayName = displayName,
                        passwordHash = passwordHash,
                        language = language,
                        avatarBase64 = if (avatarBase64.isNullOrEmpty()) null else avatarBase64,
                        status = "online"
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
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun updateDisplayName(context: Context, newDisplayName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = newDisplayName
        }
    }

    suspend fun updateLanguage(context: Context, newLanguage: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = newLanguage
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
