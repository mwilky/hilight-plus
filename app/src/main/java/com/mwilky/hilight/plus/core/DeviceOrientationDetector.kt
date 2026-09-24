package com.mwilky.hilight.plus.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.mwilky.hilight.plus.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Orientation detector that measures whether the device is currently resting face down.
 *
 * Supports:
 * 1. Instant single-sample query (`isDeviceFaceDown`).
 * 2. Ref-counted monitoring while a restricted call or notification still needs it.
 *
 * Unknown, missing, or timed-out samples are treated as not face-down.
 */
object DeviceOrientationDetector {

    const val TOKEN_NOTIFICATIONS = "notifications"
    const val TOKEN_CALL = "call"
    const val TOKEN_BATTERY = "battery"

    private const val TAG = "DeviceOrientation"

    private val monitorTokens = mutableSetOf<String>()
    private var activeSensorManager: SensorManager? = null
    private var activeListener: SensorEventListener? = null
    private var lastReportedFaceDown: Boolean? = null

    @Volatile
    var lastKnownFaceDown: Boolean = false
        private set

    var onOrientationChanged: ((isFaceDown: Boolean) -> Unit)? = null

    /**
     * Checks if gravity vector represents face-down on a surface.
     * Thresholds:
     * - Z-axis <= -6.0 m/s² (Earth gravity vector pointing towards the screen).
     * - |X| and |Y| < 6.5 m/s² (Relatively horizontal, not in upright portrait/landscape).
     */
    fun isEventFaceDown(x: Float, y: Float, z: Float): Boolean {
        return z <= -6.0f && abs(x) < 6.5f && abs(y) < 6.5f
    }

    /**
     * Samples the device orientation and returns true only if the phone is facing down.
     * Missing hardware is not face-down. A timeout keeps the last real sample instead of
     * forcing false, which would hide lights while the phone is already down and dozing.
     */
    suspend fun isDeviceFaceDown(context: Context, timeoutMs: Long = 500L): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return failClosed()

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return failClosed()

        val deferred = CompletableDeferred<Boolean>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                rememberFaceDown(isEventFaceDown(x, y, z))
                deferred.complete(lastKnownFaceDown)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hilight-plus:orientation")
            ?.apply { setReferenceCounted(false) }

        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (!registered) {
            return failClosed()
        }

        return try {
            runCatching { wakeLock?.acquire(timeoutMs + 100L) }
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: lastKnownFaceDown
        } finally {
            sensorManager.unregisterListener(listener)
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock.release() }
            }
        }
    }

    /**
     * Starts or keeps continuous monitoring for [token] while a restricted alert needs it.
     */
    @Synchronized
    fun retainMonitoring(context: Context, token: String) {
        if (!monitorTokens.add(token)) return
        if (monitorTokens.size == 1) {
            startMonitoringLocked(context)
        }
    }

    /**
     * Drops [token]. Sensors stop when nothing still needs orientation.
     */
    @Synchronized
    fun releaseMonitoring(token: String) {
        if (!monitorTokens.remove(token)) return
        if (monitorTokens.isEmpty()) {
            stopMonitoringLocked()
        }
    }

    /**
     * Stops continuous sensor monitoring and drops every retain token.
     */
    @Synchronized
    fun stopMonitoring() {
        monitorTokens.clear()
        stopMonitoringLocked()
    }

    private fun rememberFaceDown(faceDown: Boolean) {
        lastKnownFaceDown = faceDown
    }

    private fun failClosed(): Boolean {
        rememberFaceDown(false)
        return false
    }

    private fun startMonitoringLocked(context: Context) {
        if (activeListener != null) return

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        lastReportedFaceDown = null
        activeSensorManager = sensorManager
        activeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val faceDown = isEventFaceDown(x, y, z)

                rememberFaceDown(faceDown)
                if (faceDown != lastReportedFaceDown) {
                    lastReportedFaceDown = faceDown
                    DebugLog.i(TAG, "Device orientation flipped -> isFaceDown=$faceDown")
                    onOrientationChanged?.invoke(faceDown)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(activeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        DebugLog.i(TAG, "Started orientation monitoring for pending alerts")
    }

    private fun stopMonitoringLocked() {
        activeListener?.let {
            activeSensorManager?.unregisterListener(it)
            activeListener = null
            activeSensorManager = null
            lastReportedFaceDown = null
            DebugLog.i(TAG, "Stopped orientation monitoring")
        }
    }
}
