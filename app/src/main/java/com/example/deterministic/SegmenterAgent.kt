package com.example.deterministic

/**
 * Agent 2 — Segmenter
 * Splits run-on speech dictation into structured, readable sentences based on pause indicators,
 * conjunction boundaries, and punctuation rules.
 */
object SegmenterAgent {

    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.?!])\\s+")

    /**
     * Splits text into coherent sentences.
     */
    fun segment(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val rawCleaned = text.trim()
        val sentences = rawCleaned.split(SENTENCE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (s in sentences) {
            // Further segment on long run-ons chained with " and then " or " but then "
            if (s.length > 120 && s.contains(" and then ", ignoreCase = true)) {
                val parts = s.split(Regex("(?i)\\s+and then\\s+"))
                parts.forEachIndexed { index, part ->
                    val trimmedPart = part.trim()
                    if (trimmedPart.isNotBlank()) {
                        val formatted = if (index > 0) "Then $trimmedPart" else trimmedPart
                        result.add(ensurePunctuation(formatted))
                    }
                }
            } else {
                result.add(ensurePunctuation(s))
            }
        }

        return result
    }

    private fun ensurePunctuation(sentence: String): String {
        val s = sentence.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (!s.endsWith(".") && !s.endsWith("!") && !s.endsWith("?")) {
            "$s."
        } else {
            s
        }
    }
}
