package com.example.deterministic

import com.example.data.Memory

data class AssembledChapter(
    val chapterTitle: String,
    val memories: List<Memory>,
    val wordCount: Int,
    val suggestedSummary: String
)

/**
 * Agent 6 — Chapter Assembler
 * Organizes raw memories into coherent chapters using topic tags, chronological sorting,
 * and extractive heading titles.
 */
object ChapterAssemblerAgent {

    private val STANDARD_CHAPTER_ORDER = listOf(
        "Chapter 1: Early Days",
        "Chapter 2: Growing Up & Family",
        "Chapter 3: Passions & Milestones",
        "Chapter 4: The Turning Point",
        "Chapter 5: Strength, Healing & Daily Life",
        "Chapter 6: Wisdom & Legacy"
    )

    /**
     * Determines the optimal chapter for a memory deterministically based on topic and content.
     */
    fun suggestChapter(text: String, currentChapter: String): String {
        val topic = DeterministicWriterEngine.tagTopic(text)
        return when (topic) {
            "Childhood & Roots" -> "Chapter 1: Early Days"
            "Family & Heritage" -> "Chapter 2: Growing Up & Family"
            "Passions & Milestones" -> "Chapter 3: Passions & Milestones"
            "The Turning Point" -> "Chapter 4: The Turning Point"
            "Strength & Daily Life" -> "Chapter 5: Strength, Healing & Daily Life"
            "Wisdom & Legacy" -> "Chapter 6: Wisdom & Legacy"
            else -> currentChapter
        }
    }

    /**
     * Groups and sorts all memories into organized book chapters.
     */
    fun assemble(memories: List<Memory>): List<AssembledChapter> {
        val grouped = memories.groupBy { it.chapter ?: "Chapter 1: Early Days" }

        val chapters = mutableListOf<AssembledChapter>()
        for (chapTitle in STANDARD_CHAPTER_ORDER) {
            val memsInChap = grouped[chapTitle] ?: emptyList()
            val sortedMems = memsInChap.sortedBy { it.createdAt }
            val words = sortedMems.sumOf { it.transcript.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }
            val summary = if (sortedMems.isNotEmpty()) {
                val firstMem = sortedMems.first().transcript
                "Opens with: \"${firstMem.take(60)}...\" (${sortedMems.size} memories, $words words)"
            } else {
                "No memories recorded yet."
            }
            chapters.add(AssembledChapter(chapTitle, sortedMems, words, summary))
        }

        // Add any custom non-standard chapters
        for ((customTitle, customMems) in grouped) {
            if (!STANDARD_CHAPTER_ORDER.contains(customTitle)) {
                val sortedMems = customMems.sortedBy { it.createdAt }
                val words = sortedMems.sumOf { it.transcript.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }
                chapters.add(AssembledChapter(customTitle, sortedMems, words, "${sortedMems.size} memories, $words words"))
            }
        }

        return chapters
    }
}
