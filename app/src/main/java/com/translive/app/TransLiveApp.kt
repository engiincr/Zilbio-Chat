package com.translive.app

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
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

        // Firebase Cloud Messaging (FCM) Token'ını al ve arka plan servisini tetikle
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Registration Token: $token")
        }
    }

    companion object {
        private const val TAG = "TransLiveApp"
    }
}
