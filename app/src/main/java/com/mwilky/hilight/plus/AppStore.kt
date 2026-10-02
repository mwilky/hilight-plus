package com.mwilky.hilight.plus

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

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
        // Whether the one-time "no longer needs Shizuku" prompt has been shown (or wasn't needed).
        private val KEY_CONNECT_PROMPT_SHOWN = booleanPreferencesKey("connect_prompt_shown")
        // Licensing. Trial start is a local copy of the Settings.Global value the daemon owns.
        private val KEY_TRIAL_START_MS = longPreferencesKey("trial_start_ms")
        private val KEY_PURCHASED = booleanPreferencesKey("purchased")

        // Pause: the one in force (a wall-clock end and the features it covers), and the sheet's
        // last choice, which it offers again.
        private val KEY_PAUSE_UNTIL_MS = longPreferencesKey("pause_until_ms")
        private val KEY_PAUSE_FEATURES = stringPreferencesKey("pause_features")
        private val KEY_PAUSE_LAST_FEATURES = stringPreferencesKey("pause_last_features")
        private val KEY_PAUSE_LAST_DURATION = stringPreferencesKey("pause_last_duration")

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
        private val KEY_OTHER_CONTACTS_BRIGHTNESS = floatPreferencesKey("other_contacts_brightness")
        private val KEY_OTHER_CONTACTS_SPEED = floatPreferencesKey("other_contacts_speed")

        // Call Settings: Favourite (starred) Contacts
        private val KEY_FAV_CALLS_ENABLED = booleanPreferencesKey("fav_calls_enabled")
        private val KEY_FAV_CALLS_COLOR = longPreferencesKey("fav_calls_color")
        private val KEY_FAV_CALLS_PATTERN = stringPreferencesKey("fav_calls_pattern")
        private val KEY_FAV_CALLS_FACE_DOWN = stringPreferencesKey("fav_calls_face_down")
        private val KEY_FAV_CALLS_DND = stringPreferencesKey("fav_calls_dnd")
        private val KEY_FAV_CALLS_QUIET = stringPreferencesKey("fav_calls_quiet")
        private val KEY_FAV_CALLS_QUIET_START = intPreferencesKey("fav_calls_quiet_start")
        private val KEY_FAV_CALLS_QUIET_END = intPreferencesKey("fav_calls_quiet_end")
        private val KEY_FAV_CALLS_BRIGHTNESS = floatPreferencesKey("fav_calls_brightness")
        private val KEY_FAV_CALLS_SPEED = floatPreferencesKey("fav_calls_speed")

        // Call Settings: Unknown / Private Numbers
        private val KEY_UNKNOWN_NUMBERS_ENABLED = booleanPreferencesKey("unknown_numbers_enabled")
        private val KEY_UNKNOWN_NUMBERS_COLOR = longPreferencesKey("unknown_numbers_color")
        private val KEY_UNKNOWN_NUMBERS_PATTERN = stringPreferencesKey("unknown_numbers_pattern")
        private val KEY_UNKNOWN_NUMBERS_FACE_DOWN = stringPreferencesKey("unknown_numbers_face_down")
        private val KEY_UNKNOWN_NUMBERS_DND = stringPreferencesKey("unknown_numbers_dnd")
        private val KEY_UNKNOWN_NUMBERS_QUIET = stringPreferencesKey("unknown_numbers_quiet")
        private val KEY_UNKNOWN_NUMBERS_QUIET_START = intPreferencesKey("unknown_numbers_quiet_start")
        private val KEY_UNKNOWN_NUMBERS_QUIET_END = intPreferencesKey("unknown_numbers_quiet_end")
        private val KEY_UNKNOWN_NUMBERS_BRIGHTNESS = floatPreferencesKey("unknown_numbers_brightness")
        private val KEY_UNKNOWN_NUMBERS_SPEED = floatPreferencesKey("unknown_numbers_speed")

        // Call Settings: Missed Calls
        private val KEY_MISSED_CALLS_ENABLED = booleanPreferencesKey("missed_calls_enabled")
        private val KEY_MISSED_CALLS_COLOR = longPreferencesKey("missed_calls_color")
        private val KEY_MISSED_CALLS_PATTERN = stringPreferencesKey("missed_calls_pattern")
        private val KEY_MISSED_CALLS_FACE_DOWN = stringPreferencesKey("missed_calls_face_down")
        private val KEY_MISSED_CALLS_DND = stringPreferencesKey("missed_calls_dnd")
        private val KEY_MISSED_CALLS_QUIET = stringPreferencesKey("missed_calls_quiet")
        private val KEY_MISSED_CALLS_QUIET_START = intPreferencesKey("missed_calls_quiet_start")
        private val KEY_MISSED_CALLS_QUIET_END = intPreferencesKey("missed_calls_quiet_end")
        private val KEY_MISSED_CALLS_BRIGHTNESS = floatPreferencesKey("missed_calls_brightness")
        private val KEY_MISSED_CALLS_SPEED = floatPreferencesKey("missed_calls_speed")

        private val KEY_CALL_RULES_JSON = stringPreferencesKey("contact_rules_json")
        private val KEY_CALL_RULES_SORT = stringPreferencesKey("contact_rules_sort")

        // Notification & Messaging Settings
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_DURATION_SEC = intPreferencesKey("notification_duration_sec")
        private val KEY_CYCLE_NOTIFICATIONS = booleanPreferencesKey("cycle_notifications")
        private val KEY_MULTI_ALERT_MODE = stringPreferencesKey("multi_alert_mode")
        private val KEY_SPLIT_ANIMATION = stringPreferencesKey("split_animation")
        private val KEY_SPLIT_DIMMEST = floatPreferencesKey("split_dimmest")
        private val KEY_SPLIT_BRIGHTEST = floatPreferencesKey("split_brightest")
        private val KEY_SPLIT_STEADY = floatPreferencesKey("split_steady")
        private val KEY_SPLIT_SPEED = floatPreferencesKey("split_speed")
        private val KEY_DEFAULT_NOTIF_ENABLED = booleanPreferencesKey("default_notif_enabled")
        private val KEY_DEFAULT_NOTIF_COLOR = longPreferencesKey("default_notif_color")
        private val KEY_DEFAULT_NOTIF_PATTERN = stringPreferencesKey("default_notif_pattern")
        private val KEY_DEFAULT_NOTIF_FACE_DOWN = stringPreferencesKey("default_notif_face_down")
        private val KEY_DEFAULT_NOTIF_AUTO_COLOR = booleanPreferencesKey("default_notif_auto_color")
        private val KEY_DEFAULT_NOTIF_DND = stringPreferencesKey("default_notif_dnd")
        private val KEY_DEFAULT_NOTIF_QUIET = stringPreferencesKey("default_notif_quiet")
        private val KEY_DEFAULT_NOTIF_QUIET_START = intPreferencesKey("default_notif_quiet_start")
        private val KEY_DEFAULT_NOTIF_QUIET_END = intPreferencesKey("default_notif_quiet_end")
        private val KEY_DEFAULT_NOTIF_BRIGHTNESS = floatPreferencesKey("default_notif_brightness")
        private val KEY_DEFAULT_NOTIF_SPEED = floatPreferencesKey("default_notif_speed")
        private val KEY_FAV_NOTIF_ENABLED = booleanPreferencesKey("fav_notif_enabled")
        private val KEY_FAV_NOTIF_COLOR = longPreferencesKey("fav_notif_color")
        private val KEY_FAV_NOTIF_PATTERN = stringPreferencesKey("fav_notif_pattern")
        private val KEY_FAV_NOTIF_FACE_DOWN = stringPreferencesKey("fav_notif_face_down")
        private val KEY_FAV_NOTIF_DND = stringPreferencesKey("fav_notif_dnd")
        private val KEY_FAV_NOTIF_QUIET = stringPreferencesKey("fav_notif_quiet")
        private val KEY_FAV_NOTIF_QUIET_START = intPreferencesKey("fav_notif_quiet_start")
        private val KEY_FAV_NOTIF_QUIET_END = intPreferencesKey("fav_notif_quiet_end")
        private val KEY_FAV_NOTIF_BRIGHTNESS = floatPreferencesKey("fav_notif_brightness")
        private val KEY_FAV_NOTIF_SPEED = floatPreferencesKey("fav_notif_speed")
        private val KEY_MESSAGE_CONTACT_RULES_JSON = stringPreferencesKey("message_contact_rules_json")
        private val KEY_APP_RULES_JSON = stringPreferencesKey("app_rules_json")
        private val KEY_MESSAGE_CONTACT_RULES_SORT = stringPreferencesKey("message_contact_rules_sort")
        private val KEY_APP_RULES_SORT = stringPreferencesKey("app_rules_sort")

        // Battery Indicator Settings
        private val KEY_BATTERY_JSON = stringPreferencesKey("battery_settings_json")
        private val KEY_GEMINI_JSON = stringPreferencesKey("gemini_settings_json")

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

    val isConnectPromptShown: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_CONNECT_PROMPT_SHOWN] ?: false }

    val trialStartMillis: Flow<Long?> = appContext.dataStore.data
        .map { it[KEY_TRIAL_START_MS] }

    val isPurchased: Flow<Boolean> = appContext.dataStore.data
        .map { it[KEY_PURCHASED] ?: false }

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

    val splitAnimation: Flow<SplitAnimation> = appContext.dataStore.data
        .map { SplitAnimation.fromId(it[KEY_SPLIT_ANIMATION]) }

    val splitLook: Flow<SplitLook> = appContext.dataStore.data
        .map(::readSplitLook)

    val splitSpeed: Flow<Float> = appContext.dataStore.data
        .map { clampSpeed(it[KEY_SPLIT_SPEED] ?: DEFAULT_SPEED) }

    val battery: Flow<BatterySettings> = appContext.dataStore.data
        .map { BatterySettings.fromJson(it[KEY_BATTERY_JSON]) }

    val gemini: Flow<GeminiSettings> = appContext.dataStore.data
        .map { GeminiSettings.fromJson(it[KEY_GEMINI_JSON]) }

    /** The stored pause, ended or not. The daemon is handed this and ends it on time by itself. */
    val pause: Flow<PauseState?> = appContext.dataStore.data
        .map(::readPause)
        .distinctUntilChanged()

    /** The pause while it's in force, turning null by itself when it runs out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activePause: Flow<PauseState?> = pause.transformLatest { state ->
        val now = System.currentTimeMillis()
        if (state != null && state.isActive(now)) {
            emit(state)
            if (state.untilResumed) return@transformLatest
            delay(state.untilMillis - now)
        }
        emit(null)
    }.distinctUntilChanged()

    val lastPauseChoice: Flow<PauseChoice> = appContext.dataStore.data.map { prefs ->
        PauseChoice(
            features = prefs[KEY_PAUSE_LAST_FEATURES]?.let(PauseFeature::parse)?.takeIf { it.isNotEmpty() }
                ?: PauseFeature.ALL,
            duration = PauseDuration.fromId(prefs[KEY_PAUSE_LAST_DURATION])
        )
    }

    /**
     * Every Home-screen-relevant setting in one snapshot, rebuilt whenever any of them
     * change. Replaces per-field flows for values that only ever change and get read
     * together (call/notification rules and their styling).
     */
    val settingsFlow: Flow<SettingsSnapshot> = appContext.dataStore.data.map(::buildSnapshot)

    // --- Preferences Updaters ---

    /** Pauses [features] until [untilMillis], and remembers the choice for next time. */
    suspend fun startPause(features: Set<PauseFeature>, duration: PauseDuration, untilMillis: Long) {
        if (features.isEmpty()) return
        appContext.dataStore.edit {
            it[KEY_PAUSE_UNTIL_MS] = untilMillis
            it[KEY_PAUSE_FEATURES] = PauseFeature.encode(features)
            it[KEY_PAUSE_LAST_FEATURES] = PauseFeature.encode(features)
            it[KEY_PAUSE_LAST_DURATION] = duration.id
        }
    }

    suspend fun endPause() {
        appContext.dataStore.edit {
            it.remove(KEY_PAUSE_UNTIL_MS)
            it.remove(KEY_PAUSE_FEATURES)
        }
    }

    private fun readPause(prefs: Preferences): PauseState? {
        val until = prefs[KEY_PAUSE_UNTIL_MS] ?: return null
        val features = PauseFeature.parse(prefs[KEY_PAUSE_FEATURES]).takeIf { it.isNotEmpty() } ?: return null
        return PauseState(until, features)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setConnectPromptShown() {
        appContext.dataStore.edit { it[KEY_CONNECT_PROMPT_SHOWN] = true }
    }

    suspend fun setTrialStartMillis(millis: Long) {
        appContext.dataStore.edit { it[KEY_TRIAL_START_MS] = millis }
    }

    suspend fun setPurchased(purchased: Boolean) {
        appContext.dataStore.edit { it[KEY_PURCHASED] = purchased }
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

    suspend fun setFavouriteCallsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_FAV_CALLS_ENABLED] = enabled }
    }

    suspend fun setUnknownNumbersEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_UNKNOWN_NUMBERS_ENABLED] = enabled }
    }

    suspend fun setMissedCallsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_MISSED_CALLS_ENABLED] = enabled }
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

    suspend fun setSplitAnimation(animation: SplitAnimation) {
        appContext.dataStore.edit { it[KEY_SPLIT_ANIMATION] = animation.id }
    }

    suspend fun setSplitLook(look: SplitLook) {
        val clamped = look.clamped()
        appContext.dataStore.edit { prefs ->
            prefs[KEY_SPLIT_DIMMEST] = clamped.dimmest
            prefs[KEY_SPLIT_BRIGHTEST] = clamped.brightest
            prefs[KEY_SPLIT_STEADY] = clamped.steady
        }
    }

    suspend fun setSplitSpeed(speed: Float) {
        appContext.dataStore.edit { it[KEY_SPLIT_SPEED] = clampSpeed(speed) }
    }

    private fun readSplitLook(prefs: Preferences): SplitLook {
        val d = SplitLook()
        return SplitLook(
            dimmest = prefs[KEY_SPLIT_DIMMEST] ?: d.dimmest,
            brightest = prefs[KEY_SPLIT_BRIGHTEST] ?: d.brightest,
            steady = prefs[KEY_SPLIT_STEADY] ?: d.steady
        ).clamped()
    }

    suspend fun setCallRuleSort(sort: RuleSort) {
        appContext.dataStore.edit { it[KEY_CALL_RULES_SORT] = sort.name }
    }

    suspend fun setMessageRuleSort(sort: RuleSort) {
        appContext.dataStore.edit { it[KEY_MESSAGE_CONTACT_RULES_SORT] = sort.name }
    }

    suspend fun setAppRuleSort(sort: RuleSort) {
        appContext.dataStore.edit { it[KEY_APP_RULES_SORT] = sort.name }
    }

    /** Falls back to the pre-1.1.3 on/off cycle switch for installs that never picked a mode. */
    private fun readMultiAlertMode(prefs: Preferences): MultiAlertMode =
        MultiAlertMode.fromId(prefs[KEY_MULTI_ALERT_MODE])
            ?: if (prefs[KEY_CYCLE_NOTIFICATIONS] == true) MultiAlertMode.CYCLE else MultiAlertMode.LATEST

    suspend fun setDefaultNotifEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_DEFAULT_NOTIF_ENABLED] = enabled }
    }

    suspend fun setFavouriteNotifEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_FAV_NOTIF_ENABLED] = enabled }
    }

    suspend fun setOtherContactsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_OTHER_CONTACTS_PATTERN] = pattern.name
            prefs[KEY_OTHER_CONTACTS_COLOR] = color
            prefs[KEY_OTHER_CONTACTS_FACE_DOWN] = faceDown.name
            prefs[KEY_OTHER_CONTACTS_DND] = dndMode.name
            prefs[KEY_OTHER_CONTACTS_QUIET] = quietHoursMode.name
            prefs[KEY_OTHER_CONTACTS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_OTHER_CONTACTS_QUIET_END] = quietHoursEndMinutes
            prefs[KEY_OTHER_CONTACTS_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_OTHER_CONTACTS_SPEED] = clampSpeed(speed)
        }
    }

    suspend fun setFavouriteCallsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_FAV_CALLS_PATTERN] = pattern.name
            prefs[KEY_FAV_CALLS_COLOR] = color
            prefs[KEY_FAV_CALLS_FACE_DOWN] = faceDown.name
            prefs[KEY_FAV_CALLS_DND] = dndMode.name
            prefs[KEY_FAV_CALLS_QUIET] = quietHoursMode.name
            prefs[KEY_FAV_CALLS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_FAV_CALLS_QUIET_END] = quietHoursEndMinutes
            prefs[KEY_FAV_CALLS_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_FAV_CALLS_SPEED] = clampSpeed(speed)
        }
    }

    suspend fun setUnknownNumbersStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_UNKNOWN_NUMBERS_PATTERN] = pattern.name
            prefs[KEY_UNKNOWN_NUMBERS_COLOR] = color
            prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN] = faceDown.name
            prefs[KEY_UNKNOWN_NUMBERS_DND] = dndMode.name
            prefs[KEY_UNKNOWN_NUMBERS_QUIET] = quietHoursMode.name
            prefs[KEY_UNKNOWN_NUMBERS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_UNKNOWN_NUMBERS_QUIET_END] = quietHoursEndMinutes
            prefs[KEY_UNKNOWN_NUMBERS_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_UNKNOWN_NUMBERS_SPEED] = clampSpeed(speed)
        }
    }

    suspend fun setMissedCallsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_MISSED_CALLS_PATTERN] = pattern.name
            prefs[KEY_MISSED_CALLS_COLOR] = color
            prefs[KEY_MISSED_CALLS_FACE_DOWN] = faceDown.name
            prefs[KEY_MISSED_CALLS_DND] = dndMode.name
            prefs[KEY_MISSED_CALLS_QUIET] = quietHoursMode.name
            prefs[KEY_MISSED_CALLS_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_MISSED_CALLS_QUIET_END] = quietHoursEndMinutes
            prefs[KEY_MISSED_CALLS_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_MISSED_CALLS_SPEED] = clampSpeed(speed)
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
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
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
            prefs[KEY_DEFAULT_NOTIF_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_DEFAULT_NOTIF_SPEED] = clampSpeed(speed)
        }
    }

    suspend fun setFavouriteNotifStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int,
        brightness: Float,
        speed: Float
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_FAV_NOTIF_PATTERN] = pattern.name
            prefs[KEY_FAV_NOTIF_COLOR] = color
            prefs[KEY_FAV_NOTIF_FACE_DOWN] = faceDown.name
            prefs[KEY_FAV_NOTIF_DND] = dndMode.name
            prefs[KEY_FAV_NOTIF_QUIET] = quietHoursMode.name
            prefs[KEY_FAV_NOTIF_QUIET_START] = quietHoursStartMinutes
            prefs[KEY_FAV_NOTIF_QUIET_END] = quietHoursEndMinutes
            prefs[KEY_FAV_NOTIF_BRIGHTNESS] = clampBrightness(brightness)
            prefs[KEY_FAV_NOTIF_SPEED] = clampSpeed(speed)
        }
    }

    suspend fun setGemini(settings: GeminiSettings) {
        appContext.dataStore.edit { it[KEY_GEMINI_JSON] = settings.toJson().toString() }
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
            isOnlyWhenFaceDown = prefs[KEY_ONLY_WHEN_FACE_DOWN] ?: d.isOnlyWhenFaceDown,
            suppressDuringDnd = prefs[KEY_SUPPRESS_DND] ?: d.suppressDuringDnd,
            quietHoursEnabled = prefs[KEY_QUIET_HOURS_ENABLED] ?: d.quietHoursEnabled,
            quietHoursStartMinutes = prefs[KEY_QUIET_HOURS_START] ?: d.quietHoursStartMinutes,
            quietHoursEndMinutes = prefs[KEY_QUIET_HOURS_END] ?: d.quietHoursEndMinutes,
            isCallLightsEnabled = prefs[KEY_CALL_LIGHTS_ENABLED] ?: d.isCallLightsEnabled,
            contactRules = readContactRules(prefs[KEY_CALL_RULES_JSON]),
            callRuleSort = enumOr(prefs[KEY_CALL_RULES_SORT], d.callRuleSort),
            isOtherContactsEnabled = prefs[KEY_OTHER_CONTACTS_ENABLED] ?: d.isOtherContactsEnabled,
            otherContactsColor = prefs[KEY_OTHER_CONTACTS_COLOR] ?: d.otherContactsColor,
            otherContactsPattern = enumOr(prefs[KEY_OTHER_CONTACTS_PATTERN], d.otherContactsPattern),
            otherContactsFaceDownMode = enumOr(prefs[KEY_OTHER_CONTACTS_FACE_DOWN], d.otherContactsFaceDownMode),
            otherContactsDndMode = enumOr(prefs[KEY_OTHER_CONTACTS_DND], d.otherContactsDndMode),
            otherContactsQuietHoursMode = enumOr(prefs[KEY_OTHER_CONTACTS_QUIET], d.otherContactsQuietHoursMode),
            otherContactsQuietHoursStartMinutes = prefs[KEY_OTHER_CONTACTS_QUIET_START] ?: d.otherContactsQuietHoursStartMinutes,
            otherContactsQuietHoursEndMinutes = prefs[KEY_OTHER_CONTACTS_QUIET_END] ?: d.otherContactsQuietHoursEndMinutes,
            otherContactsBrightness = prefs[KEY_OTHER_CONTACTS_BRIGHTNESS] ?: d.otherContactsBrightness,
            otherContactsSpeed = prefs[KEY_OTHER_CONTACTS_SPEED] ?: d.otherContactsSpeed,
            isFavouriteCallsEnabled = prefs[KEY_FAV_CALLS_ENABLED] ?: d.isFavouriteCallsEnabled,
            favouriteCallsColor = prefs[KEY_FAV_CALLS_COLOR] ?: d.favouriteCallsColor,
            favouriteCallsPattern = enumOr(prefs[KEY_FAV_CALLS_PATTERN], d.favouriteCallsPattern),
            favouriteCallsFaceDownMode = enumOr(prefs[KEY_FAV_CALLS_FACE_DOWN], d.favouriteCallsFaceDownMode),
            favouriteCallsDndMode = enumOr(prefs[KEY_FAV_CALLS_DND], d.favouriteCallsDndMode),
            favouriteCallsQuietHoursMode = enumOr(prefs[KEY_FAV_CALLS_QUIET], d.favouriteCallsQuietHoursMode),
            favouriteCallsQuietHoursStartMinutes = prefs[KEY_FAV_CALLS_QUIET_START] ?: d.favouriteCallsQuietHoursStartMinutes,
            favouriteCallsQuietHoursEndMinutes = prefs[KEY_FAV_CALLS_QUIET_END] ?: d.favouriteCallsQuietHoursEndMinutes,
            favouriteCallsBrightness = prefs[KEY_FAV_CALLS_BRIGHTNESS] ?: d.favouriteCallsBrightness,
            favouriteCallsSpeed = prefs[KEY_FAV_CALLS_SPEED] ?: d.favouriteCallsSpeed,
            isUnknownNumbersEnabled = prefs[KEY_UNKNOWN_NUMBERS_ENABLED] ?: d.isUnknownNumbersEnabled,
            unknownNumbersColor = prefs[KEY_UNKNOWN_NUMBERS_COLOR] ?: d.unknownNumbersColor,
            unknownNumbersPattern = enumOr(prefs[KEY_UNKNOWN_NUMBERS_PATTERN], d.unknownNumbersPattern),
            unknownNumbersFaceDownMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_FACE_DOWN], d.unknownNumbersFaceDownMode),
            unknownNumbersDndMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_DND], d.unknownNumbersDndMode),
            unknownNumbersQuietHoursMode = enumOr(prefs[KEY_UNKNOWN_NUMBERS_QUIET], d.unknownNumbersQuietHoursMode),
            unknownNumbersQuietHoursStartMinutes = prefs[KEY_UNKNOWN_NUMBERS_QUIET_START] ?: d.unknownNumbersQuietHoursStartMinutes,
            unknownNumbersQuietHoursEndMinutes = prefs[KEY_UNKNOWN_NUMBERS_QUIET_END] ?: d.unknownNumbersQuietHoursEndMinutes,
            unknownNumbersBrightness = prefs[KEY_UNKNOWN_NUMBERS_BRIGHTNESS] ?: d.unknownNumbersBrightness,
            unknownNumbersSpeed = prefs[KEY_UNKNOWN_NUMBERS_SPEED] ?: d.unknownNumbersSpeed,
            isMissedCallsEnabled = prefs[KEY_MISSED_CALLS_ENABLED] ?: d.isMissedCallsEnabled,
            missedCallsColor = prefs[KEY_MISSED_CALLS_COLOR] ?: d.missedCallsColor,
            missedCallsPattern = enumOr(prefs[KEY_MISSED_CALLS_PATTERN], d.missedCallsPattern),
            missedCallsFaceDownMode = enumOr(prefs[KEY_MISSED_CALLS_FACE_DOWN], d.missedCallsFaceDownMode),
            missedCallsDndMode = enumOr(prefs[KEY_MISSED_CALLS_DND], d.missedCallsDndMode),
            missedCallsQuietHoursMode = enumOr(prefs[KEY_MISSED_CALLS_QUIET], d.missedCallsQuietHoursMode),
            missedCallsQuietHoursStartMinutes = prefs[KEY_MISSED_CALLS_QUIET_START] ?: d.missedCallsQuietHoursStartMinutes,
            missedCallsQuietHoursEndMinutes = prefs[KEY_MISSED_CALLS_QUIET_END] ?: d.missedCallsQuietHoursEndMinutes,
            missedCallsBrightness = prefs[KEY_MISSED_CALLS_BRIGHTNESS] ?: d.missedCallsBrightness,
            missedCallsSpeed = prefs[KEY_MISSED_CALLS_SPEED] ?: d.missedCallsSpeed,
            isNotificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: d.isNotificationsEnabled,
            notificationDurationSeconds = prefs[KEY_NOTIFICATION_DURATION_SEC] ?: d.notificationDurationSeconds,
            multiAlertMode = readMultiAlertMode(prefs),
            splitAnimation = SplitAnimation.fromId(prefs[KEY_SPLIT_ANIMATION]),
            splitLook = readSplitLook(prefs),
            splitSpeed = clampSpeed(prefs[KEY_SPLIT_SPEED] ?: d.splitSpeed),
            isDefaultNotifEnabled = prefs[KEY_DEFAULT_NOTIF_ENABLED] ?: d.isDefaultNotifEnabled,
            defaultNotifColor = prefs[KEY_DEFAULT_NOTIF_COLOR] ?: d.defaultNotifColor,
            defaultNotifPattern = enumOr(prefs[KEY_DEFAULT_NOTIF_PATTERN], d.defaultNotifPattern),
            defaultNotifFaceDownMode = enumOr(prefs[KEY_DEFAULT_NOTIF_FACE_DOWN], d.defaultNotifFaceDownMode),
            isDefaultNotifAutoColor = prefs[KEY_DEFAULT_NOTIF_AUTO_COLOR] ?: d.isDefaultNotifAutoColor,
            defaultNotifDndMode = enumOr(prefs[KEY_DEFAULT_NOTIF_DND], d.defaultNotifDndMode),
            defaultNotifQuietHoursMode = enumOr(prefs[KEY_DEFAULT_NOTIF_QUIET], d.defaultNotifQuietHoursMode),
            defaultNotifQuietHoursStartMinutes = prefs[KEY_DEFAULT_NOTIF_QUIET_START] ?: d.defaultNotifQuietHoursStartMinutes,
            defaultNotifQuietHoursEndMinutes = prefs[KEY_DEFAULT_NOTIF_QUIET_END] ?: d.defaultNotifQuietHoursEndMinutes,
            defaultNotifBrightness = prefs[KEY_DEFAULT_NOTIF_BRIGHTNESS] ?: d.defaultNotifBrightness,
            defaultNotifSpeed = prefs[KEY_DEFAULT_NOTIF_SPEED] ?: d.defaultNotifSpeed,
            isFavouriteNotifEnabled = prefs[KEY_FAV_NOTIF_ENABLED] ?: d.isFavouriteNotifEnabled,
            favouriteNotifColor = prefs[KEY_FAV_NOTIF_COLOR] ?: d.favouriteNotifColor,
            favouriteNotifPattern = enumOr(prefs[KEY_FAV_NOTIF_PATTERN], d.favouriteNotifPattern),
            favouriteNotifFaceDownMode = enumOr(prefs[KEY_FAV_NOTIF_FACE_DOWN], d.favouriteNotifFaceDownMode),
            favouriteNotifDndMode = enumOr(prefs[KEY_FAV_NOTIF_DND], d.favouriteNotifDndMode),
            favouriteNotifQuietHoursMode = enumOr(prefs[KEY_FAV_NOTIF_QUIET], d.favouriteNotifQuietHoursMode),
            favouriteNotifQuietHoursStartMinutes = prefs[KEY_FAV_NOTIF_QUIET_START] ?: d.favouriteNotifQuietHoursStartMinutes,
            favouriteNotifQuietHoursEndMinutes = prefs[KEY_FAV_NOTIF_QUIET_END] ?: d.favouriteNotifQuietHoursEndMinutes,
            favouriteNotifBrightness = prefs[KEY_FAV_NOTIF_BRIGHTNESS] ?: d.favouriteNotifBrightness,
            favouriteNotifSpeed = prefs[KEY_FAV_NOTIF_SPEED] ?: d.favouriteNotifSpeed,
            messageContactRules = readMessageRules(prefs[KEY_MESSAGE_CONTACT_RULES_JSON]),
            appRules = readAppRules(prefs[KEY_APP_RULES_JSON]),
            messageRuleSort = enumOr(prefs[KEY_MESSAGE_CONTACT_RULES_SORT], d.messageRuleSort),
            appRuleSort = enumOr(prefs[KEY_APP_RULES_SORT], d.appRuleSort),
            battery = BatterySettings.fromJson(prefs[KEY_BATTERY_JSON]),
            gemini = GeminiSettings.fromJson(prefs[KEY_GEMINI_JSON])
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
    val isOnlyWhenFaceDown: Boolean,
    val suppressDuringDnd: Boolean,
    val quietHoursEnabled: Boolean,
    val quietHoursStartMinutes: Int,
    val quietHoursEndMinutes: Int,
    val isCallLightsEnabled: Boolean,
    val contactRules: List<ContactRule>,
    val callRuleSort: RuleSort,
    val isOtherContactsEnabled: Boolean,
    val otherContactsColor: Long,
    val otherContactsPattern: PatternMode,
    val otherContactsFaceDownMode: FaceDownMode,
    val otherContactsDndMode: DndMode,
    val otherContactsQuietHoursMode: QuietHoursMode,
    val otherContactsQuietHoursStartMinutes: Int?,
    val otherContactsQuietHoursEndMinutes: Int?,
    val otherContactsBrightness: Float,
    val otherContactsSpeed: Float,
    val isFavouriteCallsEnabled: Boolean,
    val favouriteCallsColor: Long,
    val favouriteCallsPattern: PatternMode,
    val favouriteCallsFaceDownMode: FaceDownMode,
    val favouriteCallsDndMode: DndMode,
    val favouriteCallsQuietHoursMode: QuietHoursMode,
    val favouriteCallsQuietHoursStartMinutes: Int?,
    val favouriteCallsQuietHoursEndMinutes: Int?,
    val favouriteCallsBrightness: Float,
    val favouriteCallsSpeed: Float,
    val isUnknownNumbersEnabled: Boolean,
    val unknownNumbersColor: Long,
    val unknownNumbersPattern: PatternMode,
    val unknownNumbersFaceDownMode: FaceDownMode,
    val unknownNumbersDndMode: DndMode,
    val unknownNumbersQuietHoursMode: QuietHoursMode,
    val unknownNumbersQuietHoursStartMinutes: Int?,
    val unknownNumbersQuietHoursEndMinutes: Int?,
    val unknownNumbersBrightness: Float,
    val unknownNumbersSpeed: Float,
    val isMissedCallsEnabled: Boolean,
    val missedCallsColor: Long,
    val missedCallsPattern: PatternMode,
    val missedCallsFaceDownMode: FaceDownMode,
    val missedCallsDndMode: DndMode,
    val missedCallsQuietHoursMode: QuietHoursMode,
    val missedCallsQuietHoursStartMinutes: Int?,
    val missedCallsQuietHoursEndMinutes: Int?,
    val missedCallsBrightness: Float,
    val missedCallsSpeed: Float,
    val isNotificationsEnabled: Boolean,
    val notificationDurationSeconds: Int,
    val multiAlertMode: MultiAlertMode,
    val splitAnimation: SplitAnimation,
    val splitLook: SplitLook,
    val splitSpeed: Float,
    val isDefaultNotifEnabled: Boolean,
    val defaultNotifColor: Long,
    val defaultNotifPattern: PatternMode,
    val defaultNotifFaceDownMode: FaceDownMode,
    val isDefaultNotifAutoColor: Boolean,
    val defaultNotifDndMode: DndMode,
    val defaultNotifQuietHoursMode: QuietHoursMode,
    val defaultNotifQuietHoursStartMinutes: Int?,
    val defaultNotifQuietHoursEndMinutes: Int?,
    val defaultNotifBrightness: Float,
    val defaultNotifSpeed: Float,
    val isFavouriteNotifEnabled: Boolean,
    val favouriteNotifColor: Long,
    val favouriteNotifPattern: PatternMode,
    val favouriteNotifFaceDownMode: FaceDownMode,
    val favouriteNotifDndMode: DndMode,
    val favouriteNotifQuietHoursMode: QuietHoursMode,
    val favouriteNotifQuietHoursStartMinutes: Int?,
    val favouriteNotifQuietHoursEndMinutes: Int?,
    val favouriteNotifBrightness: Float,
    val favouriteNotifSpeed: Float,
    val messageContactRules: List<MessageContactRule>,
    val appRules: List<AppNotificationRule>,
    val messageRuleSort: RuleSort,
    val appRuleSort: RuleSort,
    val battery: BatterySettings,
    val gemini: GeminiSettings = GeminiSettings()
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
    isOnlyWhenFaceDown = false,
    suppressDuringDnd = false,
    quietHoursEnabled = false,
    quietHoursStartMinutes = 22 * 60,
    quietHoursEndMinutes = 7 * 60,
    isCallLightsEnabled = true,
    contactRules = emptyList(),
    callRuleSort = RuleSort.ADDED,
    isOtherContactsEnabled = true,
    otherContactsColor = 0xFF4285F4,
    otherContactsPattern = PatternMode.PULSE,
    otherContactsFaceDownMode = FaceDownMode.INHERIT,
    otherContactsDndMode = DndMode.INHERIT,
    otherContactsQuietHoursMode = QuietHoursMode.INHERIT,
    otherContactsQuietHoursStartMinutes = null,
    otherContactsQuietHoursEndMinutes = null,
    otherContactsBrightness = DEFAULT_BRIGHTNESS,
    otherContactsSpeed = DEFAULT_SPEED,
    // Favourites are off by default so existing installs keep their current lighting until opted in.
    isFavouriteCallsEnabled = false,
    favouriteCallsColor = 0xFFFFD600,
    favouriteCallsPattern = PatternMode.PULSE,
    favouriteCallsFaceDownMode = FaceDownMode.INHERIT,
    favouriteCallsDndMode = DndMode.INHERIT,
    favouriteCallsQuietHoursMode = QuietHoursMode.INHERIT,
    favouriteCallsQuietHoursStartMinutes = null,
    favouriteCallsQuietHoursEndMinutes = null,
    favouriteCallsBrightness = DEFAULT_BRIGHTNESS,
    favouriteCallsSpeed = DEFAULT_SPEED,
    isUnknownNumbersEnabled = true,
    unknownNumbersColor = 0xFFFBBC05,
    unknownNumbersPattern = PatternMode.PULSE,
    unknownNumbersFaceDownMode = FaceDownMode.INHERIT,
    unknownNumbersDndMode = DndMode.INHERIT,
    unknownNumbersQuietHoursMode = QuietHoursMode.INHERIT,
    unknownNumbersQuietHoursStartMinutes = null,
    unknownNumbersQuietHoursEndMinutes = null,
    unknownNumbersBrightness = DEFAULT_BRIGHTNESS,
    unknownNumbersSpeed = DEFAULT_SPEED,
    // Off by default so missed-call notifications keep following notification rules until opted in.
    isMissedCallsEnabled = false,
    missedCallsColor = 0xFFFF6D00,
    missedCallsPattern = PatternMode.PULSE,
    missedCallsFaceDownMode = FaceDownMode.INHERIT,
    missedCallsDndMode = DndMode.INHERIT,
    missedCallsQuietHoursMode = QuietHoursMode.INHERIT,
    missedCallsQuietHoursStartMinutes = null,
    missedCallsQuietHoursEndMinutes = null,
    missedCallsBrightness = DEFAULT_BRIGHTNESS,
    missedCallsSpeed = DEFAULT_SPEED,
    isNotificationsEnabled = true,
    notificationDurationSeconds = 30,
    multiAlertMode = MultiAlertMode.LATEST,
    splitAnimation = SplitAnimation.BREATHE,
    splitLook = SplitLook(),
    splitSpeed = DEFAULT_SPEED,
    isDefaultNotifEnabled = true,
    defaultNotifColor = 0xFFFFFFFF,
    defaultNotifPattern = PatternMode.PULSE,
    defaultNotifFaceDownMode = FaceDownMode.INHERIT,
    isDefaultNotifAutoColor = true,
    defaultNotifDndMode = DndMode.INHERIT,
    defaultNotifQuietHoursMode = QuietHoursMode.INHERIT,
    defaultNotifQuietHoursStartMinutes = null,
    defaultNotifQuietHoursEndMinutes = null,
    defaultNotifBrightness = DEFAULT_BRIGHTNESS,
    defaultNotifSpeed = DEFAULT_SPEED,
    isFavouriteNotifEnabled = false,
    favouriteNotifColor = 0xFFFFD600,
    favouriteNotifPattern = PatternMode.PULSE,
    favouriteNotifFaceDownMode = FaceDownMode.INHERIT,
    favouriteNotifDndMode = DndMode.INHERIT,
    favouriteNotifQuietHoursMode = QuietHoursMode.INHERIT,
    favouriteNotifQuietHoursStartMinutes = null,
    favouriteNotifQuietHoursEndMinutes = null,
    favouriteNotifBrightness = DEFAULT_BRIGHTNESS,
    favouriteNotifSpeed = DEFAULT_SPEED,
    messageContactRules = emptyList(),
    appRules = emptyList(),
    messageRuleSort = RuleSort.ADDED,
    appRuleSort = RuleSort.ADDED,
    battery = BatterySettings()
)

private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}
