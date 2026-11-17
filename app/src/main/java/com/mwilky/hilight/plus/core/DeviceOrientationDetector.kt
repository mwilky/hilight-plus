package com.mwilky.hilight.plus.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.mwilky.hilight.plus.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    const val TOKEN_GEMINI = "gemini"
    const val TOKEN_DND = "dnd"
    const val TOKEN_POCKET = "pocket"

    private const val TAG = "DeviceOrientation"
    private const val TILT_DETECTOR = "android.sensor.tilt_detector"
    private const val FLIP_WAKE_MS = 2_000L
    // How long after a tilt gravity has to answer, inside the tilt's wake lock.
    private const val TILT_ANSWER_MS = 1_500L
    // Unanswered requests in a row before the motion sensors count as stalled. One can be a
    // fluke; several in a row, with the tilt detector still firing, is the phone's fault.
    private const val STALL_AFTER = 3

    private val monitorTokens = mutableSetOf<String>()
    private var activeSensorManager: SensorManager? = null
    private var activeListener: SensorEventListener? = null
    private var lastReportedFaceDown: Boolean? = null
    private var flipWakeLock: PowerManager.WakeLock? = null
    private var tiltListener: SensorEventListener? = null

    @Volatile
    var lastKnownFaceDown: Boolean = false
        private set

    // For the debug report: whether readings are actually arriving.
    @Volatile
    private var lastSampleElapsedMs = 0L
    @Volatile
    private var monitoringRegistered: Boolean? = null
    @Volatile
    private var tiltRegistered: Boolean? = null
    @Volatile
    private var missedSamples = 0
    // Requests for a reading (a one-off check, or a flush after a tilt) that got none, in a row.
    @Volatile
    private var unanswered = 0
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val _motionStalled = MutableStateFlow(false)

    /**
     * The phone has stopped sending motion readings to apps, a fault some builds hit after hours
     * of uptime that only a restart clears. Face down can't be told while it lasts. Clears itself
     * when a reading arrives.
     */
    val motionStalled: StateFlow<Boolean> = _motionStalled.asStateFlow()

    /** What the detector holds, and when it last heard from the sensor. */
    @Synchronized
    fun describe(): String {
        val age = if (lastSampleElapsedMs == 0L) "never" else "${(SystemClock.elapsedRealtime() - lastSampleElapsedMs) / 1000}s ago"
        return "lastKnown=$lastKnownFaceDown, lastReading=$age, monitoring=${activeListener != null} " +
            "(tokens=$monitorTokens, registered=$monitoringRegistered, tilt=$tiltRegistered), " +
            "missed one-off readings=$missedSamples, unanswered in a row=$unanswered, stalled=${_motionStalled.value}"
    }

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
            } ?: run {
                // Silent until now: a sensor that never answers looks exactly like "not face down".
                missedSamples++
                DebugLog.w(TAG, "No orientation reading within ${timeoutMs}ms; keeping isFaceDown=$lastKnownFaceDown (missed $missedSamples)")
                noteUnanswered()
                lastKnownFaceDown
            }
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

    /** A real reading: also ends a stall. */
    private fun rememberFaceDown(faceDown: Boolean) {
        lastKnownFaceDown = faceDown
        lastSampleElapsedMs = SystemClock.elapsedRealtime()
        unanswered = 0
        if (_motionStalled.value) {
            _motionStalled.value = false
            DebugLog.i(TAG, "Motion readings are back")
        }
    }

    @Synchronized
    private fun noteUnanswered() {
        unanswered++
        if (unanswered >= STALL_AFTER && !_motionStalled.value) {
            _motionStalled.value = true
            DebugLog.w(TAG, "Motion sensors stalled: $unanswered requests in a row got no reading; the phone needs restarting")
        }
    }

    private fun failClosed(): Boolean {
        lastKnownFaceDown = false
        return false
    }

    private fun startMonitoringLocked(context: Context) {
        if (activeListener != null) return

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        lastReportedFaceDown = null
        // Keeps the CPU up after a tilt or flip, so gravity readings arrive and the daemon can
        // put the new look on the ring before the phone sleeps again.
        flipWakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hilight-plus:flip")
            ?.apply { setReferenceCounted(false) }
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
                    runCatching { flipWakeLock?.acquire(FLIP_WAKE_MS) }
                    DebugLog.i(TAG, "Device orientation flipped -> isFaceDown=$faceDown")
                    onOrientationChanged?.invoke(faceDown)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        monitoringRegistered = sensorManager.registerListener(activeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        tiltRegistered = null

        // Gravity doesn't wake the CPU: while the phone sleeps its readings wait for something
        // else to wake it, so a flip would go unseen. The wake-up tilt detector fires in the
        // sensor hub when the phone turns about 35 degrees, which wakes us to read gravity.
        val tilt = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .firstOrNull { it.stringType == TILT_DETECTOR && it.isWakeUpSensor }
        if (tilt != null) {
            tiltListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    runCatching { flipWakeLock?.acquire(FLIP_WAKE_MS) }
                    DebugLog.d(TAG, "Tilt detected, reading orientation")
                    val tiltAtMs = SystemClock.elapsedRealtime()
                    activeListener?.let { sensorManager.flush(it) }
                    // Gravity arrives several times a second while awake, so a tilt with none
                    // after it means the readings have stopped, not that the phone is still.
                    handler.postDelayed({
                        if (activeListener != null && lastSampleElapsedMs < tiltAtMs) noteUnanswered()
                    }, TILT_ANSWER_MS)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            tiltRegistered = sensorManager.registerListener(tiltListener, tilt, SensorManager.SENSOR_DELAY_NORMAL)
        }
        DebugLog.i(TAG, "Started orientation monitoring for pending alerts (gravity registered=$monitoringRegistered, tilt wake-up=$tiltRegistered)")
    }

    private fun stopMonitoringLocked() {
        tiltListener?.let { activeSensorManager?.unregisterListener(it) }
        tiltListener = null
        activeListener?.let {
            activeSensorManager?.unregisterListener(it)
            activeListener = null
            activeSensorManager = null
            lastReportedFaceDown = null
            flipWakeLock?.let { if (it.isHeld) runCatching { it.release() } }
            flipWakeLock = null
            DebugLog.i(TAG, "Stopped orientation monitoring")
        }
    }
}
