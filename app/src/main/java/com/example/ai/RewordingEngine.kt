package com.example.ai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class RewordOutcome(
    val rewordedText: String?,
    val notes: String?,
    val source: String,
    val faithfulProbability: Double?,
    val qualityScore: Double?,
    val shouldApply: Boolean,
    val error: String? = null
)

/**
 * AI rewording for a memoir passage.
 *
 * 1. Text model (Gemini -> Ollama) rewrites the passage, constrained to preserve
 *    every fact and the author's first-person voice.
 * 2. Jev (System One) rates how faithful the rewrite is (`noul`) and how readable
 *    it is (`score`). [shouldApply] only blocks when Jev is online AND says the
 *    rewrite changed meaning; if Jev is offline we surface the text and let the
 *    author decide (never fabricate a verdict).
 */
class RewordingEngine(
    private val textEngine: AiTextEngine,
    private val jevClient: JevClient
) {

    suspend fun reword(text: String, chapter: String): RewordOutcome {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return RewordOutcome(null, null, "offline", null, null, false, "Nothing to reword")
        }
        if (!textEngine.isConfigured) {
            return RewordOutcome(
                null, null, "offline", null, null, false,
                "No AI text provider configured. Set GEMINI_API_KEY or OLLAMA_BASE_URL."
            )
        }

        val raw = textEngine.generate(buildPrompt(trimmed, chapter), jsonMode = true)
            ?: return RewordOutcome(null, null, "offline", null, null, false, "AI text generation failed")

        val parsed = parse(raw)
            ?: return RewordOutcome(null, null, "ai", null, null, false, "AI returned unparseable output")

        val jev = evaluate(trimmed, parsed.first)
        val faithful = jev?.probability("faithful")
        val quality = jev?.score("quality")
        val shouldApply = (faithful == null || faithful >= 0.5) && (quality == null || quality >= 1.0)

        return RewordOutcome(
            rewordedText = parsed.first,
            notes = parsed.second,
            source = if (jev?.ok == true) "ai+jev(${jev.source})" else "ai",
            faithfulProbability = faithful,
            qualityScore = quality,
            shouldApply = shouldApply
        )
    }

    private fun buildPrompt(text: String, chapter: String): String = """
        You are a meticulous memoir editor helping a first-time author polish his own words.
        Reword the passage below for clarity, rhythm, and readability while:
        - preserving every fact, name, date, and detail exactly (invent NOTHING),
        - keeping the author's authentic first-person voice and emotional truth,
        - not adding poetic flourishes and not changing the meaning.

        Chapter: $chapter

        Passage:
        "$text"

        Respond in valid JSON only:
        { "rewordedText": "...", "notes": "one short sentence on what you changed" }
    """.trimIndent()

    private fun parse(raw: String): Pair<String, String?>? {
        return try {
            val json = JSONObject(raw.trim())
            val reworded = json.optString("rewordedText").trim()
            if (reworded.isBlank()) return null
            val notes = json.optString("notes").trim().ifBlank { null }
            reworded to notes
        } catch (e: Exception) {
            // Some models wrap JSON in prose or return plain text; accept the text.
            val cleaned = raw.trim().removeSurrounding("\"").trim()
            if (cleaned.isBlank()) null else cleaned to null
        }
    }

    private suspend fun evaluate(original: String, reworded: String): JevOutcome? {
        return try {
            val state = JSONObject().apply {
                put("action", "memoir_reword")
                put("original", original.take(1200))
                put("reworded", reworded.take(1200))
            }
            val questions = JSONObject().apply {
                put("faithful", JSONObject().apply {
                    put("type", "noul")
                    put("instructions", "Does the reworded passage preserve the author's original facts and meaning without inventing anything?")
                    put("criteria", JSONObject().apply { put("true", "Faithful"); put("false", "Changed meaning") })
                })
                put("quality", JSONObject().apply {
                    put("type", "score")
                    put("instructions", "Rate the readability and clarity of the reworded passage.")
                    put("criteria", JSONArray().apply { put("Unclear"); put("Okay"); put("Clear"); put("Excellent") })
                })
            }
            jevClient.decideSystemOne(state, questions)
        } catch (e: Exception) {
            Log.e("RewordingEngine", "Jev evaluation failed: ${e.message}")
            null
        }
    }
}
