package com.example.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiConfigProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.components.AutonomousBookwritingHub
import com.example.ui.theme.MikeWriteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h4000dp")
class AutonomousBookwritingHubTest {

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

    private fun memory(id: Long, transcript: String, chapter: String, prose: String? = null) = Memory(
        id = id,
        createdAt = id,
        transcript = transcript,
        formattedProse = prose ?: transcript,
        chapter = chapter
    )

    private val mixedMemories = listOf(
        memory(1, "In 1990 we moved to Texas and ran a small business.", "Chapter 2"),
        memory(2, "In 1975 I was born in a cottage with a red door.", "Chapter 1"),
        memory(3, "Um, you know, the ball was thrown by my brother and I basically ran.", "Chapter 1"),
        memory(4, "I went to school.", "Chapter 1")
    )

    @Test
    fun `hub renders every sub section`() {
        val controller = controller()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    AutonomousBookwritingHub(controller = controller, memories = mixedMemories)
                }
            }
        }

        composeTestRule.onNodeWithTag("autonomy_tab_0").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("generate_bridges_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("autonomy_tab_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("autonomy_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("batch_harmonize_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("autonomy_tab_3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Run Pipeline").performClick()
        composeTestRule.waitForIdle()

        controller.destroy()
    }

    @Test
    fun `hub renders empty state without crashing`() {
        val controller = controller()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    AutonomousBookwritingHub(controller = controller, memories = emptyList())
                }
            }
        }
        composeTestRule.onNodeWithTag("autonomy_tab_0").performClick()
        composeTestRule.onNodeWithTag("autonomy_tab_1").performClick()
        composeTestRule.onNodeWithTag("autonomy_tab_2").performClick()
        composeTestRule.onNodeWithTag("autonomy_tab_3").performClick()
        composeTestRule.waitForIdle()
        controller.destroy()
    }
}
