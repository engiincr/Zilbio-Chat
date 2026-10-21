package com.android.zilbio.data.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import com.android.zilbio.data.remote.GeminiTranslationEngine
import com.android.zilbio.domain.model.TranslationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object VoiceNoteManager {
    private const val TAG = "VoiceNoteManager"
    const val MAX_DURATION_MS = 8 * 60 * 1000L // 8 minutes hard limit
    const val AUTO_SEND_SECONDS = 60 // 60 seconds auto-send
    const val COUNTDOWN_START_SECONDS = 50 // 50 seconds countdown starts

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime = 0L
    private var accumulatedDurationMs = 0L
    private var lastResumeTimestamp = 0L
    private var amplitudeJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startRecording(context: Context, scope: CoroutineScope = defaultScope) {
        if (_isRecording.value) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            lastResumeTimestamp = recordingStartTime
            accumulatedDurationMs = 0L
            _isRecording.value = true
            _isPaused.value = false
            _recordingDurationSeconds.value = 0
            _currentAmplitude.value = 0f

            // Start amplitude & duration polling job
            amplitudeJob?.cancel()
            amplitudeJob = scope.launch(Dispatchers.IO) {
                while (isActive && _isRecording.value) {
                    delay(100)
                    if (!_isPaused.value) {
                        val totalMs = accumulatedDurationMs + (System.currentTimeMillis() - lastResumeTimestamp)
                        _recordingDurationSeconds.value = (totalMs / 1000).toInt()

                        try {
                            val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                            val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                            _currentAmplitude.value = normalized
                        } catch (e: Exception) {
                            // ignore amplitude query glitches
                        }
                    } else {
                        _currentAmplitude.value = 0f
                    }
                }
            }

            Log.i(TAG, "Recording started -> ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            release()
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
            accumulatedDurationMs += (System.currentTimeMillis() - lastResumeTimestamp)
            _isPaused.value = true
            _currentAmplitude.value = 0f
            Log.i(TAG, "Recording paused at ${accumulatedDurationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recording: ${e.message}")
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
            lastResumeTimestamp = System.currentTimeMillis()
            _isPaused.value = false
            Log.i(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming recording: ${e.message}")
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) {
            Log.w(TAG, "Not recording, cannot stop")
            return null
        }

        amplitudeJob?.cancel()
        amplitudeJob = null

        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "MediaRecorder stop failed (possibly too short): ${e.message}")
                }
                reset()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            _isPaused.value = false
            _currentAmplitude.value = 0f
            Log.i(TAG, "Recording stopped -> ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            release()
            null
        }
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error canceling recorder: ${e.message}")
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            _isPaused.value = false
            _currentAmplitude.value = 0f
            outputFile?.delete()
            outputFile = null
        }
    }

    fun getAudioDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract duration: ${e.message}")
            0L
        }
    }

    fun encodeToBase64(file: File): String? {
        return try {
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode audio file to base64: ${e.message}", e)
            null
        }
    }

    suspend fun transcribeAndTranslate(audioFile: File, targetLanguage: String): TranslationResult? =
        withContext(Dispatchers.IO) {
            try {
                if (!audioFile.exists() || audioFile.length() == 0L) {
                    Log.w(TAG, "Audio file is empty or does not exist")
                    return@withContext null
                }

                val bytes = audioFile.readBytes()
                val mimeType = if (audioFile.name.endsWith(".wav", ignoreCase = true)) {
                    "audio/wav"
                } else {
                    "audio/mp4"
                }

                val result = GeminiTranslationEngine.translateAudio(
                    wavBytes = bytes,
                    targetLanguage = targetLanguage,
                    mimeType = mimeType
                )

                if (result.translatedText.isNotBlank() || result.originalText.isNotBlank()) {
                    result
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transcribing audio note: ${e.message}", e)
                null
            }
        }

    fun release() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing media recorder: ${e.message}")
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            _isPaused.value = false
            _currentAmplitude.value = 0f
            outputFile = null
        }
    }
}
