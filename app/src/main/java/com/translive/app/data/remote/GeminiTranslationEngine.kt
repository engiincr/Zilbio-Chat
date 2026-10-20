package com.translive.app.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translive.app.domain.model.TranslationResult
import com.translive.app.util.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object GeminiTranslationEngine {
    private const val TAG = "GeminiTranslationEngine"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Track quota
    private val _quotaWarning = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val quotaWarning: SharedFlow<String> = _quotaWarning.asSharedFlow()
    private val requestCount = AtomicInteger(0)
    private val lastResetTime = AtomicLong(System.currentTimeMillis())
    private const val DAILY_LIMIT = 1400

    // ML Kit translators (lazy init)
    private val translators = ConcurrentHashMap<String, Translator>()
    private val mlKitReady = AtomicBoolean(false)

    fun initMlKit(context: Context) {
        val pairs = listOf(
            "tr" to "en",
            "en" to "tr",
            "tr" to "de",
            "de" to "tr",
            "en" to "de",
            "de" to "en"
        )

        val conditions = DownloadConditions.Builder().build()
        val total = pairs.size
        val completed = AtomicInteger(0)

        for (pair in pairs) {
            val key = "${pair.first}_${pair.second}"
            val srcMl = getMlKitLanguage(pair.first)
            val tgtMl = getMlKitLanguage(pair.second)

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcMl)
                .setTargetLanguage(tgtMl)
                .build()

            val translator = Translation.getClient(options)
            translators[key] = translator

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    val count = completed.incrementAndGet()
                    Log.d(TAG, "Downloaded ML Kit model for $key ($count/$total)")
                    if (count >= total) {
                        mlKitReady.set(true)
                        Log.i(TAG, "All ML Kit offline models downloaded and ready")
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to download ML Kit model for $key: ${e.message}")
                }
        }
    }

    suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                provider = "none"
            )
        }

        if (sourceLanguage.equals(targetLanguage, ignoreCase = true)) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                provider = "none"
            )
        }

        checkAndResetQuota()
        val count = requestCount.get()
        if (count >= DAILY_LIMIT) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası aşıldı ($count/$DAILY_LIMIT). ML Kit çevrimdışı moduna geçildi.")
            return@withContext translateWithMlKit(text, sourceLanguage, targetLanguage)
        } else if (count >= 1300) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası tükenmek üzere ($count/$DAILY_LIMIT).")
        }

        requestCount.incrementAndGet()

        val prompt = "Translate the following $sourceLanguage text to $targetLanguage. " +
                "Respond ONLY with a JSON object: {\"original\": \"...\", \"translated\": \"...\"}\n" +
                "Text: $text"

        val requestJson = JsonObject().apply {
            val contentsArray = JsonArray()
            val contentObj = JsonObject()
            val partsArray = JsonArray()
            val partObj = JsonObject().apply {
                addProperty("text", prompt)
            }
            partsArray.add(partObj)
            contentObj.add("parts", partsArray)
            contentsArray.add(contentObj)
            add("contents", contentsArray)
        }.toString()

        // 1. Try Gemini primary model
        var responseText = callGemini(ApiConfig.GEMINI_MODEL, requestJson)

        // 2. Fallback to Gemini fallback model
        if (responseText == null) {
            Log.w(TAG, "Gemini primary model failed, trying fallback: ${ApiConfig.GEMINI_FALLBACK_MODEL}")
            responseText = callGemini(ApiConfig.GEMINI_FALLBACK_MODEL, requestJson)
        }

        // 3. Parse Gemini response
        if (responseText != null) {
            try {
                val cleaned = stripMarkdownFences(responseText)
                val jsonObj = JsonParser.parseString(cleaned).asJsonObject
                val translated = if (jsonObj.has("translated")) jsonObj.get("translated").asString else text
                val original = if (jsonObj.has("original")) jsonObj.get("original").asString else text
                return@withContext TranslationResult(
                    originalText = original,
                    translatedText = translated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    provider = "gemini"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Gemini text JSON response: $responseText", e)
            }
        }

        // 4. Fall back to ML Kit
        Log.w(TAG, "Gemini translation failed, using ML Kit fallback")
        translateWithMlKit(text, sourceLanguage, targetLanguage)
    }

    suspend fun translateAudio(
        wavBytes: ByteArray,
        targetLanguage: String,
        mimeType: String = "audio/wav"
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (wavBytes.isEmpty()) {
            return@withContext TranslationResult(
                originalText = "",
                translatedText = "",
                sourceLanguage = "tr",
                targetLanguage = targetLanguage,
                provider = "none"
            )
        }

        checkAndResetQuota()
        val count = requestCount.get()
        if (count >= DAILY_LIMIT) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası aşıldı ($count/$DAILY_LIMIT). Ses çevirisi yapılamıyor.")
            return@withContext TranslationResult(
                originalText = "[Ses Kaydı]",
                translatedText = "[Kota Aşıldı]",
                sourceLanguage = "tr",
                targetLanguage = targetLanguage,
                provider = "quota_exceeded"
            )
        } else if (count >= 1300) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası tükenmek üzere ($count/$DAILY_LIMIT).")
        }

        requestCount.incrementAndGet()

        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val promptText = "Listen to this audio. Detect the language (only tr, en, or de). " +
                "Transcribe it and translate to $targetLanguage. " +
                "Respond ONLY with JSON: {\"original\": \"transcribed text\", \"translated\": \"translated text\", \"detectedLanguage\": \"xx\"}"

        val requestObj = JsonObject().apply {
            val contentsArray = JsonArray()
            val contentObj = JsonObject()
            val partsArray = JsonArray()

            val audioPart = JsonObject().apply {
                val inlineData = JsonObject().apply {
                    addProperty("mime_type", mimeType)
                    addProperty("data", base64Audio)
                }
                add("inline_data", inlineData)
            }
            partsArray.add(audioPart)

            val textPart = JsonObject().apply {
                addProperty("text", promptText)
            }
            partsArray.add(textPart)

            contentObj.add("parts", partsArray)
            contentsArray.add(contentObj)
            add("contents", contentsArray)
        }

        val requestJson = requestObj.toString()

        // 1. Try Gemini primary model
        var responseText = callGemini(ApiConfig.GEMINI_MODEL, requestJson)

        // 2. Fallback to Gemini fallback model
        if (responseText == null) {
            Log.w(TAG, "Gemini audio primary model failed, trying fallback: ${ApiConfig.GEMINI_FALLBACK_MODEL}")
            responseText = callGemini(ApiConfig.GEMINI_FALLBACK_MODEL, requestJson)
        }

        if (responseText != null) {
            try {
                val cleaned = stripMarkdownFences(responseText)
                val jsonObj = JsonParser.parseString(cleaned).asJsonObject
                val original = if (jsonObj.has("original")) jsonObj.get("original").asString else ""
                val translated = if (jsonObj.has("translated")) jsonObj.get("translated").asString else ""
                val detected = if (jsonObj.has("detectedLanguage")) jsonObj.get("detectedLanguage").asString else "tr"

                return@withContext TranslationResult(
                    originalText = original,
                    translatedText = translated,
                    sourceLanguage = detected,
                    targetLanguage = targetLanguage,
                    provider = "gemini"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Gemini audio JSON response: $responseText", e)
            }
        }

        TranslationResult(
            originalText = "",
            translatedText = "",
            sourceLanguage = "tr",
            targetLanguage = targetLanguage,
            provider = "failed"
        )
    }

    private suspend fun callGemini(model: String, requestBody: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${ApiConfig.GEMINI_BASE_URL}$model:generateContent?key=${ApiConfig.GEMINI_API_KEY}"
            val body = requestBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini request failed: code=${response.code}, message=${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JsonParser.parseString(bodyStr).asJsonObject
                val candidates = root.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates[0].asJsonObject
                    val content = firstCandidate.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    if (parts != null && parts.size() > 0) {
                        return@withContext parts[0].asJsonObject.get("text").asString
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "callGemini error for model $model: ${e.message}", e)
            null
        }
    }

    private suspend fun translateWithMlKit(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val key = "${sourceLanguage.lowercase()}_${targetLanguage.lowercase()}"
        var translator = translators[key]

        if (translator == null) {
            val srcMl = getMlKitLanguage(sourceLanguage)
            val tgtMl = getMlKitLanguage(targetLanguage)
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcMl)
                .setTargetLanguage(tgtMl)
                .build()
            translator = Translation.getClient(options)
            translators[key] = translator
        }

        val translated = try {
            Tasks.await(translator.translate(text), 8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit translation failed: ${e.message}", e)
            text
        }

        TranslationResult(
            originalText = text,
            translatedText = translated,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            provider = "mlkit"
        )
    }

    private fun getMlKitLanguage(code: String): String {
        return when (code.lowercase().trim()) {
            "tr" -> TranslateLanguage.TURKISH
            "de" -> TranslateLanguage.GERMAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    private fun stripMarkdownFences(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```json", ignoreCase = true)) {
            cleaned = cleaned.substring(7)
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3)
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3)
        }
        return cleaned.trim()
    }

    private fun checkAndResetQuota() {
        val now = System.currentTimeMillis()
        if (now - lastResetTime.get() > 24 * 60 * 60 * 1000) {
            requestCount.set(0)
            lastResetTime.set(now)
        }
    }
}
