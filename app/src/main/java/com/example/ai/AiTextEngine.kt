package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Text generation with a Gemini → Vercel AI Gateway → local chain.
 *
 * - Gemini: `generateContent` (`X-Goog-Api-Key`).
 * - AI Gateway: OpenAI-compatible `{root}/v1/chat/completions` with the shared
 *   `AI_GATEWAY_API_KEY`. This is the fast, high-quality path (verified ~1.5s).
 * - Local: native Ollama `{base}/api/generate` (`think:false` for qwen3) or
 *   OpenAI-compatible `{base}/v1/chat/completions` (llama.cpp at `:11434`).
 *
 * Every call is bounded by [AiConfigProvider.DEFAULT_MAX_OUTPUT_TOKENS]; without a
 * token cap a small local model can run for minutes, which previously hung the
 * voice loop. Returns null on failure so callers degrade deterministically.
 */
class AiTextEngine(private val config: () -> AiConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    val isConfigured: Boolean get() = config().anyTextProvider

    suspend fun generate(
        prompt: String,
        jsonMode: Boolean = false,
        maxTokens: Int = AiConfigProvider.DEFAULT_MAX_OUTPUT_TOKENS
    ): String? = withContext(Dispatchers.IO) {
        val c = config()
        if (c.geminiEnabled) {
            geminiText(prompt, jsonMode, c.geminiKey, maxTokens)?.let { return@withContext it }
        }
        if (c.gatewayTextEnabled) {
            val root = c.jevBaseUrl.trimEnd('/').removeSuffix("/typesafe")
            openAiChat("$root/v1/chat/completions", c.jevKey, c.gatewayTextModel, prompt, jsonMode, maxTokens)
                ?.let { return@withContext it }
        }
        if (c.ollamaEnabled) {
            localText(prompt, jsonMode, c.ollamaUrl, c.ollamaModel, c.ollamaApiKey, maxTokens)?.let { return@withContext it }
        }
        null
    }

    private fun geminiText(prompt: String, jsonMode: Boolean, key: String, maxTokens: Int): String? {
        return try {
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    if (jsonMode) put("responseMimeType", "application/json")
                    put("maxOutputTokens", maxTokens)
                })
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("X-Goog-Api-Key", key)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("AiTextEngine", "Gemini HTTP ${response.code}; falling back")
                    return@use null
                }
                val raw = response.body?.string()
                if (raw.isNullOrBlank()) return@use null
                JSONObject(raw).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text")?.trim()?.ifBlank { null }
            }
        } catch (e: Exception) {
            Log.e("AiTextEngine", "Gemini call failed: ${e.message}")
            null
        }
    }

    private fun localText(prompt: String, jsonMode: Boolean, baseUrl: String, model: String, apiKey: String, maxTokens: Int): String? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        if (!base.contains("/v1")) {
            nativeOllama(prompt, jsonMode, base, model, apiKey, maxTokens)?.let { return it }
        }
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        return openAiChat("$root/chat/completions", apiKey, model, prompt, jsonMode, maxTokens)
    }

    private fun nativeOllama(prompt: String, jsonMode: Boolean, base: String, model: String, apiKey: String, maxTokens: Int): String? {
        return try {
            val body = JSONObject().apply {
                put("model", model.ifBlank { AiConfigProvider.DEFAULT_OLLAMA_MODEL })
                put("prompt", prompt)
                put("stream", false)
                put("think", false)
                if (jsonMode) put("format", "json")
                put("options", JSONObject().apply {
                    put("temperature", 0.4)
                    put("num_predict", maxTokens)
                })
            }
            val builder = Request.Builder()
                .url("$base/api/generate")
                .post(body.toString().toRequestBody(jsonMedia))
            if (!AiConfig.isPlaceholder(apiKey)) builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("AiTextEngine", "Ollama native HTTP ${response.code} at $base; trying OpenAI-compatible")
                    return@use null
                }
                val raw = response.body?.string()
                if (raw.isNullOrBlank()) return@use null
                JSONObject(raw).optString("response").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.e("AiTextEngine", "Ollama native call failed: ${e.message}")
            null
        }
    }

    private fun openAiChat(endpoint: String, apiKey: String, model: String, prompt: String, jsonMode: Boolean, maxTokens: Int): String? {
        return try {
            val messages = JSONArray().apply {
                if (jsonMode) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Respond with a single valid JSON object and nothing else.")
                    })
                }
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            }
            val body = JSONObject().apply {
                put("model", model.ifBlank { AiConfigProvider.DEFAULT_OLLAMA_MODEL })
                put("messages", messages)
                put("stream", false)
                put("temperature", 0.4)
                put("max_tokens", maxTokens)
            }
            val builder = Request.Builder()
                .url(endpoint)
                .post(body.toString().toRequestBody(jsonMedia))
            if (!AiConfig.isPlaceholder(apiKey)) builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("AiTextEngine", "HTTP ${response.code} at $endpoint")
                    return@use null
                }
                val raw = response.body?.string()
                if (raw.isNullOrBlank()) return@use null
                JSONObject(raw).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")
                    ?.optString("content")?.trim()?.ifBlank { null }
            }
        } catch (e: Exception) {
            Log.e("AiTextEngine", "Chat call failed at $endpoint: ${e.message}")
            null
        }
    }
}
