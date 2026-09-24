package com.example.access

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.loop.VoiceLoopBus

/**
 * Dedicated accessibility service for Mike Write.
 * Provides switch access hooks, volume key scan controls, screen state observations, and accessibility feedback.
 */
class MikeWriteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        VoiceLoopBus.logAccessibility("Accessibility Service Connected with isAccessibilityTool=true")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        
        // Support physical Switch Access devices mapped to volume keys
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    VoiceLoopBus.logAccessibility("Switch Access Trigger: Volume Down -> Next/Toggle Record")
                    return false
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    VoiceLoopBus.logAccessibility("Switch Access Trigger: Volume Up -> Stop/Pause")
                    return false
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observes window/state transitions to maintain seamless audible accessibility
        if (event == null) return
        val eventText = event.text?.joinToString(" ")
        if (!eventText.isNullOrBlank()) {
            VoiceLoopBus.logAccessibility("Event: ${event.eventType} - $eventText")
        }
    }

    override fun onInterrupt() {
        VoiceLoopBus.logAccessibility("Accessibility Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        VoiceLoopBus.logAccessibility("Accessibility Service Destroyed")
    }
}
