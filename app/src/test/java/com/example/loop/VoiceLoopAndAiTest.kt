package com.example.loop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.access.MikeWriteAccessibilityService
import com.example.ai.Interviewer
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.feedback.AudioHapticFeedback
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceLoopAndAiTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class TestListeningEngine : ListeningEngine {
        override var isListening: Boolean = false
        override fun start(continuous: Boolean, onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
            isListening = true
        }
        override fun stop() { isListening = false }
        override fun destroy() { isListening = false }
    }

    @Test
    fun testVoiceLoopBusStateEmissions() = runBlocking {
        VoiceLoopBus.publish(LoopState.Idle)
        assertEquals(LoopState.Idle, VoiceLoopBus.state.value)

        VoiceLoopBus.publish(LoopState.Listening("Listening..."))
        assertTrue(VoiceLoopBus.state.value is LoopState.Listening)

        VoiceLoopBus.publish(LoopState.Speaking("Hello World"))
        assertTrue(VoiceLoopBus.state.value is LoopState.Speaking)

        VoiceLoopBus.publish(LoopState.Processing("Processing story..."))
        assertTrue(VoiceLoopBus.state.value is LoopState.Processing)

        VoiceLoopBus.setSpoken("Spoken phrase")
        assertEquals("Spoken phrase", VoiceLoopBus.lastSpoken.value)

        VoiceLoopBus.setRecognized("Recognized command")
        assertEquals("Recognized command", VoiceLoopBus.lastRecognized.value)

        VoiceLoopBus.triggerSwitchAction(AccessibilitySwitchAction.TOGGLE_RECORD_OR_CONFIRM)
    }

    @Test
    fun testInterviewerFallbackMethods() = runBlocking {
        val interviewer = Interviewer()

        val followUp = interviewer.followUp("I ran into the old barn during a heavy rainstorm.")
        assertNotNull(followUp)
        assertTrue(followUp.isNotBlank())

        val prompt = interviewer.generateChapterPromptWithCraft(
            chapter = "Chapter 1: Early Days",
            craftFocus = "sensory details"
        )
        assertNotNull(prompt)
        assertTrue(prompt.isNotBlank())

        val summary = interviewer.synthesizeBookSummary(
            bookTitle = "Reflections of an Engineer",
            author = "Michael",
            memoryCount = 3,
            excerpts = listOf("I built bridges across the river.")
        )
        assertNotNull(summary)
        assertTrue(summary.contains("Reflections") || summary.contains("Michael") || summary.isNotBlank())

        val critique = interviewer.synthesizePublishingCritique(
            bookTitle = "Reflections of an Engineer",
            author = "Michael",
            chaptersCount = 3,
            totalWords = 1200,
            samplePassages = listOf("Smell of iron and fresh water on the bridge.")
        )
        assertNotNull(critique)
        assertTrue(critique.isNotBlank())
    }

    @Test
    fun testAudioHapticFeedbackExecution() {
        val feedback = AudioHapticFeedback(context)
        feedback.playFeedback(AudioHapticFeedback.Cue.START_RECORDING)
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
        feedback.playFeedback(AudioHapticFeedback.Cue.MEMORY_SAVED)
        feedback.playFeedback(AudioHapticFeedback.Cue.ACTION_UNDONE)
        feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
        feedback.playFeedback(AudioHapticFeedback.Cue.HELP_TRIGGERED)
        feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
        assertNotNull(feedback)
    }

    @Test
    fun testAccessibilityServiceInstantiation() {
        val service = MikeWriteAccessibilityService()
        assertNotNull(service)
    }

    @Test
    fun testVoiceLoopControllerCommands() = runBlocking {
        val db = MikeWriteDatabase.getInstance(context)
        val settings = SettingsStore(context)
        val interviewer = Interviewer()
        val speech = SpeechEngine(context)
        val listener = TestListeningEngine()

        val controller = VoiceLoopController(
            context = context,
            speech = speech,
            listener = listener,
            db = db,
            interviewer = interviewer,
            settings = settings
        )

        // Test start / stop
        controller.start()
        assertTrue(controller.isRunning)

        controller.stopAudiobookPlayback()
        controller.stopEverything()
        assertFalse(controller.isRunning)
    }
}
