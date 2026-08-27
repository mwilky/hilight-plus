package com.mwilky.hilight.plus.core

import android.util.Log

/**
 * Clean, lightweight render engine driving the Pixel 11 rear LEDs.
 * Runs at ~30 FPS (33ms period).
 */
class LightEngine {

    private val lights = PixelLightsManager()
    private val renderer = PatternRenderer()
    private val lock = Any()

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    // State
    private var masterEnabled = true
    private var sessionPriority = 10

    // Ambient State
    private var ambientPattern = "off"
    private var ambientColor = 0xFF000000
    private var ambientBrightness = 1.0f
    private var ambientSpeedMs = 2000L

    // Alert State
    private var alertPattern: String? = null
    private var alertColor = 0xFF4285F4
    private var alertBrightness = 1.0f
    private var alertSpeedMs = 800L
    private var alertStartMs = 0L
    private var alertDurationMs = 0L
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

    fun triggerAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        synchronized(lock) {
            alertPattern = pattern
            alertColor = color
            alertBrightness = brightness
            alertSpeedMs = speedMs
            alertStartMs = System.currentTimeMillis()
            alertDurationMs = durationMs
            needsSessionReset = true
            Log.i(TAG, "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
        }
    }

    fun clearAlert() {
        synchronized(lock) {
            alertPattern = null
            alertDurationMs = 0L
            if (ambientPattern.equals("off", ignoreCase = true)) {
                lights.blank()
            }
        }
    }

    fun turnOff() {
        synchronized(lock) {
            ambientPattern = "off"
            alertPattern = null
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
            val isAlertActive = alertPattern != null && (now - alertStartMs < alertDurationMs)

            val currentPattern: String
            val currentColor: Long
            val currentBrightness: Float
            val currentSpeed: Long
            val elapsedMs: Long

            if (isAlertActive) {
                currentPattern = alertPattern ?: "off"
                currentColor = alertColor
                currentBrightness = alertBrightness
                currentSpeed = alertSpeedMs
                elapsedMs = now - alertStartMs
            } else {
                if (alertPattern != null) {
                    alertPattern = null
                    if (ambientPattern.equals("off", ignoreCase = true)) {
                        lights.blank()
                        return
                    }
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
