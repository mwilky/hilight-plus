package com.mwilky.hilight.plus

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Native favourite-caller lighting read from Settings.Secure.
 * Unknown (unread) is not treated as disabled.
 */
data class StockHiLightState(
    val favoriteCallsActive: Boolean = false,
    val known: Boolean = false,
    // Stock Gemini feedback (listening, thinking, replying), read separately.
    val geminiFeedbackActive: Boolean = false,
    val geminiKnown: Boolean = false
)

/**
 * Detects whether native Pixel Favorite Calls is active in Settings.Secure
 * under key `light_animation_favorite_calls_enabled`.
 */
object NativeHiLightDetector {

    private const val TAG = "NativeHiLightDetector"

    const val KEY_FAVORITE_CALLS = "light_animation_favorite_calls_enabled"
    const val KEY_GEMINI_FEEDBACK = "light_animation_feedback_enabled"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(StockHiLightState())
    val state: StateFlow<StockHiLightState> = _state.asStateFlow()

    private var observerRegistered = false
    private var appContext: Context? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            DebugLog.d(TAG, "ContentObserver triggered with uri: $uri")
            appContext?.let { check(it) }
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            DebugLog.d(TAG, "ContentObserver triggered (selfChange=$selfChange)")
            appContext?.let { check(it) }
        }
    }

    fun check(context: Context) {
        val app = context.applicationContext
        appContext = app
        val cr = app.contentResolver

        if (!observerRegistered) {
            try {
                for (key in listOf(KEY_FAVORITE_CALLS, KEY_GEMINI_FEEDBACK)) {
                    val uri = Settings.Secure.getUriFor(key) ?: continue
                    cr.registerContentObserver(uri, true, observer)
                    observerRegistered = true
                    DebugLog.d(TAG, "Registered ContentObserver on Settings.Secure for '$key'")
                }
            } catch (e: Throwable) {
                DebugLog.w(TAG, "Failed to register ContentObserver: $e")
            }
        }

        scope.launch {
            _state.value = readFavoriteCalls(app)
        }
    }

    private fun readFavoriteCalls(app: Context): StockHiLightState {
        if (app is Application) {
            val bridge = DaemonBridge.get(app)
            if (bridge.isConnected()) {
                val value = bridge.getSecureString(KEY_FAVORITE_CALLS)
                val gemini = bridge.getSecureString(KEY_GEMINI_FEEDBACK)
                val parsed = parseFavoriteCallsSetting(value).withGeminiFeedback(gemini)
                DebugLog.i(TAG, "Privileged read: '$KEY_FAVORITE_CALLS'='$value', '$KEY_GEMINI_FEEDBACK'='$gemini' => $parsed")
                return parsed
            }
        }
        return StockHiLightState(known = false)
    }

    fun openHiLightSettings(context: Context) {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$HiLightSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent("android.settings.HILIGHT_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$SystemDashboardActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
            }
        }
    }
}

/** Adds the stock Gemini feedback setting, read the same way as favourite calls. */
internal fun StockHiLightState.withGeminiFeedback(value: String?): StockHiLightState {
    val parsed = parseFavoriteCallsSetting(value)
    return copy(geminiFeedbackActive = parsed.favoriteCallsActive, geminiKnown = parsed.known)
}

internal fun parseFavoriteCallsSetting(value: String?): StockHiLightState {
    if (value.isNullOrBlank()) return StockHiLightState(known = false)
    val active = value == "1" || value.equals("true", ignoreCase = true)
    return StockHiLightState(favoriteCallsActive = active, known = true)
}
