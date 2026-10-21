package com.android.zilbio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ZilbioDaemonRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: "UNKNOWN"
        Log.i(TAG, "Daemon restart event received: action=$action. Reviving ZilbioPersistentDaemonService...")
        ZilbioPersistentDaemonService.start(context)
    }

    companion object {
        private const val TAG = "ZilbioRestartReceiver"
    }
}
