package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result of a Jev (TypeSafe System One) typed-decision call.
 * Answers are calibrated probabilities / scores, never free text.
 */
data class JevOutcome(
    val ok: Boolean,
    val source: String,
    val answers: JSONObject? = null,
    val error: String? = null
) {
    /** Boolean/"noul" answer as a 0..1 probability, tolerant of either wire shape. */
    fun probability(id: String): Double? {
        val a = answers?.optJSONObject(id) ?: return null
        for (key in listOf("noul", "probability", "value", "score")) {
            val d = a.optDouble(key, Double.NaN)
            if (d.isFinite()) return d
        }
        return null
    }

    fun score(id: String): Double? {
        val a = answers?.optJSONObject(id) ?: return null
        val d = a.optDouble("score", Double.NaN)
        return if (d.isFinite()) d else null
    }

    fun choice(id: String): String? =
        answers?.optJSONObject(id)?.optString("choice")?.trim()?.ifBlank { null }
}

/**
 * Jev / TypeSafe System One client with the fleet's two-tier chain.
 *
 * The two tiers speak different dialects, verified live:
 *  - **Vercel AI Gateway**: `POST {root}/v1/evaluate`, question types
 *    `boolean | choice | score`. (`/typesafe/v1/systemone` is the legacy path and
 *    403s; note `typesafe-ai/jev` is not in the free-tier subset, so this tier
 *    only answers once the team is on purchased credits.)
 *  - **Local Jev** (`:8080`, keyless): `POST {base}/v1/systemone`, types
 *    `noul | choice | score`.
 *
 * This client sends each tier its native shape and normalizes the answer back via
 * [JevOutcome.probability]/[JevOutcome.score]. Gateway first; on any failure it
 * falls back to local. Both failing => `source:"offline"`. Never fabricated.
 */
class JevClient(private val config: () -> AiConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    suspend fun decideSystemOne(state: JSONObject, questions: JSONObject): JevOutcome = withContext(Dispatchers.IO) {
        val c = config()
        if (!c.jevEnabled) {
            return@withContext JevOutcome(false, "offline", error = "Jev not configured")
        }

        var lastError: String? = null

        if (c.jevGatewayEnabled) {
            val root = c.jevBaseUrl.trimEnd('/').removeSuffix("/typesafe")
            val gateway = postSystemOne(
                endpoint = "$root/v1/evaluate",
                apiKey = c.jevKey,
                model = c.jevModel,
                state = state,
                questions = toGatewayQuestions(questions)
            )
            if (gateway.ok) return@withContext gateway.copy(source = "vercel")
            lastError = gateway.error
        }

        if (c.jevLocalEnabled) {
            val local = postSystemOne(
                endpoint = c.jevLocalUrl.trimEnd('/') + "/v1/systemone",
                apiKey = "",
                model = c.jevLocalModel,
                state = state,
                questions = questions
            )
            if (local.ok) return@withContext local.copy(source = "localjev")
            lastError = local.error ?: lastError
        }

        JevOutcome(false, "offline", error = lastError ?: "no Jev tier reachable")
    }

    /** Maps legacy `noul` questions to the gateway's `boolean` discriminator. */
    private fun toGatewayQuestions(questions: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = questions.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val q = questions.optJSONObject(id) ?: continue
            out.put(id, if (q.optString("type") == "noul") {
                JSONObject().apply {
                    put("type", "boolean")
                    put("instructions", q.optString("instructions"))
                    q.optJSONObject("criteria")?.let { put("criteria", it) }
                }
            } else {
                q
            })
        }
        return out
    }

    private fun postSystemOne(
        endpoint: String,
        apiKey: String,
        model: String,
        state: JSONObject,
        questions: JSONObject
    ): JevOutcome {
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("state", state)
                put("questions", questions)
            }
            val builder = Request.Builder()
                .url(endpoint)
                .post(body.toString().toRequestBody(jsonMedia))
            if (!AiConfig.isPlaceholder(apiKey)) {
                builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            }
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w("JevClient", "$endpoint HTTP ${response.code}: ${raw?.take(200)}")
                    return@use JevOutcome(false, "error", error = "HTTP ${response.code} ${raw?.take(160) ?: ""}".trim())
                }
                val answers = JSONObject(raw ?: "{}").optJSONObject("answers")
                    ?: return@use JevOutcome(false, "offline", error = "Jev response missing answers")
                JevOutcome(true, "vercel", answers = answers)
            }
        } catch (e: Exception) {
            Log.e("JevClient", "Jev call to $endpoint failed: ${e.message}")
            JevOutcome(false, "offline", error = e.message)
        }
    }
}
