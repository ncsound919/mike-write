package com.example.autonomous

import com.example.data.Memory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutonomousFeaturesTest {

    // -------------------------------------------------------------
    // 1. AUTONOMOUS MANUSCRIPT WEAVER TESTS
    // -------------------------------------------------------------
    @Test
    fun testManuscriptWeaverChronologicalDetectionAndInversions() {
        val memories = listOf(
            Memory(
                id = 1,
                transcript = "In 1985, I was promoted to regional director at the telecommunications firm.",
                formattedProse = "In 1985, I was promoted to regional director at the telecommunications firm.",
                chapter = "Chapter 3: Passions & Milestones",
                passageTitle = "The 1985 Promotion"
            ),
            Memory(
                id = 2,
                transcript = "Back in 1954, when I was in elementary school, we walked through the snow.",
                formattedProse = "Back in 1954, when I was in elementary school, we walked through the snow.",
                chapter = "Chapter 1: Early Days",
                passageTitle = "Winter of 1954"
            ),
            Memory(
                id = 3,
                transcript = "During college around 1968, the campus was alive with political demonstrations.",
                formattedProse = "During college around 1968, the campus was alive with political demonstrations.",
                chapter = "Chapter 2: Growing Up & Family",
                passageTitle = "Campus in 1968"
            )
        )

        val analysis = AutonomousManuscriptWeaver.analyzeTimeline(memories)

        // There should be inversions because 1985 is before 1954
        assertTrue("Should detect chronological inversions", analysis.hasInversions)
        assertTrue(analysis.inversionCount > 0)

        // Proposed order should be 1954, 1968, 1985
        val proposedYears = analysis.proposedOrder.map { it.detectedYear }
        assertEquals(listOf(1954, 1968, 1985), proposedYears)
        assertEquals(2L, analysis.proposedOrder[0].memoryId)
        assertEquals(3L, analysis.proposedOrder[1].memoryId)
        assertEquals(1L, analysis.proposedOrder[2].memoryId)
    }

    @Test
    fun testManuscriptWeaverNarrativeBridgeGeneration() = runBlocking {
        val scenes = listOf(
            AutonomousManuscriptWeaver.TimelineScene(
                memoryId = 1,
                originalIndex = 0,
                title = "Childhood",
                chapter = "Chapter 1: Early Days",
                text = "Growing up on the farm in 1950.",
                detectedYear = 1950,
                detectedAge = 6,
                detectedLifeStage = "early childhood",
                inferredChronologyScore = 1950.0,
                suggestedSequence = 1
            ),
            AutonomousManuscriptWeaver.TimelineScene(
                memoryId = 2,
                originalIndex = 1,
                title = "Leaving Home",
                chapter = "Chapter 2: Growing Up",
                text = "Packing my bags for college in 1965.",
                detectedYear = 1965,
                detectedAge = 18,
                detectedLifeStage = "early adulthood",
                inferredChronologyScore = 1965.0,
                suggestedSequence = 2
            )
        )

        val bridges = AutonomousManuscriptWeaver.generateNarrativeBridges(scenes)
        assertEquals(1, bridges.size)
        val bridge = bridges[0]
        assertEquals(1L, bridge.fromMemoryId)
        assertEquals(2L, bridge.toMemoryId)
        assertTrue("Bridge should mention chapter transition", bridge.bridgeSentence.contains("Chapter 1: Early Days"))
    }

    // -------------------------------------------------------------
    // 2. AUTONOMOUS EXPANSION ENGINE TESTS
    // -------------------------------------------------------------
    @Test
    fun testAutonomousExpansionEngineDetectsGaps() {
        val memories = listOf(
            // Factual action memory with zero sensory detail and no reflection
            Memory(
                id = 10,
                transcript = "I arrived at the headquarters building at nine in the morning and met with the committee members to submit the application paperwork for the permit.",
                formattedProse = "I arrived at the headquarters building at nine in the morning and met with the committee members to submit the application paperwork for the permit.",
                chapter = "Chapter 3: Milestones",
                passageTitle = "The Permit Meeting",
                reflection = "",
                sensoryDetails = ""
            ),
            // Short memory of only 6 words
            Memory(
                id = 11,
                transcript = "Then we moved into our house.",
                formattedProse = "Then we moved into our house.",
                chapter = "Chapter 3: Milestones",
                passageTitle = "Moving House"
            )
        )

        val report = AutonomousExpansionEngine.auditManuscriptGaps(memories)

        assertTrue("Should detect gaps in dry or brief passages", report.totalGapsFound > 0)
        assertTrue("Health score should be under 100 due to deficits", report.healthScore < 100)
        assertNotNull("Should provide top autonomous interview prompt", report.topAutonomousPrompt)

        val types = report.gaps.map { it.type }
        assertTrue("Should flag sensory atmosphere deficiency", types.contains(AutonomousExpansionEngine.GapType.SENSORY_ATMOSPHERE))
        assertTrue("Should flag emotional reflection deficiency", types.contains(AutonomousExpansionEngine.GapType.EMOTIONAL_REFLECTION))
    }

    // -------------------------------------------------------------
    // 3. AUTONOMOUS STYLE HARMONIZER TESTS
    // -------------------------------------------------------------
    @Test
    fun testAutonomousStyleHarmonizerAuditsAndRepairs() {
        val dirtyMemory = Memory(
            id = 20,
            transcript = "Needless to say, when you walk into the old garage, you could see all the tools, you know what I mean? I look at the old tractor and I say to him that we should fix it.",
            formattedProse = "Needless to say, when you walk into the old garage, you could see all the tools, you know what I mean? I look at the old tractor and I say to him that we should fix it.",
            chapter = "Chapter 2: Growing Up"
        )

        val scorecard = AutonomousStyleHarmonizer.auditStyleHealth(listOf(dirtyMemory))

        assertTrue("Should detect POV slips", scorecard.povStabilityPercent < 100)
        assertTrue("Should detect oral crutches", scorecard.oralCrutchFrequency > 0)
        assertTrue("Should detect stylistic issues", scorecard.detectedIssues.isNotEmpty())

        val result = AutonomousStyleHarmonizer.harmonizeMemory(dirtyMemory)
        assertTrue("Should perform repairs", result.changesCount > 0)

        val harmonized = result.harmonizedText
        assertFalse("Oral crutch 'needless to say' should be removed", harmonized.contains("Needless to say", ignoreCase = true))
        assertFalse("Oral crutch 'you know what I mean' should be removed", harmonized.contains("you know what I mean", ignoreCase = true))
        assertTrue("POV should be harmonized to first person", harmonized.contains("when I walked into") || harmonized.contains("I could see"))
        assertTrue("Present tense should be harmonized to past tense", harmonized.contains("I looked at") || harmonized.contains("I said to him"))
    }
}
