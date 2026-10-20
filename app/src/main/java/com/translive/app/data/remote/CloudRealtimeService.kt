package com.translive.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.translive.app.domain.model.CallSession
import com.translive.app.domain.model.CallTranscriptSegment
import com.translive.app.domain.model.Friend
import com.translive.app.domain.model.FriendRequest
import com.translive.app.domain.model.Message
import com.translive.app.domain.model.User
import com.translive.app.util.ApiConfig
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
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = ApiConfig.CLOUD_DB_URL

    // ---- USER OPERATIONS ----

    suspend fun registerUser(user: User): Boolean {
        return try {
            val userJson = gson.toJson(user)
            val putUser = putJson("users/${user.userId}", userJson)
            if (!putUser) return false

            val usernameKey = user.username.lowercase().trim()
            val userIdJson = gson.toJson(user.userId)
            putJson("usernames/$usernameKey", userIdJson)
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
            if (user.passwordHash == passwordHash) user else null
        } catch (e: Exception) {
            Log.e(TAG, "loginUser failed: ${e.message}", e)
            null
        }
    }

    suspend fun searchUserByUsername(username: String): User? {
        return try {
            val usernameKey = username.lowercase().trim()
            val userIdJson = getJson("usernames/$usernameKey") ?: return null
            val userId = gson.fromJson(userIdJson, String::class.java) ?: return null
            searchUserById(userId)
        } catch (e: Exception) {
            Log.e(TAG, "searchUserByUsername failed: ${e.message}", e)
            null
        }
    }

    suspend fun searchUserById(userId: String): User? {
        return try {
            val userJson = getJson("users/$userId") ?: return null
            gson.fromJson(userJson, User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "searchUserById failed: ${e.message}", e)
            null
        }
    }

    suspend fun updateUserStatus(userId: String, status: String) {
        try {
            val patchObj = JsonObject().apply { addProperty("status", status) }
            patchJson("users/$userId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStatus failed: ${e.message}", e)
        }
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        return try {
            val usernameKey = username.lowercase().trim()
            val existing = getJson("usernames/$usernameKey")
            existing == null || existing == "null"
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailable failed: ${e.message}", e)
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
            val type = object : TypeToken<Map<String, FriendRequest>>() {}.type
            val map: Map<String, FriendRequest>? = gson.fromJson(json, type)
            map?.values?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getIncomingRequests failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getSentRequests(userId: String): List<FriendRequest> {
        return try {
            val json = getJson("sent_requests/$userId") ?: return emptyList()
            val type = object : TypeToken<Map<String, FriendRequest>>() {}.type
            val map: Map<String, FriendRequest>? = gson.fromJson(json, type)
            map?.values?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getSentRequests failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFriends(userId: String): List<Friend> {
        return try {
            val json = getJson("friends/$userId") ?: return emptyList()
            val type = object : TypeToken<Map<String, Friend>>() {}.type
            val map: Map<String, Friend>? = gson.fromJson(json, type)
            map?.values?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getFriends failed: ${e.message}", e)
            emptyList()
        }
    }

    // ---- MESSAGE OPERATIONS ----

    suspend fun sendMessage(chatId: String, message: Message): Boolean {
        return try {
            val messageJson = gson.toJson(message)
            val putMessage = putJson("messages/$chatId/${message.messageId}", messageJson)
            val putLast = putJson("last_messages/$chatId", messageJson)
            putMessage && putLast
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}", e)
            false
        }
    }

    suspend fun getMessages(chatId: String): List<Message> {
        return try {
            val json = getJson("messages/$chatId") ?: return emptyList()
            val type = object : TypeToken<Map<String, Message>>() {}.type
            val map: Map<String, Message>? = gson.fromJson(json, type)
            map?.values?.sortedBy { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getMessages failed: ${e.message}", e)
            emptyList()
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
            val patchObj = JsonObject().apply { addProperty("status", "active") }
            patchJson("calls/$callId", patchObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "answerCall failed: ${e.message}", e)
        }
    }

    suspend fun endCall(callId: String, receiverId: String) {
        try {
            val patchObj = JsonObject().apply { addProperty("status", "ended") }
            patchJson("calls/$callId", patchObj.toString())
            deleteJson("incoming_calls/$receiverId/$callId")
        } catch (e: Exception) {
            Log.e(TAG, "endCall failed: ${e.message}", e)
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

    suspend fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        try {
            val key = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val candidateObj = JsonObject().apply {
                addProperty("candidate", candidate)
                addProperty("sdpMid", sdpMid)
                addProperty("sdpMLineIndex", sdpMLineIndex)
            }
            putJson("calls/$callId/ice_candidates/$key", candidateObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendIceCandidate failed: ${e.message}", e)
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

    // ---- SSE LISTENERS ----

    fun listenToPath(path: String, onEvent: (String) -> Unit): Call {
        val url = buildUrl(path)
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .build()

        val call = sseClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    Log.e(TAG, "SSE onFailure for path $path: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
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
                        if (!call.isCanceled()) {
                            Log.e(TAG, "SSE read error for $path: ${e.message}")
                        }
                    } finally {
                        responseBody.close()
                    }
                }
            }
        })
        return call
    }

    fun listenForIncomingCalls(userId: String, onCall: (CallSession) -> Unit): Call {
        return listenToPath("incoming_calls/$userId") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("callId")) {
                        val session = gson.fromJson(obj, CallSession::class.java)
                        if (session != null && session.status == "ringing") onCall(session)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val session = gson.fromJson(entry.value, CallSession::class.java)
                                if (session != null && session.status == "ringing") onCall(session)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenForIncomingCalls parse error: ${e.message}")
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

    fun listenForIceCandidates(callId: String, onCandidate: (String, String, Int) -> Unit): Call {
        return listenToPath("calls/$callId/ice_candidates") { rawData ->
            try {
                val dataElement = extractFirebaseData(rawData) ?: return@listenToPath
                if (dataElement.isJsonObject) {
                    val obj = dataElement.asJsonObject
                    if (obj.has("candidate")) {
                        val cand = obj.get("candidate").asString
                        val sdpMid = if (obj.has("sdpMid") && !obj.get("sdpMid").isJsonNull) obj.get("sdpMid").asString else ""
                        val sdpMLineIndex = if (obj.has("sdpMLineIndex") && !obj.get("sdpMLineIndex").isJsonNull) obj.get("sdpMLineIndex").asInt else 0
                        onCandidate(cand, sdpMid, sdpMLineIndex)
                    } else {
                        for (entry in obj.entrySet()) {
                            if (entry.value.isJsonObject) {
                                val item = entry.value.asJsonObject
                                if (item.has("candidate")) {
                                    val cand = item.get("candidate").asString
                                    val sdpMid = if (item.has("sdpMid") && !item.get("sdpMid").isJsonNull) item.get("sdpMid").asString else ""
                                    val sdpMLineIndex = if (item.has("sdpMLineIndex") && !item.get("sdpMLineIndex").isJsonNull) item.get("sdpMLineIndex").asInt else 0
                                    onCandidate(cand, sdpMid, sdpMLineIndex)
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
                    if (obj.has("sdp")) onAnswer(obj.get("sdp").asString)
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
                    if (obj.has("sdp")) onOffer(obj.get("sdp").asString)
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
                    if (obj.has("status")) onStatus(obj.get("status").asString)
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
            // Fallback for fallback catch
            false
        }
    }
}
