package com.mwilky.hilight.plus.core

import android.os.Process
import android.util.Log
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.LowBatteryPattern
import com.mwilky.hilight.plus.QuietHoursMode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running under Shell UID (2000).
 * Implements [IHiLightService] to receive strongly-typed commands from the app.
 */
class HiLightDaemonService : IHiLightService.Stub() {

    private val engine = LightEngine()

    init {
        try {
            engine.start()
            Log.i(TAG, "HiLightDaemonService started (PID ${Process.myPid()}, UID ${Process.myUid()}, ${engine.ledCount} LEDs)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start HiLightDaemonService: ${t.message}", t)
        }
    }

    override fun triggerAlert(
        pattern: String?,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean,
        dndMode: String?,
        quietHoursMode: String?,
        quietStartMinutes: Int,
        quietEndMinutes: Int
    ) {
        engine.triggerAlert(
            pattern ?: "solid",
            color,
            brightness,
            speedMs,
            durationMs,
            requiresFaceDown,
            DndMode.fromId(dndMode),
            QuietHoursMode.fromId(quietHoursMode),
            quietStartMinutes.takeIf { it >= 0 },
            quietEndMinutes.takeIf { it >= 0 }
        )
    }

    override fun postAlert(
        key: String?,
        pattern: String?,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean,
        dndMode: String?,
        quietHoursMode: String?,
        quietStartMinutes: Int,
        quietEndMinutes: Int
    ) {
        if (key != null) {
            engine.postAlert(
                key,
                pattern ?: "solid",
                color,
                brightness,
                speedMs,
                durationMs,
                requiresFaceDown,
                DndMode.fromId(dndMode),
                QuietHoursMode.fromId(quietHoursMode),
                quietStartMinutes.takeIf { it >= 0 },
                quietEndMinutes.takeIf { it >= 0 }
            )
        }
    }

    override fun startIncomingCall(
        pattern: String?,
        color: Long,
        brightness: Float,
        speedMs: Long,
        requiresFaceDown: Boolean,
        dndMode: String?,
        quietHoursMode: String?,
        quietStartMinutes: Int,
        quietEndMinutes: Int
    ) {
        engine.startIncomingCall(
            pattern ?: "solid",
            color,
            brightness,
            speedMs,
            requiresFaceDown,
            DndMode.fromId(dndMode),
            QuietHoursMode.fromId(quietHoursMode),
            quietStartMinutes.takeIf { it >= 0 },
            quietEndMinutes.takeIf { it >= 0 }
        )
    }

    override fun setDeviceFaceDown(faceDown: Boolean) {
        engine.setDeviceFaceDown(faceDown)
    }

    override fun setDndActive(dndActive: Boolean) {
        engine.setDndActive(dndActive)
    }

    override fun setDndSuppressEnabled(enabled: Boolean) {
        engine.setDndSuppressEnabled(enabled)
    }

    override fun setQuietHours(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        engine.setQuietHours(enabled, startMinutes, endMinutes)
    }

    override fun setSplitRing(enabled: Boolean) {
        engine.setSplitRing(enabled)
    }

    override fun testAlert(pattern: String?, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        engine.testAlert(pattern ?: "solid", color, brightness, speedMs, durationMs)
    }

    override fun cancelTestAlert() {
        engine.cancelTestAlert()
    }

    override fun setBatteryConfig(
        enabled: Boolean,
        chargingPattern: String?,
        lowPattern: String?,
        autoColor: Boolean,
        color: Long,
        showCharging: Boolean,
        lowWarningEnabled: Boolean,
        lowThresholdPercent: Int,
        fullTimeoutMinutes: Int,
        overridesNotifications: Boolean,
        requiresFaceDown: Boolean,
        dndMode: String?,
        quietHoursMode: String?,
        quietStartMinutes: Int,
        quietEndMinutes: Int
    ) {
        engine.setBatteryConfig(
            enabled,
            BatteryPattern.fromId(chargingPattern),
            LowBatteryPattern.fromId(lowPattern),
            autoColor,
            color,
            showCharging,
            lowWarningEnabled,
            lowThresholdPercent,
            fullTimeoutMinutes.takeIf { it >= 0 },
            overridesNotifications,
            requiresFaceDown,
            DndMode.fromId(dndMode),
            QuietHoursMode.fromId(quietHoursMode),
            quietStartMinutes.takeIf { it >= 0 },
            quietEndMinutes.takeIf { it >= 0 }
        )
    }

    override fun setBatteryState(levelPercent: Int, charging: Boolean, full: Boolean) {
        engine.setBatteryState(levelPercent, charging, full)
    }

    override fun stopIncomingCall() {
        engine.stopIncomingCall()
    }

    override fun removeAlert(key: String?) {
        if (key != null) {
            engine.removeAlert(key)
        }
    }

    override fun clearAlert() {
        engine.clearAlert()
    }

    override fun turnOff() {
        engine.turnOff()
    }

    override fun getLedCount(): Int {
        return engine.ledCount
    }

    override fun getSecureString(key: String?): String? {
        if (key.isNullOrBlank()) return null
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("settings", "get", "secure", key))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine()?.trim() }
            runCatching { process.errorStream.close() }
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (output == "null" || output.isNullOrBlank()) null else output
        } catch (t: Throwable) {
            Log.e(TAG, "getSecureString failed: ${t.message}", t)
            null
        } finally {
            process?.destroy()
        }
    }

    override fun destroy() {
        Log.i(TAG, "HiLightDaemonService destroying...")
        engine.stop()
        exitProcess(0)
    }

    companion object {
        private const val TAG = "HiLightDaemonService"
    }
}
