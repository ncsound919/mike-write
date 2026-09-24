package com.example.autonomous

import com.example.data.Memory
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UnifiedAutomationPipeline:
 * The central brain that brings together all background writing, weaving, auditing,
 * and narrative harmonization so the user doesn't have to manually execute multi-step commands.
 *
 * When a memory is recorded, it:
 * 1. Automatically analyzes story elements & assigns or auto-creates appropriate chapters.
 * 2. Autonomously checks timeline order & chronological scene sequence (Manuscript Weaver).
 * 3. Autonomously audits manuscript gaps to identify what life event is missing next (Expansion Engine).
 * 4. Autonomously checks style health (POV stability, tense consistency, oral crutches) (Style Harmonizer).
 * 5. Generates a unified, contextual next-step prompt so the storytelling seamlessly flows.
 */
object UnifiedAutomationPipeline {

    data class AutomationExecutionResult(
        val memoryId: Long,
        val chapter: String,
        val passageTitle: String,
        val isAutoChapterCreated: Boolean,
        val timelineInversionsDetected: Int,
        val manuscriptGapsCount: Int,
        val topGapPrompt: String?,
        val stylePovStability: Int,
        val detectedStyleIssuesCount: Int,
        val writingTip: String,
        val nextUnifiedPrompt: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _lastExecution = MutableStateFlow<AutomationExecutionResult?>(null)
    val lastExecution: StateFlow<AutomationExecutionResult?> = _lastExecution.asStateFlow()

    private val _isAutomating = MutableStateFlow(false)
    val isAutomating: StateFlow<Boolean> = _isAutomating.asStateFlow()

    /**
     * Executes the unified end-to-end automation pipeline on the newly saved memory
     * and the updated manuscript database.
     */
    suspend fun executePipeline(
        savedMemory: Memory,
        allManuscriptMemories: List<Memory>,
        settings: SettingsStore
    ): AutomationExecutionResult {
        _isAutomating.value = true
        VoiceLoopBus.appendLog("Unified Automation Pipeline: Started execution for memory #${savedMemory.id}")

        try {
            // Minimum memory count before cross-manuscript analysis passes run
            val hasSufficientMemories = allManuscriptMemories.size >= 3

            // 1. Timeline Weaving Analysis
            val timelineAnalysis = if (settings.autoWeaveTimeline && hasSufficientMemories) {
                AutonomousManuscriptWeaver.analyzeTimeline(allManuscriptMemories)
            } else null

            // 2. Narrative Gap Auditing
            val gapAudit = if (settings.autoGapAuditing && hasSufficientMemories) {
                AutonomousExpansionEngine.auditManuscriptGaps(allManuscriptMemories)
            } else null

            // 3. Style & Voice Harmonization
            val styleAudit = if (settings.autoVoiceHarmonizing && hasSufficientMemories) {
                AutonomousStyleHarmonizer.auditStyleHealth(allManuscriptMemories)
            } else null

            // 4. Synthesize intelligent unified follow-up
            val topPrompt = gapAudit?.topAutonomousPrompt
            val inversions = timelineAnalysis?.inversionCount ?: 0
            val gapsCount = gapAudit?.totalGapsFound ?: 0
            val povStability = styleAudit?.povStabilityPercent ?: 100
            val styleIssues = styleAudit?.detectedIssues?.size ?: 0

            val unifiedPrompt = buildString {
                // Mention chapter & author tip
                append("Saved to ${savedMemory.chapter}. ")
                if (!savedMemory.writingTip.isNullOrBlank()) {
                    append("Craft tip: ${savedMemory.writingTip} ")
                }

                // Contextual bridge based on background automation findings
                if (topPrompt != null && topPrompt.isNotBlank()) {
                    append("Next suggested memory: $topPrompt ")
                } else if (inversions > 0) {
                    append("Timeline note: Detected $inversions scene sequence variations. ")
                }

                append("Say 'record' to continue, 'review' to listen, or 'next' to proceed.")
            }.trim()

            val result = AutomationExecutionResult(
                memoryId = savedMemory.id,
                chapter = savedMemory.chapter ?: "Early Days",
                passageTitle = savedMemory.passageTitle ?: "Story Passage",
                isAutoChapterCreated = savedMemory.isAutoChapterCreated,
                timelineInversionsDetected = inversions,
                manuscriptGapsCount = gapsCount,
                topGapPrompt = topPrompt,
                stylePovStability = povStability,
                detectedStyleIssuesCount = styleIssues,
                writingTip = savedMemory.writingTip ?: "Show details rather than just telling.",
                nextUnifiedPrompt = unifiedPrompt
            )

            _lastExecution.value = result
            VoiceLoopBus.appendLog(
                "Unified Automation Pipeline complete: Inversions=$inversions, Gaps=$gapsCount, POV=$povStability%"
            )

            return result
        } finally {
            _isAutomating.value = false
        }
    }

    fun clear() {
        _lastExecution.value = null
        _isAutomating.value = false
    }
}
