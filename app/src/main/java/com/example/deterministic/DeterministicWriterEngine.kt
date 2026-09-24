package com.example.deterministic

enum class CoverageGap {
    MISSING_SENSORY,
    MISSING_PERSON,
    MISSING_PLACE,
    MISSING_OUTCOME,
    MISSING_EMOTION,
    MISSING_DETAIL,
    NONE
}

data class CoverageAnalysis(
    val gaps: List<CoverageGap>,
    val detectedTopic: String
)

/**
 * Agent 4 (Date Normalizer), Agent 5 (Topic Tagger), Agent 7 (Question Selector),
 * Agent 8 (Template Filler), Agent 9 (Echo Confirmer), Agent 10 (Consistency Checker),
 * Agent 12 (Progress Reporter).
 */
object DeterministicWriterEngine {

    // Topic keywords taxonomy (Agent 5)
    private val TOPIC_TAXONOMY = mapOf(
        "Childhood & Roots" to listOf("childhood", "kid", "school", "young", "boy", "grandfather", "grandpa", "grandma", "toys", "cart", "yard", "play", "summer"),
        "Family & Heritage" to listOf("mom", "mother", "dad", "father", "sister", "brother", "dinner", "family", "tradition", "ancestor", "holiday", "uncle", "aunt"),
        "Passions & Milestones" to listOf("career", "job", "work", "passion", "hobby", "college", "sports", "football", "music", "guitar", "first car", "achievement"),
        "The Turning Point" to listOf("accident", "hospital", "injury", "stroke", "paralysis", "doctor", "ambulance", "surgery", "sudden", "day everything changed"),
        "Strength & Daily Life" to listOf("recovery", "rehab", "therapy", "wheelchair", "caregiver", "routine", "strength", "morning", "perseverance", "breathe", "healing"),
        "Wisdom & Legacy" to listOf("lesson", "wisdom", "advice", "legacy", "generations", "future", "gratitude", "peace", "reflection", "forgiveness")
    )

    // Gap analysis indicators
    private val SENSORY_KEYWORDS = listOf("smell", "sound", "cold", "warm", "taste", "bright", "loud", "wind", "sun", "dusty", "scent", "felt", "touch", "red", "blue", "wooden")
    private val EMOTION_KEYWORDS = listOf("felt", "scared", "joy", "thrill", "happy", "cried", "tears", "proud", "angry", "peace", "love", "heart", "nervous")
    private val OUTCOME_KEYWORDS = listOf("finally", "ended", "learned", "result", "afterward", "eventually", "taught me", "never forgot")

    /**
     * Agent 4 — Date Normalizer
     * Converts relative colloquial time phrases into structured ranges or standard references.
     */
    fun normalizeDateReference(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("last summer") -> "Summer (previous year)"
            lower.contains("this morning") -> "Today (Morning)"
            lower.contains("yesterday") -> "Recent Past"
            lower.contains("when i was a kid") || lower.contains("as a child") -> "Childhood Era"
            lower.contains("in my twenties") -> "Age 20–29"
            lower.contains("in my thirties") -> "Age 30–39"
            lower.contains("in my forties") -> "Age 40–49"
            lower.contains("in elementary school") -> "Early Childhood (Ages 6–11)"
            lower.contains("in high school") -> "Adolescence (Ages 14–18)"
            lower.contains("in college") -> "College Years"
            lower.contains("after the accident") -> "Post-Injury Period"
            lower.contains("during rehab") || lower.contains("in the hospital") -> "Recovery Period"
            lower.contains("in the sixties") || lower.contains("in the 60s") -> "1960–1969"
            lower.contains("in the seventies") || lower.contains("in the 70s") -> "1970–1979"
            lower.contains("in the eighties") || lower.contains("in the 80s") -> "1980–1989"
            lower.contains("in the nineties") || lower.contains("in the 90s") -> "1990–1999"
            lower.contains("in the early 2000s") -> "2000–2005"
            else -> null
        }
    }

    /**
     * Agent 5 — Topic Tagger
     * Labels memory based on keyword matches against the fixed taxonomy.
     */
    fun tagTopic(text: String): String {
        val lower = text.lowercase()
        var bestTopic = "Life Journey"
        var maxMatches = 0

        for ((topic, keywords) in TOPIC_TAXONOMY) {
            val matches = keywords.count { lower.contains(it) }
            if (matches > maxMatches) {
                maxMatches = matches
                bestTopic = topic
            }
        }
        return bestTopic
    }

    /**
     * Identifies coverage gaps in dictated memory.
     */
    fun analyzeGaps(text: String, entities: List<EntityRecord>): CoverageAnalysis {
        val lower = text.lowercase()
        val gaps = mutableListOf<CoverageGap>()

        val hasSensory = SENSORY_KEYWORDS.any { lower.contains(it) }
        val hasEmotion = EMOTION_KEYWORDS.any { lower.contains(it) }
        val hasOutcome = OUTCOME_KEYWORDS.any { lower.contains(it) }
        val hasPerson = entities.any { it.type == EntityType.PERSON } || lower.contains("who") || lower.contains("my ")
        val hasPlace = entities.any { it.type == EntityType.PLACE }

        if (!hasSensory) gaps.add(CoverageGap.MISSING_SENSORY)
        if (!hasPerson) gaps.add(CoverageGap.MISSING_PERSON)
        if (!hasPlace) gaps.add(CoverageGap.MISSING_PLACE)
        if (!hasOutcome) gaps.add(CoverageGap.MISSING_OUTCOME)
        if (!hasEmotion) gaps.add(CoverageGap.MISSING_EMOTION)
        if (gaps.isEmpty()) gaps.add(CoverageGap.MISSING_DETAIL)

        return CoverageAnalysis(gaps, tagTopic(text))
    }

    /**
     * Agent 7 & Agent 8 — Question Selector and Template Filler
     * Selects from pre-built question bank based on coverage gaps and slots extracted entities.
     * Guaranteed zero-cost, deterministic, and instant.
     */
    fun selectNextQuestion(
        rawMemory: String,
        entities: List<EntityRecord>,
        recentQuestions: List<String> = emptyList()
    ): String {
        val analysis = analyzeGaps(rawMemory, entities)
        val primaryGap = analysis.gaps.firstOrNull() ?: CoverageGap.MISSING_DETAIL

        val personEntity = entities.firstOrNull { it.type == EntityType.PERSON }?.name
        val placeEntity = entities.firstOrNull { it.type == EntityType.PLACE }?.name
        val objectEntity = entities.firstOrNull { it.type == EntityType.OBJECT }?.name

        // Agent 8: Template slot fill if entity exists
        if (personEntity != null && (primaryGap == CoverageGap.MISSING_PERSON || primaryGap == CoverageGap.MISSING_EMOTION)) {
            val candidate = "What do you remember most about $personEntity during that moment?"
            if (!recentQuestions.contains(candidate)) return candidate
        }

        if (placeEntity != null && primaryGap == CoverageGap.MISSING_PLACE) {
            val candidate = "How did being at $placeEntity feel different back then?"
            if (!recentQuestions.contains(candidate)) return candidate
        }

        if (objectEntity != null && (primaryGap == CoverageGap.MISSING_SENSORY || primaryGap == CoverageGap.MISSING_DETAIL)) {
            val candidate = "What significance did that $objectEntity hold for you at the time?"
            if (!recentQuestions.contains(candidate)) return candidate
        }

        // Agent 7: Bank Selection by Coverage Gap
        val bank = when (primaryGap) {
            CoverageGap.MISSING_SENSORY -> listOf(
                "If you close your eyes, what scents, sounds, or physical feelings come back first?",
                "What did that setting look and sound like around you?",
                "What physical sensations or atmosphere do you remember most clearly from that day?",
                "What sounds or background noise filled the air while this was taking place?"
            )
            CoverageGap.MISSING_PERSON -> listOf(
                "Who else was there beside you, and how did they react?",
                "Who was the most important person sharing that experience with you?",
                "How did the people around you respond to what happened?",
                "What did their expression or tone of voice tell you in that instant?"
            )
            CoverageGap.MISSING_PLACE -> listOf(
                "Where exactly were you standing when that took place?",
                "What did that room or place look like at that time in your life?",
                "What do you remember about the physical setting where this happened?",
                "How did the atmosphere of that location shape how you felt?"
            )
            CoverageGap.MISSING_OUTCOME -> listOf(
                "How did that pivotal scene end, and what changed after?",
                "What was the immediate aftermath of that moment?",
                "Looking back, what was the ultimate resolution of that day?",
                "How did life feel different the morning after that took place?"
            )
            CoverageGap.MISSING_EMOTION -> listOf(
                "What were you feeling deep down right at that very second?",
                "How did your emotions shift as the events unfolded?",
                "What emotional truth stayed with you long after that day?",
                "What was the inner feeling you had that you didn't say out loud?"
            )
            CoverageGap.MISSING_DETAIL, CoverageGap.NONE -> listOf(
                "What small detail from that day has never left your mind?",
                "What is something you understand about that time now that you didn't know back then?",
                "What would you want readers to understand about your perspective in that moment?",
                "What lesson or memory from that day would you pass along to your loved ones?"
            )
        }

        // Pick first question not recently used (recency penalty)
        val selected = bank.firstOrNull { !recentQuestions.contains(it) } ?: bank.first()
        return selected
    }

    /**
     * Agent 9 — Echo Confirmer
     * Deterministic restatement for confirmation dialogs without LLM paraphrase.
     */
    fun buildEchoConfirmation(cleanedText: String): String {
        val snippet = if (cleanedText.length > 80) cleanedText.take(77) + "..." else cleanedText
        return "I heard: $snippet. Say save to keep this memory, or delete to discard."
    }

    /**
     * Agent 10 — Consistency Checker
     * Detects discrepancies in person names or conflicting dates against the established entity registry.
     */
    fun checkConsistency(entities: List<EntityRecord>, existingRegistry: List<EntityRecord>): List<String> {
        val warnings = mutableListOf<String>()
        val existingNames = existingRegistry.map { it.name.lowercase() }

        for (e in entities) {
            // Check for potential near-duplicates / spelling typos (e.g. "Tomas" vs "Tommy")
            val nearMatch = existingRegistry.find {
                it.name.length > 3 && e.name.length > 3 &&
                it.name != e.name &&
                (it.name.startsWith(e.name.take(3), ignoreCase = true) || e.name.startsWith(it.name.take(3), ignoreCase = true))
            }
            if (nearMatch != null) {
                warnings.add("Possible name variation: '${e.name}' is similar to recorded entity '${nearMatch.name}'.")
            }
        }
        return warnings
    }

    /**
     * Agent 12 — Progress Reporter
     * Deterministic counter and word math metrics.
     */
    fun generateProgressReport(memoryCount: Int, totalWords: Int, chaptersWithContent: Int, targetChapters: Int): String {
        val estimatedPages = (totalWords / 250).coerceAtLeast(1)
        val completionPct = ((chaptersWithContent.toFloat() / targetChapters.coerceAtLeast(1).toFloat()) * 100).toInt().coerceIn(0, 100)
        return "Your memoir has $memoryCount recorded memories, totaling approximately $totalWords words and $estimatedPages pages across $chaptersWithContent of $targetChapters chapters ($completionPct% mapped)."
    }
}
