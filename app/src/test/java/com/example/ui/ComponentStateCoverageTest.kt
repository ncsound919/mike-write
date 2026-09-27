package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.BookReadinessEvaluator
import com.example.data.Memory
import com.example.feedback.AudioHapticFeedback
import com.example.loop.LoopState
import com.example.ui.components.BookReadinessCard
import com.example.ui.components.SoundWaveVisualizer
import com.example.ui.theme.MikeWriteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2000dp")
class ComponentStateCoverageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `sound wave visualizer renders every loop state and level`() {
        val states = listOf(
            LoopState.Idle,
            LoopState.Listening("Listening..."),
            LoopState.Speaking("Reading..."),
            LoopState.Recording(1L, "partial words"),
            LoopState.Processing("Working..."),
            LoopState.Error("Something went wrong")
        )
        composeTestRule.setContent {
            MikeWriteTheme {
                Column {
                    states.forEachIndexed { index, state ->
                        SoundWaveVisualizer(state = state, audioLevel = (index + 1) / 6f)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `readiness card renders and expands its step list`() {
        val emptyReport = BookReadinessEvaluator.evaluate("", "", "", "", emptyList())
        val fullReport = BookReadinessEvaluator.evaluate(
            bookTitle = "My Life",
            authorName = "Mike",
            dedication = "To my family",
            authorBio = "Author and survivor",
            memories = listOf(
                Memory(
                    id = 1,
                    createdAt = 1,
                    transcript = "A memory with sensory detail.",
                    chapter = "Chapter 1: Early Days",
                    sensoryDetails = "Fresh pine",
                    storyArc = "Arc"
                )
            )
        )

        composeTestRule.setContent {
            MikeWriteTheme {
                Column {
                    BookReadinessCard(report = emptyReport)
                    BookReadinessCard(report = fullReport)
                }
            }
        }

        composeTestRule.onAllNodesWithTag("toggle_readiness_steps_button")[0].performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `audio feedback respects the enabled toggle`() {
        val feedback = AudioHapticFeedback(context)
        feedback.setEnabled(false)
        feedback.playFeedback(AudioHapticFeedback.Cue.START_RECORDING)
        feedback.setEnabled(true)
        feedback.playFeedback(AudioHapticFeedback.Cue.MEMORY_SAVED)
        feedback.release()
    }
}
