package com.android.zilbio.data.audio

import android.util.Log
import com.android.zilbio.data.remote.GeminiTranslationEngine
import com.android.zilbio.data.webrtc.SharedAudioCallEngine
import com.android.zilbio.domain.model.CallTranscriptSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VadAudioChunker(
    private val pcmChannel: Channel<SharedAudioCallEngine.PcmChunk>,
    private val translationEngine: GeminiTranslationEngine = GeminiTranslationEngine,
    var targetLanguage: String
) {
    companion object {
        private const val TAG = "VadAudioChunker"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // UI state flow
    private val _uiStatus = MutableStateFlow("Hazır (Canlı)")
    val uiStatus: StateFlow<String> = _uiStatus.asStateFlow()

    // Transcript results
    private val _transcriptSegments = MutableSharedFlow<CallTranscriptSegment>(extraBufferCapacity = 64)
    val transcriptSegments: SharedFlow<CallTranscriptSegment> = _transcriptSegments.asSharedFlow()

    private val speechThreshold = 450.0 // RMS threshold for speech detection (optimized for AEC voice communication)
    private val silenceTimeout = 450L // ms of silence before flush
    private val maxChunkDuration = 3500L // ms hard max before force flush

    @Volatile
    var isPaused: Boolean = false

    var speakerId: String = ""
    var speakerName: String = ""

    fun start() {
        scope.launch {
            var buffer = ByteArrayOutputStream()
            val preRollBuffer = java.util.ArrayDeque<ByteArray>() // ~200ms pre-roll
            var preRollBytes = 0
            var isSpeaking = false
            var chunkStartTime = 0L
            var lastSpeechTime = 0L
            var sampleRate = 16000
            var channelCount = 1

            for (chunk in pcmChannel) {
                if (isPaused) {
                    continue
                }
                sampleRate = chunk.sampleRate
                channelCount = chunk.channelCount
                val bytesPerSec = sampleRate * channelCount * 2
                val maxPreRollBytes = (bytesPerSec * 0.200).toInt() // 200ms

                val rms = calculateRms(chunk.data)

                if (rms > speechThreshold) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        chunkStartTime = System.currentTimeMillis()
                        _uiStatus.value = "Ses dinleniyor..."
                        // Flush pre-roll buffer into main buffer to preserve word onsets
                        while (!preRollBuffer.isEmpty()) {
                            buffer.write(preRollBuffer.pollFirst()!!)
                        }
                        preRollBytes = 0
                    }
                    lastSpeechTime = System.currentTimeMillis()
                    buffer.write(chunk.data)
                } else if (isSpeaking) {
                    buffer.write(chunk.data) // Include trailing audio in buffer
                } else {
                    // Maintain 200ms rolling window while silent
                    preRollBuffer.addLast(chunk.data)
                    preRollBytes += chunk.data.size
                    while (preRollBytes > maxPreRollBytes && !preRollBuffer.isEmpty()) {
                        val removed = preRollBuffer.pollFirst()
                        if (removed != null) preRollBytes -= removed.size
                    }
                }

                // Check flush conditions
                val elapsed = System.currentTimeMillis() - chunkStartTime
                val silenceElapsed = System.currentTimeMillis() - lastSpeechTime

                if (isSpeaking && (silenceElapsed >= silenceTimeout || elapsed >= maxChunkDuration)) {
                    val pcmBytes = buffer.toByteArray()
                    buffer = ByteArrayOutputStream()
                    isSpeaking = false

                    if (pcmBytes.size > (sampleRate * 2 * channelCount * 0.3)) { // At least 300ms of audio
                        try {
                            _uiStatus.value = "Çevriliyor..."
                            val wavBytes = prependWavHeader(pcmBytes, sampleRate, channelCount, 16)
                            val result = translationEngine.translateAudio(wavBytes, targetLanguage)
                            if (result.translatedText.isNotBlank()) {
                                val segment = CallTranscriptSegment(
                                    speakerId = speakerId,
                                    speakerName = speakerName,
                                    originalText = result.originalText,
                                    translatedText = result.translatedText,
                                    originalLanguage = result.sourceLanguage,
                                    timestamp = System.currentTimeMillis()
                                )
                                _transcriptSegments.emit(segment)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error translating audio chunk: ${e.message}", e)
                        } finally {
                            _uiStatus.value = "Hazır (Canlı)"
                        }
                    } else {
                        _uiStatus.value = "Hazır (Canlı)"
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private fun calculateRms(data: ByteArray): Double {
        if (data.size < 2) return 0.0
        var sum = 0.0
        val sampleCount = data.size / 2
        for (i in 0 until data.size - 1 step 2) {
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }
        return kotlin.math.sqrt(sum / sampleCount)
    }

    private fun prependWavHeader(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44 + totalAudioLen).order(ByteOrder.LITTLE_ENDIAN)

        // "RIFF"
        header.put('R'.code.toByte())
        header.put('I'.code.toByte())
        header.put('F'.code.toByte())
        header.put('F'.code.toByte())
        header.putInt(totalDataLen)

        // "WAVE"
        header.put('W'.code.toByte())
        header.put('A'.code.toByte())
        header.put('V'.code.toByte())
        header.put('E'.code.toByte())

        // "fmt "
        header.put('f'.code.toByte())
        header.put('m'.code.toByte())
        header.put('t'.code.toByte())
        header.put(' '.code.toByte())
        header.putInt(16) // Subchunk1Size
        header.putShort(1.toShort()) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        // "data"
        header.put('d'.code.toByte())
        header.put('a'.code.toByte())
        header.put('t'.code.toByte())
        header.put('a'.code.toByte())
        header.putInt(totalAudioLen)

        // Append PCM bytes
        header.put(pcmData)

        return header.array()
    }
}
