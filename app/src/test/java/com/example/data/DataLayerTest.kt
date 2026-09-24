package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataLayerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testBookReadinessEvaluatorCalculation() {
        val memories = listOf(
            Memory(
                transcript = "When I was young we had a big dog named Buster.",
                chapter = "Chapter 1: Early Days",
                storyArc = "Playing with Buster",
                sensoryDetails = "Soft golden fur and cold wet nose",
                charactersAndPerspectives = "Buster and brother Tom"
            ),
            Memory(
                transcript = "In high school I made the varsity team.",
                chapter = "Chapter 2: Growing Up & Family",
                storyArc = "High school basketball game",
                sensoryDetails = "Squeak of sneakers on hardwood court",
                charactersAndPerspectives = "Coach and teammates"
            )
        )

        val report = BookReadinessEvaluator.evaluate(
            bookTitle = "Echoes of Endurance",
            authorName = "Michael",
            dedication = "To my family",
            authorBio = "A passionate writer and survivor.",
            memories = memories
        )

        assertNotNull(report)
        assertTrue(report.completionPercentage in 0..100)
        assertTrue(report.totalStepsCount > 0)
        assertTrue(report.steps.isNotEmpty())
        assertTrue(report.readinessStatus.isNotBlank())
        assertTrue(report.nextAction.isNotBlank())

        val unstartedReport = BookReadinessEvaluator.evaluate(
            bookTitle = "",
            authorName = "",
            dedication = "",
            authorBio = "",
            memories = emptyList()
        )
        assertEquals(0, unstartedReport.completedStepsCount)
    }

    @Test
    fun testBookPublishingAuditorMetrics() {
        val memories = listOf(
            Memory(
                id = 1,
                transcript = "This is a detailed memory.",
                formattedProse = "This is a detailed memory beautifully formatted.",
                chapter = "Chapter 1: Early Days",
                storyArc = "Arc 1",
                reflection = "Reflection 1",
                charactersAndPerspectives = "Chars 1",
                sensoryDetails = "Sensory 1",
                writingTip = "Tip 1"
            )
        )

        val audit = BookPublishingAuditor.audit(
            memories = memories,
            bookTitle = "My Life",
            authorName = "Mike",
            dedication = "For everyone",
            authorBio = "Author bio",
            allPlannedChapters = listOf("Chapter 1: Early Days", "Chapter 2: Growing Up & Family")
        )

        assertNotNull(audit)
        assertTrue(audit.totalWords > 0)
        assertTrue(audit.readinessScore >= 0)
        assertNotNull(audit.chapterBreakdown)
        assertTrue(audit.chapterBreakdown.isNotEmpty())

        val manuscript = BookPublishingAuditor.generateManuscript(
            format = ManuscriptFormat.MARKDOWN,
            bookTitle = "My Life",
            authorName = "Mike",
            dedication = "For everyone",
            authorBio = "Author bio",
            memories = memories,
            allChapters = listOf("Chapter 1: Early Days")
        )
        assertTrue(manuscript.contains("My Life"))
        assertTrue(manuscript.contains("Mike"))
    }

    @Test
    fun testSettingsStorePersistence() {
        val settings = SettingsStore(context)
        
        settings.bookTitle = "Memories in the Rain"
        assertEquals("Memories in the Rain", settings.bookTitle)

        settings.authorName = "Arthur Pendelton"
        assertEquals("Arthur Pendelton", settings.authorName)

        settings.dedication = "To all dreamers"
        assertEquals("To all dreamers", settings.dedication)

        settings.authorBio = "Writer and engineer."
        assertEquals("Writer and engineer.", settings.authorBio)

        settings.speechRate = 1.15f
        assertEquals(1.15f, settings.speechRate, 0.01f)

        settings.speechPitch = 0.95f
        assertEquals(0.95f, settings.speechPitch, 0.01f)

        settings.activeInputMode = "Voice Direct"
        assertEquals("Voice Direct", settings.activeInputMode)

        settings.smartAutoSave = true
        assertTrue(settings.smartAutoSave)

        settings.autoEditorialPipeline = false
        assertFalse(settings.autoEditorialPipeline)

        settings.autoWeaveTimeline = true
        assertTrue(settings.autoWeaveTimeline)

        settings.autoGapAuditing = true
        assertTrue(settings.autoGapAuditing)

        settings.autoVoiceHarmonizing = false
        assertFalse(settings.autoVoiceHarmonizing)
    }

    @Test
    fun testChapterRepositoryAndEntities() = runBlocking {
        val db = MikeWriteDatabase.getInstance(context)
        val chapterDao = db.chapterDao()
        val repo = ChapterRepository(chapterDao)

        repo.ensureDefaultChaptersExist()
        val chapters = repo.allChapters.first()
        assertTrue(chapters.isNotEmpty())

        val firstChapter = chapters.first()
        val updatedChapter = firstChapter.copy(description = "Updated description notes")
        repo.updateChapter(updatedChapter)
        val retrieved = repo.getChapterById(firstChapter.id)
        assertEquals("Updated description notes", retrieved?.description)

        // Custom insert
        val custom = ChapterEntity(
            title = "Special Appendix",
            description = "Bonus stories",
            orderIndex = 99,
            targetWordCount = 500
        )
        val customId = repo.insertChapter(custom)
        assertTrue(customId > 0)
    }

    @Test
    fun testMemoryDaoCrudOperations() = runBlocking {
        val db = MikeWriteDatabase.getInstance(context)
        val dao = db.memoryDao()

        val memory = Memory(
            transcript = "Testing memory DAO functions.",
            chapter = "Chapter 1: Early Days",
            formattedProse = "Testing memory DAO functions in full prose.",
            passageTitle = "Test Passage"
        )

        val id = dao.insert(memory)
        assertTrue(id > 0)

        val retrieved = dao.getMemoryById(id)
        assertNotNull(retrieved)
        assertEquals("Test Passage", retrieved?.passageTitle)

        val updated = retrieved!!.copy(passageTitle = "Updated Passage Title")
        dao.update(updated)

        val afterUpdate = dao.getMemoryById(id)
        assertEquals("Updated Passage Title", afterUpdate?.passageTitle)

        val allMemories = dao.getAllMemoriesAsc().first()
        assertTrue(allMemories.any { it.id == id })

        dao.delete(afterUpdate!!)
        val afterDelete = dao.getMemoryById(id)
        assertNull(afterDelete)
    }
}
