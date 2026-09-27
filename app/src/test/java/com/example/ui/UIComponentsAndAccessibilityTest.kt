package com.example.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.data.BookReadinessEvaluator
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.LoopState
import com.example.loop.VoiceLoopController
import com.example.speech.AndroidSttEngine
import com.example.speech.SpeechEngine
import com.example.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UIComponentsAndAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testEyeGazeDwellCardRenderingAndInteraction() {
        var triggered = false

        composeTestRule.setContent {
            EyeGazeDwellCard(
                title = "Hands-Free Dictation",
                subtitle = "Dwell target for gaze selection",
                icon = Icons.Default.Mic,
                testTag = "test_dwell_card",
                onDwellTriggered = { triggered = true }
            )
        }

        composeTestRule.onNodeWithTag("test_dwell_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hands-Free Dictation").assertIsDisplayed()

        composeTestRule.onNodeWithTag("test_dwell_card").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testBookReadinessCardRendering() {
        val readinessReport = BookReadinessEvaluator.evaluate(
            bookTitle = "My Memoir",
            authorName = "Michael",
            dedication = "To my family",
            authorBio = "Author bio",
            memories = emptyList()
        )

        composeTestRule.setContent {
            BookReadinessCard(
                report = readinessReport
            )
        }

        composeTestRule.onNodeWithTag("book_readiness_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Book Publication Readiness").assertIsDisplayed()
    }

    @Test
    fun testSoundWaveVisualizerRendering() {
        composeTestRule.setContent {
            SoundWaveVisualizer(
                state = LoopState.Listening("Listening..."),
                audioLevel = 0.8f,
                modifier = Modifier
            )
        }

        // SoundWaveVisualizer renders Canvas successfully
    }

    @Test
    fun testUnifiedAutomationBarRendering() {
        val settings = SettingsStore(context)
        val db = MikeWriteDatabase.getInstance(context)
        val interviewer = Interviewer()
        val speech = SpeechEngine(context)
        val listener = AndroidSttEngine(context)
        val controller = VoiceLoopController(context, speech, listener, db, interviewer, settings)

        composeTestRule.setContent {
            UnifiedAutomationBar(
                controller = controller
            )
        }

        composeTestRule.onNodeWithTag("unified_automation_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("JUST WORKS PIPELINE").assertIsDisplayed()
    }

    @Test
    fun testAudiobookPlayerBarRendering() {
        composeTestRule.setContent {
            AudiobookPlayerBar(
                loopState = LoopState.Speaking("Now reading Chapter 1: Early Days..."),
                speechRate = 1.0f,
                onStopPlayback = {},
                onToggleSpeed = {}
            )
        }

        composeTestRule.onNodeWithTag("player_stop_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("READING ALOUD").assertIsDisplayed()
    }
}
