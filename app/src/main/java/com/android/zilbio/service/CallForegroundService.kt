package com.android.zilbio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.zilbio.R

class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        const val ACTION_START = "START_CALL_SERVICE"
        const val ACTION_STOP = "STOP_CALL_SERVICE"
        const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "call_channel"
        private const val WAKE_LOCK_TAG = "zilbio:call_foreground_wakelock"
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
        Log.d(TAG, "onStartCommand action=$action, isVideo=$isVideo")

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stopping foreground service")
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                Log.i(TAG, "Starting foreground service (isVideo=$isVideo)")
                acquireWakeLock()
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fgsType = if (isVideo) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L /* 4 hours max */)
                Log.d(TAG, "Call WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Call WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zilbio - Canlı Görüşme")
            .setContentText("Sesli görüşme ve canlı çeviri aktif")
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Zilbio Görüşme Servisi"
            val descriptionText = "Canlı sesli görüşme ve anlık çeviri bildirimi"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
