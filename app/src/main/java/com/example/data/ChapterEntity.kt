package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val orderIndex: Int = 0,
    val targetWordCount: Int = 1500,
    val status: String = "Drafting",
    val createdAt: Long = System.currentTimeMillis()
)
