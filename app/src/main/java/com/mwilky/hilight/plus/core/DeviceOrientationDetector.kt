package com.mwilky.hilight.plus.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lightweight on-demand orientation detector that measures whether the device
 * is currently resting face down (screen facing the surface).
 *
 * Runs instantaneously with zero persistent background battery drain by registering
 * a hardware sensor listener only for the single sample needed during an incoming event.
 */
object DeviceOrientationDetector {

    /**
     * Samples the device orientation and returns true if the phone is facing down.
     *
     * Thresholds:
     * - Z-axis <= -6.5 m/s² (Earth gravity vector pointing towards the screen).
     * - |X| and |Y| < 6.0 m/s² (Ensures device is relatively horizontal, not in portrait/landscape upright).
     */
    suspend fun isDeviceFaceDown(context: Context, timeoutMs: Long = 250L): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return true

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return true // Default to true if sensor unavailable so alerts still work

        val deferred = CompletableDeferred<Boolean>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Screen face down means negative Z gravity with low X/Y tilt
                val isFaceDown = z <= -6.5f && kotlin.math.abs(x) < 6.0f && kotlin.math.abs(y) < 6.0f
                deferred.complete(isFaceDown)
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
            } ?: true // On timeout fallback to true so alerts aren't missed
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }
}
