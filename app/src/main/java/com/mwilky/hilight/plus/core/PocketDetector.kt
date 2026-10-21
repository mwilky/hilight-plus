package com.mwilky.hilight.plus.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.mwilky.hilight.plus.DebugLog

/**
 * Pocket detector: the phone is in a pocket or bag when its proximity sensor is covered and it
 * isn't lying flat face down. A phone face down on a table covers the sensor too, so face down
 * always wins and never counts as a pocket; "face down only" alerts keep working there.
 *
 * Ref-counted like [DeviceOrientationDetector]: the sensor only runs while something could light.
 * Orientation is only watched while the sensor is covered, through [DeviceOrientationDetector].
 * Missing hardware, pocket mode off, or nothing to watch all report not in a pocket.
 */
object PocketDetector {

    const val TOKEN_NOTIFICATIONS = "notifications"
    const val TOKEN_CALL = "call"
    const val TOKEN_BATTERY = "battery"
    const val TOKEN_DND = "dnd"

    private const val TAG = "PocketDetector"

    // How long the sensor stays covered before it counts, so a hand passing over it, or the
    // moment the phone goes face down, doesn't blink the ring. Uncovering counts at once.
    private const val SETTLE_MS = 1_000L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val tokens = mutableSetOf<String>()
    private var enabled = false
    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var covered = false
    private var settled = false

    @Volatile
    var inPocket: Boolean = false
        private set

    var onPocketChanged: ((inPocket: Boolean) -> Unit)? = null

    /** Binary proximity sensors report either 0 or their maximum range. */
    fun isCovered(distance: Float, maximumRange: Float): Boolean = distance < maximumRange

    fun isPocketed(covered: Boolean, faceDown: Boolean): Boolean = covered && !faceDown

    private val settle = Runnable {
        synchronized(this) {
            settled = covered
        }
        evaluate()
    }

    /** Follows the pocket mode setting. Off stops the sensor and reports out of a pocket. */
    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled && tokens.isNotEmpty()) startLocked() else if (!enabled) stopLocked()
    }

    /** Starts or keeps the sensor running for [token] while it has something that could light. */
    @Synchronized
    fun retain(context: Context, token: String) {
        appContext = context.applicationContext
        if (!tokens.add(token)) return
        if (tokens.size == 1 && enabled) startLocked()
    }

    /** Drops [token]. The sensor stops when nothing could light any more. */
    @Synchronized
    fun release(token: String) {
        if (!tokens.remove(token)) return
        if (tokens.isEmpty()) stopLocked()
    }

    /** Orientation changed while the sensor is covered: face down on a table isn't a pocket. */
    fun onFaceDownChanged() {
        evaluate()
    }

    private fun evaluate() {
        val pocketed = synchronized(this) {
            settled && isPocketed(covered, DeviceOrientationDetector.lastKnownFaceDown)
        }
        report(pocketed)
    }

    private fun report(pocketed: Boolean) {
        if (inPocket == pocketed) return
        inPocket = pocketed
        DebugLog.i(TAG, "In pocket -> $pocketed")
        onPocketChanged?.invoke(pocketed)
    }

    private fun startLocked() {
        if (listener != null) return
        val context = appContext ?: return
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // The wake-up variant, so going into or out of a pocket is seen while the phone sleeps.
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: run {
                DebugLog.w(TAG, "No proximity sensor; pocket mode can't run")
                return
            }

        wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hilight-plus:pocket")
            ?.apply { setReferenceCounted(false) }
        val maximumRange = sensor.maximumRange
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                onCoveredChanged(context, isCovered(event.values[0], maximumRange))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager = manager
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        DebugLog.i(TAG, "Started pocket monitoring (wake-up=${sensor.isWakeUpSensor})")
    }

    private fun onCoveredChanged(context: Context, nowCovered: Boolean) {
        synchronized(this) {
            if (listener == null || covered == nowCovered) return
            covered = nowCovered
            settled = false
            handler.removeCallbacks(settle)
            if (nowCovered) {
                // Keeps the CPU up until the settle check, so gravity readings arrive and it
                // sees whether the phone went face down rather than into a pocket.
                runCatching { wakeLock?.acquire(SETTLE_MS + 500L) }
                DeviceOrientationDetector.retainMonitoring(context, DeviceOrientationDetector.TOKEN_POCKET)
                handler.postDelayed(settle, SETTLE_MS)
            } else {
                DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_POCKET)
            }
        }
        DebugLog.d(TAG, "Proximity covered=$nowCovered")
        if (!nowCovered) report(false)
    }

    private fun stopLocked() {
        handler.removeCallbacks(settle)
        listener?.let { sensorManager?.unregisterListener(it) }
        val wasRunning = listener != null
        listener = null
        sensorManager = null
        covered = false
        settled = false
        DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_POCKET)
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        if (wasRunning) DebugLog.i(TAG, "Stopped pocket monitoring")
        report(false)
    }
}
