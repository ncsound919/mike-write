package com.example.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Non-visual earcon tone & haptic feedback engine for screen-free confidence and accessibility.
 */
class AudioHapticFeedback(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e("AudioHapticFeedback", "Error initializing vibrator", e)
            null
        }
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.e("AudioHapticFeedback", "Error creating ToneGenerator", e)
        }
    }

    enum class Cue {
        START_RECORDING,
        STOP_RECORDING,
        MEMORY_SAVED,
        ACTION_UNDONE,
        NOT_UNDERSTOOD,
        HELP_TRIGGERED,
        BUTTON_TAP
    }

    fun playFeedback(cue: Cue) {
        try {
            when (cue) {
                Cue.START_RECORDING -> {
                    // Ascending affirmative tone & distinct double-pulse haptic
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    vibratePattern(longArrayOf(0, 50, 40, 70))
                }
                Cue.STOP_RECORDING -> {
                    // Descending completion tone
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 140)
                    vibratePattern(longArrayOf(0, 80))
                }
                Cue.MEMORY_SAVED -> {
                    // Positive affirmation chime & satisfying confirmation vibration
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 160)
                    vibratePattern(longArrayOf(0, 60, 60, 100))
                }
                Cue.ACTION_UNDONE -> {
                    // Reversal alert tone
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                    vibratePattern(longArrayOf(0, 100, 50, 60))
                }
                Cue.NOT_UNDERSTOOD -> {
                    // Gentle low warning beep
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                    vibratePattern(longArrayOf(0, 120))
                }
                Cue.HELP_TRIGGERED -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    vibratePattern(longArrayOf(0, 40, 40, 40))
                }
                Cue.BUTTON_TAP -> {
                    vibratePattern(longArrayOf(0, 25))
                }
            }
        } catch (e: Exception) {
            Log.e("AudioHapticFeedback", "Failed to play feedback for cue: $cue", e)
        }
    }

    private fun vibratePattern(timings: LongArray) {
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(timings, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(timings, -1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AudioHapticFeedback", "Vibration failed", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w("AudioHapticFeedback", "ToneGenerator release error", e)
        }
    }
}
