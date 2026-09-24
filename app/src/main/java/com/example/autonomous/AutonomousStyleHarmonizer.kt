package com.example.autonomous

import com.example.data.Memory

/**
 * Autonomous Style Harmonizer:
 * Autonomously audits and harmonizes narrative voice across memories, enforcing
 * first-person autobiographical point-of-view, pruning spoken oral crutches,
 * correcting inadvertent tense slips, and unifying character aliases.
 */
object AutonomousStyleHarmonizer {

    data class StyleIssue(
        val memoryId: Long,
        val chapter: String,
        val issueCategory: String, // "Point-of-View", "Oral Crutch", "Tense Slips", "Punctuation"
        val originalSnippet: String,
        val suggestedHarmonization: String,
        val explanation: String
    )

    data class HarmonizationResult(
        val memoryId: Long,
        val originalText: String,
        val harmonizedText: String,
        val changesCount: Int,
        val issuesFixed: List<StyleIssue>
    )

    data class StyleHealthScorecard(
        val povStabilityPercent: Int,
        val oralCrutchFrequency: Int, // lower is better
        val overallStyleIndex: Int,   // 0 - 100
        val detectedIssues: List<StyleIssue>,
        val summaryStatement: String
    )

    private val ORAL_CRUTCHES = listOf(
        Pair(Regex("(?i)\\b(you know what I mean\\??)\\b"), ""),
        Pair(Regex("(?i)\\b(like I was saying)\\b"), ""),
        Pair(Regex("(?i)\\b(and stuff like that)\\b"), "and other things"),
        Pair(Regex("(?i)\\b(and things of that nature)\\b"), ""),
        Pair(Regex("(?i)\\b(needless to say,?\\s*)"), ""),
        Pair(Regex("(?i)\\b(at the end of the day,?\\s*)"), "ultimately, "),
        Pair(Regex("(?i)\\b(to make a long story short,?\\s*)"), "in time, "),
        Pair(Regex("(?i)\\b(kind of|sort of)\\s+([a-zA-Z]+ed\\b)"), "$2"),
        Pair(Regex("(?i)\\b(basically,?\\s*)"), "")
    )

    private val POV_SLIPS = listOf(
        Pair(Regex("(?i)\\bwhen you walk into\\b"), "when I walked into"),
        Pair(Regex("(?i)\\byou felt like\\b"), "I felt like"),
        Pair(Regex("(?i)\\byou could see\\b"), "I could see"),
        Pair(Regex("(?i)\\byou never forget\\b"), "I never forgot"),
        Pair(Regex("(?i)\\byou know how it is\\b"), "as was the way back then"),
        Pair(Regex("(?i)\\byou had to be there\\b"), "one had to be there to truly understand")
    )

    private val PRESENT_TENSE_SLIPS = listOf(
        Pair(Regex("(?i)\\bI walk into\\b"), "I walked into"),
        Pair(Regex("(?i)\\bI look at\\b"), "I looked at"),
        Pair(Regex("(?i)\\bI see him\\b"), "I saw him"),
        Pair(Regex("(?i)\\bI say to him\\b"), "I said to him"),
        Pair(Regex("(?i)\\bI say to her\\b"), "I said to her"),
        Pair(Regex("(?i)\\bhe says\\b"), "he said"),
        Pair(Regex("(?i)\\bshe says\\b"), "she said"),
        Pair(Regex("(?i)\\bwe go to\\b"), "we went to"),
        Pair(Regex("(?i)\\bI realize that\\b"), "I realized that")
    )

    /**
     * Audits the manuscript and compiles a comprehensive Style Health Scorecard.
     */
    fun auditStyleHealth(memories: List<Memory>): StyleHealthScorecard {
        if (memories.isEmpty()) {
            return StyleHealthScorecard(
                povStabilityPercent = 100,
                oralCrutchFrequency = 0,
                overallStyleIndex = 100,
                detectedIssues = emptyList(),
                summaryStatement = "Manuscript is waiting for initial stories."
            )
        }

        val allIssues = mutableListOf<StyleIssue>()

        memories.forEach { memory ->
            val text = (memory.formattedProse ?: memory.transcript).trim()
            val chapter = memory.chapter ?: "Life Journey"

            // 1. Audit POV Slips
            POV_SLIPS.forEach { (pattern, replacement) ->
                val matcher = pattern.toPattern().matcher(text)
                if (matcher.find()) {
                    allIssues.add(
                        StyleIssue(
                            memoryId = memory.id,
                            chapter = chapter,
                            issueCategory = "Point-of-View",
                            originalSnippet = matcher.group(),
                            suggestedHarmonization = replacement,
                            explanation = "Second-person oral slip (\"you\") deviates from authentic first-person memoir perspective."
                        )
                    )
                }
            }

            // 2. Audit Oral Crutches
            ORAL_CRUTCHES.forEach { (pattern, replacement) ->
                val matcher = pattern.toPattern().matcher(text)
                if (matcher.find()) {
                    allIssues.add(
                        StyleIssue(
                            memoryId = memory.id,
                            chapter = chapter,
                            issueCategory = "Oral Crutch",
                            originalSnippet = matcher.group(),
                            suggestedHarmonization = if (replacement.isBlank()) "[Omit for prose clarity]" else replacement,
                            explanation = "Spoken conversation crutch that dilutes literary manuscript momentum."
                        )
                    )
                }
            }

            // 3. Audit Tense Slips
            PRESENT_TENSE_SLIPS.forEach { (pattern, replacement) ->
                val matcher = pattern.toPattern().matcher(text)
                if (matcher.find()) {
                    allIssues.add(
                        StyleIssue(
                            memoryId = memory.id,
                            chapter = chapter,
                            issueCategory = "Tense Slips",
                            originalSnippet = matcher.group(),
                            suggestedHarmonization = replacement,
                            explanation = "Present tense oral slip within retrospective past-tense autobiography."
                        )
                    )
                }
            }
        }

        val povCount = allIssues.count { it.issueCategory == "Point-of-View" }
        val crutchCount = allIssues.count { it.issueCategory == "Oral Crutch" }
        val tenseCount = allIssues.count { it.issueCategory == "Tense Slips" }

        val povStability = (100 - (povCount * 8)).coerceIn(40, 100)
        val styleIndex = (100 - (povCount * 8 + crutchCount * 4 + tenseCount * 5)).coerceIn(45, 100)

        val summary = when {
            allIssues.isEmpty() -> "Manuscript demonstrates exceptional first-person consistency and polished literary rhythm."
            allIssues.size <= 3 -> "Voice is strong and authentic, with only minor conversational fillers to harmonize."
            else -> "Detected ${allIssues.size} stylistic refinements (POV, tense, and verbal fillers) ready for autonomous harmonization."
        }

        return StyleHealthScorecard(
            povStabilityPercent = povStability,
            oralCrutchFrequency = crutchCount,
            overallStyleIndex = styleIndex,
            detectedIssues = allIssues,
            summaryStatement = summary
        )
    }

    /**
     * Autonomously harmonizes a memory by applying clean POV, tense, and oral crutch repairs.
     */
    fun harmonizeMemory(memory: Memory): HarmonizationResult {
        val original = (memory.formattedProse ?: memory.transcript).trim()
        var harmonized = original
        val appliedIssues = mutableListOf<StyleIssue>()
        var changes = 0

        // 1. Harmonize POV
        POV_SLIPS.forEach { (pattern, replacement) ->
            if (pattern.containsMatchIn(harmonized)) {
                val matched = pattern.find(harmonized)?.value ?: ""
                harmonized = pattern.replace(harmonized, replacement)
                changes++
                appliedIssues.add(
                    StyleIssue(
                        memoryId = memory.id,
                        chapter = memory.chapter ?: "Life Journey",
                        issueCategory = "Point-of-View",
                        originalSnippet = matched,
                        suggestedHarmonization = replacement,
                        explanation = "Harmonized to first-person memoir point of view."
                    )
                )
            }
        }

        // 2. Harmonize Tense
        PRESENT_TENSE_SLIPS.forEach { (pattern, replacement) ->
            if (pattern.containsMatchIn(harmonized)) {
                val matched = pattern.find(harmonized)?.value ?: ""
                harmonized = pattern.replace(harmonized, replacement)
                changes++
                appliedIssues.add(
                    StyleIssue(
                        memoryId = memory.id,
                        chapter = memory.chapter ?: "Life Journey",
                        issueCategory = "Tense Slips",
                        originalSnippet = matched,
                        suggestedHarmonization = replacement,
                        explanation = "Aligned to retrospective literary past tense."
                    )
                )
            }
        }

        // 3. Prune Oral Crutches
        ORAL_CRUTCHES.forEach { (pattern, replacement) ->
            if (pattern.containsMatchIn(harmonized)) {
                val matched = pattern.find(harmonized)?.value ?: ""
                harmonized = pattern.replace(harmonized, replacement)
                changes++
                appliedIssues.add(
                    StyleIssue(
                        memoryId = memory.id,
                        chapter = memory.chapter ?: "Life Journey",
                        issueCategory = "Oral Crutch",
                        originalSnippet = matched,
                        suggestedHarmonization = replacement,
                        explanation = "Pruned oral conversational crutch."
                    )
                )
            }
        }

        // Clean up any double spaces or punctuation oddities created by removals
        harmonized = harmonized
            .replace(Regex("\\s{2,}"), " ")
            .replace(" ,", ",")
            .replace(" .", ".")
            .trim()

        return HarmonizationResult(
            memoryId = memory.id,
            originalText = original,
            harmonizedText = harmonized,
            changesCount = changes,
            issuesFixed = appliedIssues
        )
    }

    /**
     * Batch harmonizes all memories in a list.
     */
    fun batchHarmonize(memories: List<Memory>): List<HarmonizationResult> {
        return memories.map { harmonizeMemory(it) }
    }
}
