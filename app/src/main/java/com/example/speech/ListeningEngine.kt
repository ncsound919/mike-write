package com.example.speech

interface ListeningEngine {
    val isListening: Boolean
    fun start(
        continuous: Boolean = false,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    )
    fun stop()
    fun destroy()
}
