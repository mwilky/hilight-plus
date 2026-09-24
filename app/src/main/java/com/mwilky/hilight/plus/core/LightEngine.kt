package com.mwilky.hilight.plus.core

import android.os.SystemClock
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.LowBatteryPattern
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.currentMinutesOfDay

/**
 * Clean, lightweight render engine driving the Pixel 11 rear LEDs.
 * Supports multi-notification cyclic rendering across active unread alerts.
 * Runs at ~30 FPS (33ms period).
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
    private var masterEnabled = true
    private var sessionPriority = 10
    private var deviceFaceDown = false
    private var dndActive = false
    private var dndSuppressEnabled = false
    private var quietHoursEnabled = false
    private var quietHoursStartMinutes = 22 * 60
    private var quietHoursEndMinutes = 7 * 60

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
        val expiresAtMs: Long
    )

    // In-app "test on LEDs" preview. Takes top render priority and ignores face-down/DND/quiet-hours
    // gating, but never touches the notification queue or direct alert state, so it can't disturb them.
    private var testAlert: TestAlert? = null

    // Multi-Notification Cyclic Queue
    private val activeAlerts = mutableListOf<QueuedAlert>()
    private var currentAlertIndex = 0
    private var cycleStartTimeMs = 0L
    private var needsSessionReset = false

    // Split-ring display of the queue: with two or more visible alerts each gets its own arc
    // instead of taking turns. A single visible alert still plays its own pattern.
    private var splitRing = false

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
            }
            DebugLog.i(TAG, "postAlert [key=$key]: pattern=$pattern, color=$color, speedMs=$speedMs (queue size=${activeAlerts.size}, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode)")
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
            testAlert = TestAlert(pattern, color, brightness, speedMs, now, now + durationMs)
            needsSessionReset = true
            DebugLog.i(TAG, "testAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
        }
    }

    fun cancelTestAlert() {
        synchronized(lock) {
            if (testAlert == null) return
            testAlert = null
            needsSessionReset = true
            DebugLog.i(TAG, "cancelTestAlert")
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
        }
    }

    fun setDndActive(active: Boolean) {
        synchronized(lock) {
            if (dndActive == active) return
            dndActive = active
            onLiveConditionChanged()
            DebugLog.i(TAG, "setDndActive: $active")
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
            DebugLog.i(TAG, "setSplitRing: $enabled")
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
    }

    /**
     * Removes an active alert by key (e.g. when dismissed on device).
     */
    fun removeAlert(key: String) {
        synchronized(lock) {
            val removed = activeAlerts.removeAll { it.key == key }
            if (removed) {
                if (currentAlertIndex >= activeAlerts.size) {
                    currentAlertIndex = 0
                    cycleStartTimeMs = SystemClock.elapsedRealtime()
                }
                DebugLog.i(TAG, "removeAlert [key=$key] (remaining queue=${activeAlerts.size})")
                if (incomingCallAlert == null && activeAlerts.isEmpty() && directAlert == null && !batteryActiveNow()) {
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
            directAlert = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
            if (incomingCallAlert == null && !batteryActiveNow()) {
                lights.blank()
            }
        }
    }

    fun turnOff() {
        synchronized(lock) {
            DebugLog.i(TAG, "turnOff")
            incomingCallAlert = null
            directAlert = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
            testAlert = null
            // Also drop the battery reading, so the layer can't simply light back up on the next
            // tick and undo the blank. It resumes when the app pushes a fresh reading.
            batteryStateUpdatedAtMs = 0L
            batteryCharging = false
            batteryFull = false
            batteryFullSinceMs = null
            lights.blank()
        }
    }

    private fun renderLoop() {
        while (running) {
            try {
                tick()
                Thread.sleep(FRAME_MS)
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

    private fun tick() {
        synchronized(lock) {
            if (!running) return

            if (!masterEnabled) {
                if (lights.isSessionOpen) lights.blank()
                return
            }

            val now = SystemClock.elapsedRealtime()
            noteFrameGap(now, SystemClock.uptimeMillis())
            val nowMinutes = currentMinutesOfDay()
            syncNotificationTimer(now)
            val call = incomingCallAlert
            val callVisible = call != null && alertVisible(
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
            val batterySuppressesNotifications = batteryEligible && battery!!.overridesNotifications

            var currentPattern = ambientPattern
            var currentColor = ambientColor
            var currentBrightness = ambientBrightness
            var currentSpeed = ambientSpeedMs
            var elapsedMs = now
            var renderBattery = false
            var splitColors: LongArray? = null

            fun useBatteryIfEligible() {
                if (batteryEligible) renderBattery = true
            }

            var reason: String
            if (test != null) {
                reason = "test ${test.pattern}"
                currentPattern = test.pattern
                currentColor = test.color
                currentBrightness = test.brightness
                currentSpeed = test.speedMs
                elapsedMs = now - test.startedAtMs
            } else if (call != null) {
                if (callVisible) {
                    reason = "call ${call.pattern} ${hex(call.color)}"
                    currentPattern = call.pattern
                    currentColor = call.color
                    currentBrightness = call.brightness
                    currentSpeed = call.speedMs
                    elapsedMs = now - call.startedAtMs
                } else {
                    reason = "off (call hidden by conditions)"
                    useBatteryIfEligible()
                }
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
            } else if (!batterySuppressesNotifications && activeAlerts.isNotEmpty()) {
                val eligibleStart = firstEligibleAlertIndex(currentAlertIndex)
                val splitAlerts = if (splitRing && eligibleStart != null) visibleAlertsNewestFirst(nowMinutes) else emptyList()
                if (eligibleStart == null) {
                    reason = "off (${activeAlerts.size} queued, hidden by conditions)"
                    useBatteryIfEligible()
                } else if (splitAlerts.size >= 2) {
                    reason = "split ${splitAlerts.take(PatternRenderer.MAX_SPLIT_SEGMENTS).map { it.key }}"
                    splitColors = LongArray(minOf(splitAlerts.size, PatternRenderer.MAX_SPLIT_SEGMENTS)) { splitAlerts[it].color }
                    currentBrightness = splitAlerts[0].brightness
                } else {
                    reason = "queue ${visibleAlertsNewestFirst(nowMinutes).map { it.key }}"
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
                }
            } else {
                reason = when {
                    direct != null || activeAlerts.isNotEmpty() ->
                        if (batterySuppressesNotifications) "off (notifications under battery)" else "off (latest-only alert hidden by conditions)"
                    else -> "off"
                }
                useBatteryIfEligible()
            }

            if (renderBattery) {
                reason = when {
                    batteryCharging -> "battery charging"
                    batteryFull -> "battery full"
                    else -> "battery low"
                }
            }
            noteRender(reason)

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
                        brightness = currentBrightness,
                        elapsedTimeMs = now,
                        ledCount = lights.ledCount
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

    /** Everything the engine is holding, for a debug report. */
    fun describeState(): String = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        buildString {
            appendLine("ring=$lastRenderReason, sessionOpen=${lights.isSessionOpen}, leds=${lights.ledCount}, running=$running, renderThreadAlive=${renderThread?.isAlive}, lastFrame=${now - lastTickElapsedMs}ms ago")
            appendLine("faceDown=$deviceFaceDown, dndActive=$dndActive, dndSuppress=$dndSuppressEnabled, quietHours=$quietHoursEnabled $quietHoursStartMinutes-$quietHoursEndMinutes, splitRing=$splitRing")
            appendLine("test=${testAlert?.let { "${it.pattern} ${hex(it.color)}, ${it.expiresAtMs - now}ms left" }}")
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

    /** Queue order is post order, so the newest alert is last; the split ring shows newest at the top. */
    private fun visibleAlertsNewestFirst(nowMinutes: Int): List<QueuedAlert> =
        activeAlerts.asReversed().filter { alert ->
            alertVisible(
                alert.requiresFaceDown,
                alert.dndMode,
                alert.quietHoursMode,
                alert.quietStartOverride,
                alert.quietEndOverride,
                nowMinutes
            )
        }

    private fun firstEligibleAlertIndex(startIndex: Int): Int? {
        if (activeAlerts.isEmpty()) return null
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
    ): Boolean = AlertRenderPolicy.canShowNotification(
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
        if (!config.enabled) return false
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

        // Comfortably longer than the app's battery heartbeat, so a missed beat or two doesn't
        // blink the display, but short enough that a dead app can't strand the LEDs on.
        private const val BATTERY_STATE_STALE_MS = 3 * 60_000L
    }
}
