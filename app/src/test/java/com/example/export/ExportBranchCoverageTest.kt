package com.example.export

import com.example.data.Memory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Covers the formatting/PDF branches that the happy-path export tests miss. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportBranchCoverageTest {

    private val memory = Memory(
        id = 1,
        createdAt = 1,
        transcript = "We built a treehouse in the summer.",
        formattedProse = "We built a treehouse in the summer.",
        passageTitle = "The Treehouse",
        chapter = "Chapter 1: Early Days",
        emotionalTone = "Nostalgic",
        storyArc = "Building a sanctuary"
    )

    @Test
    fun `text export handles an empty book`() {
        val out = MemoirExportEngine.formatAsText("My Life", "Mike", null, emptyList())
        assertTrue(out.contains("END OF EXPORT"))
        assertFalse(out.contains("CHAPTER:"))
    }

    @Test
    fun `text export honours a chapter filter and omits literary detail`() {
        val out = MemoirExportEngine.formatAsText(
            bookTitle = "My Life",
            authorName = "Mike",
            chapterTitle = "Chapter 1: Early Days",
            memories = listOf(memory),
            includeLiteraryDetails = false
        )
        assertTrue(out.contains("Chapter: Chapter 1: Early Days"))
        assertFalse(out.contains("CHAPTER: Chapter 1: Early Days"))
        assertFalse(out.contains("Emotional Resonance"))
    }

    @Test
    fun `text export groups a missing chapter under Prologue`() {
        val out = MemoirExportEngine.formatAsText(
            "My Life", "Mike", null, listOf(memory.copy(chapter = null))
        )
        assertTrue(out.contains("CHAPTER: Prologue"))
    }

    @Test
    fun `markdown export handles empty and filtered cases`() {
        val empty = MemoirExportEngine.formatAsMarkdown("My Life", "Mike", null, emptyList())
        assertTrue(empty.startsWith("# My Life"))
        assertFalse("empty book should have no chapter group headers", empty.contains("\n## "))

        val filtered = MemoirExportEngine.formatAsMarkdown(
            "My Life", "Mike", "Chapter 1: Early Days", listOf(memory)
        )
        assertTrue(filtered.contains("#### Chapter: Chapter 1: Early Days"))
        assertFalse(filtered.contains("\n## Chapter 1: Early Days"))
    }

    @Test
    fun `markdown export groups a missing chapter under Prologue`() {
        val out = MemoirExportEngine.formatAsMarkdown(
            "My Life", "Mike", null, listOf(memory.copy(chapter = null))
        )
        assertTrue(out.contains("## Prologue"))
    }

    @Test
    fun `pdf export writes bytes for an empty book and for a filtered chapter`() {
        val empty = ByteArrayOutputStream()
        MemoirExportEngine.generatePdf("My Life", "Mike", null, emptyList(), empty)
        assertTrue("empty-book PDF produced no bytes", empty.size() > 0)

        val filtered = ByteArrayOutputStream()
        MemoirExportEngine.generatePdf("My Life", "Mike", "Chapter 1: Early Days", listOf(memory), filtered)
        assertTrue("filtered PDF produced no bytes", filtered.size() > 0)
    }

    @Test
    fun `export format metadata is complete`() {
        assertTrue(ExportFormat.entries.all { it.extension.isNotBlank() && it.mimeType.isNotBlank() && it.displayName.isNotBlank() })
        assertTrue(ExportFormat.TXT.displayName.contains(".txt"))
    }
}
