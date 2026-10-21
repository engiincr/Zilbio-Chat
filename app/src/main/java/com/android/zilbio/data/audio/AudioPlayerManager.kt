package com.android.zilbio.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentPlayingMessageId = MutableStateFlow<String?>(null)
    val currentPlayingMessageId: StateFlow<String?> = _currentPlayingMessageId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Playback Speed (1.0x -> 1.5x -> 2.0x) (Item D)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun togglePlaybackSpeed(): Float {
        val nextSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.playbackParams = player.playbackParams.setSpeed(speed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting playback speed: ${e.message}")
        }
    }

    fun togglePlayVoiceNote(context: Context, messageId: String, audioBase64: String) {
        if (_currentPlayingMessageId.value == messageId) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            playVoiceNote(context, messageId, audioBase64)
        }
    }

    fun playVoiceNote(context: Context, messageId: String, audioBase64: String) {
        stop()

        try {
            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "audio_play_${messageId.hashCode()}.m4a")
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
            }

            mediaPlayer = player
            _currentPlayingMessageId.value = messageId
            _durationMs.value = player.duration
            _currentPositionMs.value = 0
            _progress.value = 0f

            // Apply selected speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playbackSpeed.value != 1.0f) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(_playbackSpeed.value)
                } catch (e: Exception) {
                    // Ignore speed adjustment failure on prepare
                }
            }

            player.setOnCompletionListener {
                stop()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                stop()
                true
            }

            player.start()
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice note: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player: ${e.message}")
        }
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playbackSpeed.value != 1.0f) {
                mediaPlayer?.let { player ->
                    player.playbackParams = player.playbackParams.setSpeed(_playbackSpeed.value)
                }
            }
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player: ${e.message}")
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
            val dur = _durationMs.value
            if (dur > 0) {
                _progress.value = (positionMs.toFloat() / dur).coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking player: ${e.message}")
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing player: ${e.message}")
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPlayingMessageId.value = null
            _currentPositionMs.value = 0
            _progress.value = 0f
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val player = mediaPlayer
                if (player != null && _isPlaying.value) {
                    try {
                        val current = player.currentPosition
                        val total = _durationMs.value
                        _currentPositionMs.value = current
                        if (total > 0) {
                            _progress.value = (current.toFloat() / total).coerceIn(0f, 1f)
                        }
                    } catch (e: Exception) {
                        // ignore state query error
                    }
                }
                delay(100)
            }
        }
    }
}
