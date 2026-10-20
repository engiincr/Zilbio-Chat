package com.translive.app

import android.app.Application
import android.util.Log
import com.translive.app.data.remote.GeminiTranslationEngine

class TransLiveApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize ML Kit translation models for TR, EN, DE
        try {
            GeminiTranslationEngine.initMlKit(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit translation models at startup", e)
        }
    }

    companion object {
        private const val TAG = "TransLiveApp"
    }
}
