package com.example.autonomous

import com.example.ai.Interviewer
import com.example.data.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Autonomous Manuscript Weaver:
 * Autonomously analyzes chronological anchors, life milestones, and temporal markers
 * across all dictated memories to construct an optimal reading chronology and
 * weave smooth narrative transitions between disjointed scenes.
 */
object AutonomousManuscriptWeaver {

    data class TimelineScene(
        val memoryId: Long,
        val originalIndex: Int,
        val title: String,
        val chapter: String,
        val text: String,
        val detectedYear: Int?,
        val detectedAge: Int?,
        val detectedLifeStage: String?,
        val inferredChronologyScore: Double,
        val suggestedSequence: Int
    )

    data class SequenceAnalysis(
        val scenes: List<TimelineScene>,
        val hasInversions: Boolean,
        val inversionCount: Int,
        val proposedOrder: List<TimelineScene>,
        val summaryExplanation: String
    )

    data class NarrativeBridge(
        val fromMemoryId: Long,
        val toMemoryId: Long,
        val bridgeSentence: String,
        val chapter: String
    )

    private val YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b")
    private val TWO_DIGIT_YEAR = Pattern.compile("\\b(?:in|of|spring|summer|fall|winter)\\s+['’]?(\\d{2})\\b", Pattern.CASE_INSENSITIVE)
    private val AGE_PATTERN = Pattern.compile("\\b(?:at\\s+age|when\\s+I\\s+was|around\\s+age)\\s+(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE)

    private val LIFE_STAGES = listOf(
        Triple("early childhood", listOf("toddler", "baby", "crib", "kindergarten", "first memory", "infancy", "preschool"), 1940.0),
        Triple("grade school", listOf("elementary", "grade school", "third grade", "fourth grade", "fifth grade", "grammar school"), 1948.0),
        Triple("youth & teens", listOf("junior high", "middle school", "high school", "teenager", "sixteen", "first car", "prom"), 1955.0),
        Triple("early adulthood", listOf("college", "university", "draft", "military", "boot camp", "enlisted", "first job", "apprentice"), 1962.0),
        Triple("marriage & family", listOf("wedding", "married", "honeymoon", "first child", "our first home", "born", "baby daughter", "baby son"), 1968.0),
        Triple("mid career & prime", listOf("promotion", "career", "company", "business", "mortgage", "working years", "overtime"), 1978.0),
        Triple("mature years", listOf("kids left", "empty nest", "grandchildren", "grandkids", "retirement", "retiring", "silver anniversary"), 1995.0),
        Triple("later life & reflection", listOf("golden years", "stroke", "paralysis", "wheelchair", "nursing", "senior", "octogenarian", "looking back"), 2015.0)
    )

    /**
     * Autonomously extracts chronological timeline data for a list of memories.
     */
    fun analyzeTimeline(memories: List<Memory>): SequenceAnalysis {
        if (memories.isEmpty()) {
            return SequenceAnalysis(
                scenes = emptyList(),
                hasInversions = false,
                inversionCount = 0,
                proposedOrder = emptyList(),
                summaryExplanation = "No memories recorded yet."
            )
        }

        val scenes = memories.mapIndexed { index, memory ->
            val text = (memory.formattedProse ?: memory.transcript).trim()
            val year = extractYear(text)
            val age = extractAge(text)
            val stageInfo = extractLifeStage(text)
            val stage = stageInfo?.first

            // Compute an inferred chronology score (approx year equivalent)
            val score = when {
                year != null -> year.toDouble()
                age != null -> 1950.0 + age.toDouble() // Anchor baseline
                stageInfo != null -> stageInfo.third
                else -> 1960.0 + (index * 0.5) // Neutral progression
            }

            val title = memory.passageTitle ?: if (text.length > 30) text.take(30) + "..." else text

            TimelineScene(
                memoryId = memory.id,
                originalIndex = index,
                title = title,
                chapter = memory.chapter ?: "Life Journey",
                text = text,
                detectedYear = year,
                detectedAge = age,
                detectedLifeStage = stage,
                inferredChronologyScore = score,
                suggestedSequence = index + 1
            )
        }

        val sortedScenes = scenes.sortedBy { it.inferredChronologyScore }
            .mapIndexed { newIndex, scene ->
                scene.copy(suggestedSequence = newIndex + 1)
            }

        // Count inversions: instances where original order differs from chronological order
        var inversions = 0
        for (i in 0 until scenes.size - 1) {
            if (scenes[i].inferredChronologyScore > scenes[i + 1].inferredChronologyScore + 0.9) {
                inversions++
            }
        }

        val hasInversions = inversions > 0
        val explanation = if (hasInversions) {
            "Detected $inversions chronological time jumps. For instance, stories from later eras appear before earlier foundational memories."
        } else {
            "Chronological flow is natural and steady across all ${scenes.size} scenes."
        }

        return SequenceAnalysis(
            scenes = scenes,
            hasInversions = hasInversions,
            inversionCount = inversions,
            proposedOrder = sortedScenes,
            summaryExplanation = explanation
        )
    }

    /**
     * Autonomously generates connective narrative bridge sentences between consecutive scenes.
     */
    suspend fun generateNarrativeBridges(
        orderedScenes: List<TimelineScene>,
        interviewer: Interviewer? = null
    ): List<NarrativeBridge> = withContext(Dispatchers.Default) {
        if (orderedScenes.size < 2) return@withContext emptyList()

        val bridges = mutableListOf<NarrativeBridge>()

        for (i in 0 until orderedScenes.size - 1) {
            val current = orderedScenes[i]
            val next = orderedScenes[i + 1]

            // Check if bridge is needed (different era, year jump, or change of scene)
            val yearDiff = (next.inferredChronologyScore - current.inferredChronologyScore).toInt()
            val bridge = if (current.chapter != next.chapter) {
                // Inter-chapter bridge
                "As the chapter of ${current.chapter} drew to a close, life was already shifting toward ${next.chapter}."
            } else if (yearDiff >= 3) {
                "In the years that followed that season, time moved forward with quiet momentum."
            } else if (next.detectedLifeStage != null && next.detectedLifeStage != current.detectedLifeStage) {
                "That chapter of my youth soon gave way to ${next.detectedLifeStage}, bringing its own trials and discoveries."
            } else {
                "Not long after those events, another memory surfaced with vivid clarity."
            }

            bridges.add(
                NarrativeBridge(
                    fromMemoryId = current.memoryId,
                    toMemoryId = next.memoryId,
                    bridgeSentence = bridge,
                    chapter = current.chapter
                )
            )
        }

        bridges
    }

    private fun extractYear(text: String): Int? {
        val matcher = YEAR_PATTERN.matcher(text)
        if (matcher.find()) {
            val yr = matcher.group(1)?.toIntOrNull()
            if (yr != null && yr in 1910..2026) return yr
        }

        val twoDigit = TWO_DIGIT_YEAR.matcher(text)
        if (twoDigit.find()) {
            val yr = twoDigit.group(1)?.toIntOrNull()
            if (yr != null) {
                return if (yr > 30) 1900 + yr else 2000 + yr
            }
        }
        return null
    }

    private fun extractAge(text: String): Int? {
        val matcher = AGE_PATTERN.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun extractLifeStage(text: String): Triple<String, List<String>, Double>? {
        val lower = text.lowercase()
        for (stage in LIFE_STAGES) {
            if (stage.second.any { lower.contains(it) }) {
                return stage
            }
        }
        return null
    }
}
