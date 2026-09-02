package com.mwilky.hilight.plus.core

import android.os.Process
import android.util.Log
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.core.IHiLightService
import java.io.BufferedReader
import java.io.InputStreamReader
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

    override fun setAmbient(pattern: String?, color: Long, brightness: Float, speedMs: Long) {
        engine.setAmbient(pattern ?: "off", color, brightness, speedMs)
    }

    override fun triggerAlert(pattern: String?, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        engine.triggerAlert(pattern ?: "solid", color, brightness, speedMs, durationMs)
    }

    override fun postAlert(key: String?, pattern: String?, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        if (key != null) {
            engine.postAlert(key, pattern ?: "solid", color, brightness, speedMs, durationMs)
        }
    }

    override fun removeAlert(key: String?) {
        if (key != null) {
            engine.removeAlert(key)
        }
    }

    override fun clearAlert() {
        engine.clearAlert()
    }

    override fun pauseAlerts() {
        engine.pauseAlerts()
    }

    override fun resumeAlerts() {
        engine.resumeAlerts()
    }

    override fun turnOff() {
        engine.turnOff()
    }

    override fun getLedCount(): Int {
        return engine.ledCount
    }

    override fun isSessionActive(): Boolean {
        return engine.isSessionActive
    }

    override fun getSecureInt(key: String?, defaultValue: Int): Int {
        if (key.isNullOrBlank()) return defaultValue
        val str = getSecureString(key) ?: return defaultValue
        return str.trim().toIntOrNull() ?: defaultValue
    }

    override fun getSecureString(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("settings", "get", "secure", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()?.trim()
            process.waitFor()
            if (output == "null" || output.isNullOrBlank()) null else output
        } catch (t: Throwable) {
            Log.e(TAG, "getSecureString failed: ${t.message}", t)
            null
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
