package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeechEngineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun shadow() = Shadows.shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

    @Test
    fun `speak with an uninitialized engine returns instead of hanging`() = runBlocking {
        val engine = SpeechEngine(context)
        engine.speak("nothing should block this")
        engine.destroy()
    }

    @Test
    fun `awaitReady is false before initialization`() = runBlocking {
        val engine = SpeechEngine(context)
        assertFalse(engine.awaitReady(timeoutMs = 50))
        engine.destroy()
    }

    @Test
    fun `initialization makes the engine ready`() = runBlocking {
        val engine = SpeechEngine(context)
        shadow().onInitListener.onInit(TextToSpeech.SUCCESS)
        assertTrue(engine.isReady)
        assertTrue(engine.awaitReady())
        engine.destroy()
    }

    @Test
    fun `speak sends text and resumes when the utterance completes`() = runBlocking {
        val engine = SpeechEngine(context)
        val ttsShadow = shadow()
        ttsShadow.onInitListener.onInit(TextToSpeech.SUCCESS)

        val speaking = async { engine.speak("hello world") }
        yield() // let the speak call register its continuation
        ttsShadow.utteranceProgressListener.onDone("u_1")
        speaking.await()

        assertEquals("hello world", ttsShadow.lastSpokenText)
        engine.destroy()
    }

    @Test
    fun `stop and destroy reach the underlying engine`() {
        val engine = SpeechEngine(context)
        val ttsShadow = shadow()
        ttsShadow.onInitListener.onInit(TextToSpeech.SUCCESS)

        engine.stop()
        assertTrue(ttsShadow.isStopped)

        engine.destroy()
        assertTrue(ttsShadow.isShutdown)
        assertFalse(engine.isReady)
    }

    @Test
    fun `rate and pitch setters are clamped and do not crash`() {
        val engine = SpeechEngine(context)
        shadow().onInitListener.onInit(TextToSpeech.SUCCESS)
        engine.setSpeechRate(5f)
        engine.setSpeechPitch(-1f)
        engine.setSpeechRate(0.8f)
        engine.setSpeechPitch(1.2f)
        engine.destroy()
    }
}
