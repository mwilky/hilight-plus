package com.hilight.plus

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hilight_plus_settings")

/**
 * DataStore-backed repository managing application settings, light configurations, and contact calling rules.
 */
class AppStore private constructor(private val appContext: Context) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_PATTERN = stringPreferencesKey("pattern")
        private val KEY_COLOR = longPreferencesKey("color")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_SPEED_MS = longPreferencesKey("speed_ms")
        private val KEY_AUTO_OFF_SEC = intPreferencesKey("auto_off_sec")

        // Contact Calling Settings: All Other Contacts
        private val KEY_CALL_LIGHTS_ENABLED = booleanPreferencesKey("call_lights_enabled")
        private val KEY_OTHER_CONTACTS_COLOR = longPreferencesKey("other_contacts_color")
        private val KEY_OTHER_CONTACTS_PATTERN = stringPreferencesKey("other_contacts_pattern")

        // Contact Calling Settings: Unknown / Private Numbers
        private val KEY_UNKNOWN_NUMBERS_COLOR = longPreferencesKey("unknown_numbers_color")
        private val KEY_UNKNOWN_NUMBERS_PATTERN = stringPreferencesKey("unknown_numbers_pattern")

        private val KEY_CONTACT_RULES_JSON = stringPreferencesKey("contact_rules_json")

        @Volatile
        private var instance: AppStore? = null

        fun get(context: Context): AppStore {
            val app = if (context is Application) context else context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: AppStore(app).also { instance = it }
            }
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    val isEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_ENABLED] ?: true }

    val lightStyle: Flow<LightStyle> = appContext.dataStore.data
        .map { prefs ->
            val patternName = prefs[KEY_PATTERN] ?: PatternMode.OFF.name
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.OFF)
            val color = prefs[KEY_COLOR] ?: 0xFF000000
            val speedMs = prefs[KEY_SPEED_MS] ?: 2000L
            val brightness = prefs[KEY_BRIGHTNESS] ?: 1.0f
            LightStyle(pattern, color, speedMs, brightness)
        }

    val autoOffSeconds: Flow<Int> = appContext.dataStore.data
        .map { it[KEY_AUTO_OFF_SEC] ?: 60 }

    // --- Contact Calling Settings: All Other Contacts ---

    val isCallLightsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_CALL_LIGHTS_ENABLED] ?: true }

    val otherContactsColor: Flow<Long> = appContext.dataStore.data
        .map { it[KEY_OTHER_CONTACTS_COLOR] ?: 0xFF4285F4 }

    val otherContactsPattern: Flow<PatternMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_OTHER_CONTACTS_PATTERN] ?: PatternMode.PULSE.name
            runCatching { PatternMode.valueOf(name) }.getOrDefault(PatternMode.PULSE)
        }

    // --- Contact Calling Settings: Unknown / Private Numbers ---

    val unknownNumbersColor: Flow<Long> = appContext.dataStore.data
        .map { it[KEY_UNKNOWN_NUMBERS_COLOR] ?: 0xFFFBBC05 } // Google Yellow as default for unknown

    val unknownNumbersPattern: Flow<PatternMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UNKNOWN_NUMBERS_PATTERN] ?: PatternMode.PULSE.name
            runCatching { PatternMode.valueOf(name) }.getOrDefault(PatternMode.PULSE)
        }

    val contactRules: Flow<List<ContactRule>> = appContext.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_CONTACT_RULES_JSON] ?: "[]"
            runCatching {
                val array = JSONArray(raw)
                val list = mutableListOf<ContactRule>()
                for (i in 0 until array.length()) {
                    list.add(ContactRule.fromJson(array.getJSONObject(i)))
                }
                list.toList()
            }.getOrDefault(emptyList())
        }

    // --- Preferences Updaters ---

    suspend fun setOnboardingCompleted(completed: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setLightStyle(style: LightStyle) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_PATTERN] = style.pattern.name
            prefs[KEY_COLOR] = style.color
            prefs[KEY_SPEED_MS] = style.speedMs
            prefs[KEY_BRIGHTNESS] = style.brightness
        }
    }

    suspend fun setAutoOffSeconds(seconds: Int) {
        appContext.dataStore.edit { it[KEY_AUTO_OFF_SEC] = seconds }
    }

    suspend fun setCallLightsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_CALL_LIGHTS_ENABLED] = enabled }
    }

    suspend fun setOtherContactsColor(color: Long) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_COLOR] = color }
    }

    suspend fun setOtherContactsPattern(pattern: PatternMode) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_PATTERN] = pattern.name }
    }

    suspend fun setUnknownNumbersColor(color: Long) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_COLOR] = color }
    }

    suspend fun setUnknownNumbersPattern(pattern: PatternMode) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_PATTERN] = pattern.name }
    }

    suspend fun saveContactRule(rule: ContactRule) {
        appContext.dataStore.edit { prefs ->
            val currentRules = contactRules.first().toMutableList()
            val index = currentRules.indexOfFirst { it.id == rule.id || (it.phoneNumber.isNotEmpty() && it.phoneNumber == rule.phoneNumber) }
            if (index >= 0) {
                currentRules[index] = rule
            } else {
                currentRules.add(rule)
            }
            val array = JSONArray().apply {
                currentRules.forEach { put(it.toJson()) }
            }
            prefs[KEY_CONTACT_RULES_JSON] = array.toString()
        }
    }

    suspend fun deleteContactRule(ruleId: String) {
        appContext.dataStore.edit { prefs ->
            val currentRules = contactRules.first().filterNot { it.id == ruleId }
            val array = JSONArray().apply {
                currentRules.forEach { put(it.toJson()) }
            }
            prefs[KEY_CONTACT_RULES_JSON] = array.toString()
        }
    }

    /**
     * Looks up if a specific incoming phone number matches an active contact rule.
     */
    suspend fun findRuleForPhoneNumber(incomingNumber: String): ContactRule? {
        if (incomingNumber.isBlank()) return null
        val normalized = incomingNumber.replace(Regex("[^0-9+]"), "")
        val rules = contactRules.first()
        return rules.firstOrNull { rule ->
            rule.isEnabled && (
                rule.phoneNumber == normalized ||
                (rule.phoneNumber.length >= 7 && normalized.endsWith(rule.phoneNumber.takeLast(7)))
            )
        }
    }
}
