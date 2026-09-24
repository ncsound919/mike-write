package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.loop.VoiceLoopBus

class AndroidSttEngine(private val context: Context) : ListeningEngine {

    private var recognizer: SpeechRecognizer? = null
    private var onPartialCallback: (String) -> Unit = {}
    private var onResultCallback: (String) -> Unit = {}
    private var onErrorCallback: (String) -> Unit = {}

    override var isListening: Boolean = false
        private set

    private var isContinuousMode: Boolean = false
    private var isIntentionalStop: Boolean = false
    private var hasHeardSpeechInSession: Boolean = false
    private var silenceTimeoutCount: Int = 0

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            VoiceLoopBus.appendLog("STT: Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            hasHeardSpeechInSession = true
            silenceTimeoutCount = 0
            VoiceLoopBus.appendLog("STT: Speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = (rmsdB + 2f).coerceIn(0f, 10f) / 10f
            VoiceLoopBus.setRms(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            VoiceLoopBus.setRms(0f)
            VoiceLoopBus.appendLog("STT: End of speech segment")
        }

        override fun onError(error: Int) {
            isListening = false
            VoiceLoopBus.setRms(0f)
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Speech recognition error ($error)"
            }
            VoiceLoopBus.appendLog("STT error: $errorMsg ($error)")

            if (isIntentionalStop) {
                return
            }

            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                VoiceLoopBus.appendLog("STT: Recreating recognizer after busy/client error ($error)")
                val fresh = recreateRecognizer()
                if (fresh != null && !isIntentionalStop) {
                    restartListening()
                    return
                }
            }

            // In continuous dictation mode, handle natural speech pauses and timeouts cleanly
            if (isContinuousMode && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                silenceTimeoutCount++
                VoiceLoopBus.appendLog("STT silence/timeout #$silenceTimeoutCount (heardSpeech: $hasHeardSpeechInSession)")

                // If user was speaking and then stopped speaking for a full timeout, signal silence_timeout to auto-finalize
                if (hasHeardSpeechInSession && silenceTimeoutCount >= 1) {
                    isListening = false
                    isContinuousMode = false
                    onErrorCallback("silence_timeout")
                    return
                }

                // If user hasn't spoken at all yet, retry up to 3 times before timing out cleanly
                if (!hasHeardSpeechInSession && silenceTimeoutCount >= 3) {
                    isListening = false
                    isContinuousMode = false
                    onErrorCallback("initial_silence_timeout")
                    return
                }

                restartListening()
                return
            }

            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                onErrorCallback("silence_or_timeout")
            } else {
                onErrorCallback(errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            VoiceLoopBus.setRms(0f)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            VoiceLoopBus.appendLog("STT Final result segment: '$text'")
            if (text.isNotBlank()) {
                hasHeardSpeechInSession = true
                silenceTimeoutCount = 0
                VoiceLoopBus.setRecognized(text)
                onResultCallback(text)
            }

            // If in continuous dictation mode and not intentionally stopped, keep listening for the next sentence!
            if (isContinuousMode && !isIntentionalStop) {
                restartListening()
            } else if (text.isBlank() && !isIntentionalStop) {
                onErrorCallback("empty_speech")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                hasHeardSpeechInSession = true
                silenceTimeoutCount = 0
                onPartialCallback(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun restartListening() {
        try {
            val intent = createSpeechIntent()
            recognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("Failed to restart continuous STT: ${e.message}")
        }
    }

    private fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Extra speech timeouts for extended hands-free storytelling
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
    }

    private fun recreateRecognizer(): SpeechRecognizer? {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        recognizer = null
        return try {
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }.also { recognizer = it }
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("Failed to recreate SpeechRecognizer: ${e.message}")
            null
        }
    }

    override fun start(
        continuous: Boolean,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        this.isContinuousMode = continuous
        this.isIntentionalStop = false
        this.hasHeardSpeechInSession = false
        this.silenceTimeoutCount = 0
        this.onPartialCallback = onPartial
        this.onResultCallback = onResult
        this.onErrorCallback = onError

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition is not available on this device.")
                return
            }

            if (recognizer == null) {
                recreateRecognizer()
            }

            val currentRecognizer = recognizer
            if (currentRecognizer == null) {
                onError("Failed to initialize speech recognizer.")
                return
            }

            try {
                currentRecognizer.startListening(createSpeechIntent())
                isListening = true
            } catch (e: Exception) {
                VoiceLoopBus.appendLog("startListening failed, attempting recreate: ${e.message}")
                val fresh = recreateRecognizer()
                fresh?.startListening(createSpeechIntent())
                isListening = fresh != null
            }
        } catch (e: Exception) {
            isListening = false
            VoiceLoopBus.appendLog("Failed to start recognizer: ${e.message}")
            onError(e.message ?: "Failed to start speech recognition")
        }
    }

    override fun stop() {
        isIntentionalStop = true
        isContinuousMode = false
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            // ignore
        }
        isListening = false
        VoiceLoopBus.setRms(0f)
    }

    override fun destroy() {
        isIntentionalStop = true
        isContinuousMode = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        recognizer = null
        isListening = false
        VoiceLoopBus.setRms(0f)
    }
}
