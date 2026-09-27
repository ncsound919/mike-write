package com.example.deterministic

import java.util.regex.Pattern

/**
 * Agent 1 — Cleaner
 * Strips true disfluencies, false starts, and duplicate words while guaranteeing
 * fact-preservation (output tokens are strictly a subsequence of input tokens).
 *
 * The filler list is deliberately narrow. Words such as "like", "actually",
 * "literally", "kind of" and "sort of" were previously deleted everywhere, which
 * corrupted ordinary memoir sentences ("I like to fish" became "I to fish",
 * "we actually met in 1960" lost its emphasis). A memoir must preserve the
 * author's real words; only unambiguous throat-clearing is removed.
 */
object CleanerAgent {

    // Unambiguous spoken disfluencies only.
    private val FILLER_REGEX = Pattern.compile(
        "\\b(um|uh|erm|er|ah|umm|uhh|err|ahh|you know|i mean)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Trailing discourse connectives at the end of a thought.
    private val TRAILING_CONNECTIVES_REGEX = Pattern.compile(
        "\\s*,?\\s*\\b(and|but|so|because|or|then|well|though)\\s*[.?!]?$",
        Pattern.CASE_INSENSITIVE
    )

    // Words that are legitimately repeated in English and must never be collapsed.
    private val PROTECTED_REPEATS = setOf("had", "that", "very", "really", "no", "so", "many")

    /**
     * Cleans dictated text deterministically:
     * 1. Removes true filler utterances ("um", "uh", "you know", "I mean")
     * 2. Resolves short false starts ("I was— I went" -> "I went")
     * 3. Removes immediate word repetitions ("went went" -> "went")
     * 4. Strips dangling trailing conjunctions ("...and", "...but")
     * 5. Normalizes whitespace and sentence capitalization
     */
    fun clean(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Resolve false starts. Only drop the preceding fragment when it is a
        //    short stutter (<= 2 words); otherwise keep all content so meaningful
        //    clauses are never silently lost.
        if (text.contains("\u2014") || text.contains("--")) {
            val parts = text.split(Regex("\u2014|--")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val prefixWords = parts.dropLast(1)
                    .joinToString(" ")
                    .split(Regex("\\s+"))
                    .count { it.isNotBlank() }
                text = if (prefixWords <= 2) parts.last() else parts.joinToString(", ")
            }
        }

        // 2. Remove true filler words
        text = FILLER_REGEX.matcher(text).replaceAll("")

        // 3. Normalize multiple whitespace created by removals
        text = text.replace(Regex("\\s+"), " ").trim()

        // 4. Remove immediate consecutive word repeats (except protected words)
        val words = text.split(" ")
        val deduplicatedWords = mutableListOf<String>()
        for (w in words) {
            if (w.isBlank()) continue
            val cleanWord = w.trim(',', '.', '!', '?')
            val prevRaw = deduplicatedWords.lastOrNull()
            val prevClean = prevRaw?.trim(',', '.', '!', '?')
            val isRepeat = prevClean != null && cleanWord.equals(prevClean, ignoreCase = true)
            if (isRepeat && cleanWord.lowercase() in PROTECTED_REPEATS) {
                deduplicatedWords.add(w)
            } else if (!isRepeat) {
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
