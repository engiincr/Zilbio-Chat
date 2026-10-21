package com.android.zilbio.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.zilbio.MainActivity
import com.android.zilbio.R
import com.android.zilbio.data.local.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ZilbioPersistentDaemonService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZilbioPersistentDaemonService created.")
        createDaemonNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ZilbioPersistentDaemonService onStartCommand (startId=$startId).")

        // Start Foreground Service with silent low-importance notification to prevent OS kill
        startForeground(DAEMON_NOTIFICATION_ID, buildDaemonNotification())

        serviceScope.launch {
            try {
                val user = UserPreferences.getUserSync(applicationContext)
                if (user != null && user.userId.isNotBlank()) {
                    Log.i(TAG, "Starting persistent SSE sync for user: ${user.username} (${user.userId})")
                    GlobalRealtimeSyncManager.startSync(applicationContext, user.userId)
                } else {
                    Log.d(TAG, "No logged-in user found yet for daemon sync.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in daemon sync startup: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "App task removed by user. Scheduling instant daemon revival...")
        scheduleDaemonRevival()
    }

    override fun onDestroy() {
        Log.w(TAG, "ZilbioPersistentDaemonService being destroyed. Triggering revival...")
        releaseWakeLock()
        scheduleDaemonRevival()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun scheduleDaemonRevival() {
        try {
            val restartIntent = Intent(applicationContext, ZilbioDaemonRestartReceiver::class.java).apply {
                action = ACTION_RESTART_DAEMON
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAtMillis = SystemClock.elapsedRealtime() + 1000L // 1 second later

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Daemon revival scheduled via AlarmManager for +1000ms.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule daemon revival alarm: ${e.message}")
        }
    }

    private fun createDaemonNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                DAEMON_CHANNEL_ID,
                "Zilbio Arka Plan Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zilbio gelen aramalar ve mesajlar için arka planda aktif"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildDaemonNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, DAEMON_CHANNEL_ID)
            .setContentTitle("Zilbio")
            .setContentText("Aramalar ve mesajlar için hazır")
            .setSmallIcon(R.drawable.ic_notification_zilbio)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Zilbio::DaemonWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 1000L) // 10 minutes max window before re-acquire
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring daemon WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing daemon WakeLock: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ZilbioDaemonService"
        const val DAEMON_CHANNEL_ID = "zilbio_daemon_channel"
        const val DAEMON_NOTIFICATION_ID = 1001
        const val RESTART_REQUEST_CODE = 1099
        const val ACTION_RESTART_DAEMON = "com.android.zilbio.RESTART_DAEMON"

        fun start(context: Context) {
            try {
                val intent = Intent(context, ZilbioPersistentDaemonService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "Started ZilbioPersistentDaemonService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ZilbioPersistentDaemonService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ZilbioPersistentDaemonService::class.java)
                context.stopService(intent)
                Log.i(TAG, "Stopped ZilbioPersistentDaemonService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ZilbioPersistentDaemonService: ${e.message}")
            }
        }
    }
}
