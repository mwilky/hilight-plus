package com.hilight.plus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representing which stock Pixel 11 HiLight features are active in system settings.
 */
data class StockHiLightState(
    val favoriteCallsActive: Boolean = false,
    val assistantFeedbackActive: Boolean = false,
) {
    val anyActive: Boolean get() = favoriteCallsActive || assistantFeedbackActive
    val bothActive: Boolean get() = favoriteCallsActive && assistantFeedbackActive
}

/**
 * Detects whether native Pixel 11 HiLight features are active in system settings.
 *
 * Exact system keys in Settings.Secure on Pixel 11 Pro:
 * - `light_animation_favorite_calls_enabled` (Favorite contact calls: 1 = enabled, 0 or missing = disabled)
 * - `light_animation_feedback_enabled` (Gemini / Assistant feedback: 1 = enabled, 0 or missing = disabled)
 */
object NativeHiLightDetector {

    private const val TAG = "NativeHiLightDetector"

    const val KEY_FAVORITE_CALLS = "light_animation_favorite_calls_enabled"
    const val KEY_ASSISTANT_FEEDBACK = "light_animation_feedback_enabled"

    private val _state = MutableStateFlow(StockHiLightState())
    val state: StateFlow<StockHiLightState> = _state.asStateFlow()

    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            appContext?.let { check(it) }
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            appContext?.let { check(it) }
        }
    }

    private var appContext: Context? = null

    fun check(context: Context) {
        val app = context.applicationContext
        appContext = app

        val cr = app.contentResolver

        if (!observerRegistered) {
            try {
                cr.registerContentObserver(Settings.Secure.getUriFor(KEY_FAVORITE_CALLS), true, observer)
                cr.registerContentObserver(Settings.Secure.getUriFor(KEY_ASSISTANT_FEEDBACK), true, observer)
                observerRegistered = true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register ContentObserver: $e")
            }
        }

        val callsInt = try { Settings.Secure.getInt(cr, KEY_FAVORITE_CALLS, 0) } catch (_: Throwable) { 0 }
        val callsStr = try { Settings.Secure.getString(cr, KEY_FAVORITE_CALLS) } catch (_: Throwable) { null }
        val favoriteCallsActive = (callsInt == 1) || (callsStr == "1") || (callsStr.equals("true", ignoreCase = true))

        val feedbackInt = try { Settings.Secure.getInt(cr, KEY_ASSISTANT_FEEDBACK, 0) } catch (_: Throwable) { 0 }
        val feedbackStr = try { Settings.Secure.getString(cr, KEY_ASSISTANT_FEEDBACK) } catch (_: Throwable) { null }
        val assistantFeedbackActive = (feedbackInt == 1) || (feedbackStr == "1") || (feedbackStr.equals("true", ignoreCase = true))

        val newState = StockHiLightState(
            favoriteCallsActive = favoriteCallsActive,
            assistantFeedbackActive = assistantFeedbackActive
        )
        _state.value = newState
    }

    /**
     * Opens the System settings page where HiLight settings reside.
     */
    fun openHiLightSettings(context: Context) {
        val systemIntent = Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.Settings\$SystemDashboardActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(systemIntent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Throwable) {
            }
        }
    }
}
