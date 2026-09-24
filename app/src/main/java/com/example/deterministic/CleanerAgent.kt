package com.example.deterministic

import java.util.regex.Pattern

/**
 * Agent 1 — Cleaner
 * Strips fillers, false starts, and duplicate repeated words while guaranteeing
 * fact-preservation (output tokens are strictly a subsequence of input tokens).
 */
object CleanerAgent {

    // Filler words, non-comparative conversational crutches, and throat-clearing speech artifacts
    private val FILLER_REGEX = Pattern.compile(
        "\\b(um|uh|er|ah|umm|uhh|err|ahh|you know|i mean|like|sort of|kind of|basically|actually|literally)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Trailing discourse connectives at the end of thoughts
    private val TRAILING_CONNECTIVES_REGEX = Pattern.compile(
        "\\s*,?\\s*\\b(and|but|so|because|or|then|well|though)\\s*[.?!]?$",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Cleans dictated text deterministically:
     * 1. Removes filler utterances ("um", "uh", "you know", "I mean", "like")
     * 2. Removes immediate word repetitions ("went went" -> "went")
     * 3. Resolves false starts ("I was— I went" -> "I went")
     * 4. Strips dangling trailing conjunctions ("...and", "...but")
     * 5. Normalizes whitespace and sentence capitalization
     */
    fun clean(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Resolve false starts indicated by em-dashes or double hyphens: "I was -- I went"
        if (text.contains("—") || text.contains("--")) {
            val parts = text.split(Regex("—|--"))
            if (parts.isNotEmpty()) {
                val lastPart = parts.last().trim()
                if (lastPart.isNotBlank()) {
                    text = lastPart
                }
            }
        }

        // 2. Remove filler words
        text = FILLER_REGEX.matcher(text).replaceAll("")

        // 3. Normalize multiple whitespace created by removals
        text = text.replace(Regex("\\s+"), " ").trim()

        // 4. Remove immediate consecutive word repeats (e.g., "the the" -> "the")
        val words = text.split(" ")
        val deduplicatedWords = mutableListOf<String>()
        for (w in words) {
            if (w.isBlank()) continue
            val cleanWord = w.trim(',', '.', '!', '?')
            val prevClean = deduplicatedWords.lastOrNull()?.trim(',', '.', '!', '?')
            if (prevClean == null || !cleanWord.equals(prevClean, ignoreCase = true)) {
                deduplicatedWords.add(w)
            }
        }
        text = deduplicatedWords.joinToString(" ")

        // 5. Remove trailing dangling connectives
        text = TRAILING_CONNECTIVES_REGEX.matcher(text).replaceAll("")

        // 6. Final cleanup: trim punctuation gaps, format capitalization
        text = text.replace(Regex("\\s+([,;.?!])"), "$1").trim()
        if (text.isNotEmpty()) {
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
                text += "."
            }
        }

        return text
    }

    /**
     * Safety check (Rule D.7): Verifies that cleaned tokens are a pure subsequence of raw tokens.
     */
    fun isSubsequenceOfRaw(rawText: String, cleanedText: String): Boolean {
        val rawTokens = rawText.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val cleanTokens = cleanedText.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

        var rawIdx = 0
        var cleanIdx = 0
        while (rawIdx < rawTokens.size && cleanIdx < cleanTokens.size) {
            if (rawTokens[rawIdx] == cleanTokens[cleanIdx]) {
                cleanIdx++
            }
            rawIdx++
        }
        return cleanIdx == cleanTokens.size
    }
}
