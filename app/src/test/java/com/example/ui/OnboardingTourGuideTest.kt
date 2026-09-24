package com.example.ui

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.components.OnboardingTourGuideDialog
import com.example.ui.theme.MikeWriteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingTourGuideTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class TestListeningEngine : ListeningEngine {
        override var isListening: Boolean = false
        override fun start(continuous: Boolean, onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {}
        override fun stop() { isListening = false }
        override fun destroy() { isListening = false }
    }

    private fun createController(): VoiceLoopController {
        return VoiceLoopController(
            context = context,
            speech = SpeechEngine(context),
            listener = TestListeningEngine(),
            db = MikeWriteDatabase.getInstance(context),
            interviewer = Interviewer(),
            settings = SettingsStore(context)
        )
    }

    @Test
    fun testTourGuideDialog_rendersInitialStepAndNavigates() {
        val controller = createController()
        var dismissed = false

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    OnboardingTourGuideDialog(
                        controller = controller,
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        // Verify initial step (Step 1)
        composeTestRule.onNodeWithTag("onboarding_tour_guide_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("STEP 1 OF 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Personal Memoir Companion").assertIsDisplayed()

        // Advance to Step 2
        composeTestRule.onNodeWithTag("tour_next_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("STEP 2 OF 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Big Voice Companion Orb").assertIsDisplayed()

        // Back to Step 1
        composeTestRule.onNodeWithTag("tour_previous_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("STEP 1 OF 5").assertIsDisplayed()

        // Advance through to the end
        repeat(4) {
            composeTestRule.onNodeWithTag("tour_next_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Final step should say "Start Writing Now"
        composeTestRule.onNodeWithText("STEP 5 OF 5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tour_next_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun testTourGuideDialog_skipTourDismisses() {
        val controller = createController()
        var dismissed = false

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    OnboardingTourGuideDialog(
                        controller = controller,
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Skip Tour").performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun testUserSettings_tourStatePersistence() {
        val controller = createController()
        
        // Initial state
        controller.settings.hasCompletedTour = false
        assertEquals(false, controller.settings.hasCompletedTour)

        // Mark completed
        controller.settings.hasCompletedTour = true
        assertEquals(true, controller.settings.hasCompletedTour)
    }
}
