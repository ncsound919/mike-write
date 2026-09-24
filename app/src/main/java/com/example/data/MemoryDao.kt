package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long

    @Update
    suspend fun update(memory: Memory)

    @Update
    suspend fun updateAll(memories: List<Memory>)

    @Delete
    suspend fun delete(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): Memory?

    @Query("SELECT * FROM memories ORDER BY createdAt ASC")
    fun getAllMemoriesAsc(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemoriesDesc(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMemory(): Memory?

    @Query("SELECT * FROM memories WHERE chapter = :chapter ORDER BY createdAt ASC")
    fun getMemoriesForChapter(chapter: String): Flow<List<Memory>>

    @Query("SELECT DISTINCT chapter FROM memories WHERE chapter IS NOT NULL")
    fun getAllChapters(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countMemories(): Int
}
