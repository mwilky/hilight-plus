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
 * Lightweight Accessibility & Window State monitor that detects when Gemini / Google Assistant
 * is invoked, thinking, responding, or dismissed on the Pixel 11 Pro series.
 */
class GeminiAssistantWatcher : AccessibilityService() {

    private var lastState = AssistantState.DISMISSED
    private var dismissTimer: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    enum class AssistantState { LISTENING, THINKING, RESPONDING, DISMISSED }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf(
                GOOGLE_QUICK_SEARCH_BOX_PKG,
                GEMINI_OVERLAY_PKG,
                GEMINI_APP_PKG
            )
        }
        Log.i(TAG, "GeminiAssistantWatcher connected & monitoring assistant lifecycle")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (!isAssistantPackage(pkg)) {
            if (lastState != AssistantState.DISMISSED) {
                scheduleDismiss()
            }
            return
        }

        val className = event.className?.toString() ?: ""
        val textContent = event.text.joinToString(" ").lowercase()
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""

        val controller = LightController.get(applicationContext)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                cancelDismiss()
                // Check if assistant window is active
                if (isAssistantUi(className, textContent, contentDesc)) {
                    if (lastState == AssistantState.DISMISSED) {
                        Log.i(TAG, "Gemini invoked -> LISTENING")
                        lastState = AssistantState.LISTENING
                        controller.triggerGeminiListening()
                    }
                } else {
                    scheduleDismiss()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                cancelDismiss()
                // Differentiate thinking / responding state based on active text content & indicators
                if (isThinkingIndicator(textContent, contentDesc)) {
                    if (lastState != AssistantState.THINKING) {
                        Log.i(TAG, "Gemini processing query -> THINKING")
                        lastState = AssistantState.THINKING
                        controller.triggerGeminiThinking()
                    }
                } else if (isRespondingIndicator(textContent, contentDesc)) {
                    if (lastState != AssistantState.RESPONDING) {
                        Log.i(TAG, "Gemini responding -> RESPONDING")
                        lastState = AssistantState.RESPONDING
                        controller.triggerGeminiResponding()
                    }
                }
            }
        }
    }

    private fun scheduleDismiss() {
        cancelDismiss()
        dismissTimer = Runnable {
            if (lastState != AssistantState.DISMISSED) {
                Log.i(TAG, "Gemini dismissed -> CLEARING LIGHTS")
                lastState = AssistantState.DISMISSED
                LightController.get(applicationContext).clearGeminiAlert()
            }
        }.also {
            handler.postDelayed(it, 400L) // 400ms debounce
        }
    }

    private fun cancelDismiss() {
        dismissTimer?.let {
            handler.removeCallbacks(it)
            dismissTimer = null
        }
    }

    private fun isAssistantPackage(pkg: String): Boolean {
        return pkg == GOOGLE_QUICK_SEARCH_BOX_PKG ||
            pkg == GEMINI_OVERLAY_PKG ||
            pkg == GEMINI_APP_PKG ||
            pkg.contains("gemini", ignoreCase = true) ||
            pkg.contains("assistant", ignoreCase = true)
    }

    private fun isAssistantUi(className: String, text: String, desc: String): Boolean {
        return className.contains("assistant", ignoreCase = true) ||
            className.contains("gemini", ignoreCase = true) ||
            className.contains("voice", ignoreCase = true) ||
            desc.contains("gemini") ||
            desc.contains("google assistant") ||
            text.contains("gemini")
    }

    private fun isThinkingIndicator(text: String, desc: String): Boolean {
        return desc.contains("thinking") ||
            desc.contains("processing") ||
            text.contains("thinking...") ||
            desc.contains("generating")
    }

    private fun isRespondingIndicator(text: String, desc: String): Boolean {
        return desc.contains("speaking") ||
            desc.contains("response") ||
            text.length > 30
    }

    override fun onInterrupt() {
        Log.w(TAG, "GeminiAssistantWatcher interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelDismiss()
        if (lastState != AssistantState.DISMISSED) {
            LightController.get(applicationContext).clearGeminiAlert()
        }
    }

    companion object {
        private const val TAG = "GeminiAssistantWatcher"
        const val GOOGLE_QUICK_SEARCH_BOX_PKG = "com.google.android.googlequicksearchbox"
        const val GEMINI_OVERLAY_PKG = "com.google.android.apps.googleassistant"
        const val GEMINI_APP_PKG = "com.google.android.apps.bard"

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
