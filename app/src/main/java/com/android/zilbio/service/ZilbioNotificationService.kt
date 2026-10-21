package com.android.zilbio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.zilbio.MainActivity
import com.android.zilbio.R
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.domain.model.CallSession
import com.android.zilbio.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ZilbioNotificationService {
    private const val TAG = "ZilbioNotification"

    const val CHANNEL_MESSAGES = "zilbio_messages_channel"
    const val CHANNEL_CALLS = "zilbio_calls_channel"
    const val CHANNEL_UPDATES = "zilbio_updates_channel"
    const val CHANNEL_FRIEND_REQUESTS = "zilbio_friend_requests_channel"

    private const val CALL_NOTIFICATION_ID = 2001
    private const val MESSAGE_BASE_NOTIFICATION_ID = 3000
    private const val UPDATE_NOTIFICATION_ID = 4001
    private const val FRIEND_REQUEST_BASE_NOTIFICATION_ID = 5000

    private var activeRingtone: Ringtone? = null
    private var callTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Messages Channel (Heads-up banner)
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Zilbio Mesajlar ve Tepkiler",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen mesajlar ve emoji tepkileri bildirimleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(msgChannel)

            // Calls Channel (High priority with default system ringtone)
            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Zilbio Gelen Çağrılar",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Gelen sesli ve görüntülü arama bildirimleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                setSound(defaultRingtoneUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // Application Updates Channel (Item #4 & #8)
            val updateChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "Zilbio Güncellemeler",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Yeni sürüm ve güncelleme bildirimleri"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(updateChannel)

            // Friend Requests Channel
            val friendRequestChannel = NotificationChannel(
                CHANNEL_FRIEND_REQUESTS,
                "Zilbio Arkadaşlık İstekleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen arkadaşlık istekleri bildirimleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(friendRequestChannel)
        }
    }

    fun showFriendRequestNotification(
        context: Context,
        senderId: String,
        senderUsername: String,
        senderDisplayName: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_friends", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = senderDisplayName.ifBlank { "@$senderUsername" }
        val notification = NotificationCompat.Builder(context, CHANNEL_FRIEND_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .setContentTitle("Yeni Arkadaşlık İsteği")
            .setContentText("$displayName size arkadaşlık isteği gönderdi.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = FRIEND_REQUEST_BASE_NOTIFICATION_ID + (senderId.hashCode() % 1000).let { if (it < 0) -it else it }
        notificationManager.notify(notificationId, notification)
        Log.i(TAG, "Friend request notification shown for sender $senderUsername ($senderId)")
    }

    fun showUpdateNotification(
        context: Context,
        versionName: String,
        versionCode: Int,
        releaseNotes: String? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_settings", true)
            putExtra("update_version_code", versionCode)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Zilbio güncellemesi mevcut"
        val content = if (releaseNotes.isNullOrBlank()) {
            "Yeni bir sürüm yayınlandı (v$versionName). Güncellemeyi indirmek için dokunun."
        } else {
            "v$versionName yayınlandı: $releaseNotes"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
        Log.i(TAG, "Update notification displayed for v$versionName (code $versionCode)")
    }

    fun clearUpdateNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

    fun showMessageNotification(
        context: Context,
        message: Message,
        senderDisplayName: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", message.chatId)
            putExtra("sender_id", message.senderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            message.chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val textPreview = when (message.type) {
            "voice" -> "🎤 Sesli mesaj (${message.audioDurationMs / 1000} sn)"
            "image" -> "📷 Fotoğraf"
            "video" -> "📹 Video"
            "document" -> "📄 Belge"
            else -> message.translatedText.ifEmpty { message.originalText }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle(senderDisplayName)
            .setContentText(textPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(textPreview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notifId = MESSAGE_BASE_NOTIFICATION_ID + (message.chatId.hashCode() % 1000)
        notificationManager.notify(notifId, notification)
    }

    fun showReactionNotification(
        context: Context,
        chatId: String,
        senderDisplayName: String,
        messagePreview: String,
        emoji: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val notificationText = "$senderDisplayName, '${messagePreview.take(30)}' mesajına $emoji tepkisi verdi"

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle("Yeni Tepki")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        notificationManager.notify(chatId.hashCode() + 2, notification)
    }

    fun startIncomingCallAlert(
        context: Context,
        callSession: CallSession,
        onTimeout: () -> Unit
    ) {
        stopIncomingCallAlert(context)

        // 1. Play default system ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            activeRingtone = RingtoneManager.getRingtone(context, ringtoneUri).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone: ${e.message}")
        }

        // 2. Trigger vibration
        try {
            val pattern = longArrayOf(0, 1000, 1000, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1))
                } else {
                    vibrator.vibrate(pattern, 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}")
        }

        // 3. Show Heads-up Notification with Fullscreen intent
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val callIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_call_id", callSession.callId)
            putExtra("caller_id", callSession.callerId)
            putExtra("caller_name", callSession.callerName)
            putExtra("is_video", callSession.isVideo)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            CALL_NOTIFICATION_ID,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val callTitle = if (callSession.isVideo) "Gelen Görüntülü Arama" else "Gelen Sesli Arama"
        val callSubtitle = "${callSession.callerName} arıyor..."

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle(callTitle)
            .setContentText(callSubtitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(CALL_NOTIFICATION_ID, notification)

        // 4. Strict 30-second timeout auto-dismiss (Item #2)
        callTimeoutJob = scope.launch {
            delay(30_000L)
            Log.i(TAG, "Incoming call 30s timeout expired. Dismissing and logging missed call.")
            stopIncomingCallAlert(context)

            // Save Missed Call to Firebase
            val historyItem = CallHistoryItem(
                callId = callSession.callId,
                peerId = callSession.callerId,
                peerName = callSession.callerName,
                isIncoming = true,
                isVideo = callSession.isVideo,
                isConference = callSession.isConference,
                callStatus = "missed",
                durationSeconds = 0L,
                timestamp = System.currentTimeMillis()
            )
            CloudRealtimeService.saveCallHistoryItem(callSession.receiverId, historyItem)
            CloudRealtimeService.endCallWithSignal(callSession.callId, callSession.receiverId, callSession.receiverId)
            val chatId = CloudRealtimeService.generateChatId(callSession.callerId, callSession.receiverId)
            CloudRealtimeService.logCallToChat(chatId, callSession.callerId, callSession.receiverId, callSession.isVideo, "missed", 0L)

            onTimeout()
        }
    }

    fun stopIncomingCallAlert(context: Context) {
        callTimeoutJob?.cancel()
        callTimeoutJob = null

        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration: ${e.message}")
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
}
