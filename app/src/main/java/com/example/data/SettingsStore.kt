package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mike_write_prefs", Context.MODE_PRIVATE)

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 0.95f)
        set(value) = prefs.edit().putFloat("speech_rate", value).apply()

    var speechPitch: Float
        get() = prefs.getFloat("speech_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("speech_pitch", value).apply()

    var currentChapter: String
        get() = prefs.getString("current_chapter", "Chapter 1: Early Days") ?: "Chapter 1: Early Days"
        set(value) = prefs.edit().putString("current_chapter", value).apply()

    var autoSaveMemories: Boolean
        get() = prefs.getBoolean("auto_save_memories", true)
        set(value) = prefs.edit().putBoolean("auto_save_memories", value).apply()

    var activeInputMode: String
        get() = prefs.getString("active_input_mode", "Voice Loop") ?: "Voice Loop"
        set(value) = prefs.edit().putString("active_input_mode", value).apply()

    var authorName: String
        get() = prefs.getString("author_name", "Mike") ?: "Mike"
        set(value) = prefs.edit().putString("author_name", value).apply()

    var bookTitle: String
        get() = prefs.getString("book_title", "My Journey: In My Own Words") ?: "My Journey: In My Own Words"
        set(value) = prefs.edit().putString("book_title", value).apply()

    var dedication: String
        get() = prefs.getString("dedication", "Dedicated to my family, friends, and everyone who stood by me.") ?: "Dedicated to my family, friends, and everyone who stood by me."
        set(value) = prefs.edit().putString("dedication", value).apply()

    var authorBio: String
        get() = prefs.getString("author_bio", "Mike is an author, storyteller, and survivor sharing memories and lessons from a remarkable life.") ?: "Mike is an author, storyteller, and survivor sharing memories and lessons from a remarkable life."
        set(value) = prefs.edit().putString("author_bio", value).apply()

    var liveEchoPlayback: Boolean
        get() = prefs.getBoolean("live_echo_playback", true)
        set(value) = prefs.edit().putBoolean("live_echo_playback", value).apply()

    var hasConsentedToAudioProcessing: Boolean
        get() = prefs.getBoolean("has_consented_audio_processing", false)
        set(value) = prefs.edit().putBoolean("has_consented_audio_processing", value).apply()

    var fontSizeScale: Float
        get() = prefs.getFloat("font_size_scale", 1.0f)
        set(value) = prefs.edit().putFloat("font_size_scale", value).apply()

    var highContrastMode: Boolean
        get() = prefs.getBoolean("high_contrast_mode", false)
        set(value) = prefs.edit().putBoolean("high_contrast_mode", value).apply()

    var readingTheme: String
        get() = prefs.getString("reading_theme", "Midnight") ?: "Midnight"
        set(value) = prefs.edit().putString("reading_theme", value).apply()
}
