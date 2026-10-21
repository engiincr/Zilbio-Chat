package com.android.zilbio.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.domain.model.CallSession
import com.android.zilbio.domain.model.CallTranscriptSegment
import com.android.zilbio.domain.model.DeviceSession
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.FriendRequest
import com.android.zilbio.domain.model.Message
import com.android.zilbio.domain.model.PrivacySettings
import com.android.zilbio.domain.model.User
import com.android.zilbio.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.android.zilbio.domain.model.UserPermissions
import com.android.zilbio.domain.model.isOwnerOrAdmin

sealed class UsernameChangeResult {
    object Success : UsernameChangeResult()
    data class Error(val message: String) : UsernameChangeResult()
}

object CloudRealtimeService {
    private const val TAG = "CloudRealtimeService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for SSE stream
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val gson = Gson()
    private val baseUrl = ApiConfig.CLOUD_DB_URL

    fun <T> parseFirebaseList(json: String?, clazz: Class<T>): List<T> {
        if (json.isNullOrBlank() || json.trim() == "null") return emptyList()
        val list = mutableListOf<T>()
        try {
            val rootElement = JsonParser.parseString(json)
            if (rootElement.isJsonObject) {
                val obj = rootElement.asJsonObject
                for ((key, value) in obj.entrySet()) {
                    if (value == null || value.isJsonNull) continue
                    try {
                        val parsed = gson.fromJson(value, clazz)
                        if (parsed != null) {
                            val adjusted = when (parsed) {
                                is User -> if (parsed.userId.isEmpty()) parsed.copy(userId = key) else parsed
                                is Friend -> if (parsed.userId.isEmpty()) parsed.copy(userId = key) else parsed
                                is FriendRequest -> if (parsed.senderId.isEmpty()) parsed.copy(senderId = key) else parsed
                                is Message -> if (parsed.messageId.isEmpty()) parsed.copy(messageId = key) else parsed
                                else -> parsed
                            }
                            @Suppress("UNCHECKED_CAST")
                            list.add(adjusted as T)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "parseFirebaseList item parse error for key $key: ${e.message}")
                    }
                }
            } else if (rootElement.isJsonArray) {
                val arr = rootElement.asJsonArray
                for (elem in arr) {
                    if (elem == null || elem.isJsonNull) continue
                    try {
                        val parsed = gson.fromJson(elem, clazz)
                        if (parsed != null) {
                            list.add(parsed)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "parseFirebaseList array element parse error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseFirebaseList failed: ${e.message}", e)
        }
        return list
    }

    inline fun <reified T> parseFirebaseList(json: String?): List<T> =
        parseFirebaseList(json, T::class.java)

    // ---- USER OPERATIONS ----

    suspend fun registerUser(user: User): Boolean {
        return try {
            val usernameKey = user.username.lowercase().trim().removePrefix("@")
            val finalUser = if (usernameKey == "tugkancr") {
                user.copy(
                    username = "tugkancr",
                    role = "owner",
                    permissions = user.permissions.copy(
                        isAdmin = true,
                        unlimitedTranslationQuota = true,
                        isVerifiedBadge = true
                    )
                )
            } else {
                user.copy(username = usernameKey)
            }
            val userJson = gson.toJson(finalUser)
            val putUser = putJson("users/${finalUser.userId}", userJson)
            if (!putUser) return false

            val userIdJson = gson.toJson(finalUser.userId)
            val putUsername = putJson("usernames/$usernameKey", userIdJson)
            putUsername
        } catch (e: Exception) {
            Log.e(TAG, "registerUser failed: ${e.message}", e)
            false
        }
    }


    suspend fun loginUser(username: String, passwordHash: String): User? {
        return try {
            val usernameKey = username.lowercase().trim()
            val userIdJson = getJson("usernames/$usernameKey") ?: return null
            val userId = gson.fromJson(userIdJson, String::class.java) ?: return null
            val user = searchUserById(userId) ?: return null
            if (user.passwordHash == passwordHash) {
                user
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginUser failed: ${e.message}", e)
            null
        }
    }

    suspend fun searchUserByUsername(username: String): User? {
        return try {
            val usernameKey = username.lowercase().trim().removePrefix("@")
            if (usernameKey.isEmpty()) return null

            // 1. Direct lookup in /usernames/{usernameKey}
            val userIdJson = getJson("usernames/$usernameKey")
            if (!userIdJson.isNullOrEmpty() && userIdJson != "null") {
                val resolvedUid = try {
                    gson.fromJson(userIdJson, String::class.java)?.trim()?.removeSurrounding("\"")
                } catch (e: Exception) {
                    userIdJson.trim().removeSurrounding("\"")
                }
                if (!resolvedUid.isNullOrEmpty()) {
                    val user = searchUserById(resolvedUid)
                    if (user != null) return user
                }
            }

            // 2. Fallback: Search directly across /users registry via resilient parser
            getAllUsers().find { it.username.equals(usernameKey, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "searchUserByUsername failed: ${e.message}", e)
            null
        }
    }

    suspend fun searchUserById(userId: String): User? {
        return try {
            val cleanId = userId.trim()
            if (cleanId.isEmpty()) return null

            // 1. Exact case-sensitive lookup
            val userJson = getJson("users/$cleanId")
            if (userJson != null) {
                val user = gson.fromJson(userJson, User::class.java)
                if (user != null) {
                    return if (user.userId.isEmpty()) user.copy(userId = cleanId) else user
                }
            }

            // 2. Resilient fallback across all users (handles legacy case discrepancies)
            getAllUsers().find { it.userId.equals(cleanId, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "searchUserById failed: ${e.message}", e)
            null
        }
    }

    suspend fun updateUserStatus(userId: String, status: String) {
        try {
            val patchObj = JsonObject().apply {
                addProperty("status", status)
            }
            patchJson("users/$userId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStatus failed: ${e.message}", e)
        }
    }

    suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        try {
            val patchObj = JsonObject().apply {
                addProperty("status", if (isOnline) "online" else "offline")
                addProperty("lastSeen", System.currentTimeMillis())
            }
            patchJson("users/$userId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateUserPresence failed: ${e.message}", e)
        }
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>) {
        try {
            val patchObj = JsonObject()
            for ((k, v) in updates) {
                when (v) {
                    is String -> patchObj.addProperty(k, v)
                    is Number -> patchObj.addProperty(k, v)
                    is Boolean -> patchObj.addProperty(k, v)
                }
            }
            patchJson("users/$userId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile failed: ${e.message}", e)
        }
    }

    fun validateUsernameFormat(username: String): String? {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.length < 3 || clean.length > 24) return "Kullanıcı adı 3-24 karakter arasında olmalıdır."
        val regex = Regex("^[a-z0-9_.]+$")
        if (!regex.matches(clean)) return "Sadece küçük harf, rakam, nokta ve alt çizgi kullanılabilir."
        return null
    }

    suspend fun checkUsernameAvailable(username: String, currentUserId: String? = null): Boolean {
        return try {
            val usernameKey = username.lowercase().trim().removePrefix("@")
            if (usernameKey.isEmpty()) return false
            val existing = getJson("usernames/$usernameKey")
            if (existing == null || existing == "null") {
                true
            } else {
                val ownerId = existing.trim().removeSurrounding("\"")
                currentUserId != null && ownerId.equals(currentUserId, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailable failed: ${e.message}", e)
            false
        }
    }

    suspend fun changeUsername(
        userId: String,
        oldUsername: String,
        newUsername: String
    ): UsernameChangeResult {
        val cleanOld = oldUsername.trim().removePrefix("@").lowercase()
        val cleanNew = newUsername.trim().removePrefix("@").lowercase()

        if (cleanNew == cleanOld) return UsernameChangeResult.Success

        val formatError = validateUsernameFormat(cleanNew)
        if (formatError != null) return UsernameChangeResult.Error(formatError)

        if (cleanNew == "tugkancr") {
            val user = searchUserById(userId)
            if (user == null || (!user.isOwnerOrAdmin() && cleanOld != "tugkancr")) {
                return UsernameChangeResult.Error("@tugkancr kullanıcı adı sadece sistem yöneticisine aittir.")
            }
        }

        return try {
            val isAvail = checkUsernameAvailable(cleanNew, userId)
            if (!isAvail) {
                return UsernameChangeResult.Error("Bu kullanıcı adı zaten kullanımda.")
            }

            // 1. Atomic Multi-path PATCH to Firebase root
            val multiPathMap = mutableMapOf<String, Any?>()
            multiPathMap["usernames/$cleanNew"] = userId
            multiPathMap["users/$userId/username"] = cleanNew
            if (cleanOld.isNotEmpty() && cleanOld != cleanNew) {
                multiPathMap["usernames/$cleanOld"] = null
            }
            val multiPatchSuccess = patchJson("", gson.toJson(multiPathMap))
            if (!multiPatchSuccess) {
                // Fallback sequential update with rollback if root-level patch is restricted
                val putNew = putJson("usernames/$cleanNew", gson.toJson(userId))
                if (!putNew) {
                    return UsernameChangeResult.Error("Kullanıcı adı kaydedilemedi.")
                }
                val patchUser = patchJson("users/$userId", gson.toJson(mapOf("username" to cleanNew)))
                if (!patchUser) {
                    deleteJson("usernames/$cleanNew")
                    return UsernameChangeResult.Error("Kullanıcı profili güncellenemedi.")
                }
                if (cleanOld.isNotEmpty() && cleanOld != cleanNew) {
                    deleteJson("usernames/$cleanOld")
                }
            }

            // 4. Update username in friends lists
            val friends = getFriends(userId)
            friends.forEach { friend ->
                patchJson("friends/${friend.userId}/$userId", gson.toJson(mapOf("username" to cleanNew)))
            }

            UsernameChangeResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "changeUsername error: ${e.message}", e)
            UsernameChangeResult.Error(e.localizedMessage ?: "Bilinmeyen hata")
        }
    }

    suspend fun ensureChatMembership(chatId: String, user1Id: String, user2Id: String): Boolean {
        return try {
            val put1 = putJson("chats/$chatId/members/$user1Id", "true")
            val put2 = putJson("chats/$chatId/members/$user2Id", "true")
            put1 && put2
        } catch (e: Exception) {
            Log.e(TAG, "ensureChatMembership failed: ${e.message}", e)
            false
        }
    }

    // ---- FRIEND REQUEST OPERATIONS ----

    suspend fun sendFriendRequest(senderId: String, receiverId: String, request: FriendRequest): Boolean {
        return try {
            val requestJson = gson.toJson(request)
            val putIncoming = putJson("friend_requests/$receiverId/$senderId", requestJson)
            val putSent = putJson("sent_requests/$senderId/$receiverId", requestJson)
            putIncoming && putSent
        } catch (e: Exception) {
            Log.e(TAG, "sendFriendRequest failed: ${e.message}", e)
            false
        }
    }

    suspend fun acceptFriendRequest(
        myUserId: String,
        friendUserId: String,
        myFriend: Friend,
        theirFriend: Friend
    ): Boolean {
        return try {
            val myFriendJson = gson.toJson(myFriend)
            val theirFriendJson = gson.toJson(theirFriend)

            val putMyFriend = putJson("friends/$myUserId/$friendUserId", myFriendJson)
            val putTheirFriend = putJson("friends/$friendUserId/$myUserId", theirFriendJson)

            deleteJson("friend_requests/$myUserId/$friendUserId")
            deleteJson("sent_requests/$friendUserId/$myUserId")

            // Ensure chat membership node exists for security rules
            val chatId = generateChatId(myUserId, friendUserId)
            ensureChatMembership(chatId, myUserId, friendUserId)

            putMyFriend && putTheirFriend
        } catch (e: Exception) {
            Log.e(TAG, "acceptFriendRequest failed: ${e.message}", e)
            false
        }
    }

    suspend fun declineFriendRequest(myUserId: String, senderId: String): Boolean {
        return try {
            val delIncoming = deleteJson("friend_requests/$myUserId/$senderId")
            val delSent = deleteJson("sent_requests/$senderId/$myUserId")
            delIncoming && delSent
        } catch (e: Exception) {
            Log.e(TAG, "declineFriendRequest failed: ${e.message}", e)
            false
        }
    }

    suspend fun getIncomingRequests(userId: String): List<FriendRequest> {
        return try {
            val json = getJson("friend_requests/$userId") ?: return emptyList()
            parseFirebaseList(json, FriendRequest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getIncomingRequests failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getSentRequests(userId: String): List<FriendRequest> {
        return try {
            val json = getJson("sent_requests/$userId") ?: return emptyList()
            parseFirebaseList(json, FriendRequest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getSentRequests failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFriends(userId: String): List<Friend> {
        return try {
            val json = getJson("friends/$userId") ?: return emptyList()
            parseFirebaseList(json, Friend::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getFriends failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getBlockedUsers(userId: String): List<String> {
        return try {
            val json = getJson("blocked_users/$userId") ?: return emptyList()
            val root = JsonParser.parseString(json)
            val list = mutableListOf<String>()
            if (root.isJsonObject) {
                root.asJsonObject.keySet().forEach { list.add(it) }
            } else if (root.isJsonArray) {
                root.asJsonArray.forEach { elem ->
                    if (!elem.isJsonNull) list.add(elem.asString)
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "getBlockedUsers failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun blockUser(myUserId: String, targetUserId: String): Boolean {
        return try {
            putJson("blocked_users/$myUserId/$targetUserId", "true")
        } catch (e: Exception) {
            Log.e(TAG, "blockUser failed: ${e.message}", e)
            false
        }
    }

    suspend fun unblockUser(myUserId: String, targetUserId: String): Boolean {
        return try {
            deleteJson("blocked_users/$myUserId/$targetUserId")
        } catch (e: Exception) {
            Log.e(TAG, "unblockUser failed: ${e.message}", e)
            false
        }
    }

    suspend fun isUserBlocked(myUserId: String, targetUserId: String): Boolean {
        return try {
            val res = getJson("blocked_users/$myUserId/$targetUserId")
            res != null && res.trim() != "null"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeFriend(myUserId: String, friendUserId: String): Boolean {
        return try {
            val delMy = deleteJson("friends/$myUserId/$friendUserId")
            val delTheir = deleteJson("friends/$friendUserId/$myUserId")
            Log.i(TAG, "removeFriend completed between $myUserId and $friendUserId (my: $delMy, their: $delTheir)")
            delMy && delTheir
        } catch (e: Exception) {
            Log.e(TAG, "removeFriend failed: ${e.message}", e)
            false
        }
    }

    // ---- MESSAGE OPERATIONS ----

    suspend fun sendMessage(chatId: String, message: Message): Boolean {
        return try {
            if (message.senderId.isNotEmpty() && message.receiverId.isNotEmpty()) {
                ensureChatMembership(chatId, message.senderId, message.receiverId)
            }
            val messageJson = gson.toJson(message)
            val putMessage = putJson("messages/$chatId/${message.messageId}", messageJson)
            // Strip heavy base64 strings from last_messages preview to optimize chat list loading
            val previewLastMessage = message.copy(
                audioBase64 = null,
                mediaUrlOrBase64 = if (!message.mediaUrlOrBase64.isNullOrBlank()) "[Medya]" else null
            )
            val putLast = putJson("last_messages/$chatId", gson.toJson(previewLastMessage))
            putMessage && putLast
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun updateMessageTranslation(chatId: String, messageId: String, translatedText: String) {
        try {
            val patch = JsonObject().apply {
                addProperty("translatedText", translatedText)
            }
            patchJson("messages/$chatId/$messageId", patch.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateMessageTranslation failed: ${e.message}", e)
        }
    }

    suspend fun updateDeliveryStatus(chatId: String, messageId: String, status: String) {
        try {
            val patch = JsonObject().apply {
                addProperty("deliveryStatus", status)
                if (status == "READ") {
                    addProperty("isRead", true)
                }
            }
            patchJson("messages/$chatId/$messageId", patch.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateDeliveryStatus failed: ${e.message}", e)
        }
    }

    suspend fun markChatAsRead(chatId: String, currentUserId: String): Boolean {
        return try {
            // 1. Update last_messages/{chatId}
            val patchLast = JsonObject().apply {
                addProperty("isRead", true)
                addProperty("deliveryStatus", "READ")
            }
            patchJson("last_messages/$chatId", patchLast.toString())

            // 2. Fetch messages in messages/{chatId} and update incoming unread messages
            val messagesJson = getJson("messages/$chatId")
            if (!messagesJson.isNullOrEmpty() && messagesJson != "null") {
                val type = object : TypeToken<Map<String, Message>>() {}.type
                val msgMap: Map<String, Message>? = gson.fromJson(messagesJson, type)
                msgMap?.forEach { (msgId, msg) ->
                    if (msg.receiverId == currentUserId && (!msg.isRead || msg.deliveryStatus != "READ")) {
                        val patchMsg = JsonObject().apply {
                            addProperty("isRead", true)
                            addProperty("deliveryStatus", "READ")
                        }
                        patchJson("messages/$chatId/$msgId", patchMsg.toString())
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "markChatAsRead failed: ${e.message}", e)
            false
        }
    }

    suspend fun togglePinMessage(chatId: String, messageId: String, isPinned: Boolean): Boolean {
        return try {
            val patch = JsonObject().apply {
                addProperty("isPinned", isPinned)
            }
            patchJson("messages/$chatId/$messageId", patch.toString())
        } catch (e: Exception) {
            Log.e(TAG, "togglePinMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun toggleStarMessage(chatId: String, messageId: String, isStarred: Boolean): Boolean {
        return try {
            val patch = JsonObject().apply {
                addProperty("isStarred", isStarred)
            }
            patchJson("messages/$chatId/$messageId", patch.toString())
        } catch (e: Exception) {
            Log.e(TAG, "toggleStarMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String, newTranslatedText: String = ""): Boolean {
        return try {
            val patch = JsonObject().apply {
                addProperty("originalText", newText)
                if (newTranslatedText.isNotBlank()) {
                    addProperty("translatedText", newTranslatedText)
                }
                addProperty("isEdited", true)
            }
            patchJson("messages/$chatId/$messageId", patch.toString())
        } catch (e: Exception) {
            Log.e(TAG, "editMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
        return try {
            deleteJson("messages/$chatId/$messageId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun getMessages(chatId: String): List<Message> {
        return try {
            val json = getJson("messages/$chatId") ?: return emptyList()
            parseFirebaseList(json, Message::class.java).sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "getMessages failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun toggleReaction(chatId: String, messageId: String, userId: String, emoji: String): Boolean {
        return try {
            val currentReactionJson = getJson("messages/$chatId/$messageId/reactions/$userId")
            if (currentReactionJson != null && currentReactionJson.trim().replace("\"", "") == emoji) {
                deleteJson("messages/$chatId/$messageId/reactions/$userId")
            } else {
                putJson("messages/$chatId/$messageId/reactions/$userId", gson.toJson(emoji))
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleReaction failed: ${e.message}", e)
            false
        }
    }

    // ---- DEVICE SESSIONS ----

    suspend fun registerDeviceSession(userId: String, session: DeviceSession): Boolean {
        return try {
            putJson("device_sessions/$userId/${session.sessionId}", gson.toJson(session))
        } catch (e: Exception) {
            Log.e(TAG, "registerDeviceSession failed: ${e.message}", e)
            false
        }
    }

    suspend fun getActiveSessions(userId: String): List<DeviceSession> {
        return try {
            val json = getJson("device_sessions/$userId") ?: return emptyList()
            parseFirebaseList(json, DeviceSession::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getActiveSessions failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun revokeSession(userId: String, sessionId: String): Boolean {
        return try {
            deleteJson("device_sessions/$userId/$sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "revokeSession failed: ${e.message}", e)
            false
        }
    }

    // ---- ADMIN & PERMISSION MANAGEMENT (Exclusive for @tugkancr) ----

    suspend fun getAllUsers(): List<User> {
        return try {
            val json = getJson("users") ?: return emptyList()
            parseFirebaseList(json, User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getAllUsers failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateUserPermissions(targetUserId: String, permissions: UserPermissions, role: String? = null): Boolean {
        return try {
            val patchObj = JsonObject().apply {
                add("permissions", gson.toJsonTree(permissions))
                if (role != null) {
                    addProperty("role", role)
                }
            }
            patchJson("users/$targetUserId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateUserPermissions failed: ${e.message}", e)
            false
        }
    }

    suspend fun setUserSuspension(targetUserId: String, isSuspended: Boolean): Boolean {
        return try {
            val patchObj = JsonObject().apply {
                val permObj = JsonObject().apply {
                    addProperty("isAccountSuspended", isSuspended)
                }
                add("permissions", permObj)
            }
            patchJson("users/$targetUserId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "setUserSuspension failed: ${e.message}", e)
            false
        }
    }


    suspend fun saveCallHistoryItem(userId: String, item: CallHistoryItem): Boolean {
        return try {
            putJson("call_history/$userId/${item.callId}", gson.toJson(item))
        } catch (e: Exception) {
            Log.e(TAG, "saveCallHistoryItem failed: ${e.message}", e)
            false
        }
    }

    suspend fun getCallHistory(userId: String): List<CallHistoryItem> {
        return try {
            val json = getJson("call_history/$userId") ?: return emptyList()
            val type = object : TypeToken<Map<String, CallHistoryItem>>() {}.type
            val map: Map<String, CallHistoryItem>? = gson.fromJson(json, type)
            map?.values?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getCallHistory failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun savePrivacySettings(userId: String, settings: PrivacySettings): Boolean {
        return try {
            putJson("privacy_settings/$userId", gson.toJson(settings))
        } catch (e: Exception) {
            Log.e(TAG, "savePrivacySettings failed: ${e.message}", e)
            false
        }
    }

    suspend fun getPrivacySettings(userId: String): PrivacySettings? {
        return try {
            val json = getJson("privacy_settings/$userId") ?: return null
            gson.fromJson(json, PrivacySettings::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getPrivacySettings failed: ${e.message}", e)
            null
        }
    }

    suspend fun deleteAccount(userId: String, username: String): Boolean {
        return try {
            deleteJson("users/$userId")
            deleteJson("usernames/${username.lowercase().trim()}")
            deleteJson("friends/$userId")
            deleteJson("friend_requests/$userId")
            deleteJson("sent_requests/$userId")
            deleteJson("call_history/$userId")
            deleteJson("privacy_settings/$userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount failed: ${e.message}", e)
            false
        }
    }

    // ---- CALL SIGNALING ----

    suspend fun initiateCall(callSession: CallSession): Boolean {
        return try {
            val callJson = gson.toJson(callSession)
            val putCall = putJson("calls/${callSession.callId}", callJson)
            val putIncoming = putJson("incoming_calls/${callSession.receiverId}/${callSession.callId}", callJson)
            putCall && putIncoming
        } catch (e: Exception) {
            Log.e(TAG, "initiateCall failed: ${e.message}", e)
            false
        }
    }

    suspend fun answerCall(callId: String) {
        try {
            val patchObj = JsonObject().apply {
                addProperty("status", "active")
            }
            patchJson("calls/$callId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "answerCall failed: ${e.message}", e)
        }
    }

    suspend fun endCall(callId: String, receiverId: String) {
        try {
            val patchObj = JsonObject().apply {
                addProperty("status", "ended")
            }
            patchJson("calls/$callId", patchObj.toString())
            deleteJson("incoming_calls/$receiverId/$callId")
        } catch (e: Exception) {
            Log.e(TAG, "endCall failed: ${e.message}", e)
        }
    }

    suspend fun endCallWithSignal(callId: String, receiverId: String, endedByUserId: String) {
        try {
            val patchObj = JsonObject().apply {
                addProperty("status", "ended")
                addProperty("endedBy", endedByUserId)
            }
            patchJson("calls/$callId", patchObj.toString())
            deleteJson("incoming_calls/$receiverId/$callId")
        } catch (e: Exception) {
            Log.e(TAG, "endCallWithSignal failed: ${e.message}", e)
        }
    }

    suspend fun broadcastBatteryAlert(callId: String, userId: String, batteryPct: Int) {
        try {
            putJson("calls/$callId/batteryAlerts/$userId", gson.toJson(batteryPct))
        } catch (e: Exception) {
            Log.e(TAG, "broadcastBatteryAlert failed: ${e.message}", e)
        }
    }

    suspend fun addConferenceParticipant(callId: String, newUserId: String, displayName: String): Boolean {
        return try {
            val participantsJson = getJson("calls/$callId/participants")
            val count = if (participantsJson != null && participantsJson != "null") {
                val mapType = object : TypeToken<Map<String, Boolean>>() {}.type
                val current: Map<String, Boolean>? = gson.fromJson(participantsJson, mapType)
                current?.size ?: 0
            } else {
                2
            }
            if (count >= 4) {
                return false // Maximum 4 participants allowed
            }
            putJson("calls/$callId/participants/$newUserId", "true")
            putJson("calls/$callId/participantNames/$newUserId", gson.toJson(displayName))
            true
        } catch (e: Exception) {
            Log.e(TAG, "addConferenceParticipant failed: ${e.message}", e)
            false
        }
    }

    suspend fun sendOffer(callId: String, sdp: String) {
        try {
            val offerObj = JsonObject().apply {
                addProperty("sdp", sdp)
                addProperty("type", "offer")
            }
            putJson("calls/$callId/offer", offerObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendOffer failed: ${e.message}", e)
        }
    }

    suspend fun sendAnswer(callId: String, sdp: String) {
        try {
            val answerObj = JsonObject().apply {
                addProperty("sdp", sdp)
                addProperty("type", "answer")
            }
            putJson("calls/$callId/answer", answerObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendAnswer failed: ${e.message}", e)
        }
    }

    suspend fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int, senderId: String = "") {
        try {
            val key = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val candidateObj = JsonObject().apply {
                addProperty("candidate", candidate)
                addProperty("sdpMid", sdpMid)
                addProperty("sdpMLineIndex", sdpMLineIndex)
                addProperty("senderId", senderId)
            }
            putJson("calls/$callId/ice_candidates/$key", candidateObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendIceCandidate failed: ${e.message}", e)
        }
    }

    suspend fun getOffer(callId: String): String? {
        return try {
            val json = getJson("calls/$callId/offer") ?: return null
            val root = JsonParser.parseString(json)
            if (root.isJsonObject && root.asJsonObject.has("sdp")) {
                root.asJsonObject.get("sdp").asString
            } else if (root.isJsonPrimitive) {
                root.asString
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getOffer failed: ${e.message}", e)
            null
        }
    }

    suspend fun getAnswer(callId: String): String? {
        return try {
            val json = getJson("calls/$callId/answer") ?: return null
            val root = JsonParser.parseString(json)
            if (root.isJsonObject && root.asJsonObject.has("sdp")) {
                root.asJsonObject.get("sdp").asString
            } else if (root.isJsonPrimitive) {
                root.asString
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getAnswer failed: ${e.message}", e)
            null
        }
    }

    suspend fun sendTranscriptSegment(callId: String, segment: CallTranscriptSegment) {
        try {
            val segmentJson = gson.toJson(segment)
            val timestamp = segment.timestamp
            putJson("calls/$callId/transcripts/$timestamp", segmentJson)
        } catch (e: Exception) {
            Log.e(TAG, "sendTranscriptSegment failed: ${e.message}", e)
        }
    }

    // ---- SSE LISTENERS & CALL LOGGING ----

    fun listenToPath(path: String, onEvent: (String) -> Unit): Call {
        val url = buildUrl(path)
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .build()

        val rootCall = sseClient.newCall(request)

        fun connect(activeCall: Call) {
            activeCall.enqueue(object : Callback {
                override fun onFailure(c: Call, e: IOException) {
                    if (!c.isCanceled()) {
                        Log.w(TAG, "SSE connection failure for $path: ${e.message}. Reconnecting in 2s...")
                        try {
                            Thread.sleep(2000L)
                            if (!c.isCanceled()) {
                                connect(sseClient.newCall(request))
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                override fun onResponse(c: Call, response: Response) {
                    response.body?.let { responseBody ->
                        try {
                            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                            var line: String?
                            var currentEvent: String? = null
                            while (reader.readLine().also { line = it } != null) {
                                val trimmed = line?.trim() ?: continue
                                if (trimmed.startsWith("event:")) {
                                    currentEvent = trimmed.substring(6).trim()
                                } else if (trimmed.startsWith("data:")) {
                                    val data = trimmed.substring(5).trim()
                                    if (data.isNotEmpty() && data != "null" && currentEvent != "keep-alive") {
                                        onEvent(data)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (!c.isCanceled()) {
                                Log.w(TAG, "SSE read error for $path: ${e.message}")
                            }
                        } finally {
                            responseBody.close()
                            if (!c.isCanceled()) {
                                Log.i(TAG, "SSE stream terminated for $path. Reconnecting in 1.5s...")
                                try {
                                    Thread.sleep(1500L)
                                    if (!c.isCanceled()) {
                                        connect(sseClient.newCall(request))
                                    }
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                }
            })
        }

        connect(rootCall)
        return rootCall
    }

    suspend fun getActiveIncomingCalls(userId: String): List<CallSession> {
        return try {
            val json = getJson("incoming_calls/$userId") ?: return emptyList()
            val type = object : TypeToken<Map<String, CallSession>>() {}.type
            val map: Map<String, CallSession>? = gson.fromJson(json, type)
            map?.values?.filter { it.status == "ringing" } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun logCallToChat(
        chatId: String,
        callerId: String,
        receiverId: String,
        isVideo: Boolean,
        callStatus: String,
        durationSeconds: Long
    ): Boolean {
        return try {
            val isMissed = callStatus == "missed" || callStatus == "declined"
            val text = when {
                isMissed && isVideo -> "📹 Cevapsız Görüntülü Arama"
                isMissed -> "📞 Cevapsız Sesli Arama"
                isVideo -> "📹 Görüntülü Arama (${durationSeconds} sn)"
                else -> "📞 Sesli Arama (${durationSeconds} sn)"
            }
            val msg = Message(
                messageId = UUID.randomUUID().toString(),
                senderId = callerId,
                receiverId = receiverId,
                chatId = chatId,
                originalText = text,
                translatedText = text,
                originalLanguage = "tr",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                deliveryStatus = "SENT",
                type = "call_log",
                callStatus = callStatus,
                callDurationSeconds = durationSeconds
            )
            sendMessage(chatId, msg)
        } catch (e: Exception) {
            Log.e(TAG, "logCallToChat failed: ${e.message}")
            false
        }
    }

    fun listenForIncomingCalls(userId: String, onCall: (CallSession) -> Unit): Call {
        return listenToPath("incoming_calls/$userId") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("callId")) {
                        val session = gson.fromJson(obj, CallSession::class.java)
                        if (session != null && session.status == "ringing") {
                            onCall(session)
                        }
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val session = gson.fromJson(entry.value, CallSession::class.java)
                                if (session != null && session.status == "ringing") {
                                    onCall(session)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForIncomingCalls parse error: ${e.message}")
            }
        }
    }

    fun listenToCallSession(callId: String, onSessionUpdated: (CallSession) -> Unit): Call {
        return listenToPath("calls/$callId") { rawData ->
            try {
                val data = extractFirebaseData(rawData) ?: return@listenToPath
                if (data.isJsonObject) {
                    val session = gson.fromJson(data.asJsonObject, CallSession::class.java)
                    if (session != null) {
                        onSessionUpdated(session)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenToCallSession parse error: ${e.message}")
            }
        }
    }

    fun listenForMessages(chatId: String, onMessage: (Message) -> Unit): Call {
        return listenToPath("messages/$chatId") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("messageId")) {
                        val message = gson.fromJson(obj, Message::class.java)
                        if (message != null) onMessage(message)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val message = gson.fromJson(entry.value, Message::class.java)
                                if (message != null) onMessage(message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForMessages parse error: ${e.message}")
            }
        }
    }

    fun listenToLastMessages(onLastMessageUpdated: (String, Message) -> Unit): Call {
        return listenToPath("last_messages") { rawData ->
            try {
                val data = extractFirebaseData(rawData) ?: return@listenToPath
                if (data.isJsonObject) {
                    val obj = data.asJsonObject
                    if (obj.has("chatId") && obj.has("messageId")) {
                        val msg = gson.fromJson(obj, Message::class.java)
                        if (msg != null) onLastMessageUpdated(msg.chatId, msg)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val msg = gson.fromJson(entry.value, Message::class.java)
                                if (msg != null) onLastMessageUpdated(entry.key, msg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenToLastMessages parse error: ${e.message}")
            }
        }
    }

    fun listenToUserPresence(userId: String, onPresenceChanged: (status: String, lastSeen: Long) -> Unit): Call {
        return listenToPath("users/$userId") { rawData ->
            try {
                val data = extractFirebaseData(rawData) ?: return@listenToPath
                if (data.isJsonObject) {
                    val obj = data.asJsonObject
                    val status = if (obj.has("status") && !obj.get("status").isJsonNull) obj.get("status").asString else "offline"
                    val lastSeen = if (obj.has("lastSeen") && !obj.get("lastSeen").isJsonNull) obj.get("lastSeen").asLong else 0L
                    onPresenceChanged(status, lastSeen)
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenToUserPresence parse error: ${e.message}")
            }
        }
    }

    fun listenForFriendRequests(userId: String, onRequest: (FriendRequest) -> Unit): Call {
        return listenToPath("friend_requests/$userId") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("senderId")) {
                        val request = gson.fromJson(obj, FriendRequest::class.java)
                        if (request != null) onRequest(request)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val request = gson.fromJson(entry.value, FriendRequest::class.java)
                                if (request != null) onRequest(request)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForFriendRequests parse error: ${e.message}")
            }
        }
    }

    fun listenForIceCandidates(callId: String, onCandidate: (String, String, Int, String) -> Unit): Call {
        return listenToPath("calls/$callId/ice_candidates") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("candidate")) {
                        val cand = obj.get("candidate").asString
                        val sdpMid = if (obj.has("sdpMid") && !obj.get("sdpMid").isJsonNull) obj.get("sdpMid").asString else ""
                        val sdpMLineIndex = if (obj.has("sdpMLineIndex") && !obj.get("sdpMLineIndex").isJsonNull) obj.get("sdpMLineIndex").asInt else 0
                        val senderId = if (obj.has("senderId") && !obj.get("senderId").isJsonNull) obj.get("senderId").asString else ""
                        onCandidate(cand, sdpMid, sdpMLineIndex, senderId)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val item = entry.value.asJsonObject
                                if (item.has("candidate")) {
                                    val cand = item.get("candidate").asString
                                    val sdpMid = if (item.has("sdpMid") && !item.get("sdpMid").isJsonNull) item.get("sdpMid").asString else ""
                                    val sdpMLineIndex = if (item.has("sdpMLineIndex") && !item.get("sdpMLineIndex").isJsonNull) item.get("sdpMLineIndex").asInt else 0
                                    val senderId = if (item.has("senderId") && !item.get("senderId").isJsonNull) item.get("senderId").asString else ""
                                    onCandidate(cand, sdpMid, sdpMLineIndex, senderId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForIceCandidates parse error: ${e.message}")
            }
        }
    }

    fun listenForAnswer(callId: String, onAnswer: (String) -> Unit): Call {
        return listenToPath("calls/$callId/answer") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("sdp")) {
                        onAnswer(obj.get("sdp").asString)
                    }
                } else if (dataElement.isJsonPrimitive) {
                    onAnswer(dataElement.asString)
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForAnswer parse error: ${e.message}")
            }
        }
    }

    fun listenForOffer(callId: String, onOffer: (String) -> Unit): Call {
        return listenToPath("calls/$callId/offer") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("sdp")) {
                        onOffer(obj.get("sdp").asString)
                    }
                } else if (dataElement.isJsonPrimitive) {
                    onOffer(dataElement.asString)
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForOffer parse error: ${e.message}")
            }
        }
    }

    fun listenForTranscripts(callId: String, onSegment: (CallTranscriptSegment) -> Unit): Call {
        return listenToPath("calls/$callId/transcripts") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("speakerId") || obj.has("originalText")) {
                        val segment = gson.fromJson(obj, CallTranscriptSegment::class.java)
                        if (segment != null) onSegment(segment)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val segment = gson.fromJson(entry.value, CallTranscriptSegment::class.java)
                                if (segment != null) onSegment(segment)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForTranscripts parse error: ${e.message}")
            }
        }
    }

    fun listenForCallStatus(callId: String, onStatus: (String) -> Unit): Call {
        return listenToPath("calls/$callId/status") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonPrimitive) {
                    onStatus(dataElement.asString)
                } else if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("status")) {
                        onStatus(obj.get("status").asString)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForCallStatus parse error: ${e.message}")
            }
        }
    }

    // ---- HELPERS ----

    fun generateChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun extractFirebaseData(eventJson: String): JsonElement? {
        return try {
            val root = JsonParser.parseString(eventJson)
            if (root.isJsonObject && root.asJsonObject.has("data")) {
                root.asJsonObject.get("data")
            } else {
                root
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$cleanBase/$cleanPath.json"
    }

    // ---- SEPARATE BLOB NODES (Section 9: user_avatars & voice_blobs) ----

    suspend fun saveUserAvatar(userId: String, avatarBase64: String): Boolean {
        return try {
            val json = gson.toJson(mapOf("avatarBase64" to avatarBase64))
            putJson("user_avatars/$userId", json)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserAvatar failed: ${e.message}")
            false
        }
    }

    suspend fun getUserAvatar(userId: String): String? {
        return try {
            val json = getJson("user_avatars/$userId") ?: return null
            val type = object : TypeToken<Map<String, String>>() {}.type
            val map: Map<String, String>? = gson.fromJson(json, type)
            map?.get("avatarBase64")
        } catch (e: Exception) {
            Log.e(TAG, "getUserAvatar failed: ${e.message}")
            null
        }
    }

    suspend fun saveVoiceBlob(messageId: String, audioBase64: String): Boolean {
        return try {
            val json = gson.toJson(mapOf("audioBase64" to audioBase64))
            putJson("voice_blobs/$messageId", json)
        } catch (e: Exception) {
            Log.e(TAG, "saveVoiceBlob failed: ${e.message}")
            false
        }
    }

    suspend fun getRawData(path: String): String? = getJson(path)

    suspend fun updateUserStats(userId: String, stats: Map<String, Any>): Boolean {
        return try {
            val statsJson = gson.toJson(stats)
            patchJson("users/$userId/stats", statsJson)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStats failed: ${e.message}")
            false
        }
    }

    suspend fun saveUserBadge(userId: String, badgeId: String, unlockedAt: Long): Boolean {
        return try {
            val payload = mapOf("id" to badgeId, "unlockedAt" to unlockedAt)
            val json = gson.toJson(payload)
            putJson("users/$userId/badges/$badgeId", json)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserBadge failed: ${e.message}")
            false
        }
    }

    suspend fun getVoiceBlob(messageId: String): String? {
        return try {
            val json = getJson("voice_blobs/$messageId") ?: return null
            val type = object : TypeToken<Map<String, String>>() {}.type
            val map: Map<String, String>? = gson.fromJson(json, type)
            map?.get("audioBase64")
        } catch (e: Exception) {
            Log.e(TAG, "getVoiceBlob failed: ${e.message}")
            null
        }
    }

    private suspend fun putJson(path: String, json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).put(body).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "putJson failed for $path: ${e.message}")
            false
        }
    }

    private suspend fun patchJson(path: String, json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).patch(body).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "patchJson failed for $path: ${e.message}")
            false
        }
    }

    suspend fun getAppUpdateManifest(): String? {
        return getJson("app_updates/latest")
    }

    private suspend fun getJson(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val str = response.body?.string()?.trim()
                    if (str.isNullOrEmpty() || str == "null") null else str
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getJson failed for $path: ${e.message}")
            null
        }
    }

    private suspend fun deleteJson(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val request = Request.Builder().url(url).delete().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteJson failed for $path: ${e.message}")
            false
        }
    }
}
