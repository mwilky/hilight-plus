package com.hilight.plus.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hilight.plus.LightController

/**
 * Robust Accessibility & Window State monitor that accurately detects and orchestrates
 * Gemini / Google Assistant lifecycle states (Listening -> Thinking -> Responding -> Dismissed)
 * on the Pixel 11 Pro series.
 */
class GeminiAssistantWatcher : AccessibilityService() {

    private var lastState = AssistantState.DISMISSED
    private var speechStartMs = 0L
    private var lastTextLength = 0
    private var thinkingTimer: Runnable? = null
    private var respondingTimer: Runnable? = null
    private var dismissTimer: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    enum class AssistantState { LISTENING, THINKING, RESPONDING, DISMISSED }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        Log.i(TAG, "GeminiAssistantWatcher connected & monitoring assistant lifecycle")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""

        val controller = LightController.get(applicationContext)

        if (isAssistantPackage(pkg)) {
            cancelDismiss()

            val textContent = StringBuilder()
            event.text.forEach { textContent.append(it).append(" ") }
            val desc = event.contentDescription?.toString()?.lowercase() ?: ""
            val fullText = textContent.toString().lowercase()
            val textLen = fullText.trim().length

            // 1. First invocation: Start Listening
            if (lastState == AssistantState.DISMISSED) {
                Log.i(TAG, "Gemini session started -> LISTENING")
                lastState = AssistantState.LISTENING
                speechStartMs = System.currentTimeMillis()
                lastTextLength = textLen
                controller.triggerGeminiListening()

                // Automatic state progression fallback if Gemini UI does not emit text accessibility nodes
                scheduleStateProgression(controller)
                return
            }

            // 2. Real-time content & speech updates
            if (lastState == AssistantState.LISTENING) {
                // When speech input is received or text grows, user is talking
                if (textLen > lastTextLength + 5 || desc.contains("listening")) {
                    speechStartMs = System.currentTimeMillis()
                    lastTextLength = textLen
                    // Postpone thinking transition until user pauses speech
                    scheduleThinkingTransition(controller, 1200L)
                } else if (isThinkingIndicator(fullText, desc)) {
                    transitionToThinking(controller)
                }
            } else if (lastState == AssistantState.THINKING) {
                if (isRespondingIndicator(fullText, desc, textLen)) {
                    transitionToResponding(controller)
                }
            }
        } else {
            // Non-assistant package window change - schedule clean dismissal
            if (lastState != AssistantState.DISMISSED && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
                scheduleDismiss()
            }
        }
    }

    private fun scheduleStateProgression(controller: LightController) {
        cancelTimers()
        // Natural assistant timing: Listening (approx 2.5s) -> Thinking (approx 1.8s) -> Responding
        thinkingTimer = Runnable {
            if (lastState == AssistantState.LISTENING) {
                transitionToThinking(controller)
                respondingTimer = Runnable {
                    if (lastState == AssistantState.THINKING) {
                        transitionToResponding(controller)
                    }
                }.also { handler.postDelayed(it, 2000L) }
            }
        }.also {
            handler.postDelayed(it, 2800L)
        }
    }

    private fun scheduleThinkingTransition(controller: LightController, delayMs: Long) {
        cancelTimers()
        thinkingTimer = Runnable {
            if (lastState == AssistantState.LISTENING) {
                transitionToThinking(controller)
                respondingTimer = Runnable {
                    if (lastState == AssistantState.THINKING) {
                        transitionToResponding(controller)
                    }
                }.also { handler.postDelayed(it, 2200L) }
            }
        }.also {
            handler.postDelayed(it, delayMs)
        }
    }

    private fun transitionToThinking(controller: LightController) {
        Log.i(TAG, "Gemini state transition -> THINKING")
        lastState = AssistantState.THINKING
        controller.triggerGeminiThinking()
    }

    private fun transitionToResponding(controller: LightController) {
        Log.i(TAG, "Gemini state transition -> RESPONDING")
        lastState = AssistantState.RESPONDING
        controller.triggerGeminiResponding()
    }

    private fun isThinkingIndicator(text: String, desc: String): Boolean {
        return desc.contains("thinking") ||
            desc.contains("processing") ||
            desc.contains("generating") ||
            text.contains("thinking") ||
            text.contains("working on it")
    }

    private fun isRespondingIndicator(text: String, desc: String, textLen: Int): Boolean {
        return desc.contains("speaking") ||
            desc.contains("result") ||
            desc.contains("answer") ||
            textLen > lastTextLength + 30
    }

    private fun scheduleDismiss() {
        if (dismissTimer != null) return
        dismissTimer = Runnable {
            if (lastState != AssistantState.DISMISSED) {
                Log.i(TAG, "Gemini session ended -> DISMISSED")
                lastState = AssistantState.DISMISSED
                cancelTimers()
                LightController.get(applicationContext).clearGeminiAlert()
            }
            dismissTimer = null
        }.also {
            handler.postDelayed(it, 600L) // 600ms debounce
        }
    }

    private fun cancelDismiss() {
        dismissTimer?.let {
            handler.removeCallbacks(it)
            dismissTimer = null
        }
    }

    private fun cancelTimers() {
        thinkingTimer?.let { handler.removeCallbacks(it) }
        respondingTimer?.let { handler.removeCallbacks(it) }
        thinkingTimer = null
        respondingTimer = null
    }

    private fun isAssistantPackage(pkg: String): Boolean {
        return pkg.contains("googlequicksearchbox", ignoreCase = true) ||
            pkg.contains("googleassistant", ignoreCase = true) ||
            pkg.contains("bard", ignoreCase = true) ||
            pkg.contains("gemini", ignoreCase = true)
    }

    override fun onInterrupt() {
        Log.w(TAG, "GeminiAssistantWatcher interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelDismiss()
        cancelTimers()
        if (lastState != AssistantState.DISMISSED) {
            LightController.get(applicationContext).clearGeminiAlert()
        }
    }

    companion object {
        private const val TAG = "GeminiAssistantWatcher"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedService = "${context.packageName}/${GeminiAssistantWatcher::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(":").any { it.equals(expectedService, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
