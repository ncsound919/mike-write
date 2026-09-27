package com.example

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiConfigProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.AndroidSttEngine
import com.example.speech.SpeechEngine
import com.example.ui.theme.MikeWriteTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainAppHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: MikeWriteDatabase
    private lateinit var settings: SettingsStore
    private lateinit var controller: VoiceLoopController

    @Before
    fun setUp() {
        db = MikeWriteDatabase.getInstance(application)
        settings = SettingsStore(application)
        settings.hasCompletedTour = true
        controller = VoiceLoopController(
            context = application,
            speech = SpeechEngine(application),
            listener = AndroidSttEngine(application),
            db = db,
            interviewer = Interviewer(AiTextEngine { AiConfigProvider.default() }),
            settings = settings
        )
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun `permission gate is shown when the microphone is denied`() {
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        settings.hasConsentedToAudioProcessing = true

        composeTestRule.setContent {
            MikeWriteTheme {
                MainAppHost(controller = controller, database = db)
            }
        }

        composeTestRule.onNodeWithTag("grant_mic_permission_button").assertExists()
    }

    @Test
    fun `consent gate records consent and continues`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        settings.hasConsentedToAudioProcessing = false

        composeTestRule.setContent {
            MikeWriteTheme {
                MainAppHost(controller = controller, database = db)
            }
        }

        composeTestRule.onNodeWithTag("accept_privacy_consent_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(settings.hasConsentedToAudioProcessing)
    }

    @Test
    fun `main host renders the buddy screen once granted and consented`() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        settings.hasConsentedToAudioProcessing = true
        settings.hasCompletedTour = true

        composeTestRule.setContent {
            MikeWriteTheme {
                MainAppHost(controller = controller, database = db)
            }
        }

        composeTestRule.waitForIdle()
    }
}
