package com.example.data

/**
 * Structured literary breakdown of a dictated memory or story passage.
 * Identifies core narrative layers and classic book writing elements
 * to empower a first-time author.
 */
data class BookElements(
    val storyArc: String = "",           // The core narrative incident, action, or scene
    val reflection: String = "",         // Author's internal thoughts, retrospective narration, or philosophy
    val charactersAndPerspectives: String = "", // Key people present, their emotions, attitudes, and vantage points
    val sensoryDetails: String = "",     // Sights, sounds, smells, atmosphere, and visceral texture
    val writingTip: String = "",          // Gentle coaching tip on classic storytelling craft (e.g., showing vs telling, tension)
    // Recording to Chapter Creation & Formatting Automation extensions:
    val passageTitle: String = "",       // Catchy headline title for this story passage
    val formattedProse: String = "",     // Polished manuscript-ready prose with paragraphs and dialogue
    val emotionalTone: String = "",      // Mood/tone indicator (e.g. Nostalgic, Heartfelt, Triumphant)
    val assignedChapter: String = "",    // Chapter name assigned or auto-created
    val createNewChapter: Boolean = false, // True if Gemini auto-created a brand new chapter section in Room
    val newChapterDescription: String = "", // Description for newly auto-created chapter
    val newChapterTargetWords: Int = 2000 // Target word count for newly auto-created chapter
)
