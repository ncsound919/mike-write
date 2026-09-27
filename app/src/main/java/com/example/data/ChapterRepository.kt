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
            val descriptions = mapOf(
                "Chapter 1: Early Days" to "Childhood memories, family roots, and early influences.",
                "Chapter 2: Growing Up & Family" to "Family traditions, key relatives, and growing-up stories.",
                "Chapter 3: Passions & Milestones" to "Career beginnings, passions, marriage, and personal achievements.",
                "Chapter 4: The Turning Point" to "Life pivot points, overcoming adversity, and personal strength.",
                "Chapter 5: Strength, Healing & Daily Life" to "Recovery, resilience, caregiving, and the texture of daily life.",
                "Chapter 6: Wisdom & Legacy" to "Life lessons, gratitude, and words for future generations."
            )
            val defaultChapters = buildList {
                add(
                    ChapterEntity(
                        title = BookChapters.PROLOGUE,
                        description = "Opening reflections and author introduction.",
                        orderIndex = 0,
                        targetWordCount = 1000
                    )
                )
                BookChapters.STANDARD.forEachIndexed { index, title ->
                    add(
                        ChapterEntity(
                            title = title,
                            description = descriptions[title].orEmpty(),
                            orderIndex = index + 1,
                            targetWordCount = 2000
                        )
                    )
                }
            }
            chapterDao.insertChapters(defaultChapters)
        }
    }
}
