package com.hilight.plus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hilight_plus_settings")

class AppPreferences private constructor(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_PATTERN = stringPreferencesKey("pattern")
        private val KEY_COLOR = longPreferencesKey("color")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_SPEED_MS = longPreferencesKey("speed_ms")
        private val KEY_AUTO_OFF_SEC = intPreferencesKey("auto_off_sec")

        @Volatile
        private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    val isEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ENABLED] ?: true }

    val lightStyle: Flow<LightStyle> = context.dataStore.data
        .map { prefs ->
            val patternName = prefs[KEY_PATTERN] ?: PatternMode.OFF.name
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.OFF)
            val color = prefs[KEY_COLOR] ?: 0xFF000000
            val speedMs = prefs[KEY_SPEED_MS] ?: 2000L
            val brightness = prefs[KEY_BRIGHTNESS] ?: 1.0f
            LightStyle(pattern, color, speedMs, brightness)
        }

    val autoOffSeconds: Flow<Int> = context.dataStore.data
        .map { it[KEY_AUTO_OFF_SEC] ?: 60 }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setLightStyle(style: LightStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATTERN] = style.pattern.name
            prefs[KEY_COLOR] = style.color
            prefs[KEY_SPEED_MS] = style.speedMs
            prefs[KEY_BRIGHTNESS] = style.brightness
        }
    }

    suspend fun setAutoOffSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_AUTO_OFF_SEC] = seconds }
    }
}
