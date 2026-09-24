package com.example.deterministic

import android.util.Log

enum class RoutingDecision {
    DETERMINISTIC_CLEANER,
    DETERMINISTIC_QUESTION_SELECTOR,
    DETERMINISTIC_TEMPLATE_FILLER,
    DETERMINISTIC_ECHO_CONFIRMER,
    DETERMINISTIC_TOPIC_TAGGER,
    DETERMINISTIC_CHAPTER_ASSEMBLER,
    DETERMINISTIC_PROGRESS_REPORTER,
    DETERMINISTIC_EXPORT_FORMATTER,
    LLM_FALLTHROUGH
}

data class FallthroughLogEntry(
    val timestamp: Long,
    val task: String,
    val reason: String,
    val wouldBeAgent: String
)

/**
 * Deterministic Orchestrator and Routing Engine
 * Enforces the 80/20 Deterministic-to-LLM split rule.
 * Routes transformation, selection, and slot-fill tasks on-device for free,
 * logging any fallthroughs to guide future agent development.
 */
object DeterministicRouter {

    private val fallthroughLogs = mutableListOf<FallthroughLogEntry>()

    // Budget Guardrails
    private const val MAX_LLM_CALLS_PER_SESSION = 20
    private var sessionLlmCallCount = 0

    fun resetSessionCounters() {
        sessionLlmCallCount = 0
    }

    fun canMakeLlmCall(): Boolean {
        return sessionLlmCallCount < MAX_LLM_CALLS_PER_SESSION
    }

    fun recordLlmCall(task: String, reason: String, wouldBeAgent: String) {
        sessionLlmCallCount++
        val entry = FallthroughLogEntry(
            timestamp = System.currentTimeMillis(),
            task = task,
            reason = reason,
            wouldBeAgent = wouldBeAgent
        )
        fallthroughLogs.add(entry)
        Log.i("DeterministicRouter", "LLM Fallthrough recorded [#$sessionLlmCallCount]: $task -> reason: $reason, candidate agent: $wouldBeAgent")
    }

    fun getFallthroughLogs(): List<FallthroughLogEntry> = fallthroughLogs.toList()

    fun getFallthroughStats(): Pair<Int, Int> {
        return Pair(sessionLlmCallCount, fallthroughLogs.size)
    }
}
