package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.loop.VoiceLoopBus
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class SpeechEngine(context: Context) {

    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set

    private var currentRate: Float = 0.95f
    private var currentPitch: Float = 1.0f

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(currentRate)
                tts?.setPitch(currentPitch)
                VoiceLoopBus.appendLog("TTS Engine ready (rate: $currentRate, pitch: $currentPitch)")
            } else {
                VoiceLoopBus.appendLog("TTS Engine initialization failed (status: $status)")
            }
        }
    }

    /**
     * Speaks the given text aloud and suspends until speech is completed or cancelled.
     */
    suspend fun speak(text: String): Unit = suspendCancellableCoroutine { cont ->
        if (!isReady || tts == null) {
            VoiceLoopBus.appendLog("TTS not ready yet. Skipping verbal playback: $text")
            if (cont.isActive) cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = "u_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                VoiceLoopBus.setSpoken(text)
            }

            override fun onDone(id: String?) {
                if (cont.isActive) cont.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (cont.isActive) cont.resume(Unit)
            }
        })

        cont.invokeOnCancellation {
            tts?.stop()
        }

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            if (cont.isActive) cont.resume(Unit)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentRate)
    }

    fun setSpeechPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
