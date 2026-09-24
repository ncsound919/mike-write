package com.example.data

data class ReadinessStep(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val category: String,
    val actionHint: String
)

data class BookReadinessReport(
    val completionPercentage: Int,
    val completedStepsCount: Int,
    val totalStepsCount: Int,
    val steps: List<ReadinessStep>,
    val readinessStatus: String,
    val nextAction: String
)

object BookReadinessEvaluator {

    val STANDARD_CHAPTERS = listOf(
        "Chapter 1: Early Days",
        "Chapter 2: Growing Up & Family",
        "Chapter 3: Passions & Milestones",
        "Chapter 4: The Turning Point",
        "Chapter 5: Strength, Healing & Daily Life",
        "Chapter 6: Wisdom & Legacy"
    )

    fun evaluate(
        bookTitle: String,
        authorName: String,
        dedication: String,
        authorBio: String,
        memories: List<Memory>
    ): BookReadinessReport {
        val totalWords = memories.sumOf { it.transcript.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }
        val groupedChapters = memories.groupBy { it.chapter ?: "Chapter 1: Early Days" }
        val chaptersWithContent = STANDARD_CHAPTERS.count { groupedChapters.containsKey(it) && (groupedChapters[it]?.isNotEmpty() == true) }

        val hasSensoryAnchors = memories.any { !it.sensoryDetails.isNullOrBlank() }
        val hasPerspectives = memories.any { !it.charactersAndPerspectives.isNullOrBlank() }

        val steps = listOf(
            ReadinessStep(
                id = "title_and_author",
                title = "Title & Author Defined",
                description = "Set a distinctive title and author name in settings.",
                isCompleted = bookTitle.isNotBlank() && authorName.isNotBlank(),
                category = "Front Matter",
                actionHint = "Configure in Settings tab"
            ),
            ReadinessStep(
                id = "dedication_and_bio",
                title = "Dedication & Author Bio",
                description = "Front matter dedication and back matter author biography written.",
                isCompleted = dedication.isNotBlank() && authorBio.isNotBlank(),
                category = "Front Matter",
                actionHint = "Add personal tribute in Settings"
            ),
            ReadinessStep(
                id = "first_memory",
                title = "First Dictated Memory",
                description = "Record your opening life memory via hands-free voice loop.",
                isCompleted = memories.isNotEmpty(),
                category = "Drafting",
                actionHint = "Dictate memory in Buddy Mode"
            ),
            ReadinessStep(
                id = "chapter_breadth",
                title = "Core Chapters Mapped (3+ Chapters)",
                description = "Dictate stories across at least 3 distinct life chapters ($chaptersWithContent/6 recorded).",
                isCompleted = chaptersWithContent >= 3,
                category = "Drafting",
                actionHint = "Record memories across new chapters"
            ),
            ReadinessStep(
                id = "word_count_milestone",
                title = "Manuscript Depth (1,000+ Words)",
                description = "Current manuscript: $totalWords words (~${(totalWords / 250).coerceAtLeast(1)} book pages).",
                isCompleted = totalWords >= 1000,
                category = "Depth",
                actionHint = "Add sensory details and deeper reflections"
            ),
            ReadinessStep(
                id = "sensory_and_perspectives",
                title = "Sensory & Emotional Layering",
                description = "Enrich stories with sensory details, characters, and reflections.",
                isCompleted = hasSensoryAnchors && hasPerspectives && memories.size >= 5,
                category = "Craft Polish",
                actionHint = "Review craft notes in Book Review tab"
            ),
            ReadinessStep(
                id = "all_chapters_covered",
                title = "Full Lifecycle Coverage (6 Chapters)",
                description = "Complete all 6 thematic memoir arcs ($chaptersWithContent/6 completed).",
                isCompleted = chaptersWithContent >= 6,
                category = "Publication",
                actionHint = "Fill remaining empty chapters"
            )
        )

        val completedCount = steps.count { it.isCompleted }
        val percentage = ((completedCount.toFloat() / steps.size.toFloat()) * 100).toInt().coerceIn(0, 100)

        val status = when {
            percentage == 100 -> "Publication Ready"
            percentage >= 70 -> "Refining & Polishing"
            percentage >= 40 -> "Core Manuscript Drafting"
            else -> "Initial Setup & First Stories"
        }

        val nextAction = steps.firstOrNull { !it.isCompleted }?.title ?: "Ready for final PDF/EPUB export!"

        return BookReadinessReport(
            completionPercentage = percentage,
            completedStepsCount = completedCount,
            totalStepsCount = steps.size,
            steps = steps,
            readinessStatus = status,
            nextAction = nextAction
        )
    }
}
