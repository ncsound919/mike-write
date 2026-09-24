package com.example.deterministic

data class WritingCraftRecommendation(
    val category: String,
    val observation: String,
    val actionableGuidance: String,
    val exampleBefore: String? = null,
    val exampleAfter: String? = null
)

/**
 * Deterministic Writing Craft & Pacing Coach
 * Provides tailored storytelling skills, sensory coaching, and pacing advice on-device
 * without requiring generative API calls.
 */
object CraftCoachAgent {

    private val PASSIVE_VOICE_REGEX = Regex("(?i)\\b(was|were|is|are|been)\\s+([a-z]+ed)\\b")
    private val WEAK_ADVERBS = listOf("very", "really", "extremely", "suddenly", "actually", "quite", "somewhat", "nearly", "mostly")

    /**
     * Analyzes writing mechanics deterministically and recommends craft enhancements.
     */
    fun analyzeCraft(text: String, analysis: CoverageAnalysis, entities: List<EntityRecord>): List<WritingCraftRecommendation> {
        val recommendations = mutableListOf<WritingCraftRecommendation>()
        val lower = text.lowercase()

        // 1. "Show, Don't Tell" Sensory Coaching
        if (analysis.gaps.contains(CoverageGap.MISSING_SENSORY)) {
            recommendations.add(
                WritingCraftRecommendation(
                    category = "Sensory Immersion",
                    observation = "The scene describes the action without anchoring the physical environment.",
                    actionableGuidance = "Ground the scene by naming one ambient sound (e.g., ticking clock, gravel underfoot) or tactile temperature.",
                    exampleBefore = "I sat in the room waiting for the doctor.",
                    exampleAfter = "The fluorescent lights hummed above the cold vinyl exam table while I waited."
                )
            )
        }

        // 2. Character Vantage Point & Dialogue Coaching
        if (analysis.gaps.contains(CoverageGap.MISSING_PERSON)) {
            val people = entities.filter { it.type == EntityType.PERSON }
            if (people.isNotEmpty()) {
                val person = people.first().name
                recommendations.add(
                    WritingCraftRecommendation(
                        category = "Character Presence",
                        observation = "You mentioned $person, but their emotional reaction isn't captured.",
                        actionableGuidance = "Describe a small physical tell from $person (a clenched jaw, a quiet sigh, or an averted gaze).",
                        exampleBefore = "$person was in the room with me.",
                        exampleAfter = "$person stood with arms folded tightly, staring at the floorboards in silence."
                    )
                )
            }
        }

        // 3. Pacing & Sentence Rhythm Analysis
        val sentences = SegmenterAgent.segment(text)
        if (sentences.isNotEmpty()) {
            val lengths = sentences.map { it.split(Regex("\\s+")).size }
            val avgLength = lengths.average()
            if (avgLength > 28) {
                recommendations.add(
                    WritingCraftRecommendation(
                        category = "Sentence Pacing & Cadence",
                        observation = "Sentences average ${avgLength.toInt()} words each, creating a dense reading rhythm.",
                        actionableGuidance = "Vary sentence length: place a punchy 3-5 word sentence directly after a long clause to highlight impact.",
                        exampleBefore = "The morning of the operation was bright and cold and everyone was quiet because we knew what was coming.",
                        exampleAfter = "The morning of the operation was cold. Nobody spoke. We all knew what was coming."
                    )
                )
            }
        }

        // 4. Emotional Arc & Stakes
        if (analysis.gaps.contains(CoverageGap.MISSING_EMOTION)) {
            recommendations.add(
                WritingCraftRecommendation(
                    category = "Internal Resonance",
                    observation = "The sequence of events is clear, but the inner vulnerability is guarded.",
                    actionableGuidance = "Share what was running through your mind that you couldn't admit to anyone at that table.",
                    exampleBefore = "We finished dinner and went outside.",
                    exampleAfter = "I swallowed the lump in my throat, smiled for Mom, and stepped out onto the dark porch."
                )
            )
        }

        return recommendations
    }

    /**
     * Helper to evaluate a list of memories and return high-level recommendations & gap observations.
     */
    fun evaluateStory(memories: List<com.example.data.Memory>): CraftReport {
        if (memories.isEmpty()) {
            return CraftReport(
                recommendations = listOf("Record your first story passage to begin receiving craft coaching."),
                fullCraftDetails = emptyList()
            )
        }

        val combinedText = memories.joinToString(" ") { it.transcript }
        val entities = EntityRegistryAgent.extractEntities(combinedText)
        val analysis = DeterministicWriterEngine.analyzeGaps(combinedText, entities)
        val craftDetails = analyzeCraft(combinedText, analysis, entities)

        val recs = if (craftDetails.isNotEmpty()) {
            craftDetails.map { "${it.category}: ${it.actionableGuidance}" }
        } else {
            listOf("Pacing and sensory grounding are strong. Expand on character dialogues and internal reflections next.")
        }

        return CraftReport(
            recommendations = recs,
            fullCraftDetails = craftDetails
        )
    }
}

data class CraftReport(
    val recommendations: List<String>,
    val fullCraftDetails: List<WritingCraftRecommendation>
)
