package com.example.loop

sealed interface LoopState {
    data object Idle : LoopState
    data class Speaking(val text: String) : LoopState
    data class Listening(val hint: String = "Listening...") : LoopState
    data class Recording(val startedAt: Long, val partialText: String = "") : LoopState
    data class Processing(val task: String) : LoopState
    data class Error(val message: String) : LoopState
}
