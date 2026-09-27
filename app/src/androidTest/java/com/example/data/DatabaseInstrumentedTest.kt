package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the Room schema against the device's real SQLite engine (the JVM suite uses
 * Robolectric's shadow). Uses an in-memory database so the app's on-device data is
 * untouched.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MikeWriteDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MikeWriteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun memoryCrudAndChapterQueriesRoundTrip() = runBlocking {
        val dao = db.memoryDao()
        val id = dao.insert(
            Memory(
                createdAt = 1,
                transcript = "We built a treehouse in the summer.",
                formattedProse = "We built a treehouse in the summer.",
                passageTitle = "The Treehouse",
                chapter = "Chapter 1: Early Days"
            )
        )
        assertTrue(id > 0)
        assertEquals("The Treehouse", dao.getMemoryById(id)?.passageTitle)
        assertEquals(1, dao.countMemories())

        dao.update(dao.getMemoryById(id)!!.copy(passageTitle = "Updated Title"))
        assertEquals("Updated Title", dao.getMemoryById(id)?.passageTitle)

        val inChapter = dao.getMemoriesForChapter("Chapter 1: Early Days").first()
        assertTrue(inChapter.any { it.id == id })
        assertEquals(listOf("Chapter 1: Early Days"), dao.getAllChapters().first())

        dao.deleteById(id)
        assertNull(dao.getMemoryById(id))
        assertEquals(0, dao.countMemories())
    }

    @Test
    fun chapterSchemaPersistsAndOrders() = runBlocking {
        val dao = db.chapterDao()
        val second = dao.insertChapter(ChapterEntity(title = "Chapter 2", orderIndex = 1))
        val first = dao.insertChapter(ChapterEntity(title = "Chapter 1", orderIndex = 0))
        assertEquals(2, dao.countChapters())

        val ordered = dao.getAllChapters().first()
        assertEquals(listOf("Chapter 1", "Chapter 2"), ordered.map { it.title })

        val byTitle = dao.getChapterByTitle("Chapter 2")
        assertNotNull(byTitle)
        assertEquals(second, byTitle!!.id)

        dao.updateChapter(dao.getChapterById(first)!!.copy(description = "Roots and origins"))
        assertEquals("Roots and origins", dao.getChapterById(first)?.description)

        dao.deleteChapterById(second)
        assertEquals(1, dao.countChapters())
    }
}
