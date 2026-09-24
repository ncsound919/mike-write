package com.example.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.Memory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemoirExportTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val sampleMemories = listOf(
        Memory(
            id = 1,
            transcript = "I learned how to ride a bicycle down the hill.",
            formattedProse = "I vividly remember learning how to ride a bicycle down that steep neighborhood hill.",
            passageTitle = "First Bike Ride",
            chapter = "Chapter 1: Early Days",
            emotionalTone = "Joyful",
            storyArc = "Learning balance and conquering fear",
            reflection = "Taking risks early in life teaches resilience",
            charactersAndPerspectives = "Father holding the seat with steady hands",
            sensoryDetails = "Cool evening breeze and the ticking sound of bicycle chain",
            writingTip = "Highlight sensory contrast between fear and exhilaration"
        ),
        Memory(
            id = 2,
            transcript = "Our family always spent Thanksgiving together.",
            formattedProse = "Thanksgiving was the anchor of our family's autumn traditions.",
            passageTitle = "Thanksgiving Traditions",
            chapter = "Chapter 2: Growing Up & Family",
            emotionalTone = "Warm & Nostalgic",
            storyArc = "Gathering around grandmother's oak table",
            reflection = "Family traditions outlast the changing years",
            charactersAndPerspectives = "Grandmother presiding over dinner",
            sensoryDetails = "Aroma of roasted turkey, cinnamon and fresh pumpkin pie",
            writingTip = "Use aroma cues to evoke strong memory resonance"
        )
    )

    @Test
    fun testFormatAsTextSingleChapterAndAllChapters() {
        val singleChapterText = MemoirExportEngine.formatAsText(
            bookTitle = "The Journey",
            authorName = "Michael Write",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories
        )

        assertTrue(singleChapterText.contains("THE JOURNEY") || singleChapterText.contains("The Journey", ignoreCase = true))
        assertTrue(singleChapterText.contains("Michael Write"))
        assertTrue(singleChapterText.contains("Chapter: Chapter 1: Early Days"))
        assertTrue(singleChapterText.contains("First Bike Ride"))

        val allChaptersText = MemoirExportEngine.formatAsText(
            bookTitle = "The Journey",
            authorName = "Michael Write",
            chapterTitle = null,
            memories = sampleMemories
        )

        assertTrue(allChaptersText.contains("Thanksgiving Traditions"))
    }

    @Test
    fun testFormatAsMarkdown() {
        val markdown = MemoirExportEngine.formatAsMarkdown(
            bookTitle = "The Journey",
            authorName = "Michael Write",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories
        )

        assertTrue(markdown.startsWith("# The Journey"))
        assertTrue(markdown.contains("#### Chapter: Chapter 1: Early Days"))
        assertTrue(markdown.contains("### First Bike Ride"))
    }

    @Test
    fun testSaveToStorageAndShareFile() = runBlocking {
        // Save as TXT
        val txtResult = MemoirExportEngine.saveToStorage(
            context = context,
            fileName = "memoir_test.txt",
            format = ExportFormat.TXT,
            bookTitle = "Test Book",
            authorName = "Author",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories
        )
        assertNotNull(txtResult)
        assertEquals("text/plain", txtResult.mimeType)

        // Save as MD
        val mdResult = MemoirExportEngine.saveToStorage(
            context = context,
            fileName = "memoir_test.md",
            format = ExportFormat.MARKDOWN,
            bookTitle = "Test Book",
            authorName = "Author",
            chapterTitle = null,
            memories = sampleMemories
        )
        assertNotNull(mdResult)
        assertEquals("text/markdown", mdResult.mimeType)

        // Save as PDF
        val pdfResult = MemoirExportEngine.saveToStorage(
            context = context,
            fileName = "memoir_test.pdf",
            format = ExportFormat.PDF,
            bookTitle = "Test Book",
            authorName = "Author",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories
        )
        assertNotNull(pdfResult)
        assertEquals("application/pdf", pdfResult.mimeType)

        // Create Shareable File
        val shareResult = MemoirExportEngine.createShareableFile(
            context = context,
            fileName = "share_test.pdf",
            format = ExportFormat.PDF,
            bookTitle = "Test Book",
            authorName = "Author",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories
        )
        assertNotNull(shareResult)
        assertEquals("application/pdf", shareResult.mimeType)
    }
}
