package com.mwilky.hilight.plus.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Orientation detector that measures whether the device is currently resting face down.
 *
 * Supports:
 * 1. Instant single-sample query (`isDeviceFaceDown`).
 * 2. Active monitoring session when alerts are pending while locked (`startMonitoring` / `stopMonitoring`).
 */
object DeviceOrientationDetector {

    private const val TAG = "DeviceOrientation"

    private var activeSensorManager: SensorManager? = null
    private var activeListener: SensorEventListener? = null
    private var isCurrentlyFaceDown = false

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
     * Samples the device orientation and returns true if the phone is facing down.
     */
    suspend fun isDeviceFaceDown(context: Context, timeoutMs: Long = 250L): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return true

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return true

        val deferred = CompletableDeferred<Boolean>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val faceDown = isEventFaceDown(x, y, z)
                deferred.complete(faceDown)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (!registered) {
            return true
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: true
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Starts continuous low-power sensor monitoring to detect when the phone is flipped face down.
     */
    @Synchronized
    fun startMonitoring(context: Context) {
        if (activeListener != null) return

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        activeSensorManager = sensorManager
        activeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val faceDown = isEventFaceDown(x, y, z)

                if (faceDown != isCurrentlyFaceDown) {
                    isCurrentlyFaceDown = faceDown
                    Log.i(TAG, "Device orientation flipped -> isFaceDown=$faceDown")
                    onOrientationChanged?.invoke(faceDown)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(activeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.i(TAG, "Started orientation monitoring for pending alerts")
    }

    /**
     * Stops continuous sensor monitoring.
     */
    @Synchronized
    fun stopMonitoring() {
        activeListener?.let {
            activeSensorManager?.unregisterListener(it)
            activeListener = null
            activeSensorManager = null
            Log.i(TAG, "Stopped orientation monitoring")
        }
    }
}
