package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository providing abstracted access to book chapter persistence and default chapter initialization.
 */
class ChapterRepository(private val chapterDao: ChapterDao) {

    val allChapters: Flow<List<ChapterEntity>> = chapterDao.getAllChapters()

    suspend fun insertChapter(chapter: ChapterEntity): Long {
        return chapterDao.insertChapter(chapter)
    }

    suspend fun updateChapter(chapter: ChapterEntity) {
        chapterDao.updateChapter(chapter)
    }

    suspend fun deleteChapter(chapter: ChapterEntity) {
        chapterDao.deleteChapter(chapter)
    }

    suspend fun getChapterById(id: Long): ChapterEntity? {
        return chapterDao.getChapterById(id)
    }

    suspend fun ensureDefaultChaptersExist() {
        if (chapterDao.countChapters() == 0) {
            val defaultChapters = listOf(
                ChapterEntity(
                    title = "Prologue: Introduction",
                    description = "Opening reflections and author introduction.",
                    orderIndex = 0,
                    targetWordCount = 1000
                ),
                ChapterEntity(
                    title = "Chapter 1: Early Days",
                    description = "Childhood memories, family roots, and early influences.",
                    orderIndex = 1,
                    targetWordCount = 2000
                ),
                ChapterEntity(
                    title = "Chapter 2: Heritage & Family",
                    description = "Family traditions, key relatives, and ancestral stories.",
                    orderIndex = 2,
                    targetWordCount = 2000
                ),
                ChapterEntity(
                    title = "Chapter 3: Passion & Milestones",
                    description = "Career beginnings, passions, marriage, and personal achievements.",
                    orderIndex = 3,
                    targetWordCount = 2500
                ),
                ChapterEntity(
                    title = "Chapter 4: The Turning Point",
                    description = "Life pivot points, overcoming adversity, and personal strength.",
                    orderIndex = 4,
                    targetWordCount = 2000
                ),
                ChapterEntity(
                    title = "Chapter 5: Wisdom & Legacy",
                    description = "Life lessons, gratitude, and words for future generations.",
                    orderIndex = 5,
                    targetWordCount = 1500
                )
            )
            chapterDao.insertChapters(defaultChapters)
        }
    }
}
