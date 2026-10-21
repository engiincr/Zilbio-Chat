package com.android.zilbio.data.remote

import android.util.Base64
import android.util.Log
import android.util.LruCache
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
import com.android.zilbio.domain.model.TranslationResult
import com.android.zilbio.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object GeminiTranslationEngine {
    private const val TAG = "GeminiTranslationEngine"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // High-speed LRU Cache for translated texts keyed by MD5(text + ":" + srcLang + ":" + targetLang)
    private val translationCache = LruCache<String, String>(500)

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Track quota
    private val _quotaWarning = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val quotaWarning: SharedFlow<String> = _quotaWarning.asSharedFlow()
    private val requestCount = AtomicInteger(0)
    private val lastResetTime = AtomicLong(System.currentTimeMillis())
    private const val DAILY_LIMIT = 1400

    // ML Kit translators (lazy init)
    private val translators = ConcurrentHashMap<String, Translator>()
    private val mlKitReady = AtomicBoolean(false)

    fun initMlKit() {
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
        targetLanguage: String,
        bypassQuota: Boolean = false
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
                provider = "same_language_bypass"
            )
        }

        // Fast path: Check LRU cache keyed by MD5(text + ":" + srcLang + ":" + targetLang)
        val cacheKey = md5("$text:${sourceLanguage.lowercase()}:${targetLanguage.lowercase()}")
        synchronized(translationCache) {
            translationCache.get(cacheKey)?.let { cachedTranslation ->
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = cachedTranslation,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    provider = "cache"
                )
            }
        }

        // Fast path 2: Ultra-fast ML Kit on-device translation (target response time < 50ms, 0 quota, offline)
        try {
            val mlKitRes = translateWithMlKit(text, sourceLanguage, targetLanguage)
            if (mlKitRes.translatedText.isNotBlank() && mlKitRes.translatedText != text) {
                synchronized(translationCache) {
                    translationCache.put(cacheKey, mlKitRes.translatedText)
                }
                return@withContext mlKitRes
            }
        } catch (e: Exception) {
            Log.d(TAG, "ML Kit fast translation bypassed, trying Gemini: ${e.message}")
        }

        checkAndResetQuota()
        val count = requestCount.get()
        if (!bypassQuota && count >= DAILY_LIMIT) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası aşıldı ($count/$DAILY_LIMIT). ML Kit çevrimdışı moduna geçildi.")
            return@withContext translateWithMlKit(text, sourceLanguage, targetLanguage)
        } else if (!bypassQuota && count >= 1300) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası tükenmek üzere ($count/$DAILY_LIMIT).")
        }

        if (!bypassQuota) {
            requestCount.incrementAndGet()
        }

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

        if (responseText == null && ApiConfig.ENABLE_MODEL_FALLBACK) {
            Log.w(TAG, "Gemini primary model failed, trying fallback: ${ApiConfig.GEMINI_FALLBACK_MODEL}")
            responseText = callGemini(ApiConfig.GEMINI_FALLBACK_MODEL, requestJson)
            if (responseText == null) {
                Log.w(TAG, "Gemini fallback failed, trying confirmed active model: gemini-2.0-flash")
                responseText = callGemini("gemini-2.0-flash", requestJson)
            }
            if (responseText == null) {
                responseText = callGemini("gemini-1.5-flash-8b", requestJson)
            }
            if (responseText == null) {
                responseText = callGemini("gemini-flash-latest", requestJson)
            }
        }

        // Parse Gemini response
        if (responseText != null) {
            try {
                val cleaned = stripMarkdownFences(responseText)
                val jsonObj = JsonParser.parseString(cleaned).asJsonObject
                val translated = if (jsonObj.has("translated")) jsonObj.get("translated").asString else text
                val original = if (jsonObj.has("original")) jsonObj.get("original").asString else text

                synchronized(translationCache) {
                    translationCache.put(cacheKey, translated)
                }

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

        // Fall back to ML Kit
        Log.w(TAG, "Gemini translation failed, using ML Kit fallback")
        val mlKitRes = translateWithMlKit(text, sourceLanguage, targetLanguage)
        synchronized(translationCache) {
            translationCache.put(cacheKey, mlKitRes.translatedText)
        }
        mlKitRes
    }

    suspend fun translateAudio(
        wavBytes: ByteArray,
        targetLanguage: String,
        mimeType: String = "audio/wav",
        bypassQuota: Boolean = false
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
        if (!bypassQuota && count >= DAILY_LIMIT) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası aşıldı ($count/$DAILY_LIMIT). Ses çevirisi yapılamıyor.")
            return@withContext TranslationResult(
                originalText = "[Ses Kaydı]",
                translatedText = "[Kota Aşıldı]",
                sourceLanguage = "tr",
                targetLanguage = targetLanguage,
                provider = "quota_exceeded"
            )
        } else if (!bypassQuota && count >= 1300) {
            _quotaWarning.tryEmit("Günlük Gemini API kotası tükenmek üzere ($count/$DAILY_LIMIT).")
        }

        if (!bypassQuota) {
            requestCount.incrementAndGet()
        }

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

        var responseText = callGemini(ApiConfig.GEMINI_MODEL, requestJson)
        if (responseText == null && ApiConfig.ENABLE_MODEL_FALLBACK) {
            responseText = callGemini(ApiConfig.GEMINI_FALLBACK_MODEL, requestJson)
            if (responseText == null) {
                responseText = callGemini("gemini-2.0-flash", requestJson)
            }
            if (responseText == null) {
                responseText = callGemini("gemini-1.5-flash-8b", requestJson)
            }
            if (responseText == null) {
                responseText = callGemini("gemini-flash-latest", requestJson)
            }
        }

        if (responseText != null) {
            try {
                val cleaned = stripMarkdownFences(responseText)
                val jsonObj = JsonParser.parseString(cleaned).asJsonObject
                val original = if (jsonObj.has("original")) jsonObj.get("original").asString else ""
                var translated = if (jsonObj.has("translated")) jsonObj.get("translated").asString else ""
                val detected = if (jsonObj.has("detectedLanguage")) jsonObj.get("detectedLanguage").asString else "tr"

                // Same-language bypass for call speech
                if (detected.equals(targetLanguage, ignoreCase = true)) {
                    return@withContext TranslationResult(
                        originalText = original,
                        translatedText = original,
                        sourceLanguage = detected,
                        targetLanguage = targetLanguage,
                        provider = "same_language_bypass"
                    )
                }

                // Prioritize on-device Google ML Kit translation for zero-latency call subtitles (<50ms)
                if (original.isNotBlank()) {
                    try {
                        val mlKitRes = translateWithMlKit(original, detected, targetLanguage)
                        if (mlKitRes.translatedText.isNotBlank() && mlKitRes.translatedText != original) {
                            return@withContext TranslationResult(
                                originalText = original,
                                translatedText = mlKitRes.translatedText,
                                sourceLanguage = detected,
                                targetLanguage = targetLanguage,
                                provider = "mlkit"
                            )
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "ML Kit call caption fast path failed, falling back to Gemini: ${e.message}")
                    }
                }

                return@withContext TranslationResult(
                    originalText = original,
                    translatedText = if (translated.isNotBlank()) translated else original,
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
        val keys = ApiConfig.geminiKeys
        if (keys.isEmpty()) {
            Log.w(TAG, "No Gemini API keys configured")
            return@withContext null
        }

        val body = requestBody.toRequestBody(JSON_MEDIA_TYPE)

        // Try keys sequentially (Key 1 -> Key 2 on 429 or 5xx error)
        for (key in keys) {
            try {
                val url = "${ApiConfig.GEMINI_BASE_URL}$model:generateContent?key=$key"
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Gemini key failed with code=${response.code}, message=${response.message}")
                        if (response.code == 429 || response.code >= 500) {
                            // Quota or server error, continue to next key
                            return@use
                        }
                        return@use
                    }
                    val bodyStr = response.body?.string() ?: return@use
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
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini call exception with key: ${e.message}")
            }
        }
        null
    }

    suspend fun translateWithMlKit(
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
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    Log.d(TAG, "ML Kit model ready for $key")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit background download failed for $key: ${e.message}")
                }
        }

        val translated = try {
            Tasks.await(translator.translate(text), 4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.d(TAG, "ML Kit translation not ready or failed ($key): ${e.message}")
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
        val clean = code.lowercase().trim()
        val tagLang = TranslateLanguage.fromLanguageTag(clean)
        if (tagLang != null) return tagLang

        return when (clean) {
            "tr" -> TranslateLanguage.TURKISH
            "de" -> TranslateLanguage.GERMAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "it" -> TranslateLanguage.ITALIAN
            "ru" -> TranslateLanguage.RUSSIAN
            "ar" -> TranslateLanguage.ARABIC
            "pt" -> TranslateLanguage.PORTUGUESE
            "zh" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "nl" -> TranslateLanguage.DUTCH
            "pl" -> TranslateLanguage.POLISH
            "uk" -> TranslateLanguage.UKRAINIAN
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
