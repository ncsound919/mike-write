package com.example.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SharedPreferences persistence on-device (distinct file writes, not the shadow). */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("mike_write_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("mike_write_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun valuesPersistAcrossStoreInstances() {
        val first = SettingsStore(context)
        first.bookTitle = "Device Test Title"
        first.authorName = "Device Author"
        first.currentChapter = "Chapter 3: Passions & Milestones"
        first.speechRate = 1.25f
        first.autoEditorialPipeline = false
        first.jevLocalUrl = "http://127.0.0.1:9999"

        val second = SettingsStore(context)
        assertEquals("Device Test Title", second.bookTitle)
        assertEquals("Device Author", second.authorName)
        assertEquals("Chapter 3: Passions & Milestones", second.currentChapter)
        assertEquals(1.25f, second.speechRate, 0.001f)
        assertFalse(second.autoEditorialPipeline)
        assertEquals("http://127.0.0.1:9999", second.jevLocalUrl)
    }

    @Test
    fun consentGatingStampsAndClearsTimestamp() {
        val settings = SettingsStore(context)
        settings.hasConsentedToAudioProcessing = true
        assertTrue(settings.hasConsentedToAudioProcessing)
        assertTrue(settings.consentTimestamp > 0L)
        assertTrue(settings.getConsentFormattedDate() != null)

        settings.hasConsentedToAudioProcessing = false
        assertFalse(settings.hasConsentedToAudioProcessing)
        assertEquals(0L, settings.consentTimestamp)
    }
}
