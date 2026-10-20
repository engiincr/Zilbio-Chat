package com.translive.app.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.translive.app.data.remote.GeminiTranslationEngine
import com.translive.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object VoiceNoteManager {
    private const val TAG = "VoiceNoteManager"

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun startRecording(context: Context) {
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
            _isRecording.value = true
            Log.i(TAG, "Recording started -> ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            release()
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) {
            Log.w(TAG, "Not recording, cannot stop")
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            Log.i(TAG, "Recording stopped -> ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            release()
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
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing media recorder: ${e.message}")
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            outputFile = null
        }
    }
}
