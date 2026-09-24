package com.example.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.buddy.BuddyScreen
import com.example.caregiver.CaregiverScreen
import com.example.data.BookReadinessEvaluator
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.LoopState
import com.example.loop.VoiceLoopController
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import com.example.ui.components.*
import com.example.ui.theme.MikeWriteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class TestListeningEngine : ListeningEngine {
        override var isListening: Boolean = false
        override fun start(continuous: Boolean, onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {}
        override fun stop() { isListening = false }
        override fun destroy() { isListening = false }
    }

    private val sampleMemories = listOf(
        Memory(
            id = 1,
            transcript = "I built my first telescope with my uncle.",
            formattedProse = "Under the vast country sky, my uncle taught me how to grind glass mirrors for a telescope.",
            passageTitle = "Stargazing Nights",
            chapter = "Chapter 1: Early Days",
            storyArc = "Discovering astronomy",
            reflection = "Wonder never fades",
            charactersAndPerspectives = "Uncle Dave guiding the lens",
            sensoryDetails = "Cold night air and polished brass eyepiece",
            writingTip = "Contrast the massive night sky with tiny telescope mechanics"
        ),
        Memory(
            id = 2,
            transcript = "Moving to the new city for work was terrifying.",
            formattedProse = "The city skyscrapers felt like concrete canyons when I first arrived in 1974.",
            passageTitle = "City Beginnings",
            chapter = "Chapter 3: Passions & Milestones",
            storyArc = "Starting a new career in the metropolis",
            reflection = "Growth requires leaving safety behind",
            charactersAndPerspectives = "New colleagues at the engineering firm",
            sensoryDetails = "Hissing subway doors and honking yellow taxis",
            writingTip = "Anchor the transition with sensory pace change"
        )
    )

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
    fun testBuddyScreenRenders() {
        val controller = createController()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BuddyScreen(
                        controller = controller,
                        memories = sampleMemories,
                        onOpenSettings = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testCaregiverScreenRenders() {
        val controller = createController()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    CaregiverScreen(
                        controller = controller,
                        memories = sampleMemories,
                        onBackToBuddy = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testUnifiedAutomationBarRenders() {
        val controller = createController()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    UnifiedAutomationBar(
                        controller = controller
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testBookReadinessCardRenders() {
        val report = BookReadinessEvaluator.evaluate(
            bookTitle = "Sample Title",
            authorName = "Author",
            dedication = "Dedication",
            authorBio = "Bio",
            memories = sampleMemories
        )

        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    BookReadinessCard(
                        report = report
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAudiobookPlayerBarRenders() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    AudiobookPlayerBar(
                        loopState = LoopState.Speaking("Reading chapter one"),
                        speechRate = 1.0f,
                        onStopPlayback = {},
                        onToggleSpeed = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSoundWaveVisualizerRenders() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    SoundWaveVisualizer(
                        state = LoopState.Listening(),
                        audioLevel = 0.75f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testEyeGazeDwellCardRenders() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    EyeGazeDwellCard(
                        title = "Record Story",
                        subtitle = "Hover to activate",
                        icon = Icons.Default.Mic,
                        onDwellTriggered = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAutonomousBookwritingHubRenders() {
        val controller = createController()
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    AutonomousBookwritingHub(
                        controller = controller,
                        memories = sampleMemories
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testChapterSwitcherDialogRenders() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    ChapterSwitcherDialog(
                        currentChapter = "Chapter 1: Early Days",
                        allChapters = listOf("Chapter 1: Early Days", "Chapter 2: Career & Passions"),
                        memories = sampleMemories,
                        onSelectChapter = {},
                        onAuditionChapter = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testExportChapterDialogRenders() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Surface {
                    ExportChapterDialog(
                        bookTitle = "My Life Story",
                        authorName = "Mike",
                        allChapters = listOf("Chapter 1: Early Days", "Chapter 2: Career & Passions"),
                        memories = sampleMemories,
                        initialChapter = "Chapter 1: Early Days",
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}
