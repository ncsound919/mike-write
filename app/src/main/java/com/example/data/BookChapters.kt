package com.example.data

/**
 * Single source of truth for the memoir's chapter taxonomy.
 *
 * Previously five different hardcoded chapter lists had drifted apart across
 * BuddyScreen, VoiceLoopController, BookReadinessEvaluator, ChapterRepository,
 * ChapterAssemblerAgent and Interviewer. That inconsistency broke readiness
 * scoring, export tables of contents, chapter filtering and auto-created
 * chapter placement: a passage saved under one list's name matched nothing in
 * another list. All of those sites now reference [STANDARD].
 */
object BookChapters {

    /** The canonical six thematic memoir chapters, in reading order. */
    val STANDARD: List<String> = listOf(
        "Chapter 1: Early Days",
        "Chapter 2: Growing Up & Family",
        "Chapter 3: Passions & Milestones",
        "Chapter 4: The Turning Point",
        "Chapter 5: Strength, Healing & Daily Life",
        "Chapter 6: Wisdom & Legacy"
    )

    const val DEFAULT_CHAPTER: String = "Chapter 1: Early Days"
    const val PROLOGUE: String = "Prologue: Introduction"
}
