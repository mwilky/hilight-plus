package com.mwilky.hilight.plus

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates persistent preferences ([AppStore]) and privileged hardware lighting execution ([ShizukuBridge]).
 */
class LightController private constructor(private val app: Application) {

    // First, so everything constructed below already logs into the shareable log.
    val debugLog = DebugLogStore.get(app).also {
        DebugLog.i(TAG, "App process started (${BuildConfig.VERSION_NAME}, pid ${Process.myPid()})")
    }
    val store = AppStore.get(app)
    val shizuku = ShizukuBridge.get(app)
    val licensing = Licensing(app, store, shizuku)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile
    private var lastDndActive = false

    val isEnabled: StateFlow<Boolean> = store.isEnabled
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
            setDndActive(
                isSystemDndActive(
                    context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
                )
            )
        }
    }

    // Latest known battery reading and settings, kept so the orientation monitor can be
    // re-evaluated whenever either changes, without re-reading the DataStore.
    @Volatile
    private var lastBatterySettings = BatterySettings()
    @Volatile
    private var lastMasterEnabled = true
    @Volatile
    private var lastBatteryRequiresFaceDown = false
    @Volatile
    private var lastBatteryLevel = 100
    @Volatile
    private var lastBatteryCharging = false
    @Volatile
    private var lastBatteryFull = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            onBatteryStateChanged(
                percent = (level * 100) / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
                full = status == BatteryManager.BATTERY_STATUS_FULL
            )
        }
    }

    init {
        lastDndActive = isSystemDndActive(
            app.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        )
        app.registerReceiver(
            dndReceiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            Context.RECEIVER_EXPORTED
        )

        // Sticky broadcast: registering returns the current battery state immediately.
        app.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )?.let { batteryReceiver.onReceive(app, it) }

        DeviceOrientationDetector.onOrientationChanged = { faceDown ->
            shizuku.setDeviceFaceDown(faceDown)
        }

        shizuku.onAvailabilityChanged = {
            shizuku.setDeviceFaceDown(DeviceOrientationDetector.lastKnownFaceDown)
            shizuku.setDndActive(lastDndActive)
            NativeHiLightDetector.check(app)
            syncState()
            scope.launch { pushLiveConditions() }
        }

        scope.launch {
            shizuku.state.collect { NativeHiLightDetector.check(app) }
        }
        scope.launch {
            store.isEnabled.collect { syncState() }
        }
        scope.launch {
            licensing.isEntitled.collect { shizuku.setEntitled(it) }
        }
        scope.launch {
            store.isNotificationsEnabled.collect { enabled ->
                if (!enabled) shizuku.clearAlert()
            }
        }
        scope.launch {
            store.isCallLightsEnabled.collect { enabled ->
                if (!enabled) shizuku.stopIncomingCall()
            }
        }
        scope.launch {
            store.suppressDuringDnd.collect { enabled ->
                shizuku.setDndSuppressEnabled(enabled)
            }
        }
        scope.launch {
            store.multiAlertMode.collect { mode ->
                shizuku.setSplitRing(mode == MultiAlertMode.SPLIT)
            }
        }
        scope.launch {
            combine(
                store.quietHoursEnabled,
                store.quietHoursStartMinutes,
                store.quietHoursEndMinutes
            ) { enabled, start, end -> Triple(enabled, start, end) }
                .collect { (enabled, start, end) ->
                    shizuku.setQuietHours(enabled, start, end)
                }
        }
        scope.launch {
            combine(
                store.battery,
                store.isEnabled,
                store.isOnlyWhenFaceDown,
                licensing.isEntitled
            ) { settings, enabled, globalFaceDown, entitled -> Triple(settings, enabled && entitled, globalFaceDown) }
                .collect { (settings, enabled, globalFaceDown) -> pushBatteryConfig(settings, enabled, globalFaceDown) }
        }
        // Heartbeat: the daemon renders the battery layer indefinitely from its own cached
        // reading, so it treats silence as "the app is gone" and goes dark. Keep telling it we're
        // here while the layer could be showing something.
        scope.launch {
            while (isActive) {
                delay(BATTERY_HEARTBEAT_MS)
                if (batteryLayerCouldRender()) {
                    shizuku.setBatteryState(lastBatteryLevel, lastBatteryCharging, lastBatteryFull)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            store.setEnabled(enabled)
        }
    }

    // --- Production Alert & Event Controls ---

    /**
     * Triggers a transient alert effect for [durationMs] (e.g. notifications, preview effects).
     */
    fun triggerAlertEffect(
        pattern: PatternMode,
        color: Long,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        durationMs: Long = 3000L,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null
    ) {
        val calculatedSpeed = pattern.speedMs(speedMs)
        shizuku.triggerAlert(
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            durationMs = durationMs,
            requiresFaceDown = requiresFaceDown,
            dndMode = dndMode,
            quietHoursMode = quietHoursMode,
            quietStartMinutes = quietStartMinutes ?: -1,
            quietEndMinutes = quietEndMinutes ?: -1
        )
    }

    /**
     * Enqueues or updates a notification alert in the multi-notification cyclic queue.
     */
    fun postNotificationAlert(
        key: String,
        pattern: PatternMode,
        color: Long,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        durationMs: Long = 0L,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null
    ) {
        val calculatedSpeed = pattern.speedMs(speedMs)
        shizuku.postAlert(
            key = key,
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            durationMs = durationMs,
            requiresFaceDown = requiresFaceDown,
            dndMode = dndMode,
            quietHoursMode = quietHoursMode,
            quietStartMinutes = quietStartMinutes ?: -1,
            quietEndMinutes = quietEndMinutes ?: -1
        )
    }

    /**
     * Removes an active notification alert by key when dismissed or swiped away.
     */
    fun removeNotificationAlert(key: String) {
        shizuku.removeAlert(key)
    }

    /**
     * Previews a pattern/color on the physical LEDs from the rule editor. Ignores face-down,
     * DND and quiet-hours gating, and never disturbs whatever notification is actually active.
     */
    fun testPattern(pattern: PatternMode, color: Long, durationMs: Long = 3000L) {
        shizuku.testAlert(
            pattern = pattern.id,
            color = color,
            brightness = 1.0f,
            speedMs = pattern.speedMs(),
            durationMs = durationMs
        )
    }

    /** Diagnostic: lights only LED [index] for [durationMs], via the same test channel as [testPattern]. */
    fun testSingleLed(index: Int, durationMs: Long) {
        shizuku.testAlert(
            pattern = PatternRenderer.SINGLE_LED_PREFIX + index,
            color = 0xFFFF0000,
            brightness = 1.0f,
            speedMs = 1000L,
            durationMs = durationMs
        )
    }

    /**
     * Cancels an in-progress LED test preview, e.g. when the rule editor is closed early.
     */
    fun cancelTestPattern() {
        shizuku.cancelTestAlert()
    }

    /**
     * Starts an indefinite incoming call ring alert until answered or ended.
     */
    fun startIncomingCallAlert(
        pattern: PatternMode = PatternMode.PULSE,
        color: Long = 0xFF4285F4,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null
    ) {
        val calculatedSpeed = pattern.speedMs(speedMs)
        shizuku.startIncomingCall(
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            requiresFaceDown = requiresFaceDown,
            dndMode = dndMode,
            quietHoursMode = quietHoursMode,
            quietStartMinutes = quietStartMinutes ?: -1,
            quietEndMinutes = quietEndMinutes ?: -1
        )
    }

    fun setDeviceFaceDown(faceDown: Boolean) {
        shizuku.setDeviceFaceDown(faceDown)
    }

    fun setDndActive(dndActive: Boolean) {
        lastDndActive = dndActive
        shizuku.setDndActive(dndActive)
    }

    private suspend fun pushLiveConditions() {
        shizuku.setDndActive(lastDndActive)
        shizuku.setDndSuppressEnabled(store.suppressDuringDnd.first())
        shizuku.setSplitRing(store.multiAlertMode.first() == MultiAlertMode.SPLIT)
        shizuku.setQuietHours(
            store.quietHoursEnabled.first(),
            store.quietHoursStartMinutes.first(),
            store.quietHoursEndMinutes.first()
        )
        pushBatteryConfig(
            store.battery.first(),
            store.isEnabled.first() && licensing.isEntitled.value,
            store.isOnlyWhenFaceDown.first()
        )
        shizuku.setBatteryState(lastBatteryLevel, lastBatteryCharging, lastBatteryFull)
    }

    private fun pushBatteryConfig(settings: BatterySettings, masterEnabled: Boolean, globalOnlyWhenFaceDown: Boolean) {
        lastBatterySettings = settings
        lastMasterEnabled = masterEnabled
        lastBatteryRequiresFaceDown = settings.faceDownMode.requiresFaceDown(globalOnlyWhenFaceDown)
        shizuku.setBatteryConfig(
            enabled = masterEnabled && settings.enabled,
            chargingPattern = settings.chargingPattern,
            lowPattern = settings.lowPattern,
            autoColor = settings.autoColor,
            color = settings.color,
            showCharging = settings.showCharging,
            lowWarningEnabled = settings.lowWarningEnabled,
            lowThresholdPercent = settings.lowThresholdPercent,
            fullTimeoutMinutes = settings.fullTimeout.minutes,
            overridesNotifications = settings.overridesNotifications,
            requiresFaceDown = lastBatteryRequiresFaceDown,
            dndMode = settings.dndMode,
            quietHoursMode = settings.quietHoursMode,
            quietStartMinutes = settings.quietHoursStartMinutes,
            quietEndMinutes = settings.quietHoursEndMinutes
        )
        syncBatteryOrientationMonitor()
    }

    private fun onBatteryStateChanged(percent: Int, charging: Boolean, full: Boolean) {
        lastBatteryLevel = percent
        lastBatteryCharging = charging
        lastBatteryFull = full
        shizuku.setBatteryState(percent, charging, full)
        syncBatteryOrientationMonitor()
    }

    /** Whether the battery layer has anything to show, ignoring where it's allowed to show it. */
    private fun batteryLayerCouldRender(): Boolean {
        if (!lastMasterEnabled || !lastBatterySettings.enabled) return false
        val lowEligible = lastBatterySettings.lowWarningEnabled &&
            !lastBatteryCharging && !lastBatteryFull &&
            lastBatteryLevel <= lastBatterySettings.lowThresholdPercent
        val chargingEligible = lastBatterySettings.showCharging && (lastBatteryCharging || lastBatteryFull)
        return chargingEligible || lowEligible
    }

    /**
     * Only runs the orientation sensor for the battery layer while its face-down mode currently
     * requires it and it actually has something to show (charging, freshly full, or low).
     */
    private fun syncBatteryOrientationMonitor() {
        val lowEligible = lastBatterySettings.lowWarningEnabled &&
            !lastBatteryCharging && !lastBatteryFull &&
            lastBatteryLevel <= lastBatterySettings.lowThresholdPercent
        val chargingEligible = lastBatterySettings.showCharging && (lastBatteryCharging || lastBatteryFull)
        val wantsToRender = chargingEligible || lowEligible
        if (lastMasterEnabled && lastBatterySettings.enabled && lastBatteryRequiresFaceDown && wantsToRender) {
            DeviceOrientationDetector.retainMonitoring(app, DeviceOrientationDetector.TOKEN_BATTERY)
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_BATTERY)
        }
    }

    /**
     * Stops the call override without deleting pending notification alerts.
     */
    fun stopIncomingCallAlert() {
        shizuku.stopIncomingCall()
    }

    /**
     * Clears notification / transient alerts without interrupting an incoming call.
     */
    fun clearAlert() {
        shizuku.clearAlert()
        syncState()
    }

    fun syncState() {
        scope.launch {
            val enabled = store.isEnabled.first()
            if (!enabled) {
                shizuku.turnOff()
            }
        }
    }

    fun refreshStatus() {
        shizuku.refresh()
        licensing.refreshPurchases()
    }

    companion object {
        private const val TAG = "LightController"

        // Well inside the daemon's staleness window, so ordinary jitter never trips it.
        private const val BATTERY_HEARTBEAT_MS = 60_000L

        @Volatile
        private var instance: LightController? = null

        fun get(context: Context): LightController {
            val app = if (context is Application) context else context.applicationContext as Application
            return instance ?: synchronized(this) {
                instance ?: LightController(app).also { instance = it }
            }
        }
    }
}
