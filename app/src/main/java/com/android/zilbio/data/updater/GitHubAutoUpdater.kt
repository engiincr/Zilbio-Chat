package com.android.zilbio.data.updater

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Backward compatibility facade delegating to AppUpdateManager.
 */
object GitHubAutoUpdater {
    const val GITHUB_REPO_URL = "https://github.com/engiincr/Zilbio-Chat"
    const val GITHUB_MAIN_ZIP_URL = "https://github.com/engiincr/Zilbio-Chat/archive/refs/heads/main.zip"
    const val DEFAULT_VERSION_NAME = "V3.0.5"

    val updateState: StateFlow<UpdateState> get() = AppUpdateManager.updateState
    val isDownloading: StateFlow<Boolean> get() = AppUpdateManager.isDownloading
    val downloadProgress: StateFlow<Int> get() = AppUpdateManager.downloadProgress

    fun checkForUpdates(context: Context, manual: Boolean = false, autoDownload: Boolean = false) {
        AppUpdateManager.checkForUpdates(context, manual, autoDownload)
    }

    fun startDownload(context: Context, downloadUrl: String? = null, latestSha: String = "") {
        val manifest = AppUpdateManager.latestManifest.value ?: UpdateManifest(
            versionCode = 6,
            versionName = "3.0.5",
            commitSha = latestSha,
            downloadUrl = downloadUrl ?: "https://github.com/engiincr/Zilbio-Chat/releases/download/v3.0.5/Zilbio-latest.apk"
        )
        AppUpdateManager.startDownload(context, manifest)
    }

    fun installApk(context: Context, apkFile: File, latestSha: String = "") {
        AppUpdateManager.installApk(context, apkFile)
    }
}
