package com.example.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiConfigProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.buddy.BuddyScreen
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.theme.MikeWriteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h4000dp")
class BuddyScreenTest {

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
            approved = true
        ),
        Memory(
            id = 2,
            createdAt = 2,
            transcript = "I started my first job at the machine shop.",
            formattedProse = "I started my first job at the machine shop.",
            chapter = "Chapter 2: Growing Up & Family",
            approved = true
        )
    )

    @Test
    fun `buddy screen controls respond to interaction`() {
        val controller = controller()
        var settingsOpened = false
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BuddyScreen(
                        controller = controller,
                        memories = memories,
                        onOpenSettings = { settingsOpened = true }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("text_size_stepper_button").performClick()
        composeTestRule.onNodeWithTag("input_mode_stepper_button").performClick()
        composeTestRule.onNodeWithTag("active_chapter_header_chip").performClick()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        assertTrue(settingsOpened)

        composeTestRule.onNodeWithTag("copy_memory_1").performClick()
        composeTestRule.onNodeWithTag("inspect_memory_1").performClick()
        composeTestRule.waitForIdle()

        controller.destroy()
    }

    @Test
    fun `buddy screen opens the export dialog`() {
        val controller = controller()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BuddyScreen(controller = controller, memories = memories, onOpenSettings = {})
                }
            }
        }

        composeTestRule.onNodeWithTag("export_chapter_button").performClick()
        composeTestRule.waitForIdle()
        controller.destroy()
    }
}
