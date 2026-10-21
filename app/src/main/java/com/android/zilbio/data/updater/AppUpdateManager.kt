package com.android.zilbio.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.zilbio.BuildConfig
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.service.ZilbioNotificationService
import com.android.zilbio.util.ApiConfig
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val commitSha: String = "",
    val downloadUrl: String,
    val sha256: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val updatedAt: Long = 0L
)

sealed class UpdateState {
    object Idle : UpdateState()
    data class Checking(val isManual: Boolean) : UpdateState()
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isManual: Boolean
    ) : UpdateState()
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(
        val apkFile: File,
        val manifest: UpdateManifest
    ) : UpdateState()
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Int,
        val lastChecked: Long
    ) : UpdateState()
    data class Error(val message: String, val isManual: Boolean) : UpdateState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    const val GITHUB_REPO = "engiincr/Zilbio-Chat"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/engiincr/Zilbio-Chat/releases/latest"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _latestManifest = MutableStateFlow<UpdateManifest?>(null)
    val latestManifest: StateFlow<UpdateManifest?> = _latestManifest.asStateFlow()

    /**
     * Checks for updates from Firebase RTDB OTA manifest with GitHub Releases fallback.
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
                UserPreferences.setLastUpdateCheckTime(context, System.currentTimeMillis())

                val manifest = fetchUpdateManifest()
                if (manifest == null) {
                    val errMsg = "Güncelleme sunucusuna ulaşılamadı. Lütfen internetinizi kontrol edin."
                    _updateState.value = UpdateState.Error(errMsg, manual)
                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                _latestManifest.value = manifest
                val currentVersionCode = BuildConfig.VERSION_CODE
                val currentVersionName = BuildConfig.VERSION_NAME

                Log.i(TAG, "Version comparison: Installed code=$currentVersionCode ($currentVersionName), Remote code=${manifest.versionCode} (${manifest.versionName})")

                val isNewer = manifest.versionCode > currentVersionCode ||
                        (manifest.versionCode == currentVersionCode && manifest.commitSha.isNotBlank() && manifest.commitSha != BuildConfig.BUILD_COMMIT_SHA && BuildConfig.BUILD_COMMIT_SHA != "local")

                if (isNewer) {
                    _updateState.value = UpdateState.UpdateAvailable(manifest, manual)

                    if (autoDownload) {
                        startDownload(context, manifest)
                    } else if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Yeni sürüm bulundu: v${manifest.versionName}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    _updateState.value = UpdateState.UpToDate(
                        installedVersionName = currentVersionName,
                        installedVersionCode = currentVersionCode,
                        lastChecked = System.currentTimeMillis()
                    )
                    ZilbioNotificationService.clearUpdateNotification(context)

                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Zilbio en güncel sürümde (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}", e)
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
     * Silent update check for background workers. Returns non-null UpdateManifest if newer.
     */
    suspend fun checkUpdateSilently(context: Context): UpdateManifest? = withContext(Dispatchers.IO) {
        try {
            UserPreferences.setLastUpdateCheckTime(context, System.currentTimeMillis())
            val manifest = fetchUpdateManifest() ?: return@withContext null
            val isNewer = manifest.versionCode > BuildConfig.VERSION_CODE ||
                    (manifest.versionCode == BuildConfig.VERSION_CODE && manifest.commitSha.isNotBlank() && manifest.commitSha != BuildConfig.BUILD_COMMIT_SHA && BuildConfig.BUILD_COMMIT_SHA != "local")
            if (isNewer) {
                _latestManifest.value = manifest
                manifest
            } else {
                ZilbioNotificationService.clearUpdateNotification(context)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Silent update check failed: ${e.message}")
            null
        }
    }

    /**
     * Dual-source resolution: Queries Firebase RTDB OTA manifest first, falls back to GitHub Releases.
     */
    private suspend fun fetchUpdateManifest(): UpdateManifest? = withContext(Dispatchers.IO) {
        // Source 1: Firebase Realtime Database (/app_updates/latest.json)
        try {
            val rtdbJson = CloudRealtimeService.getAppUpdateManifest()
            if (!rtdbJson.isNullOrBlank() && rtdbJson != "null") {
                val obj = JSONObject(rtdbJson)
                val versionCode = obj.optInt("versionCode", 0)
                val versionName = obj.optString("versionName", "")
                val downloadUrl = obj.optString("downloadUrl", "")
                if (versionCode > 0 && downloadUrl.startsWith("http")) {
                    Log.i(TAG, "Resolved OTA manifest from Firebase RTDB: code=$versionCode, name=$versionName")
                    return@withContext UpdateManifest(
                        versionCode = versionCode,
                        versionName = versionName.ifBlank { "v$versionCode" },
                        commitSha = obj.optString("commitSha", ""),
                        downloadUrl = downloadUrl,
                        sha256 = obj.optString("sha256", ""),
                        releaseNotes = obj.optString("releaseNotes", ""),
                        mandatory = obj.optBoolean("mandatory", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase OTA manifest retrieval failed: ${e.message}, trying GitHub API fallback...")
        }

        // Source 2: GitHub Releases API fallback (/repos/engiincr/Zilbio-Chat/releases/latest)
        try {
            val req = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Zilbio-Android-App")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val raw = resp.body?.string() ?: ""
                    val json = JSONObject(raw)
                    val tagName = json.optString("tag_name", "v3.0.2")
                    val bodyNotes = json.optString("body", "")
                    val assets = json.optJSONArray("assets")

                    var downloadUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name == "Zilbio-latest.apk" || name.endsWith(".apk")) {
                                // MUST use browser_download_url for direct binary download!
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (downloadUrl.startsWith("http")) {
                        val parsedCode = extractVersionCode(tagName, bodyNotes)
                        val parsedName = tagName.removePrefix("v").removePrefix("V")
                        Log.i(TAG, "Resolved OTA manifest from GitHub API fallback: code=$parsedCode, name=$parsedName, url=$downloadUrl")
                        return@withContext UpdateManifest(
                            versionCode = parsedCode,
                            versionName = parsedName,
                            downloadUrl = downloadUrl,
                            releaseNotes = bodyNotes.take(300)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub Releases API fallback failed: ${e.message}")
        }

        null
    }

    private fun extractVersionCode(tagName: String, body: String): Int {
        val bodyMatch = Regex("versionCode[\\s:=]+(\\d+)", RegexOption.IGNORE_CASE).find(body)
        if (bodyMatch != null) {
            return bodyMatch.groupValues[1].toIntOrNull() ?: 4
        }
        val tagClean = tagName.removePrefix("v").removePrefix("V")
        val parts = tagClean.split(".")
        return if (parts.size >= 3) {
            val major = parts[0].toIntOrNull() ?: 3
            val minor = parts[1].toIntOrNull() ?: 0
            val patch = parts[2].toIntOrNull() ?: 2
            major * 100 + minor * 10 + patch
        } else {
            4
        }
    }

    /**
     * Downloads the APK artifact with full redirect following and integrity verification.
     */
    fun startDownload(context: Context, manifest: UpdateManifest) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0

        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Zilbio güncellemesi indiriliyor...", Toast.LENGTH_SHORT).show()
                }

                val downloadUrl = manifest.downloadUrl.trim()
                if (!downloadUrl.startsWith("http")) {
                    _isDownloading.value = false
                    _updateState.value = UpdateState.Error("Geçersiz indirme bağlantısı: $downloadUrl", true)
                    return@launch
                }

                Log.i(TAG, "Initiating APK download from: $downloadUrl")

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Zilbio-Android-App")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _isDownloading.value = false
                    val errorDetail = when (response.code) {
                        400 -> "Sunucu isteği reddetti (HTTP 400 Bad Request). İndirme bağlantısı geçersiz."
                        404 -> "Güncelleme dosyası sunucuda bulunamadı (HTTP 404 Not Found)."
                        403 -> "İndirme izni reddedildi (HTTP 403 Forbidden)."
                        else -> "APK indirilemedi: HTTP ${response.code}"
                    }
                    _updateState.value = UpdateState.Error(errorDetail, true)
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
                val tempApkFile = File(updatesDir, "Zilbio-update.apk.tmp")
                val finalApkFile = File(updatesDir, "Zilbio-latest.apk")

                if (tempApkFile.exists()) tempApkFile.delete()
                if (finalApkFile.exists()) finalApkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempApkFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
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

                // Integrity & Binary Validation (Item #6.4 & #10)
                if (!tempApkFile.exists() || tempApkFile.length() < 1024 * 1024) { // APK must be > 1MB
                    tempApkFile.delete()
                    _isDownloading.value = false
                    _updateState.value = UpdateState.Error("İndirilen dosya geçersiz veya eksik (${tempApkFile.length()} bytes)", true)
                    return@launch
                }

                // Verify ZIP Magic Bytes ('PK\x03\x04')
                val isZip = isZipArchive(tempApkFile)
                if (!isZip) {
                    tempApkFile.delete()
                    _isDownloading.value = false
                    _updateState.value = UpdateState.Error("İndirilen dosya geçerli bir Android APK paketi değil (HTML hata sayfası olabilir)", true)
                    return@launch
                }

                // Verify SHA-256 Checksum if provided in manifest
                if (manifest.sha256.isNotBlank()) {
                    val computedSha = computeFileSha256(tempApkFile)
                    if (!computedSha.equals(manifest.sha256.trim(), ignoreCase = true)) {
                        Log.e(TAG, "SHA-256 mismatch! Expected: ${manifest.sha256}, Computed: $computedSha")
                        tempApkFile.delete()
                        _isDownloading.value = false
                        _updateState.value = UpdateState.Error("Paket güvenlik doğrulaması başarısız oldu (SHA-256 uyuşmazlığı)", true)
                        return@launch
                    }
                    Log.i(TAG, "SHA-256 verified successfully: $computedSha")
                }

                // Rename temp to final
                if (!tempApkFile.renameTo(finalApkFile)) {
                    tempApkFile.copyTo(finalApkFile, overwrite = true)
                    tempApkFile.delete()
                }

                _isDownloading.value = false
                _downloadProgress.value = 100
                _updateState.value = UpdateState.ReadyToInstall(finalApkFile, manifest)

                Log.i(TAG, "Download and verification completed: ${finalApkFile.absolutePath} (${finalApkFile.length()} bytes)")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Güncelleme hazır, kurulum başlatılıyor...", Toast.LENGTH_SHORT).show()
                    installApk(context, finalApkFile, manifest)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _isDownloading.value = false
                val err = "İndirme hatası: ${e.localizedMessage ?: "Ağ kesintisi"}"
                _updateState.value = UpdateState.Error(err, true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Checks if file starts with ZIP / APK magic bytes ('P' 'K' 0x03 0x04)
     */
    private fun isZipArchive(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val b = ByteArray(4)
                val read = fis.read(b)
                read == 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Computes SHA-256 hex string of a file
     */
    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Seamlessly launches Android PackageInstaller via FileProvider
     */
    fun installApk(context: Context, apkFile: File, manifest: UpdateManifest? = null) {
        try {
            if (!apkFile.exists() || apkFile.length() < 1024 * 1024) {
                Toast.makeText(context, "Hata: Kurulacak APK dosyası bulunamadı", Toast.LENGTH_SHORT).show()
                return
            }

            scope.launch {
                manifest?.commitSha?.let { sha ->
                    if (sha.isNotBlank()) UserPreferences.setInstalledCommitSha(context, sha)
                }
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
            Log.i(TAG, "PackageInstaller intent dispatched for: ${apkFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Install launch failed: ${e.message}", e)
            Toast.makeText(context, "Kurulum başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
