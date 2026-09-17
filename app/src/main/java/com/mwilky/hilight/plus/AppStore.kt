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
        private val KEY_OTHER_CONTACTS_QUIET_START = intPreferencesKey("other_contacts_quiet_start")
        private val KEY_OTHER_CONTACTS_QUIET_END = intPreferencesKey("other_contacts_quiet_end")

        // Call Settings: Unknown / Private Numbers
        private val KEY_UNKNOWN_NUMBERS_ENABLED = booleanPreferencesKey("unknown_numbers_enabled")
        private val KEY_UNKNOWN_NUMBERS_COLOR = longPreferencesKey("unknown_numbers_color")
        private val KEY_UNKNOWN_NUMBERS_PATTERN = stringPreferencesKey("unknown_numbers_pattern")
        private val KEY_UNKNOWN_NUMBERS_FACE_DOWN = stringPreferencesKey("unknown_numbers_face_down")
        private val KEY_UNKNOWN_NUMBERS_DND = stringPreferencesKey("unknown_numbers_dnd")
        private val KEY_UNKNOWN_NUMBERS_QUIET = stringPreferencesKey("unknown_numbers_quiet")
        private val KEY_UNKNOWN_NUMBERS_QUIET_START = intPreferencesKey("unknown_numbers_quiet_start")
        private val KEY_UNKNOWN_NUMBERS_QUIET_END = intPreferencesKey("unknown_numbers_quiet_end")

        private val KEY_CALL_RULES_JSON = stringPreferencesKey("contact_rules_json")

        // Notification & Messaging Settings
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_DURATION_SEC = intPreferencesKey("notification_duration_sec")
        private val KEY_CYCLE_NOTIFICATIONS = booleanPreferencesKey("cycle_notifications")
        private val KEY_MULTI_ALERT_MODE = stringPreferencesKey("multi_alert_mode")
        private val KEY_DEFAULT_NOTIF_ENABLED = booleanPreferencesKey("default_notif_enabled")
        private val KEY_DEFAULT_NOTIF_COLOR = longPreferencesKey("default_notif_color")
        private val KEY_DEFAULT_NOTIF_PATTERN = stringPreferencesKey("default_notif_pattern")
        private val KEY_DEFAULT_NOTIF_FACE_DOWN = stringPreferencesKey("default_notif_face_down")
        private val KEY_DEFAULT_NOTIF_AUTO_COLOR = booleanPreferencesKey("default_notif_auto_color")
        private val KEY_DEFAULT_NOTIF_DND = stringPreferencesKey("default_notif_dnd")
        private val KEY_DEFAULT_NOTIF_QUIET = stringPreferencesKey("default_notif_quiet")
        private val KEY_DEFAULT_NOTIF_QUIET_START = intPreferencesKey("default_notif_quiet_start")
        private val KEY_DEFAULT_NOTIF_QUIET_END = intPreferencesKey("default_notif_quiet_end")
        private val KEY_MESSAGE_CONTACT_RULES_JSON = stringPreferencesKey("message_contact_rules_json")
        private val KEY_APP_RULES_JSON = stringPreferencesKey("app_rules_json")

        // Battery Indicator Settings
        private val KEY_BATTERY_JSON = stringPreferencesKey("battery_settings_json")

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

    val isCallLightsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_CALL_LIGHTS_ENABLED] ?: true }

    val isNotificationsEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }

    val multiAlertMode: Flow<MultiAlertMode> = appContext.dataStore.data
        .map { readMultiAlertMode(it) }

    val isCycleNotifications: Flow<Boolean> = multiAlertMode.map { it.keepsQueue }

    val battery: Flow<BatterySettings> = appContext.dataStore.data
        .map { BatterySettings.fromJson(it[KEY_BATTERY_JSON]) }

    /**
     * Every Home-screen-relevant setting in one snapshot, rebuilt whenever any of them
     * change. Replaces per-field flows for values that only ever change and get read
     * together (call/notification rules and their styling).
     */
    val settingsFlow: Flow<SettingsSnapshot> = appContext.dataStore.data.map(::buildSnapshot)

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

    suspend fun setCallLightsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_CALL_LIGHTS_ENABLED] = enabled }
    }

    suspend fun setOtherContactsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_OTHER_CONTACTS_ENABLED] = enabled }
    }

    suspend fun setUnknownNumbersEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotificationDurationSeconds(seconds: Int) {
        appContext.dataStore.edit { it[KEY_NOTIFICATION_DURATION_SEC] = seconds }
    }

    suspend fun setMultiAlertMode(mode: MultiAlertMode) {
        appContext.dataStore.edit { it[KEY_MULTI_ALERT_MODE] = mode.id }
    }

    /** Falls back to the pre-1.1.3 on/off cycle switch for installs that never picked a mode. */
    private fun readMultiAlertMode(prefs: Preferences): MultiAlertMode =
        MultiAlertMode.fromId(prefs[KEY_MULTI_ALERT_MODE])
            ?: if (prefs[KEY_CYCLE_NOTIFICATIONS] == true) MultiAlertMode.CYCLE else MultiAlertMode.LATEST

    suspend fun setDefaultNotifEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_ENABLED] = enabled }
    }

    suspend fun setOtherContactsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_OTHER_CONTACTS_PATTERN] = pattern.name
            prefs[KEY_OTHER_CONTACTS_COLOR] = color
            prefs[KEY_OTHER_CONTACTS_FACE_DOWN] = faceDown.name
            prefs[KEY_OTHER_CONTACTS_DND] = dndMode.name
            prefs[KEY_OTHER_CONTACTS_QUIET] = quietHoursMode.name
            prefs[KEY_OTHER_CONTACTS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_OTHER_CONTACTS_QUIET_END] = quietHoursEndMinutes
        }
    }

    suspend fun setUnknownNumbersStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_UNKNOWN_NUMBERS_PATTERN] = pattern.name
            prefs[KEY_UNKNOWN_NUMBERS_COLOR] = color
            prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN] = faceDown.name
            prefs[KEY_UNKNOWN_NUMBERS_DND] = dndMode.name
            prefs[KEY_UNKNOWN_NUMBERS_QUIET] = quietHoursMode.name
            prefs[KEY_UNKNOWN_NUMBERS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_UNKNOWN_NUMBERS_QUIET_END] = quietHoursEndMinutes
        }
    }

    suspend fun setDefaultNotifStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        autoColor: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_NOTIF_PATTERN] = pattern.name
            prefs[KEY_DEFAULT_NOTIF_COLOR] = color
            prefs[KEY_DEFAULT_NOTIF_FACE_DOWN] = faceDown.name
            prefs[KEY_DEFAULT_NOTIF_AUTO_COLOR] = autoColor
            prefs[KEY_DEFAULT_NOTIF_DND] = dndMode.name
            prefs[KEY_DEFAULT_NOTIF_QUIET] = quietHoursMode.name
            prefs[KEY_DEFAULT_NOTIF_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_DEFAULT_NOTIF_QUIET_END] = quietHoursEndMinutes
        }
    }

    suspend fun setBattery(settings: BatterySettings) {
        appContext.dataStore.edit { it[KEY_BATTERY_JSON] = settings.toJson().toString() }
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

    suspend fun snapshot(): SettingsSnapshot = buildSnapshot(appContext.dataStore.data.first())

    private fun buildSnapshot(prefs: Preferences): SettingsSnapshot {
        val d = DEFAULT_SETTINGS_SNAPSHOT
        return SettingsSnapshot(
            isEnabled = prefs[KEY_ENABLED] ?: d.isEnabled,
            isOnlyWhenFaceDown = prefs[KEY_ONLY_WHEN_FACE_DOWN] ?: d.isOnlyWhenFaceDown,
            suppressDuringDnd = prefs[KEY_SUPPRESS_DND] ?: d.suppressDuringDnd,
            quietHoursEnabled = prefs[KEY_QUIET_HOURS_ENABLED] ?: d.quietHoursEnabled,
            quietHoursStartMinutes = prefs[KEY_QUIET_HOURS_START] ?: d.quietHoursStartMinutes,
            quietHoursEndMinutes = prefs[KEY_QUIET_HOURS_END] ?: d.quietHoursEndMinutes,
            isCallLightsEnabled = prefs[KEY_CALL_LIGHTS_ENABLED] ?: d.isCallLightsEnabled,
            contactRules = readContactRules(prefs[KEY_CALL_RULES_JSON]),
            isOtherContactsEnabled = prefs[KEY_OTHER_CONTACTS_ENABLED] ?: d.isOtherContactsEnabled,
            otherContactsColor = prefs[KEY_OTHER_CONTACTS_COLOR] ?: d.otherContactsColor,
            otherContactsPattern = enumOr(prefs[KEY_OTHER_CONTACTS_PATTERN], d.otherContactsPattern),
            otherContactsFaceDownMode = enumOr(prefs[KEY_OTHER_CONTACTS_FACE_DOWN], d.otherContactsFaceDownMode),
            otherContactsDndMode = enumOr(prefs[KEY_OTHER_CONTACTS_DND], d.otherContactsDndMode),
            otherContactsQuietHoursMode = enumOr(prefs[KEY_OTHER_CONTACTS_QUIET], d.otherContactsQuietHoursMode),
            otherContactsQuietHoursStartMinutes = prefs[KEY_OTHER_CONTACTS_QUIET_START] ?: d.otherContactsQuietHoursStartMinutes,
            otherContactsQuietHoursEndMinutes = prefs[KEY_OTHER_CONTACTS_QUIET_END] ?: d.otherContactsQuietHoursEndMinutes,
            isUnknownNumbersEnabled = prefs[KEY_UNKNOWN_NUMBERS_ENABLED] ?: d.isUnknownNumbersEnabled,
            unknownNumbersColor = prefs[KEY_UNKNOWN_NUMBERS_COLOR] ?: d.unknownNumbersColor,
            unknownNumbersPattern = enumOr(prefs[KEY_UNKNOWN_NUMBERS_PATTERN], d.unknownNumbersPattern),
            unknownNumbersFaceDownMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN], d.unknownNumbersFaceDownMode),
            unknownNumbersDndMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_DND], d.unknownNumbersDndMode),
            unknownNumbersQuietHoursMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_QUIET], d.unknownNumbersQuietHoursMode),
            unknownNumbersQuietHoursStartMinutes = prefs[KEY_UNKNOWN_NUMBERS_QUIET_START] ?: d.unknownNumbersQuietHoursStartMinutes,
            unknownNumbersQuietHoursEndMinutes = prefs[KEY_UNKNOWN_NUMBERS_QUIET_END] ?: d.unknownNumbersQuietHoursEndMinutes,
            isNotificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: d.isNotificationsEnabled,
            notificationDurationSeconds = prefs[KEY_NOTIFICATION_DURATION_SEC] ?: d.notificationDurationSeconds,
            multiAlertMode = readMultiAlertMode(prefs),
            isDefaultNotifEnabled = prefs[KEY_DEFAULT_NOTIF_ENABLED] ?: d.isDefaultNotifEnabled,
            defaultNotifColor = prefs[KEY_DEFAULT_NOTIF_COLOR] ?: d.defaultNotifColor,
            defaultNotifPattern = enumOr(prefs[KEY_DEFAULT_NOTIF_PATTERN], d.defaultNotifPattern),
            defaultNotifFaceDownMode = enumOr(prefs[KEY_DEFAULT_NOTIF_FACE_DOWN], d.defaultNotifFaceDownMode),
            isDefaultNotifAutoColor = prefs[KEY_DEFAULT_NOTIF_AUTO_COLOR] ?: d.isDefaultNotifAutoColor,
            defaultNotifDndMode = enumOr(prefs[KEY_DEFAULT_NOTIF_DND], d.defaultNotifDndMode),
            defaultNotifQuietHoursMode = enumOr(prefs[KEY_DEFAULT_NOTIF_QUIET], d.defaultNotifQuietHoursMode),
            defaultNotifQuietHoursStartMinutes = prefs[KEY_DEFAULT_NOTIF_QUIET_START] ?: d.defaultNotifQuietHoursStartMinutes,
            defaultNotifQuietHoursEndMinutes = prefs[KEY_DEFAULT_NOTIF_QUIET_END] ?: d.defaultNotifQuietHoursEndMinutes,
            messageContactRules = readMessageRules(prefs[KEY_MESSAGE_CONTACT_RULES_JSON]),
            appRules = readAppRules(prefs[KEY_APP_RULES_JSON]),
            battery = BatterySettings.fromJson(prefs[KEY_BATTERY_JSON])
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
    val otherContactsQuietHoursStartMinutes: Int?,
    val otherContactsQuietHoursEndMinutes: Int?,
    val isUnknownNumbersEnabled: Boolean,
    val unknownNumbersColor: Long,
    val unknownNumbersPattern: PatternMode,
    val unknownNumbersFaceDownMode: FaceDownMode,
    val unknownNumbersDndMode: DndMode,
    val unknownNumbersQuietHoursMode: QuietHoursMode,
    val unknownNumbersQuietHoursStartMinutes: Int?,
    val unknownNumbersQuietHoursEndMinutes: Int?,
    val isNotificationsEnabled: Boolean,
    val notificationDurationSeconds: Int,
    val multiAlertMode: MultiAlertMode,
    val isDefaultNotifEnabled: Boolean,
    val defaultNotifColor: Long,
    val defaultNotifPattern: PatternMode,
    val defaultNotifFaceDownMode: FaceDownMode,
    val isDefaultNotifAutoColor: Boolean,
    val defaultNotifDndMode: DndMode,
    val defaultNotifQuietHoursMode: QuietHoursMode,
    val defaultNotifQuietHoursStartMinutes: Int?,
    val defaultNotifQuietHoursEndMinutes: Int?,
    val messageContactRules: List<MessageContactRule>,
    val appRules: List<AppNotificationRule>,
    val battery: BatterySettings
) {
    /** CYCLE and SPLIT both keep every waiting alert queued; only LATEST uses the timed single alert. */
    val isCycleNotifications: Boolean get() = multiAlertMode.keepsQueue

    fun findRuleForContactName(contactName: String): ContactRule? =
        firstEnabledNameMatch(contactName, contactRules, ContactRule::name, ContactRule::isEnabled)

    fun findMessageRuleForSender(senderName: String): MessageContactRule? =
        firstEnabledNameMatch(senderName, messageContactRules, MessageContactRule::name, MessageContactRule::isEnabled)

    fun findRuleForPackage(packageName: String): AppNotificationRule? {
        if (packageName.isBlank()) return null
        return appRules.firstOrNull { it.isEnabled && it.packageName.equals(packageName, ignoreCase = true) }
    }
}

/**
 * The single source of truth for every setting's default value, shared by
 * [AppStore.buildSnapshot]'s per-key fallbacks and [com.mwilky.hilight.plus.ui.HomeViewModel]'s
 * initial UI state, so the two can't silently drift apart.
 */
val DEFAULT_SETTINGS_SNAPSHOT = SettingsSnapshot(
    isEnabled = true,
    isOnlyWhenFaceDown = false,
    suppressDuringDnd = false,
    quietHoursEnabled = false,
    quietHoursStartMinutes = 22 * 60,
    quietHoursEndMinutes = 7 * 60,
    isCallLightsEnabled = true,
    contactRules = emptyList(),
    isOtherContactsEnabled = true,
    otherContactsColor = 0xFF4285F4,
    otherContactsPattern = PatternMode.PULSE,
    otherContactsFaceDownMode = FaceDownMode.INHERIT,
    otherContactsDndMode = DndMode.INHERIT,
    otherContactsQuietHoursMode = QuietHoursMode.INHERIT,
    otherContactsQuietHoursStartMinutes = null,
    otherContactsQuietHoursEndMinutes = null,
    isUnknownNumbersEnabled = true,
    unknownNumbersColor = 0xFFFBBC05,
    unknownNumbersPattern = PatternMode.PULSE,
    unknownNumbersFaceDownMode = FaceDownMode.INHERIT,
    unknownNumbersDndMode = DndMode.INHERIT,
    unknownNumbersQuietHoursMode = QuietHoursMode.INHERIT,
    unknownNumbersQuietHoursStartMinutes = null,
    unknownNumbersQuietHoursEndMinutes = null,
    isNotificationsEnabled = true,
    notificationDurationSeconds = 30,
    multiAlertMode = MultiAlertMode.LATEST,
    isDefaultNotifEnabled = true,
    defaultNotifColor = 0xFFFFFFFF,
    defaultNotifPattern = PatternMode.PULSE,
    defaultNotifFaceDownMode = FaceDownMode.INHERIT,
    isDefaultNotifAutoColor = true,
    defaultNotifDndMode = DndMode.INHERIT,
    defaultNotifQuietHoursMode = QuietHoursMode.INHERIT,
    defaultNotifQuietHoursStartMinutes = null,
    defaultNotifQuietHoursEndMinutes = null,
    messageContactRules = emptyList(),
    appRules = emptyList(),
    battery = BatterySettings()
)

private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}
