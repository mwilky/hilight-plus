package com.mwilky.hilight.plus.core

import android.os.Process
import android.util.Log
import com.mwilky.hilight.plus.core.IHiLightService
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running under Shell UID (2000).
 * Implements [com.hilight.plus.core.IHiLightService] to receive strongly-typed commands from the app.
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

    override fun clearAlert() {
        engine.clearAlert()
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

    override fun destroy() {
        Log.i(TAG, "HiLightDaemonService destroying...")
        engine.stop()
        exitProcess(0)
    }

    companion object {
        private const val TAG = "HiLightDaemonService"
    }
}
