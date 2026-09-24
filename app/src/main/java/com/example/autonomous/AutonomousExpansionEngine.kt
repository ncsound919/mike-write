package com.example.autonomous

import com.example.data.Memory

/**
 * Autonomous Expansion Engine:
 * Autonomously audits chapters and stories for literary holes, missing sensory layers,
 * unexplained character departures, and unanchored time leaps. Produces a prioritized
 * queue of autonomous interview questions to systematically expand the manuscript.
 */
object AutonomousExpansionEngine {

    enum class GapType(val label: String, val iconDesc: String) {
        SENSORY_ATMOSPHERE("Sensory Atmosphere", "Missing sights, sounds, or physical textures"),
        CHARACTER_PERSPECTIVE("Character Depth", "Mentioned people lacking emotional vantage or dialogue"),
        PACING_LEAP("Pacing Leap", "Abrupt jump in timeline without connective bridge"),
        EMOTIONAL_REFLECTION("Emotional Reflection", "Factual recounting missing internal meaning"),
        CONFLICT_RESOLUTION("Story Resolution", "Stated tension or challenge left unresolved")
    }

    data class ManuscriptGap(
        val id: String,
        val memoryId: Long,
        val chapter: String,
        val type: GapType,
        val severity: String, // "High", "Medium", "Gentle"
        val title: String,
        val diagnosis: String,
        val autonomousInterviewPrompt: String,
        val targetPassageTitle: String
    )

    data class GapAnalysisReport(
        val totalGapsFound: Int,
        val highPriorityGaps: Int,
        val gaps: List<ManuscriptGap>,
        val topAutonomousPrompt: String?,
        val healthScore: Int // 0 to 100
    )

    private val SENSORY_WORDS = setOf(
        "smell", "scent", "aroma", "odor", "fragrance", "sound", "noise", "whisper",
        "creak", "hum", "buzz", "silence", "echo", "cold", "warm", "heat", "chill",
        "rough", "smooth", "soft", "wooden", "leather", "smoke", "taste", "sweet",
        "bitter", "bright", "dim", "shadow", "sunlight", "golden", "rain", "wind",
        "dust", "breeze", "color", "red", "blue", "green", "silver", "shone"
    )

    private val EMOTIONAL_WORDS = setOf(
        "felt", "feel", "fear", "afraid", "terrified", "loved", "heart", "wept",
        "cried", "grief", "joy", "proud", "shame", "regret", "hoped", "wondered",
        "realized", "learned", "longing", "yearned", "sorrow", "peace", "grateful",
        "ache", "lonely", "alive", "courage", "doubt", "relief"
    )

    /**
     * Audits a list of memories and generates an autonomous gap analysis report.
     */
    fun auditManuscriptGaps(memories: List<Memory>): GapAnalysisReport {
        if (memories.isEmpty()) {
            return GapAnalysisReport(
                totalGapsFound = 0,
                highPriorityGaps = 0,
                gaps = emptyList(),
                topAutonomousPrompt = "Tell me about the place where you grew up. What is the very first room you remember?",
                healthScore = 100
            )
        }

        val gaps = mutableListOf<ManuscriptGap>()

        memories.forEachIndexed { index, memory ->
            val text = (memory.formattedProse ?: memory.transcript).trim()
            val lower = text.lowercase()
            val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
            val wordCount = words.size
            val passageTitle = memory.passageTitle ?: "Story ${index + 1}"
            val chapter = memory.chapter ?: "Life Journey"

            // 1. Sensory Atmosphere Audit
            val sensoryCount = words.count { SENSORY_WORDS.contains(it) }
            if (wordCount >= 25 && sensoryCount == 0) {
                gaps.add(
                    ManuscriptGap(
                        id = "gap_sensory_${memory.id}",
                        memoryId = memory.id,
                        chapter = chapter,
                        type = GapType.SENSORY_ATMOSPHERE,
                        severity = if (wordCount > 60) "High" else "Medium",
                        title = "Atmosphere Deficit in \"$passageTitle\"",
                        diagnosis = "This scene describes events clearly, but lacks physical sights, sounds, or weather details to ground the reader in the room.",
                        autonomousInterviewPrompt = "In \"$passageTitle\", what physical sights, sounds, or smells stood out in the air that day?",
                        targetPassageTitle = passageTitle
                    )
                )
            }

            // 2. Emotional Reflection Audit
            val emotionalCount = words.count { EMOTIONAL_WORDS.contains(it) }
            val hasExplicitReflection = !memory.reflection.isNullOrBlank() && memory.reflection.length > 20
            if (wordCount >= 20 && emotionalCount == 0 && !hasExplicitReflection) {
                gaps.add(
                    ManuscriptGap(
                        id = "gap_reflection_${memory.id}",
                        memoryId = memory.id,
                        chapter = chapter,
                        type = GapType.EMOTIONAL_REFLECTION,
                        severity = "High",
                        title = "Unexpressed Reflection in \"$passageTitle\"",
                        diagnosis = "The external actions are recorded, but your personal emotional reaction and what this taught you about yourself remain unspoken.",
                        autonomousInterviewPrompt = "Looking back at \"$passageTitle\", what was going through your mind privately in that exact moment?",
                        targetPassageTitle = passageTitle
                    )
                )
            }

            // 3. Character Depth Audit
            val charactersDesc = memory.charactersAndPerspectives ?: ""
            val mentionsPeople = lower.contains("mother") || lower.contains("father") ||
                    lower.contains("brother") || lower.contains("sister") ||
                    lower.contains("wife") || lower.contains("husband") ||
                    lower.contains("friend") || lower.contains("boss") ||
                    lower.contains("grandma") || lower.contains("grandpa")
            val hasDialogue = text.contains("\"") || text.contains("said") || text.contains("told")

            if (mentionsPeople && !hasDialogue && charactersDesc.isBlank()) {
                gaps.add(
                    ManuscriptGap(
                        id = "gap_character_${memory.id}",
                        memoryId = memory.id,
                        chapter = chapter,
                        type = GapType.CHARACTER_PERSPECTIVE,
                        severity = "Medium",
                        title = "Untapped Character Voice in \"$passageTitle\"",
                        diagnosis = "Key people are named in this passage, but we do not hear their words or see their facial reactions.",
                        autonomousInterviewPrompt = "When that happened in \"$passageTitle\", what did the people with you say or how did their expressions change?",
                        targetPassageTitle = passageTitle
                    )
                )
            }

            // 4. Brief Passage Expansion
            if (wordCount in 1..20) {
                gaps.add(
                    ManuscriptGap(
                        id = "gap_brief_${memory.id}",
                        memoryId = memory.id,
                        chapter = chapter,
                        type = GapType.CONFLICT_RESOLUTION,
                        severity = "Gentle",
                        title = "Brief Memory Awaiting Expansion in \"$passageTitle\"",
                        diagnosis = "This story is only $wordCount words long. An extra paragraph of details will make it a full chapter scene.",
                        autonomousInterviewPrompt = "You mentioned: \"$text\". What happened immediately after that moment?",
                        targetPassageTitle = passageTitle
                    )
                )
            }
        }

        // Pacing Leap Audit between consecutive memories
        for (i in 0 until memories.size - 1) {
            val curr = memories[i]
            val next = memories[i + 1]
            if (curr.chapter == next.chapter) {
                val currText = (curr.formattedProse ?: curr.transcript)
                val nextText = (next.formattedProse ?: next.transcript)
                // Check if either has a year leap
                val yr1 = extractYear(currText)
                val yr2 = extractYear(nextText)
                if (yr1 != null && yr2 != null && Math.abs(yr2 - yr1) >= 5) {
                    val gapYears = Math.abs(yr2 - yr1)
                    gaps.add(
                        ManuscriptGap(
                            id = "gap_leap_${curr.id}_${next.id}",
                            memoryId = next.id,
                            chapter = curr.chapter ?: "Life Journey",
                            type = GapType.PACING_LEAP,
                            severity = "High",
                            title = "$gapYears-Year Time Jump in ${curr.chapter}",
                            diagnosis = "There is a leap from $yr1 to $yr2 without explaining how your life transformed across those years.",
                            autonomousInterviewPrompt = "Between $yr1 and $yr2, what major change redirected your life path?",
                            targetPassageTitle = next.passageTitle ?: "Transition"
                        )
                    )
                }
            }
        }

        val highPriority = gaps.count { it.severity == "High" }
        val topPrompt = gaps.firstOrNull { it.severity == "High" }?.autonomousInterviewPrompt
            ?: gaps.firstOrNull()?.autonomousInterviewPrompt
            ?: "What untold story from your life deserves a place in this chapter?"

        // Health score: 100 minus penalty for gaps
        val penalty = (highPriority * 12 + (gaps.size - highPriority) * 5).coerceAtMost(70)
        val score = (100 - penalty).coerceIn(30, 100)

        return GapAnalysisReport(
            totalGapsFound = gaps.size,
            highPriorityGaps = highPriority,
            gaps = gaps,
            topAutonomousPrompt = topPrompt,
            healthScore = score
        )
    }

    private fun extractYear(text: String): Int? {
        val matcher = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull()
        }
        return null
    }
}
