package com.android.zilbio.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.zilbio.data.local.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    data class Checking(val isManual: Boolean) : UpdateState()
    data class UpdateAvailable(
        val latestSha: String,
        val commitMessage: String,
        val commitDate: String,
        val downloadUrl: String,
        val isManual: Boolean
    ) : UpdateState()
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val latestSha: String) : UpdateState()
    data class UpToDate(val currentSha: String, val lastChecked: Long) : UpdateState()
    data class Error(val message: String, val isManual: Boolean) : UpdateState()
}

object GitHubAutoUpdater {
    private const val TAG = "GitHubAutoUpdater"

    const val GITHUB_REPO_URL = "https://github.com/engiincr/Zilbio-Chat"
    const val GITHUB_COMMITS_API = "https://api.github.com/repos/engiincr/Zilbio-Chat/commits/main"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/engiincr/Zilbio-Chat/releases/latest"
    const val GITHUB_LATEST_APK_URL = "https://github.com/engiincr/Zilbio-Chat/releases/download/latest/Zilbio-latest.apk"
    const val GITHUB_MAIN_ZIP_URL = "https://github.com/engiincr/Zilbio-Chat/archive/refs/heads/main.zip"
    val FIREBASE_CONFIG_URL: String get() = "${com.android.zilbio.util.ApiConfig.cloudDbUrl}/app_config.json"
    const val DEFAULT_VERSION_NAME = "V3.0.2"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _dynamicConfig = MutableStateFlow<Map<String, Any>>(emptyMap())
    val dynamicConfig: StateFlow<Map<String, Any>> = _dynamicConfig.asStateFlow()

    /**
     * Checks for new commit SHA or Release on GitHub repository.
     * If an update is detected, automatically downloads and initiates seamless installation.
     */
    fun checkForUpdates(context: Context, manual: Boolean = false, autoDownload: Boolean = false) {
        if (_isDownloading.value) {
            if (manual) {
                Toast.makeText(context, "Güncelleme zaten indiriliyor...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        _updateState.value = UpdateState.Checking(manual)

        scope.launch {
            try {
                // 1. Sync dynamic runtime configs (Zero-install Hot Reload)
                syncDynamicConfig(context)

                // 2. Fetch latest commit info from GitHub API
                val request = Request.Builder()
                    .url(GITHUB_COMMITS_API)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Zilbio-Android-App")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errMsg = "GitHub API yanıt vermedi: HTTP ${response.code}"
                    _updateState.value = UpdateState.Error(errMsg, manual)
                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val latestSha = json.optString("sha", "")
                val commitObj = json.optJSONObject("commit")
                val commitMessage = commitObj?.optString("message", "Güncelleme") ?: "Yeni sürüm"
                val committerObj = commitObj?.optJSONObject("committer")
                val commitDate = committerObj?.optString("date", "") ?: ""

                val installedSha = UserPreferences.getInstalledCommitSha(context).firstOrNull() ?: ""

                Log.d(TAG, "Installed SHA: $installedSha, Remote Latest SHA: $latestSha")

                if (latestSha.isNotEmpty() && (installedSha.isEmpty() || installedSha != latestSha)) {
                    // Update Available
                    _updateState.value = UpdateState.UpdateAvailable(
                        latestSha = latestSha,
                        commitMessage = commitMessage,
                        commitDate = commitDate,
                        downloadUrl = GITHUB_LATEST_APK_URL,
                        isManual = manual
                    )

                    if (autoDownload) {
                        startDownload(context, GITHUB_LATEST_APK_URL, latestSha)
                    } else if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Yeni güncelleme bulundu: ${latestSha.take(7)}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    _updateState.value = UpdateState.UpToDate(
                        currentSha = if (installedSha.isNotEmpty()) installedSha else latestSha,
                        lastChecked = System.currentTimeMillis()
                    )
                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Uygulamanız güncel ($DEFAULT_VERSION_NAME)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                val msg = "Güncelleme kontrolü başarısız: ${e.localizedMessage ?: "Bağlantı hatası"}"
                _updateState.value = UpdateState.Error(msg, manual)
                if (manual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Silent background download of the latest APK with progress tracking.
     */
    fun startDownload(context: Context, downloadUrl: String = GITHUB_LATEST_APK_URL, latestSha: String) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0

        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Zilbio güncellemesi indiriliyor...", Toast.LENGTH_SHORT).show()
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Zilbio-Android-App")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _isDownloading.value = false
                    _updateState.value = UpdateState.Error("APK indirilemedi: HTTP ${response.code}", true)
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    _isDownloading.value = false
                    _updateState.value = UpdateState.Error("Boş dosya yanıtı alındı", true)
                    return@launch
                }

                val totalBytes = body.contentLength()
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "Zilbio-latest.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            _downloadProgress.value = progress
                            _updateState.value = UpdateState.Downloading(progress, downloadedBytes, totalBytes)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _isDownloading.value = false
                _downloadProgress.value = 100
                _updateState.value = UpdateState.ReadyToInstall(apkFile, latestSha)

                Log.d(TAG, "Download completed: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Güncelleme indirildi, kuruluyor...", Toast.LENGTH_SHORT).show()
                    installApk(context, apkFile, latestSha)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _isDownloading.value = false
                _updateState.value = UpdateState.Error("İndirme hatası: ${e.localizedMessage}", true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Güncelleme indirilemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Triggers the Android PackageInstaller to seamlessly install the APK.
     */
    fun installApk(context: Context, apkFile: File, latestSha: String = "") {
        try {
            if (!apkFile.exists() || apkFile.length() < 1024) {
                Toast.makeText(context, "Hata: İndirilen dosya geçersiz", Toast.LENGTH_SHORT).show()
                return
            }

            scope.launch {
                UserPreferences.setInstalledCommitSha(context, latestSha)
            }

            // Android 8.0+ Unknown sources check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Lütfen Zilbio için güncelleme yükleme izni verin", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Kurulum başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Synchronizes dynamic live configurations (Zero-Install Hot Reload).
     */
    suspend fun syncDynamicConfig(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(FIREBASE_CONFIG_URL)
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val raw = resp.body?.string() ?: ""
                    if (raw.isNotBlank() && raw != "null") {
                        val json = JSONObject(raw)
                        val map = mutableMapOf<String, Any>()
                        json.keys().forEach { k ->
                            map[k] = json.get(k)
                        }
                        _dynamicConfig.value = map
                        Log.d(TAG, "Dynamic config synchronized: ${map.keys}")
                    }
                }
            } catch (e: Exception) {
                // Silently fallback to bundled config
            }
        }
    }
}
