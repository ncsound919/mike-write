package com.example.loop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VoiceLoopBus {
    private val _state = MutableStateFlow<LoopState>(LoopState.Idle)
    val state: StateFlow<LoopState> = _state.asStateFlow()

    private val _lastSpoken = MutableStateFlow<String>("")
    val lastSpoken: StateFlow<String> = _lastSpoken.asStateFlow()

    private val _lastRecognized = MutableStateFlow<String>("")
    val lastRecognized: StateFlow<String> = _lastRecognized.asStateFlow()

    private val _audioLevels = MutableStateFlow<Float>(0f)
    val audioLevels: StateFlow<Float> = _audioLevels.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(listOf("Mike Write system initialized."))
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    fun publish(s: LoopState) {
        _state.value = s
    }

    fun setSpoken(text: String) {
        _lastSpoken.value = text
        appendLog("Spoken: $text")
    }

    fun setRecognized(text: String) {
        _lastRecognized.value = text
        appendLog("Recognized: $text")
    }

    fun setRms(rms: Float) {
        _audioLevels.value = rms
    }

    fun logAccessibility(msg: String) {
        appendLog("Accessibility: $msg")
    }

    fun appendLog(msg: String) {
        val current = _systemLogs.value.toMutableList()
        if (current.size > 50) current.removeAt(0)
        current.add(msg)
        _systemLogs.value = current
    }
}
