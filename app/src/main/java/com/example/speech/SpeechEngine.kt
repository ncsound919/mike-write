package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.loop.VoiceLoopBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Thin wrapper over Android TextToSpeech.
 *
 * The listener is registered exactly once and dispatches completion by utterance
 * id. The previous implementation installed a brand-new
 * [UtteranceProgressListener] on every [speak] call, so two overlapping
 * utterances clobbered each other's continuation and the earlier `say(...)` call
 * never resumed — hanging the voice loop indefinitely.
 */
class SpeechEngine(context: Context) {

    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set

    private val readyDeferred = CompletableDeferred<Boolean>()
    private var currentRate: Float = 0.95f
    private var currentPitch: Float = 1.0f

    private val utteranceCounter = AtomicLong(0)
    private val pendingText = ConcurrentHashMap<String, String>()
    private val continuations = ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<Unit>>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId == null) return
            pendingText[utteranceId]?.let { VoiceLoopBus.setSpoken(it) }
        }

        override fun onDone(utteranceId: String?) {
            complete(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            complete(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            complete(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            complete(utteranceId)
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(currentRate)
                tts?.setPitch(currentPitch)
                tts?.setOnUtteranceProgressListener(progressListener)
                readyDeferred.complete(true)
                VoiceLoopBus.appendLog("TTS Engine ready (rate: $currentRate, pitch: $currentPitch)")
            } else {
                readyDeferred.complete(false)
                VoiceLoopBus.appendLog("TTS Engine initialization failed (status: $status)")
            }
        }
    }

    /** Suspends until the TTS engine is fully initialized or the timeout expires. */
    suspend fun awaitReady(timeoutMs: Long = 2000L): Boolean {
        if (isReady) return true
        return withTimeoutOrNull(timeoutMs) { readyDeferred.await() } ?: false
    }

    /**
     * Speaks [text] aloud and suspends until speech completes, errors, stops, or is
     * cancelled.
     */
    suspend fun speak(text: String): Unit = suspendCancellableCoroutine { cont ->
        val engine = tts
        if (!isReady || engine == null) {
            VoiceLoopBus.appendLog("TTS engine unavailable. Skipping playback.")
            if (cont.isActive) cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = "u_${utteranceCounter.incrementAndGet()}"
        pendingText[utteranceId] = text
        continuations[utteranceId] = cont

        cont.invokeOnCancellation {
            continuations.remove(utteranceId)
            pendingText.remove(utteranceId)
            try {
                engine.stop()
            } catch (e: Exception) {
                VoiceLoopBus.appendLog("TTS stop on cancellation failed: ${e.message}")
            }
        }

        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("TTS speak threw: ${e.message}")
            android.speech.tts.TextToSpeech.ERROR
        }

        if (result != TextToSpeech.SUCCESS) {
            continuations.remove(utteranceId)
            pendingText.remove(utteranceId)
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun complete(utteranceId: String?) {
        if (utteranceId == null) return
        val cont = continuations.remove(utteranceId)
        pendingText.remove(utteranceId)
        if (cont?.isActive == true) cont.resume(Unit)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("TTS stop failed: ${e.message}")
        }
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
        continuations.values.forEach { if (it.isActive) it.resume(Unit) }
        continuations.clear()
        pendingText.clear()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("TTS shutdown failed: ${e.message}")
        }
        tts = null
        isReady = false
    }
}
