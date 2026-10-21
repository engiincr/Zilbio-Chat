package com.android.zilbio

import android.app.Application
import android.util.Log
import com.android.zilbio.data.remote.GeminiTranslationEngine

class ZilbioApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize ML Kit translation models for TR, EN, DE
        try {
            GeminiTranslationEngine.initMlKit(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit translation models at startup", e)
        }

        try {
            com.android.zilbio.service.ZilbioNotificationService.initChannels(this)
            com.android.zilbio.service.ZilbioPersistentDaemonService.start(this)
            com.android.zilbio.data.updater.AppUpdateWorker.scheduleHourlyPeriodicCheck(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize background services at startup", e)
        }
    }

    companion object {
        private const val TAG = "ZilbioApp"
    }
}
