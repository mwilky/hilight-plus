package com.mwilky.hilight.plus.core

import android.os.SystemClock
import android.util.Log
import com.mwilky.hilight.plus.DndMode
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

    // Ambient state: the idle render when nothing else is active. Always off.
    private val ambientPattern = "off"
    private val ambientColor = 0xFF000000
    private val ambientBrightness = 1.0f
    private val ambientSpeedMs = 2000L

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

    // Standard-mode notification / transient alert state.
    private var directAlertPattern: String? = null
    private var directAlertColor = 0xFF000000
    private var directAlertBrightness = 1.0f
    private var directAlertSpeedMs = 800L
    private var directRequiresFaceDown = false
    private var directDndMode = DndMode.ALWAYS
    private var directQuietHoursMode = QuietHoursMode.ALWAYS
    private var directQuietStartOverride: Int? = null
    private var directQuietEndOverride: Int? = null
    private val directAlertTimer = PausableAlertTimer()

    // Multi-Notification Cyclic Queue
    private val activeAlerts = mutableListOf<QueuedAlert>()
    private var currentAlertIndex = 0
    private var cycleStartTimeMs = 0L
    private var needsSessionReset = false

    fun start(): Boolean {
        synchronized(lock) {
            if (running) return true
            if (!lights.connect()) {
                Log.e(TAG, "Failed to connect to lights backend")
                return false
            }
            running = true
            renderThread = Thread(::renderLoop, "HiLightPlus-Engine").apply {
                isDaemon = false
                start()
            }
            Log.i(TAG, "LightEngine started with ${lights.ledCount} LEDs")
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
        }
    }

    fun stopIncomingCall() {
        synchronized(lock) {
            if (incomingCallAlert == null) return
            incomingCallAlert = null
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
            directAlertPattern = pattern
            directAlertColor = color
            directAlertBrightness = brightness
            directAlertSpeedMs = speedMs
            directRequiresFaceDown = requiresFaceDown
            directDndMode = dndMode
            directQuietHoursMode = quietHoursMode
            directQuietStartOverride = quietStartOverride
            directQuietEndOverride = quietEndOverride
            directAlertTimer.start(
                durationMs = durationMs,
                nowMs = now,
                paused = !shouldRunNotificationTimer()
            )
            activeAlerts.clear()
            currentAlertIndex = 0
            needsSessionReset = true
            Log.i(TAG, "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
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
            Log.i(TAG, "postAlert [key=$key]: pattern=$pattern, color=$color, speedMs=$speedMs (queue size=${activeAlerts.size}, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode)")
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
            Log.i(TAG, "setDeviceFaceDown: $faceDown")
        }
    }

    fun setDndActive(active: Boolean) {
        synchronized(lock) {
            if (dndActive == active) return
            dndActive = active
            onLiveConditionChanged()
            Log.i(TAG, "setDndActive: $active")
        }
    }

    fun setDndSuppressEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (dndSuppressEnabled == enabled) return
            dndSuppressEnabled = enabled
            onLiveConditionChanged()
            Log.i(TAG, "setDndSuppressEnabled: $enabled")
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
            Log.i(TAG, "setQuietHours: enabled=$enabled, start=$startMinutes, end=$endMinutes")
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
                Log.i(TAG, "removeAlert [key=$key] (remaining queue=${activeAlerts.size})")
                if (incomingCallAlert == null && activeAlerts.isEmpty() && directAlertPattern == null && ambientPattern.equals("off", ignoreCase = true)) {
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
            directAlertPattern = null
            directRequiresFaceDown = false
            directDndMode = DndMode.ALWAYS
            directQuietHoursMode = QuietHoursMode.ALWAYS
            directQuietStartOverride = null
            directQuietEndOverride = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
            if (incomingCallAlert == null && ambientPattern.equals("off", ignoreCase = true)) {
                lights.blank()
            }
        }
    }

    fun turnOff() {
        synchronized(lock) {
            incomingCallAlert = null
            directAlertPattern = null
            directRequiresFaceDown = false
            directDndMode = DndMode.ALWAYS
            directQuietHoursMode = QuietHoursMode.ALWAYS
            directQuietStartOverride = null
            directQuietEndOverride = null
            directAlertTimer.clear()
            activeAlerts.clear()
            currentAlertIndex = 0
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
                Log.w(TAG, "Render loop error: ${t.message}", t)
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

            if (directAlertPattern != null && directAlertTimer.remainingMs(now) == 0L) {
                directAlertPattern = null
                directRequiresFaceDown = false
                directDndMode = DndMode.ALWAYS
                directQuietHoursMode = QuietHoursMode.ALWAYS
                directQuietStartOverride = null
                directQuietEndOverride = null
                directAlertTimer.clear()
            }
            val isDirectAlertActive = directAlertPattern != null &&
                notificationVisible(
                    directRequiresFaceDown,
                    directDndMode,
                    directQuietHoursMode,
                    directQuietStartOverride,
                    directQuietEndOverride,
                    nowMinutes
                )

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

            val currentPattern: String
            val currentColor: Long
            val currentBrightness: Float
            val currentSpeed: Long
            val elapsedMs: Long

            if (call != null) {
                if (callVisible) {
                    currentPattern = call.pattern
                    currentColor = call.color
                    currentBrightness = call.brightness
                    currentSpeed = call.speedMs
                    elapsedMs = now - call.startedAtMs
                } else {
                    currentPattern = ambientPattern
                    currentColor = ambientColor
                    currentBrightness = ambientBrightness
                    currentSpeed = ambientSpeedMs
                    elapsedMs = now
                }
            } else if (isDirectAlertActive) {
                currentPattern = directAlertPattern ?: "off"
                currentColor = directAlertColor
                currentBrightness = directAlertBrightness
                currentSpeed = directAlertSpeedMs
                elapsedMs = directAlertTimer.elapsedMs(now)
            } else if (activeAlerts.isNotEmpty()) {
                val eligibleStart = firstEligibleAlertIndex(currentAlertIndex)
                if (eligibleStart == null) {
                    currentPattern = ambientPattern
                    currentColor = ambientColor
                    currentBrightness = ambientBrightness
                    currentSpeed = ambientSpeedMs
                    elapsedMs = now
                } else {
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
                currentPattern = ambientPattern
                currentColor = ambientColor
                currentBrightness = ambientBrightness
                currentSpeed = ambientSpeedMs
                elapsedMs = now
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
        if (directAlertPattern == null) return false
        return notificationVisible(
            directRequiresFaceDown,
            directDndMode,
            directQuietHoursMode,
            directQuietStartOverride,
            directQuietEndOverride
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

    private fun syncNotificationTimer(now: Long) {
        if (directAlertPattern == null) return
        if (shouldRunNotificationTimer()) {
            directAlertTimer.resume(now)
        } else {
            directAlertTimer.pause(now)
        }
    }

    companion object {
        private const val TAG = "LightEngine"
        private const val FRAME_MS = 33L // ~30 FPS
    }
}
