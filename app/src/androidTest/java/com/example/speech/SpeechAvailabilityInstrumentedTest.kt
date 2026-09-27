package com.example.speech

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device check that the STT engine tracks the real speech-service availability. The
 * microphone permission is pre-granted so a listening session can actually open.
 */
@RunWith(AndroidJUnit4::class)
class SpeechAvailabilityInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} android.permission.RECORD_AUDIO"
            )
            .close()
    }

    @Test
    fun engineStartStopIsSafeOnRealHardware() {
        // Whether a session actually opens depends on the bound system speech service
        // (which may be busy/absent for the instrumentation process); the guarantee we
        // assert here is that start/stop/destroy never crash. State transitions are
        // covered deterministically in the JVM unit tests with the recognizer shadow.
        val engine = AndroidSttEngine(context)
        try {
            engine.start(continuous = false, onPartial = {}, onResult = {}, onError = {})
        } finally {
            engine.stop()
            engine.destroy()
        }
    }

    @Test
    fun speechRecognitionIsAvailableOnThisDevice() {
        // This is a real, permission-bearing device used for QA; if this ever fails the
        // product cannot function for the author, so assert it loudly.
        assertTrue(
            "No speech recognition service on this device",
            SpeechRecognizer.isRecognitionAvailable(context)
        )
    }
}
