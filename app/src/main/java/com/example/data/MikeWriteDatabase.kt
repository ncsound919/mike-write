package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Memory::class, ChapterEntity::class], version = 5, exportSchema = true)
abstract class MikeWriteDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var INSTANCE: MikeWriteDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 added chapter and prompt columns
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN chapter TEXT DEFAULT 'Prologue'")
                    db.execSQL("ALTER TABLE memories ADD COLUMN prompt TEXT")
                } catch (ignored: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 to 3 added literary compartmentalization elements
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN storyArc TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN reflection TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN charactersAndPerspectives TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN sensoryDetails TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN writingTip TEXT")
                } catch (ignored: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 3 to 4 added prose formatting fields and chapters table
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN formattedProse TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN passageTitle TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN emotionalTone TEXT")
                    db.execSQL("ALTER TABLE memories ADD COLUMN isAutoChapterCreated INTEGER NOT NULL DEFAULT 0")
                } catch (ignored: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chapters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        targetWordCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_chapter` ON `memories` (`chapter`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_createdAt` ON `memories` (`createdAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_orderIndex` ON `chapters` (`orderIndex`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_title` ON `chapters` (`title`)")
                } catch (ignored: Exception) {}
            }
        }

        fun getInstance(context: Context): MikeWriteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MikeWriteDatabase::class.java,
                    "mikewrite.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

