package com.example.deterministic

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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

    private val fallthroughLogs = CopyOnWriteArrayList<FallthroughLogEntry>()

    // Budget Guardrails
    private const val MAX_LLM_CALLS_PER_SESSION = 20
    private val sessionLlmCallCount = AtomicInteger(0)

    fun resetSessionCounters() {
        sessionLlmCallCount.set(0)
    }

    fun canMakeLlmCall(): Boolean {
        return sessionLlmCallCount.get() < MAX_LLM_CALLS_PER_SESSION
    }

    fun recordLlmCall(task: String, reason: String, wouldBeAgent: String) {
        val currentCount = sessionLlmCallCount.incrementAndGet()
        val entry = FallthroughLogEntry(
            timestamp = System.currentTimeMillis(),
            task = task,
            reason = reason,
            wouldBeAgent = wouldBeAgent
        )
        fallthroughLogs.add(entry)
        Log.i("DeterministicRouter", "LLM Fallthrough recorded [#$currentCount]: $task -> reason: $reason, candidate agent: $wouldBeAgent")
    }

    fun getFallthroughLogs(): List<FallthroughLogEntry> = fallthroughLogs.toList()

    fun getFallthroughStats(): Pair<Int, Int> {
        return Pair(sessionLlmCallCount.get(), fallthroughLogs.size)
    }
}
