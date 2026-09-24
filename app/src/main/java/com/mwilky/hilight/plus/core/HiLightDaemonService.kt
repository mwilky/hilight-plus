package com.mwilky.hilight.plus.core

import android.os.Process
import android.os.RemoteException
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.LowBatteryPattern
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.SplitAnimation
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

    @Volatile
    private var entitled = true

    private var logSink: DebugLog.Sink? = null

    init {
        DebugLog.source = "daemon"
        killStaleDaemons()
        DebugLog.d(TAG, "System lights before start:\n${lightsDump()}")
        try {
            engine.start()
            DebugLog.i(TAG, "HiLightDaemonService started (PID ${Process.myPid()}, UID ${Process.myUid()}, ${engine.ledCount} LEDs)")
        } catch (t: Throwable) {
            DebugLog.e(TAG, "Failed to start HiLightDaemonService: ${t.message}", t)
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
        if (!entitled) return
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
        if (key != null && entitled) {
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
        if (!entitled) return
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

    override fun setSplitAnimation(animation: String?) {
        engine.setSplitAnimation(SplitAnimation.fromId(animation))
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
            enabled && entitled,
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
        return runSettings("get", "secure", key)?.takeIf { it.isNotBlank() }
    }

    override fun getGlobalString(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return runSettings("get", "global", key)?.takeIf { it.isNotBlank() }
    }

    /**
     * Writes to Settings.Global, which the shell UID may do and which survives the app's data
     * being cleared. Used for the trial start so a data clear doesn't restart the clock.
     */
    override fun putGlobalString(key: String?, value: String?): Boolean {
        if (key.isNullOrBlank() || value.isNullOrBlank()) return false
        runSettings("put", "global", key, value) ?: return false
        return true
    }

    /**
     * Runs the `settings` shell command. Returns the first line of stdout, an empty string when
     * the command succeeded silently (e.g. `put`), or null on failure / timeout / "null".
     */
    private fun runSettings(vararg args: String): String? {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("settings", *args))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine()?.trim() }
            runCatching { process.errorStream.close() }
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0 || output == "null") null else output.orEmpty()
        } catch (t: Throwable) {
            DebugLog.e(TAG, "settings ${args.joinToString(" ")} failed: ${t.message}", t)
            null
        } finally {
            process?.destroy()
        }
    }

    /**
     * Runs a shell command and returns all of its stdout, or null on failure / timeout. For
     * diagnostics only: nothing here should depend on the exact output format.
     */
    private fun runCommand(vararg args: String): String? {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf(*args))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            runCatching { process.errorStream.close() }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            output.trimEnd()
        } catch (t: Throwable) {
            DebugLog.w(TAG, "${args.joinToString(" ")} failed: ${t.message}")
            null
        } finally {
            process?.destroy()
        }
    }

    /** The system lights service's own view, including every open session on the ring. */
    private fun lightsDump(): String = runCommand("dumpsys", "lights") ?: "unavailable"

    /**
     * An older copy of this process that Shizuku lost track of would keep its light session, and
     * its last frame, alive, and can outrank ours. Only one should ever run, so end the others.
     */
    private fun killStaleDaemons() {
        val name = "${BuildConfig.APPLICATION_ID}:$PROCESS_SUFFIX"
        val pids = runCommand("pidof", name)?.split(' ')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        for (pid in pids) {
            if (pid == Process.myPid()) continue
            DebugLog.w(TAG, "Killing stale daemon process $pid")
            Process.killProcess(pid)
        }
    }

    /**
     * Trial expired and not purchased: stop showing alerts and refuse new ones. The test
     * channel stays open so the paywall can still demo the lights.
     */
    override fun setEntitled(entitled: Boolean) {
        if (this.entitled == entitled) return
        this.entitled = entitled
        DebugLog.i(TAG, "setEntitled: $entitled")
        if (!entitled) {
            engine.stopIncomingCall()
            engine.clearAlert()
        }
    }

    /**
     * Sends this process's log lines to the app from now on, starting with any logged before the
     * app registered. A newer sink (the app reconnected) replaces the old one.
     */
    override fun setLogSink(sink: ILogSink?) {
        logSink?.let { DebugLog.detach(it) }
        logSink = null
        if (sink == null) return
        val forwarder = object : DebugLog.Sink {
            override fun write(line: String) {
                try {
                    sink.onLog(line)
                } catch (_: RemoteException) {
                    DebugLog.detach(this)
                }
            }
        }
        logSink = forwarder
        DebugLog.attach(forwarder)
    }

    override fun dumpState(): String =
        "pid=${Process.myPid()} uid=${Process.myUid()} entitled=$entitled\n" + engine.describeState() +
            "\n\n-- dumpsys lights --\n" + lightsDump()

    override fun isRingStuck(): Boolean {
        val mismatch = engine.ringMismatch() ?: return false
        DebugLog.w(TAG, "Ring doesn't match what this process sent: $mismatch\n${lightsDump()}")
        return true
    }

    /**
     * Logs what's holding the ring, then exits. The process ending is what makes the lights
     * service drop every session it opened, including any stuck one this process lost hold of.
     * The app reconnects and a fresh process starts.
     */
    override fun restart() {
        DebugLog.w(TAG, "Reset requested. Engine state:\n${engine.describeState()}")
        DebugLog.w(TAG, "System lights at reset:\n${lightsDump()}")
        engine.stop()
        exitProcess(0)
    }

    override fun destroy() {
        DebugLog.i(TAG, "HiLightDaemonService destroying...")
        engine.stop()
        exitProcess(0)
    }

    companion object {
        private const val TAG = "HiLightDaemonService"

        /** Shizuku names the process `<applicationId>:<suffix>`. */
        const val PROCESS_SUFFIX = "hilight_daemon"
    }
}
