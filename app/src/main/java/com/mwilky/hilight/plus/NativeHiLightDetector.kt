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
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representing if stock Pixel Favorite Calls is active in system settings.
 */
data class StockHiLightState(
    val favoriteCallsActive: Boolean = false
) {
    val anyActive: Boolean get() = favoriteCallsActive
}

/**
 * Detects whether native Pixel Favorite Calls is active in Settings.Secure
 * under key `light_animation_favorite_calls_enabled`.
 *
 * Queries the key using the privileged Shizuku daemon under Shell UID (2000)
 * to bypass Android 12+ (S+) SecurityException for unreadable @hide system settings.
 */
object NativeHiLightDetector {

    private const val TAG = "NativeHiLightDetector"

    const val KEY_FAVORITE_CALLS = "light_animation_favorite_calls_enabled"

    private val _state = MutableStateFlow(StockHiLightState())
    val state: StateFlow<StockHiLightState> = _state.asStateFlow()

    private var observerRegistered = false
    private var appContext: Context? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "ContentObserver triggered with uri: $uri")
            appContext?.let { check(it) }
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.d(TAG, "ContentObserver triggered (selfChange=$selfChange)")
            appContext?.let { check(it) }
        }
    }

    fun check(context: Context) {
        val app = context.applicationContext
        appContext = app
        val cr = app.contentResolver

        if (!observerRegistered) {
            try {
                // Register observer so whenever the key changes in Settings, Android invokes our callback
                val uri = Settings.Secure.getUriFor(KEY_FAVORITE_CALLS)
                if (uri != null) {
                    cr.registerContentObserver(uri, true, observer)
                    observerRegistered = true
                    Log.d(TAG, "Registered ContentObserver on Settings.Secure for '$KEY_FAVORITE_CALLS'")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register ContentObserver: $e")
            }
        }

        var isFavoriteCallsActive = false

        // Direct primary read via privileged Shizuku daemon (Shell UID 2000)
        if (app is Application) {
            val bridge = ShizukuBridge.get(app)
            if (bridge.isConnected()) {
                val shizukuVal = bridge.getSecureString(KEY_FAVORITE_CALLS)
                if (shizukuVal != null) {
                    isFavoriteCallsActive = shizukuVal == "1" || shizukuVal.equals("true", ignoreCase = true)
                    Log.i(TAG, "Shizuku privileged read: '$KEY_FAVORITE_CALLS'='$shizukuVal' => active=$isFavoriteCallsActive")
                }
            }
        }

        _state.value = StockHiLightState(favoriteCallsActive = isFavoriteCallsActive)
    }

    /**
     * Opens the System settings page where HiLight settings reside.
     */
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
