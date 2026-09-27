package com.example.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiConfigProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.caregiver.CaregiverScreen
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.theme.MikeWriteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h4000dp")
class CaregiverScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private class NoopListener : ListeningEngine {
        override var isListening: Boolean = false
        override fun start(
            continuous: Boolean,
            onPartial: (String) -> Unit,
            onResult: (String) -> Unit,
            onError: (String) -> Unit
        ) {}
        override fun stop() {}
        override fun destroy() {}
    }

    private fun controller(): VoiceLoopController = VoiceLoopController(
        context = application,
        speech = SpeechEngine(application),
        listener = NoopListener(),
        db = MikeWriteDatabase.getInstance(application),
        interviewer = Interviewer(AiTextEngine { AiConfigProvider.default() }),
        settings = SettingsStore(application)
    )

    private val memories = listOf(
        Memory(
            id = 1,
            createdAt = 1,
            transcript = "We built a treehouse in the summer with Grandpa.",
            formattedProse = "We built a treehouse in the summer with Grandpa.",
            passageTitle = "The Treehouse",
            chapter = "Chapter 1: Early Days",
            sensoryDetails = "Fresh pine and warm sun"
        ),
        Memory(
            id = 2,
            createdAt = 2,
            transcript = "I started my first job at the machine shop.",
            formattedProse = "I started my first job at the machine shop.",
            chapter = "Chapter 2: Growing Up & Family"
        )
    )

    private fun selectTab(label: String) {
        composeTestRule.onNodeWithText(label, substring = true).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `caregiver screen renders every tab and its controls`() {
        val controller = controller()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    CaregiverScreen(controller = controller, memories = memories, onBackToBuddy = {})
                }
            }
        }

        // Tab 0: Settings & Mic — metadata, switches and mic health test.
        composeTestRule.onNodeWithTag("live_echo_playback_switch").performClick()
        composeTestRule.onNodeWithTag("earcons_enabled_switch").performClick()
        composeTestRule.onNodeWithTag("book_title_field").performTextInput("My Life")
        composeTestRule.onNodeWithTag("author_name_field").performTextInput("Mike")
        composeTestRule.onNodeWithTag("gemini_api_key_field").performTextInput("AIzaTest")
        composeTestRule.onNodeWithTag("run_mic_test_button").performClick()
        composeTestRule.waitForIdle()

        selectTab("Book (2)")
        selectTab("Publishing")
        selectTab("Diagnostics")
        selectTab("Legal")
        selectTab("Autonomy Hub")

        controller.destroy()
    }

    @Test
    fun `caregiver screen handles an empty library`() {
        val controller = controller()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    CaregiverScreen(controller = controller, memories = emptyList(), onBackToBuddy = {})
                }
            }
        }
        selectTab("Book (0)")
        selectTab("Publishing")
        controller.destroy()
    }
}
