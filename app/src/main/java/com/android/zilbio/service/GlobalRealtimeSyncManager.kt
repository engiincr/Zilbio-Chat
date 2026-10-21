package com.android.zilbio.service

import android.content.Context
import android.util.Log
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.domain.model.CallSession
import com.android.zilbio.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call

object GlobalRealtimeSyncManager {
    private const val TAG = "GlobalRealtimeSync"

    // Currently opened chat room ID (Item #12 Smart Suppression Rule)
    var currentlyActiveChatId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeCalls = mutableListOf<Call>()
    private var pollingJob: Job? = null

    private val _incomingCallState = MutableStateFlow<CallSession?>(null)
    val incomingCallState: StateFlow<CallSession?> = _incomingCallState.asStateFlow()

    // Hot In-Memory Caches for Zero-Lag UI everywhere (Phase 5)
    val friendsFlow = MutableStateFlow<List<Friend>>(emptyList())
    val lastMessagesFlow = MutableStateFlow<Map<String, Message>>(emptyMap())

    private var currentUserId: String? = null
    private val lastSeenMessageTimestamps = mutableMapOf<String, Long>()

    fun startSync(context: Context, userId: String) {
        if (currentUserId == userId && activeCalls.isNotEmpty()) {
            return // Already syncing for this user
        }
        stopSync()
        currentUserId = userId

        ZilbioNotificationService.initChannels(context)

        // Pre-warm in-memory friend cache
        scope.launch {
            try {
                val friends = CloudRealtimeService.getFriends(userId)
                friendsFlow.value = friends
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load initial friends cache: ${e.message}")
            }
        }

        // 1. Listen for Incoming Calls via SSE
        val callListener = CloudRealtimeService.listenForIncomingCalls(userId) { session ->
            if (session.status == "ringing" && session.receiverId == userId) {
                handleIncomingCall(context, userId, session)
            }
        }
        activeCalls.add(callListener)

        // 2. High-reliability 1.5s Polling Fallback for Incoming Calls
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(1500L)
                try {
                    if (_incomingCallState.value == null) {
                        val activeCalls = CloudRealtimeService.getActiveIncomingCalls(userId)
                        val ringing = activeCalls.firstOrNull { it.status == "ringing" }
                        if (ringing != null && _incomingCallState.value == null) {
                            Log.i(TAG, "Polling backup detected incoming call: ${ringing.callId} from ${ringing.callerName}")
                            handleIncomingCall(context, userId, ringing)
                        }
                    }
                } catch (e: Exception) {
                    // Polling network blip ignored
                }
            }
        }

        // 3. Listen for Last Messages across all chats (Global Sync & Notifications)
        val msgListener = CloudRealtimeService.listenToLastMessages { chatId, message ->
            val cur = lastMessagesFlow.value.toMutableMap()
            cur[chatId] = message
            lastMessagesFlow.value = cur
            handleIncomingLastMessage(context, userId, chatId, message)
        }
        activeCalls.add(msgListener)

        // 4. Mark User Status as Online
        scope.launch {
            CloudRealtimeService.updateUserPresence(userId, true)
        }
    }

    private fun handleIncomingCall(context: Context, userId: String, session: CallSession) {
        if (_incomingCallState.value?.callId == session.callId) {
            return // Already handling this call
        }
        scope.launch {
            try {
                val privacySettings = UserPreferences.getPrivacySettings(context).first()
                if (privacySettings.silenceNonContactCalls) {
                    val friends = CloudRealtimeService.getFriends(userId)
                    val isFriend = friends.any { it.userId == session.callerId }
                    if (!isFriend) {
                        Log.i(TAG, "Silencing call from non-contact ${session.callerId} according to privacy settings.")
                        CloudRealtimeService.saveCallHistoryItem(
                            userId,
                            CallHistoryItem(
                                callId = session.callId,
                                peerId = session.callerId,
                                peerName = session.callerName,
                                isIncoming = true,
                                isVideo = session.isVideo,
                                callStatus = "missed",
                                durationSeconds = 0
                            )
                        )
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking privacy settings: ${e.message}")
            }

            Log.i(TAG, "Incoming call received from ${session.callerName} (${session.callId})")
            _incomingCallState.value = session
            ZilbioNotificationService.startIncomingCallAlert(context, session) {
                _incomingCallState.value = null
            }
        }
    }

    private fun handleIncomingLastMessage(
        context: Context,
        userId: String,
        chatId: String,
        message: Message
    ) {
        // Only act on messages sent by friends to me
        if (message.senderId == userId || message.senderId.isEmpty()) return

        val prevTimestamp = lastSeenMessageTimestamps[chatId] ?: 0L
        if (message.timestamp <= prevTimestamp && prevTimestamp != 0L) {
            return // Skip old messages
        }
        lastSeenMessageTimestamps[chatId] = message.timestamp

        // Smart Suppression Rule: If currently active inside this chat room, do NOT show banner notification
        if (currentlyActiveChatId == chatId) {
            Log.d(TAG, "Message received for active chat $chatId; suppressing banner notification.")
            return
        }

        // Outside this chat -> Fire heads-up notification
        scope.launch {
            val sender = CloudRealtimeService.searchUserById(message.senderId)
            val senderName = sender?.displayName?.ifEmpty { sender.username } ?: "Arkadaşın"

            // Mark message as DELIVERED in Firebase
            if (message.deliveryStatus != "DELIVERED" && message.deliveryStatus != "READ") {
                CloudRealtimeService.updateDeliveryStatus(chatId, message.messageId, "DELIVERED")
            }

            ZilbioNotificationService.showMessageNotification(
                context = context,
                message = message,
                senderDisplayName = senderName
            )
        }
    }

    fun dismissIncomingCall(context: Context) {
        _incomingCallState.value = null
        ZilbioNotificationService.stopIncomingCallAlert(context)
    }

    fun clearIncomingCall() {
        _incomingCallState.value = null
    }

    fun stopSync() {
        pollingJob?.cancel()
        pollingJob = null
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        currentUserId?.let { uid ->
            scope.launch {
                CloudRealtimeService.updateUserPresence(uid, false)
            }
        }
    }
}
