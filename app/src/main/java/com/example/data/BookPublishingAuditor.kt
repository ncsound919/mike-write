package com.example.data

/**
 * Audit metrics assessing manuscript completeness and readiness for book publishing.
 */
data class PublishingReadinessReport(
    val totalWords: Int,
    val estimatedPages: Int,
    val chapterBreakdown: Map<String, ChapterStats>,
    val totalChaptersWithContent: Int,
    val targetChaptersCount: Int,
    val structureCompletionPercent: Int,
    val hasFrontMatter: Boolean,
    val hasDedication: Boolean,
    val hasAuthorBio: Boolean,
    val readinessScore: Int, // 0 to 100
    val readinessStage: String, // e.g., "Seedling / Rough Draft", "Developing Manuscript", "Ready for Final Typeset"
    val recommendations: List<String>
)

data class ChapterStats(
    val passageCount: Int,
    val wordCount: Int,
    val estimatedReadMinutes: Int
)

object BookPublishingAuditor {

    fun audit(
        memories: List<Memory>,
        bookTitle: String,
        authorName: String,
        dedication: String,
        authorBio: String,
        allPlannedChapters: List<String>
    ): PublishingReadinessReport {
        val wordsPerPassage = memories.map { mem ->
            mem.transcript.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        }
        val totalWords = wordsPerPassage.sum()
        // Standard trade book has ~250 words per typeset page
        val estimatedPages = (totalWords / 250).coerceAtLeast(if (totalWords > 0) 1 else 0)

        val grouped = memories.groupBy { it.chapter?.trim() ?: "Unassigned" }
        val chapterStatsMap = mutableMapOf<String, ChapterStats>()

        allPlannedChapters.forEach { chap ->
            val passages = grouped[chap] ?: emptyList()
            val words = passages.sumOf { p -> p.transcript.split(Regex("\\s+")).count { it.isNotBlank() } }
            val readMinutes = (words / 150).coerceAtLeast(if (words > 0) 1 else 0) // ~150 wpm reading speed
            chapterStatsMap[chap] = ChapterStats(passages.size, words, readMinutes)
        }

        val chaptersWithContent = chapterStatsMap.count { it.value.passageCount > 0 }
        val targetChapters = allPlannedChapters.size.coerceAtLeast(1)
        val structureCompletionPercent = ((chaptersWithContent.toFloat() / targetChapters.toFloat()) * 100).toInt().coerceIn(0, 100)

        val hasDedicationBool = dedication.isNotBlank()
        val hasBioBool = authorBio.isNotBlank()
        val hasFrontMatterBool = bookTitle.isNotBlank() && authorName.isNotBlank()

        // Weighted readiness score calculation
        // 40% from chapter breadth
        // 30% from word count (targeting initial 5000+ words milestone for full life story)
        // 15% from front matter & metadata (dedication, bio, title)
        // 15% from literary layer enrichment (story arc, reflection, sensory details present)
        val chapterScore = (structureCompletionPercent * 0.40f)
        val wordTarget = 3000f // benchmark for initial memoir draft booklet
        val wordScore = ((totalWords / wordTarget).coerceAtMost(1.0f) * 30f)
        var metaScore = 0f
        if (hasFrontMatterBool) metaScore += 5f
        if (hasDedicationBool) metaScore += 5f
        if (hasBioBool) metaScore += 5f

        val memoriesWithLiteraryLayers = memories.count {
            !it.storyArc.isNullOrBlank() || !it.reflection.isNullOrBlank() || !it.sensoryDetails.isNullOrBlank()
        }
        val enrichmentRatio = if (memories.isNotEmpty()) memoriesWithLiteraryLayers.toFloat() / memories.size else 0f
        val layerScore = enrichmentRatio * 15f

        val readinessScore = (chapterScore + wordScore + metaScore + layerScore).toInt().coerceIn(0, 100)

        val stage = when {
            readinessScore >= 80 -> "Ready for Typeset & Export"
            readinessScore >= 50 -> "Developing Manuscript"
            readinessScore >= 25 -> "Early Draft Gathering"
            else -> "Initial Voice Outlining"
        }

        val recommendations = mutableListOf<String>()
        if (chaptersWithContent < targetChapters) {
            val missing = chapterStatsMap.filter { it.value.passageCount == 0 }.keys.take(2).joinToString(", ")
            recommendations.add("Explore missing chapters: $missing")
        }
        if (!hasDedicationBool) {
            recommendations.add("Add a book dedication in publishing settings.")
        }
        if (!hasBioBool) {
            recommendations.add("Add an author biographical statement.")
        }
        if (totalWords < 1500) {
            recommendations.add("Expand scenes with vivid dialogue and sensory descriptions.")
        }
        if (enrichmentRatio < 0.5f && memories.isNotEmpty()) {
            recommendations.add("Ask Mike Write for craft coaching on earlier passages.")
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Manuscript is fully organized and ready for publication export.")
        }

        return PublishingReadinessReport(
            totalWords = totalWords,
            estimatedPages = estimatedPages,
            chapterBreakdown = chapterStatsMap,
            totalChaptersWithContent = chaptersWithContent,
            targetChaptersCount = targetChapters,
            structureCompletionPercent = structureCompletionPercent,
            hasFrontMatter = hasFrontMatterBool,
            hasDedication = hasDedicationBool,
            hasAuthorBio = hasBioBool,
            readinessScore = readinessScore,
            readinessStage = stage,
            recommendations = recommendations
        )
    }

    /**
     * Formats book manuscript as standardized Markdown or Clean Plain Text ready for print or e-book conversion.
     */
    fun generateManuscript(
        format: ManuscriptFormat,
        bookTitle: String,
        authorName: String,
        dedication: String,
        authorBio: String,
        memories: List<Memory>,
        allChapters: List<String>
    ): String = buildString {
        when (format) {
            ManuscriptFormat.MARKDOWN -> {
                appendLine("# $bookTitle")
                appendLine("### By $authorName")
                appendLine()
                if (dedication.isNotBlank()) {
                    appendLine("> *\"$dedication\"*")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("## Table of Contents")
                allChapters.forEachIndexed { idx, ch ->
                    appendLine("${idx + 1}. $ch")
                }
                appendLine()
                appendLine("---")
                appendLine()

                val grouped = memories.groupBy { it.chapter?.trim() ?: "Unassigned Memories" }
                allChapters.forEach { ch ->
                    appendLine("## $ch")
                    appendLine()
                    val chapterMemories = grouped[ch] ?: emptyList()
                    if (chapterMemories.isEmpty()) {
                        appendLine("*(Chapter in progress)*")
                    } else {
                        chapterMemories.forEachIndexed { i, m ->
                            appendLine(m.transcript)
                            appendLine()
                            if (!m.reflection.isNullOrBlank() || !m.storyArc.isNullOrBlank()) {
                                appendLine("> **Author's Reflection:** ${m.reflection ?: m.storyArc}")
                                appendLine()
                            }
                        }
                    }
                    appendLine()
                }

                if (authorBio.isNotBlank()) {
                    appendLine("---")
                    appendLine("## About the Author")
                    appendLine()
                    appendLine(authorBio)
                    appendLine()
                }
            }
            ManuscriptFormat.STANDARD_TEXT -> {
                appendLine("==================================================")
                appendLine(bookTitle.uppercase())
                appendLine("By $authorName")
                appendLine("==================================================")
                appendLine()
                if (dedication.isNotBlank()) {
                    appendLine("DEDICATION:")
                    appendLine(dedication)
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine()
                }

                val grouped = memories.groupBy { it.chapter?.trim() ?: "Unassigned Memories" }
                allChapters.forEach { ch ->
                    appendLine("CHAPTER: $ch")
                    appendLine()
                    val chapterMemories = grouped[ch] ?: emptyList()
                    if (chapterMemories.isEmpty()) {
                        appendLine("[No recordings in this chapter yet]")
                    } else {
                        chapterMemories.forEachIndexed { i, m ->
                            appendLine("Section ${i + 1}:")
                            appendLine(m.transcript)
                            if (!m.storyArc.isNullOrBlank()) {
                                appendLine("  Arc: ${m.storyArc}")
                            }
                            if (!m.reflection.isNullOrBlank()) {
                                appendLine("  Reflection: ${m.reflection}")
                            }
                            appendLine()
                        }
                    }
                    appendLine("--------------------------------------------------")
                    appendLine()
                }

                if (authorBio.isNotBlank()) {
                    appendLine("ABOUT THE AUTHOR:")
                    appendLine(authorBio)
                    appendLine()
                    appendLine("==================================================")
                }
            }
        }
    }
}

enum class ManuscriptFormat {
    MARKDOWN,
    STANDARD_TEXT
}
