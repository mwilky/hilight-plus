package com.mwilky.hilight.plus

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hilight_plus_settings")

/**
 * DataStore-backed repository managing application settings, light configurations,
 * call contact rules, message contact rules, and per-app notification rules.
 */
class AppStore private constructor(private val appContext: Context) {

    @Volatile private var lastGoodContactRules: List<ContactRule> = emptyList()
    @Volatile private var lastGoodMessageRules: List<MessageContactRule> = emptyList()
    @Volatile private var lastGoodAppRules: List<AppNotificationRule> = emptyList()

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_PATTERN = stringPreferencesKey("pattern")
        private val KEY_COLOR = longPreferencesKey("color")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_SPEED_MS = longPreferencesKey("speed_ms")
        private val KEY_AUTO_OFF_SEC = intPreferencesKey("auto_off_sec")

        // Smart Condition Settings
        private val KEY_ONLY_WHEN_FACE_DOWN = booleanPreferencesKey("only_when_face_down")
        private val KEY_SUPPRESS_DND = booleanPreferencesKey("suppress_during_dnd")
        private val KEY_QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val KEY_QUIET_HOURS_START = intPreferencesKey("quiet_hours_start_min")
        private val KEY_QUIET_HOURS_END = intPreferencesKey("quiet_hours_end_min")

        // Call Settings: All Other Contacts
        private val KEY_CALL_LIGHTS_ENABLED = booleanPreferencesKey("call_lights_enabled")
        private val KEY_OTHER_CONTACTS_ENABLED = booleanPreferencesKey("other_contacts_enabled")
        private val KEY_OTHER_CONTACTS_COLOR = longPreferencesKey("other_contacts_color")
        private val KEY_OTHER_CONTACTS_PATTERN = stringPreferencesKey("other_contacts_pattern")
        private val KEY_OTHER_CONTACTS_FACE_DOWN = stringPreferencesKey("other_contacts_face_down")
        private val KEY_OTHER_CONTACTS_DND = stringPreferencesKey("other_contacts_dnd")
        private val KEY_OTHER_CONTACTS_QUIET = stringPreferencesKey("other_contacts_quiet")

        // Call Settings: Unknown / Private Numbers
        private val KEY_UNKNOWN_NUMBERS_ENABLED = booleanPreferencesKey("unknown_numbers_enabled")
        private val KEY_UNKNOWN_NUMBERS_COLOR = longPreferencesKey("unknown_numbers_color")
        private val KEY_UNKNOWN_NUMBERS_PATTERN = stringPreferencesKey("unknown_numbers_pattern")
        private val KEY_UNKNOWN_NUMBERS_FACE_DOWN = stringPreferencesKey("unknown_numbers_face_down")
        private val KEY_UNKNOWN_NUMBERS_DND = stringPreferencesKey("unknown_numbers_dnd")
        private val KEY_UNKNOWN_NUMBERS_QUIET = stringPreferencesKey("unknown_numbers_quiet")

        private val KEY_CALL_RULES_JSON = stringPreferencesKey("contact_rules_json")

        // Notification & Messaging Settings
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_DURATION_SEC = intPreferencesKey("notification_duration_sec")
        private val KEY_STOP_ON_DISMISS = booleanPreferencesKey("stop_on_dismiss")
        private val KEY_STOP_ON_UNLOCK = booleanPreferencesKey("stop_on_unlock")
        private val KEY_UNLOCK_BEHAVIOR = stringPreferencesKey("unlock_behavior")
        private val KEY_CYCLE_NOTIFICATIONS = booleanPreferencesKey("cycle_notifications")
        private val KEY_DEFAULT_NOTIF_ENABLED = booleanPreferencesKey("default_notif_enabled")
        private val KEY_DEFAULT_NOTIF_COLOR = longPreferencesKey("default_notif_color")
        private val KEY_DEFAULT_NOTIF_PATTERN = stringPreferencesKey("default_notif_pattern")
        private val KEY_DEFAULT_NOTIF_FACE_DOWN = stringPreferencesKey("default_notif_face_down")
        private val KEY_DEFAULT_NOTIF_AUTO_COLOR = booleanPreferencesKey("default_notif_auto_color")
        private val KEY_DEFAULT_NOTIF_DND = stringPreferencesKey("default_notif_dnd")
        private val KEY_DEFAULT_NOTIF_QUIET = stringPreferencesKey("default_notif_quiet")
        private val KEY_MESSAGE_CONTACT_RULES_JSON = stringPreferencesKey("message_contact_rules_json")
        private val KEY_APP_RULES_JSON = stringPreferencesKey("app_rules_json")

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

    val isOnlyWhenFaceDown: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_ONLY_WHEN_FACE_DOWN] ?: false }

    val suppressDuringDnd: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_SUPPRESS_DND] ?: false }

    val quietHoursEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_QUIET_HOURS_ENABLED] ?: false }

    val quietHoursStartMinutes: Flow<Int> = appContext.dataStore.data
        .map { it[KEY_QUIET_HOURS_START] ?: 22 * 60 }

    val quietHoursEndMinutes: Flow<Int> = appContext.dataStore.data
        .map { it[KEY_QUIET_HOURS_END] ?: 7 * 60 }

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

    // --- Call Settings: All Other Contacts ---

    val isCallLightsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_CALL_LIGHTS_ENABLED] ?: true }

    val isOtherContactsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_OTHER_CONTACTS_ENABLED] ?: true }

    val otherContactsColor: Flow<Long> = appContext.dataStore.data
        .map { it[KEY_OTHER_CONTACTS_COLOR] ?: 0xFF4285F4 }

    val otherContactsPattern: Flow<PatternMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_OTHER_CONTACTS_PATTERN] ?: PatternMode.PULSE.name
            runCatching { PatternMode.valueOf(name) }.getOrDefault(PatternMode.PULSE)
        }

    val otherContactsFaceDownMode: Flow<FaceDownMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_OTHER_CONTACTS_FACE_DOWN] ?: FaceDownMode.INHERIT.name
            runCatching { FaceDownMode.valueOf(name) }.getOrDefault(FaceDownMode.INHERIT)
        }

    val otherContactsDndMode: Flow<DndMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_OTHER_CONTACTS_DND] ?: DndMode.INHERIT.name
            runCatching { DndMode.valueOf(name) }.getOrDefault(DndMode.INHERIT)
        }

    val otherContactsQuietHoursMode: Flow<QuietHoursMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_OTHER_CONTACTS_QUIET] ?: QuietHoursMode.INHERIT.name
            runCatching { QuietHoursMode.valueOf(name) }.getOrDefault(QuietHoursMode.INHERIT)
        }

    // --- Call Settings: Unknown / Private Numbers ---

    val isUnknownNumbersEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_UNKNOWN_NUMBERS_ENABLED] ?: true }

    val unknownNumbersColor: Flow<Long> = appContext.dataStore.data
        .map { it[KEY_UNKNOWN_NUMBERS_COLOR] ?: 0xFFFBBC05 }

    val unknownNumbersPattern: Flow<PatternMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UNKNOWN_NUMBERS_PATTERN] ?: PatternMode.PULSE.name
            runCatching { PatternMode.valueOf(name) }.getOrDefault(PatternMode.PULSE)
        }

    val unknownNumbersFaceDownMode: Flow<FaceDownMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN] ?: FaceDownMode.INHERIT.name
            runCatching { FaceDownMode.valueOf(name) }.getOrDefault(FaceDownMode.INHERIT)
        }

    val unknownNumbersDndMode: Flow<DndMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UNKNOWN_NUMBERS_DND] ?: DndMode.INHERIT.name
            runCatching { DndMode.valueOf(name) }.getOrDefault(DndMode.INHERIT)
        }

    val unknownNumbersQuietHoursMode: Flow<QuietHoursMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UNKNOWN_NUMBERS_QUIET] ?: QuietHoursMode.INHERIT.name
            runCatching { QuietHoursMode.valueOf(name) }.getOrDefault(QuietHoursMode.INHERIT)
        }

    val contactRules: Flow<List<ContactRule>> = appContext.dataStore.data
        .map { readContactRules(it[KEY_CALL_RULES_JSON]) }

    // --- Notification & Messaging Settings ---

    val isNotificationsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }

    val notificationDurationSeconds: Flow<Int> = appContext.dataStore.data
        .map { it[KEY_NOTIFICATION_DURATION_SEC] ?: 30 }

    val isStopOnDismiss: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_STOP_ON_DISMISS] ?: false }

    val isStopOnUnlock: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_STOP_ON_UNLOCK] ?: false }

    val unlockBehavior: Flow<UnlockBehavior> = appContext.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_UNLOCK_BEHAVIOR]
            if (raw != null) {
                runCatching { UnlockBehavior.valueOf(raw) }.getOrDefault(UnlockBehavior.NONE)
            } else {
                // Migration: if old boolean stop_on_unlock was true -> CLEAR, else NONE
                if (prefs[KEY_STOP_ON_UNLOCK] == true) UnlockBehavior.CLEAR else UnlockBehavior.NONE
            }
        }

    val isCycleNotifications: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_CYCLE_NOTIFICATIONS] ?: false }

    val isDefaultNotifEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_DEFAULT_NOTIF_ENABLED] ?: true }

    val defaultNotifColor: Flow<Long> = appContext.dataStore.data
        .map { it[KEY_DEFAULT_NOTIF_COLOR] ?: 0xFFFFFFFF }

    val defaultNotifPattern: Flow<PatternMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_DEFAULT_NOTIF_PATTERN] ?: PatternMode.PULSE.name
            runCatching { PatternMode.valueOf(name) }.getOrDefault(PatternMode.PULSE)
        }

    val defaultNotifFaceDownMode: Flow<FaceDownMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_DEFAULT_NOTIF_FACE_DOWN] ?: FaceDownMode.INHERIT.name
            runCatching { FaceDownMode.valueOf(name) }.getOrDefault(FaceDownMode.INHERIT)
        }

    val isDefaultNotifAutoColor: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_DEFAULT_NOTIF_AUTO_COLOR] ?: true }

    val defaultNotifDndMode: Flow<DndMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_DEFAULT_NOTIF_DND] ?: DndMode.INHERIT.name
            runCatching { DndMode.valueOf(name) }.getOrDefault(DndMode.INHERIT)
        }

    val defaultNotifQuietHoursMode: Flow<QuietHoursMode> = appContext.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_DEFAULT_NOTIF_QUIET] ?: QuietHoursMode.INHERIT.name
            runCatching { QuietHoursMode.valueOf(name) }.getOrDefault(QuietHoursMode.INHERIT)
        }

    val messageContactRules: Flow<List<MessageContactRule>> = appContext.dataStore.data
        .map { readMessageRules(it[KEY_MESSAGE_CONTACT_RULES_JSON]) }

    val appRules: Flow<List<AppNotificationRule>> = appContext.dataStore.data
        .map { readAppRules(it[KEY_APP_RULES_JSON]) }

    // --- Preferences Updaters ---

    suspend fun setOnboardingCompleted(completed: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setOnlyWhenFaceDown(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ONLY_WHEN_FACE_DOWN] = enabled }
    }

    suspend fun setSuppressDuringDnd(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_SUPPRESS_DND] = enabled }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun setQuietHoursWindow(startMinutes: Int, endMinutes: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_QUIET_HOURS_START] = startMinutes
            prefs[KEY_QUIET_HOURS_END] = endMinutes
        }
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

    suspend fun setOtherContactsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_ENABLED] = enabled }
    }

    suspend fun setOtherContactsColor(color: Long) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_COLOR] = color }
    }

    suspend fun setOtherContactsPattern(pattern: PatternMode) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_PATTERN] = pattern.name }
    }

    suspend fun setOtherContactsFaceDownMode(mode: FaceDownMode) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_FACE_DOWN] = mode.name }
    }

    suspend fun setUnknownNumbersEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_ENABLED] = enabled }
    }

    suspend fun setUnknownNumbersColor(color: Long) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_COLOR] = color }
    }

    suspend fun setUnknownNumbersPattern(pattern: PatternMode) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_PATTERN] = pattern.name }
    }

    suspend fun setUnknownNumbersFaceDownMode(mode: FaceDownMode) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_FACE_DOWN] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotificationDurationSeconds(seconds: Int) {
        appContext.dataStore.edit { it[KEY_NOTIFICATION_DURATION_SEC] = seconds }
    }

    suspend fun setStopOnDismiss(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_STOP_ON_DISMISS] = enabled }
    }

    suspend fun setStopOnUnlock(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_STOP_ON_UNLOCK] = enabled }
    }

    suspend fun setUnlockBehavior(behavior: UnlockBehavior) {
        appContext.dataStore.edit { it[KEY_UNLOCK_BEHAVIOR] = behavior.name }
    }

    suspend fun setCycleNotifications(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_CYCLE_NOTIFICATIONS] = enabled }
    }

    suspend fun setDefaultNotifEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_ENABLED] = enabled }
    }

    suspend fun setDefaultNotifColor(color: Long) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_COLOR] = color }
    }

    suspend fun setDefaultNotifPattern(pattern: PatternMode) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_PATTERN] = pattern.name }
    }

    suspend fun setDefaultNotifFaceDownMode(mode: FaceDownMode) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_FACE_DOWN] = mode.name }
    }

    suspend fun setDefaultNotifAutoColor(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_AUTO_COLOR] = enabled }
    }

    suspend fun setOtherContactsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_OTHER_CONTACTS_PATTERN] = pattern.name
            prefs[KEY_OTHER_CONTACTS_COLOR] = color
            prefs[KEY_OTHER_CONTACTS_FACE_DOWN] = faceDown.name
            prefs[KEY_OTHER_CONTACTS_DND] = dndMode.name
            prefs[KEY_OTHER_CONTACTS_QUIET] = quietHoursMode.name
        }
    }

    suspend fun setUnknownNumbersStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_UNKNOWN_NUMBERS_PATTERN] = pattern.name
            prefs[KEY_UNKNOWN_NUMBERS_COLOR] = color
            prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN] = faceDown.name
            prefs[KEY_UNKNOWN_NUMBERS_DND] = dndMode.name
            prefs[KEY_UNKNOWN_NUMBERS_QUIET] = quietHoursMode.name
        }
    }

    suspend fun setDefaultNotifStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        autoColor: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_NOTIF_PATTERN] = pattern.name
            prefs[KEY_DEFAULT_NOTIF_COLOR] = color
            prefs[KEY_DEFAULT_NOTIF_FACE_DOWN] = faceDown.name
            prefs[KEY_DEFAULT_NOTIF_AUTO_COLOR] = autoColor
            prefs[KEY_DEFAULT_NOTIF_DND] = dndMode.name
            prefs[KEY_DEFAULT_NOTIF_QUIET] = quietHoursMode.name
        }
    }

    suspend fun saveContactRule(rule: ContactRule) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readContactRules(prefs[KEY_CALL_RULES_JSON]).toMutableList()
            val index = currentRules.indexOfFirst { it.id == rule.id || it.name.equals(rule.name, ignoreCase = true) }
            if (index >= 0) {
                currentRules[index] = rule
            } else {
                currentRules.add(rule)
            }
            prefs[KEY_CALL_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun deleteContactRule(ruleId: String) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readContactRules(prefs[KEY_CALL_RULES_JSON]).filterNot { it.id == ruleId }
            prefs[KEY_CALL_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun saveMessageContactRule(rule: MessageContactRule) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readMessageRules(prefs[KEY_MESSAGE_CONTACT_RULES_JSON]).toMutableList()
            val index = currentRules.indexOfFirst { it.id == rule.id || it.name.equals(rule.name, ignoreCase = true) }
            if (index >= 0) {
                currentRules[index] = rule
            } else {
                currentRules.add(rule)
            }
            prefs[KEY_MESSAGE_CONTACT_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun deleteMessageContactRule(ruleId: String) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readMessageRules(prefs[KEY_MESSAGE_CONTACT_RULES_JSON]).filterNot { it.id == ruleId }
            prefs[KEY_MESSAGE_CONTACT_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun saveAppRule(rule: AppNotificationRule) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readAppRules(prefs[KEY_APP_RULES_JSON]).toMutableList()
            val index = currentRules.indexOfFirst { it.packageName == rule.packageName }
            if (index >= 0) {
                currentRules[index] = rule
            } else {
                currentRules.add(rule)
            }
            prefs[KEY_APP_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun deleteAppRule(packageName: String) {
        appContext.dataStore.edit { prefs ->
            val currentRules = readAppRules(prefs[KEY_APP_RULES_JSON]).filterNot { it.packageName == packageName }
            prefs[KEY_APP_RULES_JSON] = encodeRuleArray(currentRules) { it.toJson() }
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = appContext.dataStore.data.first()
        return SettingsSnapshot(
            isEnabled = prefs[KEY_ENABLED] ?: true,
            isOnlyWhenFaceDown = prefs[KEY_ONLY_WHEN_FACE_DOWN] ?: false,
            suppressDuringDnd = prefs[KEY_SUPPRESS_DND] ?: false,
            quietHoursEnabled = prefs[KEY_QUIET_HOURS_ENABLED] ?: false,
            quietHoursStartMinutes = prefs[KEY_QUIET_HOURS_START] ?: 22 * 60,
            quietHoursEndMinutes = prefs[KEY_QUIET_HOURS_END] ?: 7 * 60,
            isCallLightsEnabled = prefs[KEY_CALL_LIGHTS_ENABLED] ?: true,
            contactRules = readContactRules(prefs[KEY_CALL_RULES_JSON]),
            isOtherContactsEnabled = prefs[KEY_OTHER_CONTACTS_ENABLED] ?: true,
            otherContactsColor = prefs[KEY_OTHER_CONTACTS_COLOR] ?: 0xFF4285F4,
            otherContactsPattern = enumOr(prefs[KEY_OTHER_CONTACTS_PATTERN], PatternMode.PULSE),
            otherContactsFaceDownMode = enumOr(prefs[KEY_OTHER_CONTACTS_FACE_DOWN], FaceDownMode.INHERIT),
            otherContactsDndMode = enumOr(prefs[KEY_OTHER_CONTACTS_DND], DndMode.INHERIT),
            otherContactsQuietHoursMode = enumOr(prefs[KEY_OTHER_CONTACTS_QUIET], QuietHoursMode.INHERIT),
            isUnknownNumbersEnabled = prefs[KEY_UNKNOWN_NUMBERS_ENABLED] ?: true,
            unknownNumbersColor = prefs[KEY_UNKNOWN_NUMBERS_COLOR] ?: 0xFFFBBC05,
            unknownNumbersPattern = enumOr(prefs[KEY_UNKNOWN_NUMBERS_PATTERN], PatternMode.PULSE),
            unknownNumbersFaceDownMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN], FaceDownMode.INHERIT),
            unknownNumbersDndMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_DND], DndMode.INHERIT),
            unknownNumbersQuietHoursMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_QUIET], QuietHoursMode.INHERIT),
            isNotificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            notificationDurationSeconds = prefs[KEY_NOTIFICATION_DURATION_SEC] ?: 30,
            unlockBehavior = unlockBehaviorFrom(prefs),
            isCycleNotifications = prefs[KEY_CYCLE_NOTIFICATIONS] ?: false,
            isDefaultNotifEnabled = prefs[KEY_DEFAULT_NOTIF_ENABLED] ?: true,
            defaultNotifColor = prefs[KEY_DEFAULT_NOTIF_COLOR] ?: 0xFFFFFFFF,
            defaultNotifPattern = enumOr(prefs[KEY_DEFAULT_NOTIF_PATTERN], PatternMode.PULSE),
            defaultNotifFaceDownMode = enumOr(prefs[KEY_DEFAULT_NOTIF_FACE_DOWN], FaceDownMode.INHERIT),
            isDefaultNotifAutoColor = prefs[KEY_DEFAULT_NOTIF_AUTO_COLOR] ?: true,
            defaultNotifDndMode = enumOr(prefs[KEY_DEFAULT_NOTIF_DND], DndMode.INHERIT),
            defaultNotifQuietHoursMode = enumOr(prefs[KEY_DEFAULT_NOTIF_QUIET], QuietHoursMode.INHERIT),
            messageContactRules = readMessageRules(prefs[KEY_MESSAGE_CONTACT_RULES_JSON]),
            appRules = readAppRules(prefs[KEY_APP_RULES_JSON])
        )
    }

    private fun readContactRules(raw: String?): List<ContactRule> {
        val parsed = parseRuleArray(raw, lastGoodContactRules, ContactRule::fromJson)
        lastGoodContactRules = parsed
        return parsed
    }

    private fun readMessageRules(raw: String?): List<MessageContactRule> {
        val parsed = parseRuleArray(raw, lastGoodMessageRules, MessageContactRule::fromJson)
        lastGoodMessageRules = parsed
        return parsed
    }

    private fun readAppRules(raw: String?): List<AppNotificationRule> {
        val parsed = parseRuleArray(raw, lastGoodAppRules, AppNotificationRule::fromJson)
        lastGoodAppRules = parsed
        return parsed
    }

    private fun unlockBehaviorFrom(prefs: Preferences): UnlockBehavior {
        val raw = prefs[KEY_UNLOCK_BEHAVIOR]
        return if (raw != null) {
            runCatching { UnlockBehavior.valueOf(raw) }.getOrDefault(UnlockBehavior.NONE)
        } else if (prefs[KEY_STOP_ON_UNLOCK] == true) {
            UnlockBehavior.CLEAR
        } else {
            UnlockBehavior.NONE
        }
    }
}

data class SettingsSnapshot(
    val isEnabled: Boolean,
    val isOnlyWhenFaceDown: Boolean,
    val suppressDuringDnd: Boolean,
    val quietHoursEnabled: Boolean,
    val quietHoursStartMinutes: Int,
    val quietHoursEndMinutes: Int,
    val isCallLightsEnabled: Boolean,
    val contactRules: List<ContactRule>,
    val isOtherContactsEnabled: Boolean,
    val otherContactsColor: Long,
    val otherContactsPattern: PatternMode,
    val otherContactsFaceDownMode: FaceDownMode,
    val otherContactsDndMode: DndMode,
    val otherContactsQuietHoursMode: QuietHoursMode,
    val isUnknownNumbersEnabled: Boolean,
    val unknownNumbersColor: Long,
    val unknownNumbersPattern: PatternMode,
    val unknownNumbersFaceDownMode: FaceDownMode,
    val unknownNumbersDndMode: DndMode,
    val unknownNumbersQuietHoursMode: QuietHoursMode,
    val isNotificationsEnabled: Boolean,
    val notificationDurationSeconds: Int,
    val unlockBehavior: UnlockBehavior,
    val isCycleNotifications: Boolean,
    val isDefaultNotifEnabled: Boolean,
    val defaultNotifColor: Long,
    val defaultNotifPattern: PatternMode,
    val defaultNotifFaceDownMode: FaceDownMode,
    val isDefaultNotifAutoColor: Boolean,
    val defaultNotifDndMode: DndMode,
    val defaultNotifQuietHoursMode: QuietHoursMode,
    val messageContactRules: List<MessageContactRule>,
    val appRules: List<AppNotificationRule>
) {
    fun findRuleForContactName(contactName: String): ContactRule? =
        firstEnabledNameMatch(contactName, contactRules, ContactRule::name, ContactRule::isEnabled)

    fun findMessageRuleForSender(senderName: String): MessageContactRule? =
        firstEnabledNameMatch(senderName, messageContactRules, MessageContactRule::name, MessageContactRule::isEnabled)

    fun findRuleForPackage(packageName: String): AppNotificationRule? {
        if (packageName.isBlank()) return null
        return appRules.firstOrNull { it.isEnabled && it.packageName.equals(packageName, ignoreCase = true) }
    }
}

private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}
