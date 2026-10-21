package com.android.zilbio.data.updater

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.service.ZilbioNotificationService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "AppUpdateWorker executing periodic background update check...")
        return try {
            val manifest = AppUpdateManager.checkUpdateSilently(applicationContext)
            if (manifest != null) {
                val lastNotified = UserPreferences.getLastNotifiedVersionCode(applicationContext).first()
                if (manifest.versionCode > lastNotified) {
                    Log.i(TAG, "New version found in background check: v${manifest.versionName} (code ${manifest.versionCode}). Notifying user.")
                    ZilbioNotificationService.showUpdateNotification(
                        context = applicationContext,
                        versionName = manifest.versionName,
                        versionCode = manifest.versionCode,
                        releaseNotes = manifest.releaseNotes
                    )
                    UserPreferences.setLastNotifiedVersionCode(applicationContext, manifest.versionCode)
                } else {
                    Log.d(TAG, "New version v${manifest.versionName} was already notified to user (lastNotified=$lastNotified). Skipping duplicate alert.")
                }
            } else {
                Log.d(TAG, "Background check complete: App is up to date.")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Periodic update check encountered error: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        const val WORK_NAME_HOURLY_CHECK = "zilbio_hourly_update_check"

        fun scheduleHourlyPeriodicCheck(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_HOURLY_CHECK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.i(TAG, "Scheduled hourly periodic update check with WorkManager (Policy: KEEP)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic update check: ${e.message}", e)
            }
        }
    }
}
