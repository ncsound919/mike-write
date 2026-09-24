package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["chapter"]),
        Index(value = ["createdAt"])
    ]
)
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val transcript: String,
    val audioPath: String? = null,
    val chapter: String? = "Prologue",
    val approved: Boolean = true,
    val prompt: String? = null,
    // Literary compartmentalization elements:
    val storyArc: String? = null,
    val reflection: String? = null,
    val charactersAndPerspectives: String? = null,
    val sensoryDetails: String? = null,
    val writingTip: String? = null,
    // Formatting & Chapter Automation fields:
    val formattedProse: String? = null,
    val passageTitle: String? = null,
    val emotionalTone: String? = null,
    val isAutoChapterCreated: Boolean = false
)
