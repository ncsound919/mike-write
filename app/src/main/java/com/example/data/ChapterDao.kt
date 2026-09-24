package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, createdAt ASC")
    fun getAllChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE title = :title LIMIT 1")
    suspend fun getChapterByTitle(title: String): ChapterEntity?

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterById(id: Long)

    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun countChapters(): Int
}
