package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that the real Compose host boots on-device without crashing. Microphone
 * permission is pre-granted via the shell so the privacy/consent gate is what renders;
 * consent is left unset, so the voice loop never opens the mic during the test.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {

    @Before
    fun grantMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
            .close()
    }

    @Test
    fun mainActivityReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Reaching this callback means the activity reached RESUMED and the
                // Compose content was set without throwing.
                requireNotNull(activity)
            }
        }
    }
}
