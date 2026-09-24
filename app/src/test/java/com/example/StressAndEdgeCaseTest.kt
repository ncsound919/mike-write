package com.example

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.autonomous.AutonomousExpansionEngine
import com.example.autonomous.AutonomousManuscriptWeaver
import com.example.autonomous.AutonomousStyleHarmonizer
import com.example.autonomous.UnifiedAutomationPipeline
import com.example.buddy.BuddyScreen
import com.example.caregiver.CaregiverScreen
import com.example.data.ChapterEntity
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.deterministic.CleanerAgent
import com.example.deterministic.DeterministicWriterEngine
import com.example.loop.LoopState
import com.example.loop.VoiceLoopBus
import com.example.loop.VoiceLoopController
import com.example.speech.Command
import com.example.speech.CommandParser
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.components.*
import com.example.ui.theme.MikeWriteTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StressAndEdgeCaseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class StressTestListeningEngine : ListeningEngine {
        override var isListening: Boolean = false
        var lastCallback: ((String) -> Unit)? = null
        var errorCallback: ((String) -> Unit)? = null

        override fun start(
            continuous: Boolean,
            onPartial: (String) -> Unit,
            onResult: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            isListening = true
            lastCallback = onResult
            errorCallback = onError
        }

        override fun stop() {
            isListening = false
        }

        override fun destroy() {
            isListening = false
        }
    }

    private fun createController(listener: ListeningEngine = StressTestListeningEngine()): VoiceLoopController {
        return VoiceLoopController(
            context = context,
            speech = SpeechEngine(context),
            listener = listener,
            db = MikeWriteDatabase.getInstance(context),
            interviewer = Interviewer(),
            settings = SettingsStore(context)
        )
    }

    // 1. CONCURRENCY & RAPID MULTI-THREAD STRESS TESTING
    @Test
    fun testConcurrency_rapidSwitchAndVoiceCommands() = runBlocking {
        val controller = createController()
        val jobList = mutableListOf<Job>()

        // Dispatch 30 parallel concurrent utterances and switch actions
        repeat(30) { index ->
            val job = launch(Dispatchers.Default) {
                when (index % 5) {
                    0 -> controller.handleUtterance("record")
                    1 -> controller.handleUtterance("done")
                    2 -> controller.handleUtterance("prompt me")
                    3 -> controller.handleUtterance("review")
                    else -> controller.handleUtterance("breakdown")
                }
            }
            jobList.add(job)
        }

        jobList.joinAll()
        // Ensure controller is in a valid state and did not crash or hang
        assertNotNull(VoiceLoopBus.state.value)
    }

    // 2. EXTREME STRING & INPUT SIZE EDGE CASES
    @Test
    fun testEdgeCases_massiveTranscriptProcessing() = runBlocking {
        val controller = createController()
        val db = MikeWriteDatabase.getInstance(context)

        // Generate a huge 10,000-word transcript
        val wordSample = "Growing up in a bustling neighborhood with old oak trees and brick houses. "
        val massiveStory = buildString {
            repeat(1000) { append(wordSample) }
        }

        val cleaned = CleanerAgent.clean(massiveStory)
        assertTrue(cleaned.isNotBlank())

        val analyzed = Interviewer().analyzeBookElements(cleaned, "Chapter 1: Early Days", listOf("Chapter 1: Early Days"))
        assertNotNull(analyzed.formattedProse)
        assertTrue(analyzed.formattedProse.isNotBlank())

        val memory = Memory(
            createdAt = System.currentTimeMillis(),
            transcript = massiveStory,
            formattedProse = analyzed.formattedProse,
            passageTitle = "Massive Passage",
            chapter = "Chapter 1: Early Days"
        )

        val id = db.memoryDao().insert(memory)
        assertTrue(id > 0)

        val retrieved = db.memoryDao().getMemoryById(id)
        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
    }

    @Test
    fun testEdgeCases_emptyWhitespaceAndSpecialUnicode() = runBlocking {
        val controller = createController()

        // Blank and pure whitespace strings
        val emptyInputs = listOf("", "   ", "\n\t\r", "   \n   \t  ")
        for (input in emptyInputs) {
            val cleaned = CleanerAgent.clean(input)
            assertTrue(cleaned.isBlank())
            // Utterance should handle empty without crashing
            controller.handleUtterance(input)
        }

        // Special symbols, emojis, and international characters
        val specialInputs = listOf(
            "🌟 In 1968, we watched the Apollo mission on a grainy TV! 🚀",
            "Story with symbols: @ # $ % ^ & * ( ) _ + = ~ ` < > ? / | \\",
            "Arabic / Hebrew script: السلام عليكم مرحبا بالعالم",
            "Accents: Café, façade, naïve, résumé, São Paulo, München"
        )

        for (input in specialInputs) {
            val cleaned = CleanerAgent.clean(input)
            assertTrue(cleaned.isNotBlank())
            val elements = Interviewer().analyzeBookElements(cleaned, "Chapter 1: Early Days", emptyList())
            assertNotNull(elements.formattedProse)
        }
    }

    // 3. DATABASE STRESS TESTING (BULK INSERTIONS & QUERIES)
    @Test
    fun testDatabaseStress_bulkInsertAndAggregations() = runBlocking {
        val db = MikeWriteDatabase.getInstance(context)
        val sampleChapters = listOf(
            "Chapter 1: Early Days",
            "Chapter 2: Career & Passions",
            "Chapter 3: Family & Love",
            "Chapter 4: Turning Points"
        )

        // Bulk insert 100 memories across different chapters
        val memoriesToInsert = (1..100).map { i ->
            val chap = sampleChapters[i % sampleChapters.size]
            Memory(
                createdAt = System.currentTimeMillis() + i,
                transcript = "This is memory entry number $i describing an important life moment.",
                formattedProse = "This is memory entry number $i describing an important life moment with rich narrative depth.",
                passageTitle = "Passage #$i",
                emotionalTone = if (i % 2 == 0) "Joyful" else "Reflective",
                chapter = chap,
                approved = true
            )
        }

        db.memoryDao().insertAll(memoriesToInsert)

        val all = db.memoryDao().getAllMemories().first()
        assertTrue(all.size >= 100)

        // Test chapter-filtered queries
        val chap1Memories = db.memoryDao().getMemoriesByChapter("Chapter 1: Early Days").first()
        assertTrue(chap1Memories.isNotEmpty())

        // Test autonomous pipeline stress on bulk data
        val weaverResult = AutonomousManuscriptWeaver.weaveChapters(all)
        assertTrue(weaverResult.chapters.isNotEmpty())

        val harmonizerResult = AutonomousStyleHarmonizer.harmonizeManuscript(all)
        assertNotNull(harmonizerResult.harmonizedManuscript)
    }

    // 4. COMMAND PARSER STRESS & NOISE TOLERANCE
    @Test
    fun testCommandParser_robustnessWithNoisyUtterances() {
        val testCases = mapOf(
            "hey can you please record my voice" to Command.RECORD,
            "start recording now" to Command.RECORD,
            "dictate story" to Command.RECORD,
            "that's it all done finished" to Command.DONE,
            "stop recording please" to Command.STOP,
            "wrap up the passage" to Command.DONE,
            "could you prompt me with a question" to Command.PROMPT,
            "interview question please" to Command.PROMPT,
            "let's review the whole book" to Command.REVIEW,
            "read it aloud to me" to Command.REVIEW,
            "playback live draft" to Command.PLAYBACK,
            "show me the breakdown of my story" to Command.DECONSTRUCT,
            "give me a writing tip" to Command.TIP,
            "scratch that undo please" to Command.UNDO,
            "save this passage" to Command.SAVE,
            "yes keep it" to Command.YES,
            "delete discard no" to Command.DELETE,
            "can you speak slower" to Command.SLOWER,
            "speak faster please" to Command.FASTER,
            "help what can i say" to Command.HELP,
            "export my memoir to pdf" to Command.EXPORT,
            "auto sequence timeline" to Command.AUTO_SEQUENCE,
            "find gaps in my stories" to Command.FIND_GAPS,
            "harmonize my voice" to Command.HARMONIZE
        )

        for ((input, expected) in testCases) {
            val parsed = CommandParser.parse(input)
            assertEquals("Failed parsing for input: '$input'", expected, parsed)
        }
    }

    // 5. STT ERROR RECOVERY STRESS
    @Test
    fun testSttErrorRecovery_handlesAllErrorCodesWithoutCrashing() = runBlocking {
        val listener = StressTestListeningEngine()
        val controller = createController(listener)

        controller.start()

        val errors = listOf(
            "ERROR_NO_MATCH",
            "ERROR_NETWORK",
            "ERROR_AUDIO",
            "ERROR_SERVER",
            "ERROR_SPEECH_TIMEOUT",
            "silence_timeout",
            "initial_silence_timeout",
            "unknown_hardware_glitch"
        )

        for (err in errors) {
            listener.errorCallback?.invoke(err)
            // Verify loop didn't crash
            assertNotNull(VoiceLoopBus.state.value)
        }
    }

    // 6. UI COMPONENT STRESS & EMPTY/EXTREME STATE RENDERING
    @Test
    fun testUiInteractivity_rendersWithZeroMemories() {
        val controller = createController()

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BuddyScreen(
                        controller = controller,
                        memories = emptyList(),
                        onOpenSettings = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testUiInteractivity_rendersWithMassiveMemoryList() {
        val controller = createController()
        val heavyMemories = (1..50).map { i ->
            Memory(
                id = i.toLong(),
                createdAt = System.currentTimeMillis() - (i * 100000L),
                transcript = "This is a detailed memory story #$i reflecting on family, work, and lifelong learning.",
                formattedProse = "This is a detailed memory story #$i reflecting on family, work, and lifelong learning.",
                passageTitle = "Life Story Passage #$i",
                emotionalTone = "Inspiring",
                chapter = "Chapter 1: Early Days",
                approved = true
            )
        }

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BuddyScreen(
                        controller = controller,
                        memories = heavyMemories,
                        onOpenSettings = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testUiInteractivity_componentsStress() {
        val controller = createController()

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    UnifiedAutomationBar(
                        controller = controller,
                        fontScale = 1.5f
                    )
                    BookReadinessCard(
                        memories = emptyList(),
                        fontScale = 1.25f,
                        onExportClick = {},
                        onReviewAloud = {}
                    )
                    SoundWaveVisualizer(
                        state = LoopState.Recording(System.currentTimeMillis(), "Visualizer test"),
                        audioLevel = 0.95f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}
