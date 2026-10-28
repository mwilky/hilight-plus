package com.mwilky.hilight.plus.core

import android.os.SystemClock
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.GeminiState
import com.mwilky.hilight.plus.LowBatteryPattern
import com.mwilky.hilight.plus.PauseFeature
import com.mwilky.hilight.plus.PauseState
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.SplitAnimation
import com.mwilky.hilight.plus.SplitLook
import com.mwilky.hilight.plus.currentMinutesOfDay

/**
 * Clean, lightweight render engine driving the Pixel 11 rear LEDs.
 * Supports multi-notification cyclic rendering across active unread alerts.
 *
 * Whatever the ring should show is handed to the lights hardware as one looping [RingEffect]
 * when it changes, and the hardware plays it by itself, including while the CPU sleeps. The loop
 * then only wakes for state changes and timers. Without effect support it falls back to pushing
 * frames at ~30 FPS.
 */
class LightEngine {

    data class QueuedAlert(
        val key: String,
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        val expiresAtMs: Long,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    private val lights = PixelLightsManager()
    private val renderer = PatternRenderer()
    private val lock = Any()

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    // State
    private var sessionPriority = 10
    private var deviceFaceDown = false
    private var dndActive = false
    private var dndSuppressEnabled = false
    private var quietHoursEnabled = false
    private var quietHoursStartMinutes = 22 * 60
    private var quietHoursEndMinutes = 7 * 60
    // A pause from the tile or the app keeps its features dark until it runs out. Whether the
    // last tick had one in force, so the moment it runs out is handled once.
    private var pause: PauseState? = null
    private var pauseInForce = false
    // The ring sits round the rear lenses, so while any app has a rear camera open it stays
    // dark, as if everything were paused, rather than tint photos or video.
    private var rearCameraActive = false
    // Lit in a pocket or bag, the ring only glows through fabric, so it stays dark the same way.
    // Alerts keep waiting and show once the phone comes out.
    private var inPocket = false

    // Ambient state: the idle render when nothing else is active and battery isn't showing. Always off.
    private val ambientPattern = "off"
    private val ambientColor = 0xFF000000
    private val ambientBrightness = 1.0f
    private val ambientSpeedMs = 2000L

    private data class BatteryConfig(
        val enabled: Boolean,
        val chargingPattern: BatteryPattern,
        val lowPattern: LowBatteryPattern,
        val autoColor: Boolean,
        val color: Long,
        val showCharging: Boolean,
        val lowWarningEnabled: Boolean,
        val lowThresholdPercent: Int,
        val fullTimeoutMinutes: Int?,
        val overridesNotifications: Boolean,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    // Battery indicator layer: renders in the idle slot beneath calls and (unless configured to
    // override them) notifications. Null config means the feature hasn't been set up / is off.
    private var batteryConfig: BatteryConfig? = null
    private var batteryLevel = 100
    private var batteryCharging = false
    private var batteryFull = false
    private var batteryFullSinceMs: Long? = null

    // The battery layer is the only thing that renders indefinitely, so it must never be left
    // holding the LEDs on a reading the app can no longer update - if the app process dies or
    // the binder drops while charging, nothing would otherwise turn them off. The app heartbeats
    // the battery state; once that stops arriving, the layer goes dark on its own.
    private var batteryStateUpdatedAtMs = 0L

    private data class IncomingCallAlert(
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        val startedAtMs: Long,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    // Incoming calls override notification output without deleting its state.
    private var incomingCallAlert: IncomingCallAlert? = null

    private data class DirectAlert(
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    // Standard-mode notification / transient alert state.
    private var directAlert: DirectAlert? = null
    private val directAlertTimer = PausableAlertTimer()

    private data class TestAlert(
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        val startedAtMs: Long,
        val expiresAtMs: Long,
        // Set when previewing the split ring instead of a pattern.
        val split: SplitTest? = null
    )

    private data class SplitTest(val colors: List<Long>, val animation: SplitAnimation, val look: SplitLook, val speed: Float)

    // In-app "test on LEDs" preview. Takes top render priority and ignores face-down/DND/quiet-hours
    // gating, but never touches the notification queue or direct alert state, so it can't disturb them.
    private var testAlert: TestAlert? = null

    /** How the ring looks during one Gemini state, and the conditions it shows under. */
    data class GeminiLookSpec(
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    // Taking over from stock Gemini feedback: what Gemini is doing, when it started, and how each
    // state looks. Sits above notifications and battery and below calls. A state its conditions
    // hide leaves the ring to whatever is beneath.
    private var geminiEnabled = false
    private var geminiLooks: Map<GeminiState, GeminiLookSpec> = emptyMap()
    private var geminiState: GeminiState? = null
    private var geminiStateSinceMs = 0L

    /** The Do Not Disturb indicator: its look while DND is on, and when it may show. */
    data class DndIndicatorSpec(
        val showIndicator: Boolean,
        val pattern: String,
        val color: Long,
        val brightness: Float,
        val speedMs: Long,
        // How long after DND turns on the look stays; null for as long as it's on.
        val showForMs: Long?,
        val flashOnChange: Boolean,
        val overridesBattery: Boolean,
        val requiresFaceDown: Boolean,
        val quietHoursMode: QuietHoursMode,
        val quietStartOverride: Int?,
        val quietEndOverride: Int?
    )

    // Do Not Disturb indicator layer: its look shares the idle slot with the battery layer, beneath
    // calls, Gemini and notifications; its brief on/off flash goes over all but calls. Null when
    // the feature is off.
    private var dndIndicator: DndIndicatorSpec? = null
    // Whether the app has reported DND since this process started: the first report only catches
    // up with how things already were, so it never flashes.
    private var dndKnown = false
    private var dndActiveSinceMs = 0L
    // Like the battery layer, the indicator can stay on for hours, so it goes dark by itself once
    // the app stops confirming DND is still on: nothing else could turn it off if the app died.
    private var dndStateUpdatedAtMs = 0L

    private data class DndFlash(val turningOn: Boolean, val startedAtMs: Long)

    private var dndFlash: DndFlash? = null

    // Multi-Notification Cyclic Queue
    private val activeAlerts = mutableListOf<QueuedAlert>()
    private var currentAlertIndex = 0
    private var cycleStartTimeMs = 0L
    private var needsSessionReset = false

    // Split-ring display of the queue: with two or more visible alerts each gets its own arc
    // instead of taking turns. A single visible alert still plays its own pattern.
    private var splitRing = false
    private var splitAnimation = SplitAnimation.BREATHE
    private var splitLook = SplitLook()
    private var splitSpeed = 1f
    // The arcs on show and when that set appeared, so a changed set restarts the animation
    // (Spotlight on the newest arc, Rotate with the newest at the top).
    private var splitKeys: List<String>? = null
    private var splitStartedAtMs = 0L
    // Announcing a new arrival: while the split ring is up, a newly posted notification plays one
    // loop of its own pattern before the arcs come back. Which one, and when it was posted.
    private var splitAnnounceNew = false
    private data class SplitIntro(val key: String, val startedAtMs: Long)
    private var splitIntro: SplitIntro? = null

    // Hardware effects: what the open session is showing, so an effect is only sent when that
    // changes, and whether the render loop is pushing frames (fallback) and so needs to tick fast.
    private var shownEffectKey: List<Any?>? = null
    private var shownEffectInfo: String? = null
    private var effectsFailed = false
    private var framesDriving = true
    private var directGeneration = 0
    private var wakePending = false
    // When the queue's next turn is due, so the loop wakes to send it.
    private var nextTurnAtMs: Long? = null
    private val forceFramesProperty = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
    }.getOrNull()

    // What the ring was last showing, so each change is logged once rather than every frame.
    private var lastRenderReason: String? = null
    private var lastTickElapsedMs = 0L
    private var lastTickUptimeMs = 0L

    fun start(): Boolean {
        synchronized(lock) {
            if (running) return true
            if (!lights.connect()) {
                DebugLog.e(TAG, "Failed to connect to lights backend")
                return false
            }
            running = true
            renderThread = Thread(::renderLoop, "HiLightPlus-Engine").apply {
                isDaemon = false
                start()
            }
            DebugLog.i(TAG, "LightEngine started with ${lights.ledCount} LEDs")
            return true
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            lights.blank()
            renderThread?.interrupt()
            renderThread = null
        }
    }

    val ledCount: Int
        get() = lights.ledCount

    /**
     * Starts a call override until explicitly stopped.
     */
    fun startIncomingCall(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartOverride: Int? = null,
        quietEndOverride: Int? = null
    ) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            incomingCallAlert = IncomingCallAlert(
                pattern,
                color,
                brightness,
                speedMs,
                now,
                requiresFaceDown,
                dndMode,
                quietHoursMode,
                quietStartOverride,
                quietEndOverride
            )
            syncNotificationTimer(now)
            needsSessionReset = true
            DebugLog.i(TAG, "startIncomingCall: pattern=$pattern, color=${hex(color)}, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
            wake()
        }
    }

    fun stopIncomingCall() {
        synchronized(lock) {
            if (incomingCallAlert == null) return
            incomingCallAlert = null
            DebugLog.i(TAG, "stopIncomingCall")
            val now = SystemClock.elapsedRealtime()
            syncNotificationTimer(now)
            cycleStartTimeMs = now
            needsSessionReset = true
            wake()
        }
    }

    /**
     * Replaces the standard-mode notification / transient alert.
     * Existing suppression remains in effect.
     */
    fun triggerAlert(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartOverride: Int? = null,
        quietEndOverride: Int? = null
    ) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            directAlert = DirectAlert(
                pattern, color, brightness, speedMs,
                requiresFaceDown, dndMode, quietHoursMode, quietStartOverride, quietEndOverride
            )
            directAlertTimer.start(
                durationMs = durationMs,
                nowMs = now,
                paused = !shouldRunNotificationTimer()
            )
            activeAlerts.clear()
            currentAlertIndex = 0
            needsSessionReset = true
            DebugLog.i(TAG, "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
            directGeneration++
            wake()
        }
    }

    /**
     * Enqueues or updates an alert in the multi-notification cyclic queue.
     */
    fun postAlert(
        key: String,
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartOverride: Int? = null,
        quietEndOverride: Int? = null
    ) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            val expiresAt = if (durationMs <= 0L) Long.MAX_VALUE else now + durationMs
            val alert = QueuedAlert(
                key = key,
                pattern = pattern,
                color = color,
                brightness = brightness,
                speedMs = speedMs,
                expiresAtMs = expiresAt,
                requiresFaceDown = requiresFaceDown,
                dndMode = dndMode,
                quietHoursMode = quietHoursMode,
                quietStartOverride = quietStartOverride,
                quietEndOverride = quietEndOverride
            )

            // Remove any existing entry with this key
            activeAlerts.removeAll { it.key == key }
            // Add latest alert to the queue
            activeAlerts.add(alert)

            // If this is the only active alert, start its cycle timer fresh
            if (activeAlerts.size == 1) {
                currentAlertIndex = 0
                cycleStartTimeMs = now
            }
            if (notificationVisible(requiresFaceDown, dndMode, quietHoursMode, quietStartOverride, quietEndOverride)) {
                needsSessionReset = true
                // Only matters once there are arcs to come back to; a lone alert plays its own
                // pattern anyway. Checked as the ring is drawn, since the others may yet go.
                if (splitRing && splitAnnounceNew) splitIntro = SplitIntro(key, now)
            }
            DebugLog.i(TAG, "postAlert [key=$key]: pattern=$pattern, color=$color, speedMs=$speedMs (queue size=${activeAlerts.size}, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode)")
            wake()
        }
    }

    /**
     * Renders a transient preview pattern with top priority, ignoring face-down/DND/quiet-hours
     * gating. Does not touch the notification queue or direct alert, so whatever was playing
     * resumes automatically once the test expires or is cancelled.
     */
    fun testAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            testAlert = TestAlert(pattern, color, brightness, speedMs, now, now + durationMs.coerceIn(0L, MAX_TEST_MS))
            needsSessionReset = true
            DebugLog.i(TAG, "testAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
            wake()
        }
    }

    /**
     * The split ring's version of [testAlert]: [colors] (newest first) as arcs in [animation], on
     * the same test channel, so it expires, cancels and gives the ring back the same way.
     */
    fun testSplit(colors: LongArray, animation: SplitAnimation, look: SplitLook, speed: Float, durationMs: Long) {
        if (colors.isEmpty()) return
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            val split = SplitTest(colors.take(PatternRenderer.MAX_SPLIT_SEGMENTS), animation, look, speed)
            testAlert = TestAlert("split", 0L, 1f, 0L, now, now + durationMs.coerceIn(0L, MAX_TEST_MS), split)
            needsSessionReset = true
            DebugLog.i(TAG, "testSplit: ${split.colors.size} arcs, ${animation.id}, $look, speed=$speed, durationMs=$durationMs")
            wake()
        }
    }

    fun cancelTestAlert() {
        synchronized(lock) {
            if (testAlert == null) return
            testAlert = null
            needsSessionReset = true
            DebugLog.i(TAG, "cancelTestAlert")
            wake()
        }
    }

    /** Replaces the Gemini looks. Turning the feature off drops any state on show. */
    fun setGeminiConfig(enabled: Boolean, looks: Map<GeminiState, GeminiLookSpec>) {
        synchronized(lock) {
            geminiEnabled = enabled
            geminiLooks = looks
            if (!enabled) geminiState = null
            needsSessionReset = true
            wake()
            DebugLog.i(TAG, "setGeminiConfig: enabled=$enabled, looks=${looks.keys.map { it.id }}")
        }
    }

    /** What Gemini is doing now, from its log; null when idle. */
    fun setGeminiState(state: GeminiState?) {
        synchronized(lock) {
            if (geminiState == state) return
            geminiState = if (geminiEnabled) state else null
            geminiStateSinceMs = SystemClock.elapsedRealtime()
            needsSessionReset = true
            wake()
        }
    }

    fun setDeviceFaceDown(faceDown: Boolean) {
        synchronized(lock) {
            if (deviceFaceDown == faceDown) return
            deviceFaceDown = faceDown
            val now = SystemClock.elapsedRealtime()
            syncNotificationTimer(now)
            cycleStartTimeMs = now
            needsSessionReset = true
            DebugLog.i(TAG, "setDeviceFaceDown: $faceDown")
            wake()
        }
    }

    fun setDndActive(active: Boolean) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            // Refreshed even when nothing changed: the app repeats it as the indicator's heartbeat.
            dndStateUpdatedAtMs = now
            val known = dndKnown
            dndKnown = true
            if (dndActive == active) return
            dndActive = active
            if (active) dndActiveSinceMs = now
            dndFlash = if (known && dndIndicator?.flashOnChange == true) DndFlash(active, now) else null
            onLiveConditionChanged()
            DebugLog.i(TAG, "setDndActive: $active${if (dndFlash != null) " (flash)" else ""}")
        }
    }

    /** Replaces the Do Not Disturb indicator; null turns it off. */
    fun setDndIndicatorConfig(spec: DndIndicatorSpec?) {
        synchronized(lock) {
            if (dndIndicator == spec) return
            dndIndicator = spec
            if (spec?.flashOnChange != true) dndFlash = null
            onLiveConditionChanged()
            DebugLog.i(TAG, "setDndIndicatorConfig: ${spec?.let { "${it.pattern}, indicator=${it.showIndicator}, showFor=${it.showForMs}, flash=${it.flashOnChange}, overBattery=${it.overridesBattery}" } ?: "off"}")
        }
    }

    fun setDndSuppressEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (dndSuppressEnabled == enabled) return
            dndSuppressEnabled = enabled
            onLiveConditionChanged()
            DebugLog.i(TAG, "setDndSuppressEnabled: $enabled")
        }
    }

    fun setSplitRing(enabled: Boolean) {
        synchronized(lock) {
            if (splitRing == enabled) return
            splitRing = enabled
            splitIntro = null
            DebugLog.i(TAG, "setSplitRing: $enabled")
            wake()
        }
    }

    fun setSplitAnimation(animation: SplitAnimation) {
        synchronized(lock) {
            if (splitAnimation == animation) return
            splitAnimation = animation
            splitKeys = null
            DebugLog.i(TAG, "setSplitAnimation: ${animation.id}")
            wake()
        }
    }

    fun setSplitSpeed(speed: Float) {
        synchronized(lock) {
            if (splitSpeed == speed) return
            splitSpeed = speed
            DebugLog.i(TAG, "setSplitSpeed: $speed")
            wake()
        }
    }

    fun setSplitAnnounceNew(enabled: Boolean) {
        synchronized(lock) {
            if (splitAnnounceNew == enabled) return
            splitAnnounceNew = enabled
            if (!enabled) splitIntro = null
            DebugLog.i(TAG, "setSplitAnnounceNew: $enabled")
            wake()
        }
    }

    fun setSplitLook(look: SplitLook) {
        synchronized(lock) {
            if (splitLook == look) return
            splitLook = look
            DebugLog.i(TAG, "setSplitLook: $look")
            wake()
        }
    }

    fun setQuietHours(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        synchronized(lock) {
            if (quietHoursEnabled == enabled &&
                quietHoursStartMinutes == startMinutes &&
                quietHoursEndMinutes == endMinutes
            ) {
                return
            }
            quietHoursEnabled = enabled
            quietHoursStartMinutes = startMinutes
            quietHoursEndMinutes = endMinutes
            onLiveConditionChanged()
            DebugLog.i(TAG, "setQuietHours: enabled=$enabled, start=$startMinutes, end=$endMinutes")
        }
    }

    /** Keeps [features] dark until [untilEpochMs] (wall clock). No features, or a time passed, ends it. */
    fun setPause(untilEpochMs: Long, features: Set<PauseFeature>) {
        synchronized(lock) {
            val next = PauseState(untilEpochMs, features).takeIf { features.isNotEmpty() && untilEpochMs > 0L }
            if (next == pause) return
            pause = next
            pauseInForce = next?.isActive() == true
            onLiveConditionChanged()
            DebugLog.i(TAG, "setPause: ${describePause()}")
        }
    }

    fun setRearCameraActive(active: Boolean) {
        synchronized(lock) {
            if (rearCameraActive == active) return
            rearCameraActive = active
            onLiveConditionChanged()
            DebugLog.i(TAG, "setRearCameraActive: $active")
        }
    }

    fun setInPocket(pocketed: Boolean) {
        synchronized(lock) {
            if (inPocket == pocketed) return
            inPocket = pocketed
            onLiveConditionChanged()
            DebugLog.i(TAG, "setInPocket: $pocketed")
        }
    }

    private fun paused(feature: PauseFeature): Boolean = rearCameraActive || inPocket || pause?.pauses(feature) == true

    private fun describePause(): String {
        val p = pause ?: return "none"
        val features = p.features.joinToString(",") { it.id }
        return when {
            p.untilResumed -> "$features until resumed"
            p.isActive() -> "$features for ${(p.untilMillis - System.currentTimeMillis()) / 1000}s"
            else -> "$features, ended"
        }
    }

    /**
     * Replaces the battery indicator configuration. Takes effect on the next tick.
     */
    fun setBatteryConfig(
        enabled: Boolean,
        chargingPattern: BatteryPattern,
        lowPattern: LowBatteryPattern,
        autoColor: Boolean,
        color: Long,
        showCharging: Boolean,
        lowWarningEnabled: Boolean,
        lowThresholdPercent: Int,
        fullTimeoutMinutes: Int?,
        overridesNotifications: Boolean,
        requiresFaceDown: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietStartOverride: Int?,
        quietEndOverride: Int?
    ) {
        synchronized(lock) {
            batteryConfig = BatteryConfig(
                enabled, chargingPattern, lowPattern, autoColor, color, showCharging,
                lowWarningEnabled, lowThresholdPercent, fullTimeoutMinutes,
                overridesNotifications, requiresFaceDown, dndMode, quietHoursMode,
                quietStartOverride, quietEndOverride
            )
            onLiveConditionChanged()
            DebugLog.i(TAG, "setBatteryConfig: enabled=$enabled, chargingPattern=$chargingPattern, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode")
        }
    }

    /**
     * Updates the live battery reading. Only triggers a re-render when something changed.
     */
    fun setBatteryState(levelPercent: Int, charging: Boolean, full: Boolean) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            // Refreshed even when nothing changed: this doubles as the app's heartbeat, and a
            // heartbeat that reports the same reading still proves the app is still there.
            batteryStateUpdatedAtMs = now
            if (batteryLevel == levelPercent && batteryCharging == charging && batteryFull == full) return
            batteryFullSinceMs = if (full && !batteryFull) now else if (!full) null else batteryFullSinceMs
            batteryLevel = levelPercent
            batteryCharging = charging
            batteryFull = full
            onLiveConditionChanged()
            DebugLog.i(TAG, "setBatteryState: level=$levelPercent, charging=$charging, full=$full")
        }
    }

    private fun onLiveConditionChanged() {
        val now = SystemClock.elapsedRealtime()
        syncNotificationTimer(now)
        cycleStartTimeMs = now
        needsSessionReset = true
        wake()
    }

    /**
     * Removes an active alert by key (e.g. when dismissed on device).
     */
    fun removeAlert(key: String) {
        synchronized(lock) {
            val removed = activeAlerts.removeAll { it.key == key }
            if (splitIntro?.key == key) splitIntro = null
            if (removed) {
                if (currentAlertIndex >= activeAlerts.size) {
                    currentAlertIndex = 0
                    cycleStartTimeMs = SystemClock.elapsedRealtime()
                }
                DebugLog.i(TAG, "removeAlert [key=$key] (remaining queue=${activeAlerts.size})")
                wake()
                if (incomingCallAlert == null && activeAlerts.isEmpty() && directAlert == null && !idleLayerActiveNow()) {
                    lights.blank()
                }
            }
        }
    }

    /**
     * Clears notification / transient alerts without interrupting a call.
     */
    fun clearAlert() {
        synchronized(lock) {
            DebugLog.i(TAG, "clearAlert (queue=${activeAlerts.size}, latest=${directAlert != null})")
            wake()
            directAlert = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
            splitIntro = null
            if (incomingCallAlert == null && !idleLayerActiveNow()) {
                lights.blank()
            }
        }
    }

    fun turnOff() {
        synchronized(lock) {
            DebugLog.i(TAG, "turnOff")
            wake()
            incomingCallAlert = null
            directAlert = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
            splitIntro = null
            testAlert = null
            geminiState = null
            // Also drop the battery reading, so the layer can't simply light back up on the next
            // tick and undo the blank. It resumes when the app pushes a fresh reading.
            batteryStateUpdatedAtMs = 0L
            batteryCharging = false
            batteryFull = false
            batteryFullSinceMs = null
            // Same for the DND indicator: dark until the app reports DND again.
            dndStateUpdatedAtMs = 0L
            dndFlash = null
            lights.blank()
        }
    }

    private fun renderLoop() {
        while (running) {
            try {
                tick()
                // While the hardware plays an effect there's nothing to push, so sleep until
                // something changes or a timer is due.
                synchronized(lock) {
                    if (!wakePending) {
                        val waitMs = if (framesDriving) FRAME_MS else idleWaitMs(SystemClock.elapsedRealtime())
                        lockObject.wait(waitMs)
                    }
                    wakePending = false
                }
            } catch (e: InterruptedException) {
                break
            } catch (t: Throwable) {
                DebugLog.w(TAG, "Render loop error: ${t.message}", t)
                try {
                    Thread.sleep(250)
                } catch (ignored: InterruptedException) {
                    break
                }
            }
        }
    }

    // Any wraps java.lang.Object, whose monitor methods Kotlin hides.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lockObject get() = lock as java.lang.Object

    /** Wakes the render loop for a state change. Call with [lock] held. */
    private fun wake() {
        wakePending = true
        lockObject.notifyAll()
    }

    /** How long the loop can sleep while an effect plays: until the next timer, at most a second. */
    private fun idleWaitMs(now: Long): Long {
        var due = now + IDLE_TICK_MS
        testAlert?.let { due = minOf(due, it.expiresAtMs) }
        if (directAlert != null) due = minOf(due, now + directAlertTimer.remainingMs(now))
        activeAlerts.forEach { if (it.expiresAtMs != Long.MAX_VALUE) due = minOf(due, it.expiresAtMs) }
        nextTurnAtMs?.let { due = minOf(due, it) }
        geminiState?.let { due = minOf(due, geminiStateSinceMs + GeminiLogParser.maxDurationMs(it)) }
        splitIntro?.let { intro ->
            activeAlerts.firstOrNull { it.key == intro.key }?.let { due = minOf(due, maxOf(now, intro.startedAtMs + splitIntroMs(it))) }
        }
        // Only deadlines still ahead: one already passed would keep waking the loop every frame.
        dndFlash?.let { due = minOf(due, maxOf(now, it.startedAtMs + PatternRenderer.DND_FLASH_MS)) }
        dndIndicator?.showForMs?.let { showFor ->
            val end = dndActiveSinceMs + showFor
            if (dndActive && end > now) due = minOf(due, end)
        }
        return (due - now).coerceIn(FRAME_MS, IDLE_TICK_MS)
    }

    private fun effectsEnabled(): Boolean {
        if (!lights.supportsEffects || effectsFailed) return false
        // `adb shell setprop debug.hilightplus.frames true` forces the frame route, for comparison.
        val forced = runCatching { forceFramesProperty?.invoke(null, FORCE_FRAMES_PROPERTY, false) as? Boolean }.getOrNull()
        return forced != true
    }

    /**
     * Shows [key]'s look as a hardware effect, building it with [build] only when the look
     * changed since it was last sent. Looks that don't move go out as effects too: a frame sent
     * while the hardware plays an effect may not replace it. [iterations] above 0 lets the
     * hardware stop by itself after that many loops.
     */
    private fun showEffect(key: List<Any?>, iterations: Int = 0, build: () -> RingEffect) {
        framesDriving = false
        if (needsSessionReset || !lights.isSessionOpen) {
            lights.openSession(sessionPriority)
            needsSessionReset = false
            shownEffectKey = null
        }
        if (key == shownEffectKey && lights.isSessionOpen) return
        val effect = build()
        val shown = lights.playEffect(effect, iterations)
        if (!shown) {
            // Refused, not a lost session: frames from here on, until the daemon restarts.
            if (lights.isSessionOpen) {
                effectsFailed = true
                DebugLog.w(TAG, "Light effect refused; falling back to frames")
            }
            framesDriving = true
            shownEffectKey = null
            return
        }
        shownEffectKey = key
        val kind = if (effect.isStatic) "static effect" else "effect"
        shownEffectInfo = "$kind ${effect.durationMs}ms x${if (iterations > 0) iterations else "∞"}, ${effect.leds.maxOf { it.colors.size }} keyframes"
        DebugLog.i(TAG, "Ring effect: $shownEffectInfo")
    }

    /** One loop of a rule pattern, lasting [loopMs], as a hardware effect. */
    private fun patternEffect(pattern: String, color: Long, brightness: Float, speedMs: Long, loopMs: Long): RingEffect =
        RingEffect.sample(loopMs, lights.effectPeriodMs, lights.ledCount) { t ->
            renderer.renderFrame(pattern, color, brightness, speedMs, t, lights.ledCount)
        }

    /** Loops needed to cover [remainingMs], so a timed alert's effect ends by itself. */
    private fun loopsFor(remainingMs: Long, loopMs: Long): Int =
        ((remainingMs + loopMs - 1) / maxOf(1L, loopMs)).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun tick() {
        synchronized(lock) {
            if (!running) return

            val now = SystemClock.elapsedRealtime()
            // Gaps only matter while frames drive the ring; an effect keeps playing through them.
            if (framesDriving) noteFrameGap(now, SystemClock.uptimeMillis()) else lastTickElapsedMs = 0L
            val nowMinutes = currentMinutesOfDay()
            val pauseNow = pause?.isActive() == true
            if (pauseNow != pauseInForce) {
                pauseInForce = pauseNow
                // Ran out (or back in force after a clock change): what was held back lights
                // again from the start of its pattern, or goes dark.
                DebugLog.i(TAG, if (pauseNow) "Pause back in force" else "Pause ended")
                cycleStartTimeMs = now
                needsSessionReset = true
            }
            syncNotificationTimer(now)
            val call = incomingCallAlert
            val callVisible = call != null && !paused(PauseFeature.CALLS) && alertVisible(
                call.requiresFaceDown,
                call.dndMode,
                call.quietHoursMode,
                call.quietStartOverride,
                call.quietEndOverride,
                nowMinutes
            )

            if (testAlert != null && now >= testAlert!!.expiresAtMs) {
                testAlert = null
                DebugLog.d(TAG, "Test alert expired")
            }
            val test = testAlert

            geminiState?.let {
                if (now - geminiStateSinceMs >= GeminiLogParser.maxDurationMs(it)) {
                    DebugLog.w(TAG, "Gemini ${it.id} ran past its limit; treating Gemini as idle")
                    geminiState = null
                }
            }
            val geminiLook = geminiState?.takeIf { geminiEnabled && !paused(PauseFeature.GEMINI) }
                ?.let { state -> geminiLooks[state]?.let { state to it } }
                ?.takeIf { (_, look) ->
                    alertVisible(
                        look.requiresFaceDown,
                        look.dndMode,
                        look.quietHoursMode,
                        look.quietStartOverride,
                        look.quietEndOverride,
                        nowMinutes
                    )
                }

            if (directAlert != null && directAlertTimer.remainingMs(now) == 0L) {
                directAlert = null
                directAlertTimer.clear()
                DebugLog.i(TAG, "Latest-only alert timed out")
            }
            val direct = directAlert

            // Prune expired alerts from cyclic queue.
            if (activeAlerts.isNotEmpty()) {
                val beforeSize = activeAlerts.size
                activeAlerts.removeAll { it.expiresAtMs != Long.MAX_VALUE && it.expiresAtMs <= now }
                if (activeAlerts.size != beforeSize) {
                    if (currentAlertIndex >= activeAlerts.size) {
                        currentAlertIndex = 0
                        cycleStartTimeMs = now
                    }
                }
            }

            val battery = batteryConfig
            val batteryEligible = battery != null &&
                batteryVisible(battery, nowMinutes) &&
                batteryLayerWantsToRender(battery, now)
            // When on, the battery layer takes the whole notification-priority slot rather than
            // just the gaps between alerts, so it isn't flickered on and off as alerts cycle.
            val batterySuppressesNotifications = batteryEligible && battery.overridesNotifications

            if (dndFlash?.let { now - it.startedAtMs >= PatternRenderer.DND_FLASH_MS } == true) dndFlash = null
            // Dropped here too, not only where the split ring is drawn: with a single alert
            // waiting that code never runs, and a stale announcement would keep the loop awake.
            splitIntro?.let { intro ->
                val alert = activeAlerts.firstOrNull { it.key == intro.key }
                if (alert == null || now - intro.startedAtMs >= splitIntroMs(alert)) splitIntro = null
            }
            val dndShow = dndLayerNow(now, nowMinutes)

            var currentPattern = ambientPattern
            var currentColor = ambientColor
            var currentBrightness = ambientBrightness
            var currentSpeed = ambientSpeedMs
            var elapsedMs = now
            var renderBattery = false
            var renderDnd: DndShow? = null
            var splitColors: LongArray? = null
            var splitAnimationShown = splitAnimation
            var splitLookShown = splitLook
            var splitSpeedShown = splitSpeed
            // When a split test is on show: when it started, which its animation counts from.
            var splitTestStartMs: Long? = null
            // For the effect route: what identifies the look on show, and how long a timed one has left.
            var effectKey: List<Any?> = emptyList()
            var effectRemainingMs: Long? = null
            nextTurnAtMs = null

            // The idle slot: the DND layer or the battery layer. The battery wins unless the DND
            // indicator is set to go over it; a flash always does (it only gets here beneath a
            // call hidden by its conditions).
            fun useIdleLayer() {
                if (dndShow != null && (dndShow is DndShow.Flash || dndShow.spec.overridesBattery || !batteryEligible)) {
                    renderDnd = dndShow
                } else if (batteryEligible) {
                    renderBattery = true
                }
            }

            var reason: String
            if (test?.split != null) {
                reason = "test split ${test.split.animation.id} ${test.split.colors.size} arcs"
                splitColors = test.split.colors.toLongArray()
                splitAnimationShown = test.split.animation
                splitLookShown = test.split.look
                splitSpeedShown = test.split.speed
                splitTestStartMs = test.startedAtMs
                effectKey = listOf("test", test)
                effectRemainingMs = test.expiresAtMs - now
            } else if (test != null) {
                reason = "test ${test.pattern}"
                currentPattern = test.pattern
                currentColor = test.color
                currentBrightness = test.brightness
                currentSpeed = test.speedMs
                elapsedMs = now - test.startedAtMs
                effectKey = listOf("test", test)
                effectRemainingMs = test.expiresAtMs - now
            } else if (call != null) {
                if (callVisible) {
                    reason = "call ${call.pattern} ${hex(call.color)}"
                    currentPattern = call.pattern
                    currentColor = call.color
                    currentBrightness = call.brightness
                    currentSpeed = call.speedMs
                    elapsedMs = now - call.startedAtMs
                    effectKey = listOf("call", call)
                } else {
                    reason = "off (call hidden by conditions)"
                    useIdleLayer()
                }
            } else if (dndShow is DndShow.Flash) {
                // The on/off flash is the answer to the user switching DND, and only lasts a
                // moment, so it goes over Gemini, notifications and battery; whatever it covered
                // carries on once it's done. Calls still beat it.
                reason = "dnd flash"
                renderDnd = dndShow
            } else if (geminiLook != null) {
                val (state, look) = geminiLook
                reason = "gemini ${state.id} ${look.pattern}"
                currentPattern = look.pattern
                currentColor = look.color
                currentBrightness = look.brightness
                currentSpeed = look.speedMs
                elapsedMs = now - geminiStateSinceMs
                effectKey = listOf("gemini", state, geminiStateSinceMs, look)
            } else if (!batterySuppressesNotifications && direct != null && notificationVisible(
                    direct.requiresFaceDown, direct.dndMode, direct.quietHoursMode,
                    direct.quietStartOverride, direct.quietEndOverride, nowMinutes
                )
            ) {
                reason = "latest-only ${direct.pattern} ${hex(direct.color)}"
                currentPattern = direct.pattern
                currentColor = direct.color
                currentBrightness = direct.brightness
                currentSpeed = direct.speedMs
                elapsedMs = directAlertTimer.elapsedMs(now)
                effectKey = listOf("latest", directGeneration, direct)
                effectRemainingMs = directAlertTimer.remainingMs(now)
            } else if (!batterySuppressesNotifications && activeAlerts.isNotEmpty()) {
                val eligibleStart = firstEligibleAlertIndex(currentAlertIndex)
                val splitAlerts = if (splitRing && eligibleStart != null) visibleAlertsNewestFirst(nowMinutes) else emptyList()
                if (eligibleStart == null) {
                    reason = "off (${activeAlerts.size} queued, hidden by conditions)"
                    useIdleLayer()
                } else if (splitAlerts.size >= 2 && splitIntroAlert(splitAlerts, now) != null) {
                    // A new arrival announces itself with one loop of its own pattern, timed so
                    // the hardware stops by itself; the arcs come back on the next tick after.
                    val intro = splitIntro!!
                    val alert = splitIntroAlert(splitAlerts, now)!!
                    reason = "split intro ${alert.key} ${alert.pattern}"
                    currentPattern = alert.pattern
                    currentColor = alert.color
                    currentBrightness = alert.brightness
                    currentSpeed = alert.speedMs
                    elapsedMs = now - intro.startedAtMs
                    effectKey = listOf("splitIntro", intro, alert)
                    effectRemainingMs = splitIntroMs(alert) - elapsedMs
                } else if (splitAlerts.size >= 2) {
                    val keys = splitAlerts.take(PatternRenderer.MAX_SPLIT_SEGMENTS).map { it.key }
                    if (keys != splitKeys) {
                        splitKeys = keys
                        splitStartedAtMs = now
                    }
                    reason = "split ${splitAnimation.id} $keys"
                    splitColors = LongArray(minOf(splitAlerts.size, PatternRenderer.MAX_SPLIT_SEGMENTS)) { splitAlerts[it].color }
                } else {
                    val turns = visibleAlertsNewestFirst(nowMinutes)
                    reason = "queue ${turns.map { it.key }}"
                    if (currentAlertIndex != eligibleStart) {
                        currentAlertIndex = eligibleStart
                        cycleStartTimeMs = now
                    }

                    // Check if current alert's animation cycle has completed
                    val currentAlert = activeAlerts[currentAlertIndex]
                    val singleCycleDuration = currentAlert.speedMs.coerceAtLeast(300L)
                    val alertElapsedInCycle = now - cycleStartTimeMs

                    if (alertElapsedInCycle >= singleCycleDuration) {
                        val next = firstEligibleAlertIndex((currentAlertIndex + 1) % activeAlerts.size)
                        currentAlertIndex = next ?: currentAlertIndex
                        cycleStartTimeMs = now
                    }

                    val activeAlertToRender = activeAlerts[currentAlertIndex.coerceIn(0, activeAlerts.size - 1)]
                    currentPattern = activeAlertToRender.pattern
                    currentColor = activeAlertToRender.color
                    currentBrightness = activeAlertToRender.brightness
                    currentSpeed = activeAlertToRender.speedMs

                    // Clamp elapsed time strictly within [0, singleCycleDuration]
                    elapsedMs = (now - cycleStartTimeMs).coerceIn(0L, singleCycleDuration)

                    // A lone alert simply loops. With several, each turn is its own effect, sent
                    // when the turn starts: one effect holding every turn wouldn't fit the hardware.
                    effectKey = if (turns.size <= 1) {
                        listOf("queue", activeAlertToRender)
                    } else {
                        nextTurnAtMs = cycleStartTimeMs + singleCycleDuration
                        listOf("turn", currentAlertIndex, cycleStartTimeMs, activeAlertToRender)
                    }
                }
            } else {
                reason = when {
                    direct != null || activeAlerts.isNotEmpty() ->
                        if (batterySuppressesNotifications) "off (notifications under battery)" else "off (latest-only alert hidden by conditions)"
                    else -> "off"
                }
                useIdleLayer()
            }
            if (splitColors == null) splitKeys = null

            if (renderBattery) {
                reason = when {
                    batteryCharging -> "battery charging"
                    batteryFull -> "battery full"
                    else -> "battery low"
                }
            }
            // The DND layer is drawn as an ordinary pattern, so it takes the pattern route below,
            // effect or frames, timed so the hardware stops by itself.
            renderDnd?.let { show ->
                val spec = show.spec
                currentColor = spec.color
                currentBrightness = spec.brightness
                when (show) {
                    is DndShow.Flash -> {
                        reason = "dnd flash ${if (show.flash.turningOn) "on" else "off"}"
                        currentPattern = if (show.flash.turningOn) PatternRenderer.DND_FLASH_ON else PatternRenderer.DND_FLASH_OFF
                        currentSpeed = PatternRenderer.DND_FLASH_MS
                        elapsedMs = now - show.flash.startedAtMs
                        effectKey = listOf("dndFlash", show.flash, spec)
                        effectRemainingMs = PatternRenderer.DND_FLASH_MS - elapsedMs
                    }
                    is DndShow.Look -> {
                        reason = "dnd ${spec.pattern}"
                        currentPattern = spec.pattern
                        currentSpeed = spec.speedMs
                        elapsedMs = now - dndActiveSinceMs
                        effectKey = listOf("dnd", spec, dndActiveSinceMs)
                        effectRemainingMs = spec.showForMs?.let { dndActiveSinceMs + it - now }
                    }
                }
            }
            noteRender(reason)
            val useEffects = effectsEnabled()
            framesDriving = !useEffects
            val period = lights.effectPeriodMs
            val count = lights.ledCount

            if (renderBattery && battery != null && useEffects) {
                val low = !batteryCharging && !batteryFull && battery.lowWarningEnabled &&
                    batteryLevel <= battery.lowThresholdPercent
                val loopMs = PatternRenderer.batteryLoopMs(battery.chargingPattern, batteryCharging, batteryFull, low, battery.lowPattern)
                showEffect(listOf("battery", battery, batteryLevel, batteryCharging, batteryFull, low)) {
                    RingEffect.sample(loopMs, period, count) { t ->
                        renderer.renderBatteryFrame(
                            pattern = battery.chargingPattern,
                            lowPattern = battery.lowPattern,
                            levelPercent = batteryLevel,
                            charging = batteryCharging,
                            full = batteryFull,
                            low = low,
                            autoColor = battery.autoColor,
                            fixedColor = battery.color,
                            brightness = 1.0f,
                            elapsedTimeMs = t,
                            ledCount = count
                        )
                    }
                }
                return
            }

            if (splitColors != null && useEffects) {
                val colors = splitColors
                val animation = splitAnimationShown
                val look = splitLookShown
                val speed = splitSpeedShown
                val loopMs = PatternRenderer.splitLoopMs(animation, colors.size, count, speed)
                // A test is timed and has its own key, so the real split ring is re-sent after it.
                val key = if (splitTestStartMs != null) effectKey else listOf("split", colors.toList(), look, animation, speed)
                val iterations = effectRemainingMs?.let { loopsFor(it, loopMs) } ?: 0
                showEffect(key, iterations) {
                    RingEffect.sample(loopMs, period, count) { t ->
                        renderer.renderSplitFrame(colors, look, t, count, animation, speed)
                    }
                }
                return
            }

            if (!currentPattern.equals("off", ignoreCase = true) && useEffects) {
                val loopMs = currentSpeed.coerceAtLeast(200L)
                val iterations = effectRemainingMs?.let { loopsFor(it, loopMs) } ?: 0
                val pattern = currentPattern
                val color = currentColor
                val brightness = currentBrightness
                val speed = currentSpeed
                showEffect(effectKey, iterations) { patternEffect(pattern, color, brightness, speed, loopMs) }
                return
            }
            shownEffectKey = null

            if (renderBattery && battery != null) {
                if (needsSessionReset || !lights.isSessionOpen) {
                    lights.openSession(sessionPriority)
                    needsSessionReset = false
                }
                val frame = renderer.renderBatteryFrame(
                    pattern = battery.chargingPattern,
                    lowPattern = battery.lowPattern,
                    levelPercent = batteryLevel,
                    charging = batteryCharging,
                    full = batteryFull,
                    low = !batteryCharging && !batteryFull && battery.lowWarningEnabled &&
                        batteryLevel <= battery.lowThresholdPercent,
                    autoColor = battery.autoColor,
                    fixedColor = battery.color,
                    brightness = 1.0f,
                    elapsedTimeMs = now,
                    ledCount = lights.ledCount
                )
                lights.pushFrame(frame)
                return
            }

            if (splitColors != null) {
                if (needsSessionReset || !lights.isSessionOpen) {
                    lights.openSession(sessionPriority)
                    needsSessionReset = false
                }
                lights.pushFrame(
                    renderer.renderSplitFrame(
                        colors = splitColors,
                        look = splitLookShown,
                        elapsedTimeMs = now - (splitTestStartMs ?: splitStartedAtMs),
                        ledCount = lights.ledCount,
                        animation = splitAnimationShown,
                        speed = splitSpeedShown
                    )
                )
                return
            }

            if (currentPattern.equals("off", ignoreCase = true)) {
                if (lights.isSessionOpen) {
                    lights.blank()
                }
                return
            }

            // Force session acquisition if requested or if not currently open
            if (needsSessionReset || !lights.isSessionOpen) {
                lights.openSession(sessionPriority)
                needsSessionReset = false
            }

            val frame = renderer.renderFrame(
                pattern = currentPattern,
                colorLong = currentColor,
                brightness = currentBrightness,
                speedMs = currentSpeed,
                elapsedTimeMs = elapsedMs,
                ledCount = lights.ledCount
            )

            lights.pushFrame(frame)
        }
    }

    /**
     * The LEDs hold whatever frame was pushed last, so a gap between ticks while lit shows up as
     * the ring frozen mid-animation. Uptime stops while the CPU is suspended and elapsed time
     * doesn't, which tells a device that slept apart from a render loop that was blocked.
     */
    private fun noteFrameGap(elapsedMs: Long, uptimeMs: Long) {
        if (lastTickElapsedMs != 0L && lights.isSessionOpen) {
            val gap = elapsedMs - lastTickElapsedMs
            if (gap > FRAME_GAP_LOG_MS) {
                val asleep = gap - (uptimeMs - lastTickUptimeMs)
                if (asleep > FRAME_GAP_LOG_MS) {
                    DebugLog.w(TAG, "Ring froze for ${gap}ms: device was asleep for ${asleep}ms (showing: $lastRenderReason)")
                } else {
                    DebugLog.w(TAG, "Ring froze for ${gap}ms while awake: render loop blocked (showing: $lastRenderReason)")
                }
            }
        }
        lastTickElapsedMs = elapsedMs
        lastTickUptimeMs = uptimeMs
    }

    private fun noteRender(reason: String) {
        if (reason == lastRenderReason) return
        lastRenderReason = reason
        DebugLog.i(TAG, "Ring -> $reason")
    }

    /** See [PixelLightsManager.ringMismatch]. Under the lock so it can't race a frame. */
    fun ringMismatch(): String? = synchronized(lock) { lights.ringMismatch() }

    /** Everything the engine is holding, for a debug report. */
    fun describeState(): String = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        buildString {
            appendLine("ring=$lastRenderReason, sessionOpen=${lights.isSessionOpen}, unclosedSessions=${lights.unclosedSessionCount}, leds=${lights.ledCount}, running=$running, renderThreadAlive=${renderThread?.isAlive}, lastFrame=${now - lastTickElapsedMs}ms ago")
            appendLine("route=${if (framesDriving) "frames" else "effects"}, effectsSupported=${lights.supportsEffects}, effectsFailed=$effectsFailed, period=${lights.effectPeriodMs}ms, showing=${shownEffectKey?.let { shownEffectInfo }}")
            appendLine("faceDown=$deviceFaceDown, dndActive=$dndActive, dndSuppress=$dndSuppressEnabled, quietHours=$quietHoursEnabled $quietHoursStartMinutes-$quietHoursEndMinutes, splitRing=$splitRing (${splitAnimation.id}, $splitLook, speed $splitSpeed, announceNew=$splitAnnounceNew${splitIntro?.let { ", announcing ${it.key}" } ?: ""})")
            appendLine("pause=${describePause()}, rearCamera=$rearCameraActive, inPocket=$inPocket")
            appendLine("test=${testAlert?.let { "${it.pattern} ${hex(it.color)}, ${it.expiresAtMs - now}ms left" }}")
            appendLine("gemini=${if (geminiEnabled) "on, ${geminiState?.id ?: "idle"}${geminiState?.let { ", for ${(now - geminiStateSinceMs) / 1000}s" } ?: ""}, looks=${geminiLooks.map { "${it.key.id}:${it.value.pattern}" }}" else "off"}")
            val dndAge = if (dndStateUpdatedAtMs == 0L) "never" else "${(now - dndStateUpdatedAtMs) / 1000}s ago"
            appendLine("dndIndicator=${dndIndicator?.let { "${it.pattern} ${hex(it.color)}, indicator=${it.showIndicator}, showFor=${it.showForMs}, flash=${it.flashOnChange}, overBattery=${it.overridesBattery}, faceDown=${it.requiresFaceDown}, quiet=${it.quietHoursMode}" } ?: "off"}, dnd on for ${if (dndActive) "${(now - dndActiveSinceMs) / 1000}s" else "-"}, updated $dndAge, flash=${dndFlash?.let { if (it.turningOn) "on" else "off" }}")
            appendLine("call=${incomingCallAlert?.let { "${it.pattern} ${hex(it.color)}, ringing ${(now - it.startedAtMs) / 1000}s, faceDown=${it.requiresFaceDown}, dnd=${it.dndMode}, quiet=${it.quietHoursMode}" }}")
            appendLine("latestOnly=${directAlert?.let { "${it.pattern} ${hex(it.color)}, ${directAlertTimer.remainingMs(now)}ms left, faceDown=${it.requiresFaceDown}, dnd=${it.dndMode}, quiet=${it.quietHoursMode}" }}")
            appendLine("queue (${activeAlerts.size}):")
            activeAlerts.forEach {
                val expiry = if (it.expiresAtMs == Long.MAX_VALUE) "until dismissed" else "${it.expiresAtMs - now}ms left"
                appendLine("  ${it.key}: ${it.pattern} ${hex(it.color)}, $expiry, faceDown=${it.requiresFaceDown}, dnd=${it.dndMode}, quiet=${it.quietHoursMode}")
            }
            val stateAge = if (batteryStateUpdatedAtMs == 0L) "never" else "${(now - batteryStateUpdatedAtMs) / 1000}s ago"
            append("battery=${batteryConfig?.let { "enabled=${it.enabled}, pattern=${it.chargingPattern}, overrides=${it.overridesNotifications}" }}, level=$batteryLevel, charging=$batteryCharging, full=$batteryFull, updated $stateAge")
        }
    }

    private fun hex(color: Long): String = "#%08X".format(color and 0xFFFFFFFFL)

    /** One loop of [alert]'s pattern, the same length as its turn when alerts take turns. */
    private fun splitIntroMs(alert: QueuedAlert): Long = alert.speedMs.coerceAtLeast(300L)

    /**
     * The alert announcing itself before the split ring, if one is: still waiting and visible,
     * and with its one loop not yet played out. An announcement that's over is dropped.
     */
    private fun splitIntroAlert(visible: List<QueuedAlert>, now: Long): QueuedAlert? {
        val intro = splitIntro ?: return null
        val alert = visible.firstOrNull { it.key == intro.key }
        if (alert == null || now - intro.startedAtMs >= splitIntroMs(alert)) {
            splitIntro = null
            return null
        }
        return alert
    }

    /** Queue order is post order, so the newest alert is last; the split ring shows newest at the top. */
    private fun visibleAlertsNewestFirst(nowMinutes: Int): List<QueuedAlert> =
        activeAlerts.asReversed().filter { alert ->
            !paused(PauseFeature.NOTIFICATIONS) && alertVisible(
                alert.requiresFaceDown,
                alert.dndMode,
                alert.quietHoursMode,
                alert.quietStartOverride,
                alert.quietEndOverride,
                nowMinutes
            )
        }

    private fun firstEligibleAlertIndex(startIndex: Int): Int? {
        if (activeAlerts.isEmpty() || paused(PauseFeature.NOTIFICATIONS)) return null
        val nowMinutes = currentMinutesOfDay()
        val start = startIndex.coerceAtLeast(0) % activeAlerts.size
        for (offset in activeAlerts.indices) {
            val i = (start + offset) % activeAlerts.size
            val alert = activeAlerts[i]
            if (alertVisible(
                    alert.requiresFaceDown,
                    alert.dndMode,
                    alert.quietHoursMode,
                    alert.quietStartOverride,
                    alert.quietEndOverride,
                    nowMinutes
                )
            ) {
                return i
            }
        }
        return null
    }

    private fun shouldRunNotificationTimer(): Boolean {
        val direct = directAlert ?: return false
        return notificationVisible(
            direct.requiresFaceDown,
            direct.dndMode,
            direct.quietHoursMode,
            direct.quietStartOverride,
            direct.quietEndOverride
        )
    }

    private fun notificationVisible(
        requiresFaceDown: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietStartOverride: Int?,
        quietEndOverride: Int?,
        nowMinutes: Int = currentMinutesOfDay()
    ): Boolean = !paused(PauseFeature.NOTIFICATIONS) && AlertRenderPolicy.canShowNotification(
        callActive = incomingCallAlert != null,
        requiresFaceDown = requiresFaceDown,
        deviceFaceDown = deviceFaceDown,
        dndMode = dndMode,
        dndSuppressEnabled = dndSuppressEnabled,
        dndActive = dndActive,
        quietHoursMode = quietHoursMode,
        quietHoursEnabled = quietHoursEnabled,
        quietHoursStartMinutes = quietHoursStartMinutes,
        quietHoursEndMinutes = quietHoursEndMinutes,
        quietStartOverride = quietStartOverride,
        quietEndOverride = quietEndOverride,
        nowMinutes = nowMinutes
    )

    private fun alertVisible(
        requiresFaceDown: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietStartOverride: Int?,
        quietEndOverride: Int?,
        nowMinutes: Int = currentMinutesOfDay()
    ): Boolean = AlertRenderPolicy.canShowAlert(
        requiresFaceDown,
        deviceFaceDown,
        dndMode,
        dndSuppressEnabled,
        dndActive,
        quietHoursMode,
        quietHoursEnabled,
        quietHoursStartMinutes,
        quietHoursEndMinutes,
        quietStartOverride,
        quietEndOverride,
        nowMinutes
    )

    private fun batteryVisible(config: BatteryConfig, nowMinutes: Int): Boolean {
        if (!config.enabled || paused(PauseFeature.BATTERY)) return false
        return AlertRenderPolicy.canShowAlert(
            requiresFaceDown = config.requiresFaceDown,
            deviceFaceDown = deviceFaceDown,
            dndMode = config.dndMode,
            dndSuppressEnabled = dndSuppressEnabled,
            dndActive = dndActive,
            quietHoursMode = config.quietHoursMode,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStartMinutes = quietHoursStartMinutes,
            quietHoursEndMinutes = quietHoursEndMinutes,
            quietStartOverride = config.quietStartOverride,
            quietEndOverride = config.quietEndOverride,
            nowMinutes = nowMinutes
        )
    }

    /** Whether the battery has something worth showing right now (charging, freshly full, or low). */
    private fun batteryLayerWantsToRender(config: BatteryConfig, now: Long): Boolean {
        // No reading yet, or the app stopped heartbeating: refuse to light rather than hold the
        // LEDs on a stale state nothing can clear.
        if (batteryStateUpdatedAtMs == 0L) return false
        if (now - batteryStateUpdatedAtMs > BATTERY_STATE_STALE_MS) return false
        // Charging and its "full" tail are one switch; the low-battery warning is independent.
        if (batteryCharging) return config.showCharging
        if (batteryFull) {
            if (!config.showCharging) return false
            val since = batteryFullSinceMs ?: return true
            val timeoutMinutes = config.fullTimeoutMinutes ?: return true
            return (now - since) < timeoutMinutes * 60_000L
        }
        return config.lowWarningEnabled && batteryLevel <= config.lowThresholdPercent
    }

    private fun batteryActiveNow(): Boolean {
        val config = batteryConfig ?: return false
        return batteryVisible(config, currentMinutesOfDay()) &&
            batteryLayerWantsToRender(config, SystemClock.elapsedRealtime())
    }

    /** Whether a layer that fills the idle slot (battery or DND) has something to show, so the ring shouldn't be blanked. */
    private fun idleLayerActiveNow(): Boolean =
        batteryActiveNow() || dndLayerNow(SystemClock.elapsedRealtime(), currentMinutesOfDay()) != null

    private sealed interface DndShow {
        val spec: DndIndicatorSpec

        data class Flash(override val spec: DndIndicatorSpec, val flash: DndFlash) : DndShow
        data class Look(override val spec: DndIndicatorSpec) : DndShow
    }

    /** What the DND layer would show now, before the idle slot decides between it and the battery. */
    private fun dndLayerNow(now: Long, nowMinutes: Int): DndShow? {
        val spec = dndIndicator ?: return null
        val visible = !paused(PauseFeature.DND) && AlertRenderPolicy.canShowAlert(
            requiresFaceDown = spec.requiresFaceDown,
            deviceFaceDown = deviceFaceDown,
            quietHoursMode = spec.quietHoursMode,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStartMinutes = quietHoursStartMinutes,
            quietHoursEndMinutes = quietHoursEndMinutes,
            quietStartOverride = spec.quietStartOverride,
            quietEndOverride = spec.quietEndOverride,
            nowMinutes = nowMinutes
        )
        val flash = dndFlash
        return when (
            AlertRenderPolicy.dndLayer(
                visible = visible,
                stateUpdatedAtMs = dndStateUpdatedAtMs,
                dndActive = dndActive,
                dndActiveSinceMs = dndActiveSinceMs,
                flashStartedAtMs = flash?.startedAtMs,
                showIndicator = spec.showIndicator,
                showForMs = spec.showForMs,
                nowMs = now
            )
        ) {
            AlertRenderPolicy.DndLayer.NONE -> null
            AlertRenderPolicy.DndLayer.FLASH -> DndShow.Flash(spec, flash!!)
            AlertRenderPolicy.DndLayer.LOOK -> DndShow.Look(spec)
        }
    }

    private fun syncNotificationTimer(now: Long) {
        if (directAlert == null) return
        if (shouldRunNotificationTimer()) {
            directAlertTimer.resume(now)
        } else {
            directAlertTimer.pause(now)
        }
    }

    companion object {
        private const val TAG = "LightEngine"
        private const val FRAME_MS = 33L // ~30 FPS
        private const val FRAME_GAP_LOG_MS = 1_000L
        // Longest the loop sleeps while an effect plays, so conditions like quiet hours and the
        // battery heartbeat are still checked about once a second.
        private const val IDLE_TICK_MS = 1_000L
        private const val FORCE_FRAMES_PROPERTY = "debug.hilightplus.frames"

        // Comfortably longer than the app's battery heartbeat, so a missed beat or two doesn't
        // blink the display, but short enough that a dead app can't strand the LEDs on.
        private const val BATTERY_STATE_STALE_MS = 3 * 60_000L

        // A test always hands the ring back, even if a caller (or a mismatched app build) asks
        // for a duration that would otherwise leave it lit for good.
        private const val MAX_TEST_MS = 60_000L
    }
}
