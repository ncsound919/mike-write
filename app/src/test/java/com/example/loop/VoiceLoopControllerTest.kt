package com.example.loop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.ai.LocalHttpServer
import com.example.ai.chatCompletion
import com.example.ai.testConfig
import com.example.data.BookChapters
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceLoopControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeListener : ListeningEngine {
        override var isListening: Boolean = false
        var onResult: ((String) -> Unit)? = null
        var onError: ((String) -> Unit)? = null
        override fun start(
            continuous: Boolean,
            onPartial: (String) -> Unit,
            onResult: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            isListening = true
            this.onResult = onResult
            this.onError = onError
        }
        override fun stop() { isListening = false }
        override fun destroy() { isListening = false }
    }

    private lateinit var db: MikeWriteDatabase
    private lateinit var settings: SettingsStore
    private lateinit var listener: FakeListener
    private lateinit var controller: VoiceLoopController

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        db = MikeWriteDatabase.getInstance(context)
        db.memoryDao().getAllMemoriesAsc().first().forEach { db.memoryDao().delete(it) }
        settings = SettingsStore(context)
        settings.smartAutoSave = false
        settings.autoEditorialPipeline = false
        settings.liveEchoPlayback = false
        settings.currentChapter = BookChapters.DEFAULT_CHAPTER
        listener = FakeListener()
        controller = VoiceLoopController(
            context = context,
            speech = SpeechEngine(context),
            listener = listener,
            db = db,
            interviewer = Interviewer(AiTextEngine { testConfig() }),
            settings = settings
        )
        controller.start()
    }

    @After
    fun tearDown() {
        controller.destroy()
        Dispatchers.resetMain()
    }

    private suspend fun recordedMemoryCount(): Int = db.memoryDao().getAllMemoriesAsc().first().size

    private suspend fun insertMemory(transcript: String, prose: String? = null): Long =
        db.memoryDao().insert(
            Memory(createdAt = System.currentTimeMillis(), transcript = transcript, formattedProse = prose, chapter = BookChapters.DEFAULT_CHAPTER)
        )

    // ---- Recording -> confirmation -> save / delete / undo ----

    @Test
    fun `record then done then save persists the dictated story`() = runBlocking {
        controller.handleUtterance("record")
        assertTrue(controller.isRecording)

        listener.onResult!!("We built a treehouse in the summer with Grandpa")
        controller.handleUtterance("done")

        controller.handleUtterance("save")
        val saved = db.memoryDao().getAllMemoriesAsc().first()
        assertTrue(saved.any { it.transcript.contains("treehouse") })
    }

    @Test
    fun `delete discards the pending draft without saving`() = runBlocking {
        controller.handleUtterance("record")
        listener.onResult!!("A story I want to discard")
        controller.handleUtterance("done")

        controller.handleUtterance("delete")
        assertEquals(0, recordedMemoryCount())
    }

    @Test
    fun `undo after save removes the memory and restores the draft`() = runBlocking {
        controller.handleUtterance("record")
        listener.onResult!!("A story to keep then undo")
        controller.handleUtterance("done")
        controller.handleUtterance("save")
        assertEquals(1, recordedMemoryCount())

        controller.handleUtterance("undo")
        assertEquals(0, recordedMemoryCount())
    }

    @Test
    fun `save with nothing pending is a no-op`() = runBlocking {
        controller.handleUtterance("save")
        assertEquals(0, recordedMemoryCount())
    }

    @Test
    fun `recording stt error with content auto-completes`() = runBlocking {
        controller.handleUtterance("record")
        listener.onResult!!("Content captured before the silence")
        listener.onError!!("silence_timeout")
        controller.handleUtterance("save")
        assertEquals(1, recordedMemoryCount())
    }

    @Test
    fun `record toggles off when already recording`() = runBlocking {
        controller.handleUtterance("record")
        assertTrue(controller.isRecording)
        controller.handleUtterance("record")
        assertFalse(controller.isRecording)
    }

    // ---- Simple command surface ----

    @Test
    fun `slower and faster adjust and clamp the speech rate`() = runBlocking {
        settings.speechRate = 1.0f
        controller.handleUtterance("slower")
        assertEquals(0.85f, settings.speechRate, 0.001f)
        controller.handleUtterance("faster")
        assertEquals(1.0f, settings.speechRate, 0.001f)

        repeat(10) { controller.handleUtterance("slower") }
        assertEquals(0.6f, settings.speechRate, 0.001f)
        repeat(20) { controller.handleUtterance("faster") }
        assertEquals(1.5f, settings.speechRate, 0.001f)
    }

    @Test
    fun `chapter command cycles to the next standard chapter`() = runBlocking {
        settings.currentChapter = BookChapters.STANDARD.first()
        controller.handleUtterance("chapter")
        assertEquals(BookChapters.STANDARD[1], settings.currentChapter)

        settings.currentChapter = BookChapters.STANDARD.last()
        controller.handleUtterance("chapter")
        assertEquals(BookChapters.STANDARD.first(), settings.currentChapter)
    }

    @Test
    fun `help and unknown commands keep the loop responsive`() = runBlocking {
        controller.handleUtterance("help")
        controller.handleUtterance("purple monkey dishwasher")
        assertTrue(controller.isRunning)
    }

    @Test
    fun `stop pauses the loop when not recording`() = runBlocking {
        controller.handleUtterance("stop")
        assertTrue(controller.isPaused)
    }

    // ---- Review / breakdown / tip ----

    @Test
    fun `review with no memories stays on the idle loop`() = runBlocking {
        controller.handleUtterance("review")
        controller.handleUtterance("next")
        controller.handleUtterance("back")
        controller.handleUtterance("breakdown")
        controller.handleUtterance("writing tip")
        assertTrue(controller.isRunning)
    }

    @Test
    fun `review walks saved memories and advances`() = runBlocking {
        insertMemory("First memory about the lake")
        insertMemory("Second memory about the workshop")
        controller.handleUtterance("review")
        controller.handleUtterance("next")
        controller.handleUtterance("back")
        controller.handleUtterance("breakdown")
        controller.handleUtterance("writing tip")
        assertTrue(controller.isRunning)
    }

    // ---- Book-level commands on empty and populated books ----

    @Test
    fun `book level commands handle an empty library`() = runBlocking {
        controller.handleUtterance("prompt me")
        controller.handleUtterance("read book")
        controller.handleUtterance("export")
        controller.handleUtterance("playback")
        controller.handleUtterance("auto write")
        controller.handleUtterance("find gaps")
        controller.handleUtterance("harmonize")
        assertTrue(controller.isRunning)
    }

    @Test
    fun `book level commands run against a populated library`() = runBlocking {
        insertMemory("In 1975 I was born in a small cottage with a red door.")
        insertMemory("In 1990 we moved to Texas and started a business.")
        controller.handleUtterance("prompt me")
        controller.handleUtterance("read book")
        controller.handleUtterance("export")
        controller.handleUtterance("playback")
        controller.handleUtterance("auto write")
        controller.handleUtterance("find gaps")
        controller.handleUtterance("harmonize")
        controller.handleUtterance("unified automation")
        assertTrue(controller.isRunning)
    }

    @Test
    fun `readiness command reports against populated library`() = runBlocking {
        insertMemory("A memory with some sensory detail.", prose = "A polished memory.")
        controller.handleUtterance("readiness")
        assertTrue(controller.isRunning)
    }

    // ---- AI reword flow (local server) ----

    @Test
    fun `reword with no passage does not crash`() = runBlocking {
        controller.handleUtterance("reword")
        assertTrue(controller.isRunning)
    }

    @Test
    fun `reword then save updates the latest passage prose`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Reworded prose","notes":"tightened"}"""))
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.9},"quality":{"score":2.0}}}""")
            .start()
        try {
            settings.jevBaseUrl = server.baseUrl
            settings.jevApiKey = "vck_test"
            settings.jevLocalUrl = server.baseUrl
            insertMemory("The original dictated story", prose = "The original prose")

            controller.handleUtterance("reword")
            controller.handleUtterance("save")

            val latest = db.memoryDao().getLatestMemory()
            assertNotNull(latest)
            assertEquals("Reworded prose", latest!!.formattedProse)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `reword then delete keeps the original prose`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Reworded prose"}"""))
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.9},"quality":{"score":2.0}}}""")
            .start()
        try {
            settings.jevBaseUrl = server.baseUrl
            settings.jevApiKey = "vck_test"
            settings.jevLocalUrl = server.baseUrl
            insertMemory("The original dictated story", prose = "The original prose")

            controller.handleUtterance("reword")
            controller.handleUtterance("delete")

            val latest = db.memoryDao().getLatestMemory()
            assertEquals("The original prose", latest!!.formattedProse)
        } finally {
            server.stop()
        }
    }

    // ---- Lifecycle ----

    @Test
    fun `pause and resume control the loop state`() = runBlocking {
        controller.pauseListening()
        assertTrue(controller.isPaused)
        controller.resumeListening()
        assertFalse(controller.isPaused)
    }

    @Test
    fun `stop everything resets transient state`() = runBlocking {
        controller.handleUtterance("record")
        controller.stopEverything()
        assertFalse(controller.isRunning)
        assertFalse(controller.isRecording)
    }
}
