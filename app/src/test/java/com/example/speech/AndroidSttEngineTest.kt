package com.example.speech

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.example.loop.VoiceLoopBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidSttEngineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @Before
    fun setUp() {
        // SpeechRecognizer.isRecognitionAvailable() queries for a recognition service;
        // register a stub so the engine proceeds past its availability guard.
        val resolveInfo = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = context.packageName
                name = "com.example.StubRecognitionService"
            }
        }
        Shadows.shadowOf(context.packageManager)
            .addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), resolveInfo)
    }

    private fun listenerShadow() =
        Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())

    private fun results(vararg texts: String): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*texts))
    }

    @Test
    fun `ready and end of speech toggle listening`() {
        val engine = AndroidSttEngine(context)
        engine.start(continuous = false, onPartial = {}, onResult = {}, onError = {})
        val shadow = listenerShadow()

        shadow.triggerOnReadyForSpeech(Bundle())
        assertTrue(engine.isListening)

        shadow.triggerOnEndOfSpeech()
        assertFalse(engine.isListening)
        engine.destroy()
    }

    @Test
    fun `final result is delivered and recorded`() {
        val engine = AndroidSttEngine(context)
        var recognized: String? = null
        engine.start(false, {}, { recognized = it }, {})
        val shadow = listenerShadow()

        shadow.triggerOnResults(results("Hello story segment"))
        assertEquals("Hello story segment", recognized)
        assertEquals("Hello story segment", VoiceLoopBus.lastRecognized.value)
        assertFalse(engine.isListening)
        engine.destroy()
    }

    @Test
    fun `partial results are delivered to the callback`() {
        val engine = AndroidSttEngine(context)
        var partial: String? = null
        engine.start(false, { partial = it }, {}, {})
        listenerShadow().triggerOnPartialResults(results("partial words"))

        assertEquals("partial words", partial)
        engine.destroy()
    }

    @Test
    fun `rms updates the shared audio level`() {
        val engine = AndroidSttEngine(context)
        engine.start(false, {}, {}, {})
        listenerShadow().triggerOnRmsChanged(8f)
        assertEquals(1.0f, VoiceLoopBus.audioLevels.value, 0.001f)
        engine.destroy()
    }

    @Test
    fun `non-continuous network error is reported`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(false, {}, {}, { error = it })
        listenerShadow().triggerOnError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals("Network error", error)
        engine.destroy()
    }

    @Test
    fun `continuous silence after speech auto-finalizes`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(true, {}, {}, { error = it })
        val shadow = listenerShadow()

        // A partial result marks that speech was heard this session.
        shadow.triggerOnPartialResults(results("the author started speaking"))
        shadow.triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals("silence_timeout", error)
        engine.destroy()
    }

    @Test
    fun `continuous initial silence times out after three retries`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(true, {}, {}, { error = it })
        val shadow = listenerShadow()

        repeat(3) { shadow.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH) }
        assertEquals("initial_silence_timeout", error)
        engine.destroy()
    }

    @Test
    fun `busy client error recreates instead of reporting`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(false, {}, {}, { error = it })
        listenerShadow().triggerOnError(SpeechRecognizer.ERROR_CLIENT)
        assertNull(error)
        engine.destroy()
    }

    @Test
    fun `errors are ignored after an intentional stop`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(false, {}, {}, { error = it })
        engine.stop()
        listenerShadow().triggerOnError(SpeechRecognizer.ERROR_NETWORK)
        assertNull(error)
        engine.destroy()
    }

    @Test
    fun `blank final result reports empty speech`() {
        val engine = AndroidSttEngine(context)
        var error: String? = null
        engine.start(false, {}, {}, { error = it })
        listenerShadow().triggerOnResults(results())
        assertEquals("empty_speech", error)
        engine.destroy()
    }
}
