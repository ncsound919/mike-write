package com.example.speech

/**
 * Determines when a spoken segment should end the active dictation session.
 *
 * This exists because the previous implementation matched end-phrases such as
 * "done", "stop" and "finished" anywhere inside a sentence and then *deleted* the
 * matched word from the transcript. For memoir dictation that silently truncated
 * stories and corrupted sentences ("I was done with work" became "I was  with
 * work" and ended the recording; "we finished the meal" lost the word "finished").
 *
 * A segment now ends dictation only when it is genuinely a command:
 *  - the entire segment is a bare command ("done", "stop", ...), or
 *  - the segment is short (<= 4 words) and ends with an explicit multi-word
 *    phrase ("that's it", "all done", "stop recording", ...).
 *
 * Story text is never deleted mid-sentence.
 */
object RecordingStopDetector {

    /** Bare words that only count as a command when they are the whole segment. */
    private val BARE_COMMANDS = setOf(
        "done", "finished", "finish", "complete", "completed", "stop"
    )

    /** Explicit phrases that may be the whole segment or trail a short segment. */
    private val TRAILING_PHRASES = listOf(
        "stop recording", "stop listening", "stop dictation",
        "that's it", "that is it", "that's all", "that is all",
        "i'm done", "i am done", "i'm finished", "i am finished",
        "all done", "we're done", "we are done", "i'm done recording"
    )

    fun isStopCommand(utterance: String): Boolean {
        val t = normalize(utterance)
        if (t.isBlank()) return false
        if (t in BARE_COMMANDS) return true
        if (TRAILING_PHRASES.any { t == it }) return true
        val wordCount = t.split(' ').count { it.isNotBlank() }
        return wordCount <= 4 && TRAILING_PHRASES.any { t.endsWith(" $it") || t.endsWith(", $it") }
    }

    /** Strips a trailing command phrase from a segment known to be a stop command. */
    fun stripTrailingStopPhrase(segment: String): String {
        val original = segment.trim()
        if (original.isBlank()) return ""
        val t = normalize(original)
        val match = (BARE_COMMANDS + TRAILING_PHRASES)
            .filter { phrase -> t == phrase || t.endsWith(" $phrase") || t.endsWith(", $phrase") }
            .maxByOrNull { it.length }
            ?: return original
        if (t == match) return ""
        val cut = original.length - match.length
        return original.substring(0, cut)
            .trim()
            .trimEnd(',', ';', ':', '-', '\u2014', '\u2013', ' ')
            .trim()
    }

    /**
     * Removes only an explicit trailing phrase ("that's it", "stop recording") and
     * never bare words, so an in-flight partial is not corrupted mid-sentence.
     */
    fun stripExplicitTrailingPhrase(segment: String): String {
        val original = segment.trim()
        if (original.isBlank()) return ""
        val t = normalize(original)
        val match = TRAILING_PHRASES
            .filter { phrase -> t == phrase || t.endsWith(" $phrase") || t.endsWith(", $phrase") }
            .maxByOrNull { it.length }
            ?: return original
        if (t == match) return ""
        val cut = original.length - match.length
        return original.substring(0, cut)
            .trim()
            .trimEnd(',', ';', ':', '-', '\u2014', '\u2013', ' ')
            .trim()
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('\u2019', '\'')
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
