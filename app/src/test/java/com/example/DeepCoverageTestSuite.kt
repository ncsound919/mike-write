package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.autonomous.AutonomousExpansionEngine
import com.example.autonomous.AutonomousManuscriptWeaver
import com.example.autonomous.AutonomousStyleHarmonizer
import com.example.autonomous.UnifiedAutomationPipeline
import com.example.data.Memory
import com.example.data.SettingsStore
import com.example.feedback.AudioHapticFeedback
import com.example.loop.AccessibilitySwitchAction
import com.example.loop.LoopState
import com.example.loop.VoiceLoopBus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeepCoverageTestSuite {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        settings = SettingsStore(context)
        VoiceLoopBus.publish(LoopState.Idle)
    }

    // -------------------------------------------------------------
    // 1. AudioHapticFeedback Tests
    // -------------------------------------------------------------
    @Test
    fun testAudioHapticFeedbackAllCues() {
        val feedback = AudioHapticFeedback(context)
        
        AudioHapticFeedback.Cue.values().forEach { cue ->
            feedback.playFeedback(cue)
        }
        
        feedback.release()
        assertTrue(true)
    }

    // -------------------------------------------------------------
    // 2. VoiceLoopBus Thread Safety & Log Rotation Tests
    // -------------------------------------------------------------
    @Test
    fun testVoiceLoopBusLogBufferAndState() = runBlocking {
        VoiceLoopBus.setSpoken("Hello, this is a test prompt.")
        assertEquals("Hello, this is a test prompt.", VoiceLoopBus.lastSpoken.value)

        VoiceLoopBus.setRecognized("Recognized spoken text")
        assertEquals("Recognized spoken text", VoiceLoopBus.lastRecognized.value)

        VoiceLoopBus.setRms(0.75f)
        assertEquals(0.75f, VoiceLoopBus.audioLevels.value, 0.01f)

        VoiceLoopBus.setMicTestResult("Mic hardware test ok")
        assertEquals("Mic hardware test ok", VoiceLoopBus.micTestResult.value)

        VoiceLoopBus.logAccessibility("Switch activated")
        assertTrue(VoiceLoopBus.systemLogs.value.any { it.contains("Switch activated") })

        for (i in 1..70) {
            VoiceLoopBus.appendLog("Log entry #$i")
        }
        val logs = VoiceLoopBus.systemLogs.value
        assertTrue(logs.size <= 52)
        assertTrue(logs.last().contains("#70"))

        // Subscribe before emitting: switchActions is a replay-0 SharedFlow, so a
        // collector started after tryEmit would suspend forever. UNDISPATCHED starts
        // the collector synchronously up to its first suspension (the subscription),
        // and withTimeout guarantees the test fails instead of hanging.
        val action = withTimeout(5_000) {
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                VoiceLoopBus.switchActions.first()
            }
            VoiceLoopBus.triggerSwitchAction(AccessibilitySwitchAction.TOGGLE_RECORD_OR_CONFIRM)
            next.await()
        }
        assertEquals(AccessibilitySwitchAction.TOGGLE_RECORD_OR_CONFIRM, action)
    }

    // -------------------------------------------------------------
    // 3. Interviewer Fallback & Logic Tests
    // -------------------------------------------------------------
    @Test
    fun testInterviewerFallbackBehavior() = runBlocking {
        val interviewer = Interviewer()

        val elements = interviewer.analyzeBookElements(
            memoryText = "I worked at the old lumber mill in the summer of 1965.",
            chapter = "Chapter 1: Early Days",
            existingChapters = listOf("Chapter 1: Early Days")
        )

        assertNotNull(elements)
        assertTrue(elements.passageTitle.isNotBlank())
        assertTrue(elements.emotionalTone.isNotBlank())

        val followUp = interviewer.followUp("I learned to drive a tractor.", "Chapter 1: Early Days")
        assertNotNull(followUp)
        assertTrue(followUp.isNotBlank())

        val summary = interviewer.synthesizeBookSummary(
            bookTitle = "My Life Journey",
            author = "Michael",
            memoryCount = 3,
            excerpts = listOf("I was born in Ohio.", "We moved to California.", "I served in the navy.")
        )
        assertNotNull(summary)
        assertTrue(summary.contains("My Life Journey"))

        val craftPrompt = interviewer.generateChapterPromptWithCraft("Chapter 1: Early Days", "Sensory details")
        assertNotNull(craftPrompt)
        assertTrue(craftPrompt.isNotBlank())
    }

    // -------------------------------------------------------------
    // 4. Autonomous Expansion Engine Deep Edge Cases
    // -------------------------------------------------------------
    @Test
    fun testAutonomousExpansionEngineDeepAnalysis() {
        val memories = listOf(
            Memory(
                id = 101,
                transcript = "I went to school.",
                formattedProse = "I went to school.",
                chapter = "Chapter 1: Early Days",
                passageTitle = "First Day of School"
            ),
            Memory(
                id = 102,
                transcript = "In 1980 I started my business in Chicago. My brother John helped me.",
                formattedProse = "In 1980 I started my business in Chicago. My brother John helped me.",
                chapter = "Chapter 2: Career",
                passageTitle = "Business Launch"
            ),
            Memory(
                id = 103,
                transcript = "By 1995 our company had expanded across three states with fifty employees.",
                formattedProse = "By 1995 our company had expanded across three states with fifty employees.",
                chapter = "Chapter 2: Career",
                passageTitle = "Expansion"
            )
        )

        val report = AutonomousExpansionEngine.auditManuscriptGaps(memories)
        assertNotNull(report)
        assertTrue(report.totalGapsFound > 0)
        assertTrue(report.healthScore in 0..100)
        assertNotNull(report.topAutonomousPrompt)

        val pacingGaps = report.gaps.filter { it.type == AutonomousExpansionEngine.GapType.PACING_LEAP }
        assertTrue(pacingGaps.isNotEmpty())
    }

    // -------------------------------------------------------------
    // 5. Autonomous Style Harmonizer Deep Edge Cases
    // -------------------------------------------------------------
    @Test
    fun testAutonomousStyleHarmonizerDeepAnalysis() {
        val memories = listOf(
            Memory(
                id = 1,
                transcript = "Um, you know, I was— I basically went to the store, like, actually.",
                formattedProse = "Um, you know, I basically went to the store, like, actually.",
                chapter = "Chapter 1: Early Days"
            ),
            Memory(
                id = 2,
                transcript = "The ball was hit by John and the game was won by our team.",
                formattedProse = "The ball was hit by John and the game was won by our team.",
                chapter = "Chapter 1: Early Days"
            )
        )

        val scorecard = AutonomousStyleHarmonizer.auditStyleHealth(memories)
        assertNotNull(scorecard)
        assertTrue(scorecard.povStabilityPercent in 0..100)
        assertTrue(scorecard.detectedIssues.isNotEmpty())
        assertTrue(scorecard.summaryStatement.isNotBlank())
    }

    // -------------------------------------------------------------
    // 6. Autonomous Manuscript Weaver Deep Edge Cases
    // -------------------------------------------------------------
    @Test
    fun testAutonomousManuscriptWeaverDeepAnalysis() {
        val memories = listOf(
            Memory(
                id = 10,
                transcript = "In 1990 we moved to Texas.",
                chapter = "Chapter 2: Growing Up"
            ),
            Memory(
                id = 11,
                transcript = "In 1975 I was born in a small cottage.",
                chapter = "Chapter 1: Early Days"
            )
        )

        val report = AutonomousManuscriptWeaver.analyzeTimeline(memories)
        assertNotNull(report)
        assertTrue(report.scenes.isNotEmpty())
        assertTrue(report.hasInversions)
        assertEquals(1, report.inversionCount)
        assertEquals("In 1975 I was born in a small cottage.", report.proposedOrder.first().text)
    }

    // -------------------------------------------------------------
    // 7. Unified Automation Pipeline Deep Lifecycle
    // -------------------------------------------------------------
    @Test
    fun testUnifiedAutomationPipelineLifecycle() = runBlocking {
        val memory = Memory(
            id = 50,
            transcript = "We bought our first family farm in Ohio.",
            formattedProse = "We bought our first family farm in Ohio.",
            chapter = "Chapter 3: Passions & Milestones",
            passageTitle = "The Family Farm",
            writingTip = "Include smell of fresh earth."
        )

        val allMemories = listOf(
            Memory(id = 1, transcript = "In 1970 I went to school.", chapter = "Chapter 1"),
            Memory(id = 2, transcript = "In 1980 I graduated.", chapter = "Chapter 2"),
            memory
        )

        val result = UnifiedAutomationPipeline.executePipeline(
            savedMemory = memory,
            allManuscriptMemories = allMemories,
            settings = settings
        )

        assertNotNull(result)
        assertEquals(50L, result.memoryId)
        assertFalse(UnifiedAutomationPipeline.isAutomating.value)
        assertNotNull(UnifiedAutomationPipeline.lastExecution.value)

        UnifiedAutomationPipeline.clear()
        assertNull(UnifiedAutomationPipeline.lastExecution.value)
    }
}
