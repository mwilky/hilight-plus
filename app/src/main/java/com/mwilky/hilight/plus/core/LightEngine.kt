package com.mwilky.hilight.plus.core

import android.util.Log

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
        val expiresAtMs: Long
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
    private var isAlertsPaused = false

    // Ambient State
    private var ambientPattern = "off"
    private var ambientColor = 0xFF000000
    private var ambientBrightness = 1.0f
    private var ambientSpeedMs = 2000L

    // Direct / Incoming Call Alert State (Highest priority override)
    private var directAlertPattern: String? = null
    private var directAlertColor = 0xFF000000
    private var directAlertBrightness = 1.0f
    private var directAlertSpeedMs = 800L
    private var directAlertStartMs = 0L
    private var directAlertDurationMs = 0L

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

    val isSessionActive: Boolean
        get() = lights.isSessionOpen

    fun setMasterEnabled(enabled: Boolean) {
        synchronized(lock) {
            masterEnabled = enabled
            if (!enabled) {
                lights.blank()
            }
        }
    }

    fun setPriority(priority: Int) {
        synchronized(lock) {
            sessionPriority = priority
        }
    }

    fun setAmbient(pattern: String, color: Long, brightness: Float, speedMs: Long) {
        synchronized(lock) {
            ambientPattern = pattern
            ambientColor = color
            ambientBrightness = brightness
            ambientSpeedMs = speedMs
        }
    }

    /**
     * Triggers a direct / persistent alert (e.g. incoming phone call ring).
     */
    fun triggerAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        synchronized(lock) {
            directAlertPattern = pattern
            directAlertColor = color
            directAlertBrightness = brightness
            directAlertSpeedMs = speedMs
            directAlertStartMs = System.currentTimeMillis()
            directAlertDurationMs = durationMs
            activeAlerts.clear()
            currentAlertIndex = 0
            isAlertsPaused = false
            needsSessionReset = true
            Log.i(TAG, "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
        }
    }

    /**
     * Enqueues or updates an alert in the multi-notification cyclic queue.
     */
    fun postAlert(key: String, pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiresAt = now + durationMs
            val alert = QueuedAlert(
                key = key,
                pattern = pattern,
                color = color,
                brightness = brightness,
                speedMs = speedMs,
                expiresAtMs = expiresAt
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
            if (!isAlertsPaused) {
                needsSessionReset = true
            }
            Log.i(TAG, "postAlert [key=$key]: pattern=$pattern, color=$color, speedMs=$speedMs (queue size=${activeAlerts.size}, isPaused=$isAlertsPaused)")
        }
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
                    cycleStartTimeMs = System.currentTimeMillis()
                }
                Log.i(TAG, "removeAlert [key=$key] (remaining queue=${activeAlerts.size})")
                if (activeAlerts.isEmpty() && directAlertPattern == null && ambientPattern.equals("off", ignoreCase = true)) {
                    lights.blank()
                }
            }
        }
    }

    /**
     * Pauses active alert rendering (e.g. on device unlock when behavior is PAUSE).
     * Keeps activeAlerts in memory so they can resume when locked.
     */
    fun pauseAlerts() {
        synchronized(lock) {
            isAlertsPaused = true
            Log.i(TAG, "pauseAlerts: alerts paused, keeping ${activeAlerts.size} queued alerts")
            if (ambientPattern.equals("off", ignoreCase = true)) {
                lights.blank()
            }
        }
    }

    /**
     * Resumes paused alert rendering (e.g. when device is locked again).
     */
    fun resumeAlerts() {
        synchronized(lock) {
            if (isAlertsPaused) {
                isAlertsPaused = false
                cycleStartTimeMs = System.currentTimeMillis()
                needsSessionReset = true
                Log.i(TAG, "resumeAlerts: resumed alerts with ${activeAlerts.size} queued alerts")
            }
        }
    }

    fun clearAlert() {
        synchronized(lock) {
            directAlertPattern = null
            directAlertDurationMs = 0L
            activeAlerts.clear()
            currentAlertIndex = 0
            isAlertsPaused = false
            if (ambientPattern.equals("off", ignoreCase = true)) {
                lights.blank()
            }
        }
    }

    fun turnOff() {
        synchronized(lock) {
            ambientPattern = "off"
            directAlertPattern = null
            activeAlerts.clear()
            currentAlertIndex = 0
            isAlertsPaused = false
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

            val now = System.currentTimeMillis()

            // 1. Check Direct Alert (Incoming Call)
            val isDirectAlertActive = directAlertPattern != null && (now - directAlertStartMs < directAlertDurationMs)

            // 2. Prune expired alerts from cyclic queue
            if (activeAlerts.isNotEmpty()) {
                val beforeSize = activeAlerts.size
                activeAlerts.removeAll { it.expiresAtMs <= now }
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

            if (isDirectAlertActive) {
                currentPattern = directAlertPattern ?: "off"
                currentColor = directAlertColor
                currentBrightness = directAlertBrightness
                currentSpeed = directAlertSpeedMs
                elapsedMs = now - directAlertStartMs
            } else if (!isAlertsPaused && activeAlerts.isNotEmpty()) {
                if (currentAlertIndex >= activeAlerts.size) {
                    currentAlertIndex = 0
                    cycleStartTimeMs = now
                }

                // Check if current alert's animation cycle has completed
                val currentAlert = activeAlerts[currentAlertIndex]
                val singleCycleDuration = currentAlert.speedMs.coerceAtLeast(300L)
                val alertElapsedInCycle = now - cycleStartTimeMs

                if (alertElapsedInCycle >= singleCycleDuration) {
                    // Advance to next alert in queue and reset cycle clock to now
                    currentAlertIndex = (currentAlertIndex + 1) % activeAlerts.size
                    cycleStartTimeMs = now
                }

                val activeAlertToRender = activeAlerts[currentAlertIndex.coerceIn(0, activeAlerts.size - 1)]
                currentPattern = activeAlertToRender.pattern
                currentColor = activeAlertToRender.color
                currentBrightness = activeAlertToRender.brightness
                currentSpeed = activeAlertToRender.speedMs

                // Clamp elapsed time strictly within [0, singleCycleDuration]
                elapsedMs = (now - cycleStartTimeMs).coerceIn(0L, singleCycleDuration)
            } else {
                if (directAlertPattern != null) {
                    directAlertPattern = null
                }
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

    companion object {
        private const val TAG = "LightEngine"
        private const val FRAME_MS = 33L // ~30 FPS
    }
}
