package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Memory::class, ChapterEntity::class], version = 4, exportSchema = false)
abstract class MikeWriteDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var INSTANCE: MikeWriteDatabase? = null

        fun getInstance(context: Context): MikeWriteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MikeWriteDatabase::class.java,
                    "mikewrite.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
