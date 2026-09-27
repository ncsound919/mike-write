package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiConfigProviderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `runtime Gemini key overrides build config and enables the provider`() {
        val settings = SettingsStore(context)
        settings.geminiApiKey = ""
        assertFalse("blank key should leave Gemini disabled", AiConfigProvider.from(settings).geminiEnabled)

        settings.geminiApiKey = "AIzaSyRuntimeEnteredKey"
        val config = AiConfigProvider.from(settings)
        assertTrue(config.geminiEnabled)
        assertEquals("AIzaSyRuntimeEnteredKey", config.geminiKey)
        assertTrue(config.anyTextProvider)

        settings.geminiApiKey = ""
        assertFalse(AiConfigProvider.from(settings).geminiEnabled)
    }

    @Test
    fun `Gemini key persists through the settings store`() {
        SettingsStore(context).geminiApiKey = "AIzaSyPersisted"
        assertEquals("AIzaSyPersisted", SettingsStore(context).geminiApiKey)
        SettingsStore(context).geminiApiKey = ""
    }
}
