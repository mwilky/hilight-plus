package com.mwilky.hilight.plus

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
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
import kotlinx.coroutines.withContext

/**
 * Coordinates persistent preferences ([AppStore]) and privileged hardware lighting execution ([DaemonBridge]).
 */
class LightController private constructor(private val app: Application) {

    // First, so everything constructed below already logs into the shareable log.
    val debugLog = DebugLogStore.get(app).also {
        DebugLog.i(TAG, "App process started (${BuildConfig.VERSION_NAME}, pid ${Process.myPid()})")
    }
    val store = AppStore.get(app)
    val daemon = DaemonBridge.get(app)
    val licensing = Licensing(app, store, daemon)

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

    // A ring left on one of our old sessions (the render loop can't reach it any more) is only
    // noticed by the user when they pick the phone up, so that's when to check for it.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            scope.launch { healStuckRing() }
        }
    }
    private var lastAutoResetMs = 0L

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

        // Exported because SystemUI, not the system server, sends USER_PRESENT, and a
        // not-exported receiver never gets it. Only the system may send this action.
        app.registerReceiver(
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            Context.RECEIVER_EXPORTED
        )

        // Sticky broadcast: registering returns the current battery state immediately.
        app.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )?.let { batteryReceiver.onReceive(app, it) }

        DeviceOrientationDetector.onOrientationChanged = { faceDown ->
            daemon.setDeviceFaceDown(faceDown)
        }

        daemon.onAvailabilityChanged = {
            daemon.setDeviceFaceDown(DeviceOrientationDetector.lastKnownFaceDown)
            daemon.setDndActive(lastDndActive)
            NativeHiLightDetector.check(app)
            syncState()
            scope.launch { pushLiveConditions() }
        }

        scope.launch {
            daemon.state.collect { NativeHiLightDetector.check(app) }
        }
        scope.launch {
            store.isEnabled.collect { syncState() }
        }
        scope.launch {
            licensing.isEntitled.collect { daemon.setEntitled(it) }
        }
        scope.launch {
            store.isNotificationsEnabled.collect { enabled ->
                if (!enabled) daemon.clearAlert()
            }
        }
        scope.launch {
            store.isCallLightsEnabled.collect { enabled ->
                if (!enabled) daemon.stopIncomingCall()
            }
        }
        scope.launch {
            store.suppressDuringDnd.collect { enabled ->
                daemon.setDndSuppressEnabled(enabled)
            }
        }
        scope.launch {
            store.multiAlertMode.collect { mode ->
                daemon.setSplitRing(mode == MultiAlertMode.SPLIT)
            }
        }
        scope.launch {
            store.splitAnimation.collect { animation ->
                daemon.setSplitAnimation(animation)
            }
        }
        scope.launch {
            combine(
                store.quietHoursEnabled,
                store.quietHoursStartMinutes,
                store.quietHoursEndMinutes
            ) { enabled, start, end -> Triple(enabled, start, end) }
                .collect { (enabled, start, end) ->
                    daemon.setQuietHours(enabled, start, end)
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
                    daemon.setBatteryState(lastBatteryLevel, lastBatteryCharging, lastBatteryFull)
                }
            }
        }
    }

    /**
     * Restarts the daemon when the LEDs don't show what it sent, which clears a session it lost
     * hold of. At most once per [AUTO_RESET_MIN_GAP_MS], so a check that keeps failing (another
     * system feature legitimately using the ring) can't restart it on every unlock.
     */
    private suspend fun healStuckRing() {
        if (!withContext(Dispatchers.IO) { daemon.isRingStuck() }) {
            DebugLog.d(TAG, "Ring check after unlock: matches")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastAutoResetMs != 0L && now - lastAutoResetMs < AUTO_RESET_MIN_GAP_MS) {
            DebugLog.w(TAG, "Ring looks stuck again soon after an automatic reset; leaving it")
            return
        }
        lastAutoResetMs = now
        DebugLog.w(TAG, "Ring looks stuck after unlock -> resetting the lights service")
        daemon.resetDaemon()
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
        daemon.triggerAlert(
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
        daemon.postAlert(
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
        daemon.removeAlert(key)
    }

    /**
     * Previews a pattern/color on the physical LEDs from the rule editor. Ignores face-down,
     * DND and quiet-hours gating, and never disturbs whatever notification is actually active.
     */
    fun testPattern(pattern: PatternMode, color: Long, durationMs: Long = 3000L) {
        daemon.testAlert(
            pattern = pattern.id,
            color = color,
            brightness = 1.0f,
            speedMs = pattern.speedMs(),
            durationMs = durationMs
        )
    }

    /** Diagnostic: lights only LED [index] for [durationMs], via the same test channel as [testPattern]. */
    fun testSingleLed(index: Int, durationMs: Long) {
        daemon.testAlert(
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
        daemon.cancelTestAlert()
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
        daemon.startIncomingCall(
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
        daemon.setDeviceFaceDown(faceDown)
    }

    fun setDndActive(dndActive: Boolean) {
        lastDndActive = dndActive
        daemon.setDndActive(dndActive)
    }

    private suspend fun pushLiveConditions() {
        daemon.setDndActive(lastDndActive)
        daemon.setDndSuppressEnabled(store.suppressDuringDnd.first())
        daemon.setSplitRing(store.multiAlertMode.first() == MultiAlertMode.SPLIT)
        daemon.setSplitAnimation(store.splitAnimation.first())
        daemon.setQuietHours(
            store.quietHoursEnabled.first(),
            store.quietHoursStartMinutes.first(),
            store.quietHoursEndMinutes.first()
        )
        pushBatteryConfig(
            store.battery.first(),
            store.isEnabled.first() && licensing.isEntitled.value,
            store.isOnlyWhenFaceDown.first()
        )
        daemon.setBatteryState(lastBatteryLevel, lastBatteryCharging, lastBatteryFull)
    }

    private fun pushBatteryConfig(settings: BatterySettings, masterEnabled: Boolean, globalOnlyWhenFaceDown: Boolean) {
        lastBatterySettings = settings
        lastMasterEnabled = masterEnabled
        lastBatteryRequiresFaceDown = settings.faceDownMode.requiresFaceDown(globalOnlyWhenFaceDown)
        daemon.setBatteryConfig(
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
        daemon.setBatteryState(percent, charging, full)
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
        daemon.stopIncomingCall()
    }

    /**
     * Clears notification / transient alerts without interrupting an incoming call.
     */
    fun clearAlert() {
        daemon.clearAlert()
        syncState()
    }

    fun syncState() {
        scope.launch {
            val enabled = store.isEnabled.first()
            if (!enabled) {
                daemon.turnOff()
            }
        }
    }

    fun refreshStatus() {
        daemon.refresh()
        licensing.refreshPurchases()
    }

    companion object {
        private const val TAG = "LightController"
        private const val AUTO_RESET_MIN_GAP_MS = 30 * 60_000L

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
