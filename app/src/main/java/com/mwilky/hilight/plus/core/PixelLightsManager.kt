package com.mwilky.hilight.plus.core

import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.mwilky.hilight.plus.DebugLog
import java.lang.reflect.Method

/**
 * Handles communication with Android 17 (API 37) `ILightsManager` via reflection under Shell UID (2000).
 * Targets the 8 rear `LIGHT_TYPE_APPLICATION` LEDs on Pixel 11 Pro series devices.
 */
class PixelLightsManager {

    private var token: IBinder = Binder()
    private var service: Any? = null
    private var mGetLights: Method? = null
    private var mOpenSession: Method? = null
    private var mCloseSession: Method? = null
    private var mSetLightStates: Method? = null
    private var mGetLightState: Method? = null

    private var ledIds: IntArray = intArrayOf()
    var isSessionOpen: Boolean = false
        private set

    // Sessions whose close failed, with how many retries they've had. The lights service keeps
    // such a session, and its last frame, for as long as this process lives, and it can outrank
    // every session opened after it, so the ring would stay frozen on it whatever we push.
    private val unclosedTokens = linkedMapOf<IBinder, Int>()

    val unclosedSessionCount: Int
        get() = unclosedTokens.size

    // What our open session last set, per LED. Only meaningful while the session is open.
    private var lastFrame: IntArray = intArrayOf()

    fun connect(): Boolean {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "lights") as? IBinder
                ?: throw IllegalStateException("Lights service binder not found")

            val stubClass = Class.forName("android.hardware.lights.ILightsManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            service = asInterfaceMethod.invoke(null, binder)

            val ifaceClass = Class.forName("android.hardware.lights.ILightsManager")
            mGetLights = ifaceClass.getMethod("getLights")
            mOpenSession = ifaceClass.getMethod("openSession", IBinder::class.java, Int::class.javaPrimitiveType)
            mCloseSession = ifaceClass.getMethod("closeSession", IBinder::class.java)
            mSetLightStates = ifaceClass.getMethod(
                "setLightStates",
                IBinder::class.java,
                IntArray::class.java,
                Array<LightState>::class.java
            )
            // Only used to check the ring against what we sent, so its absence isn't fatal.
            mGetLightState = runCatching { ifaceClass.getMethod("getLightState", Int::class.javaPrimitiveType) }.getOrNull()

            @Suppress("UNCHECKED_CAST")
            val allLights = mGetLights?.invoke(service) as? List<Light> ?: emptyList()
            ledIds = allLights
                .filter { it.type == Light.LIGHT_TYPE_APPLICATION }
                .map { it.id }
                .toIntArray()

            DebugLog.i(TAG, "Connected to ${ledIds.size} Pixel 11 rear LEDs (IDs: ${ledIds.joinToString()})")
            return ledIds.isNotEmpty()
        } catch (e: Throwable) {
            DebugLog.e(TAG, "Failed to initialize PixelLightsManager: ${e.message}", e)
            return false
        }
    }

    val ledCount: Int
        get() = ledIds.size

    fun openSession(priority: Int = 10): Boolean {
        val s = service ?: return false
        // If already open, close old session first to guarantee clean session acquisition
        if (isSessionOpen) {
            closeSession()
        }
        retryUnclosed(s)
        token = Binder() // Fresh token per session to avoid dead token locks in Android lights manager
        return try {
            mOpenSession?.invoke(s, token, priority)
            isSessionOpen = true
            true
        } catch (e: Throwable) {
            DebugLog.w(TAG, "openSession failed: ${e.message}")
            // The service may have opened it before failing; make sure it gets closed.
            unclosedTokens[token] = 0
            false
        }
    }

    fun closeSession() {
        val s = service ?: return
        if (!isSessionOpen) return
        try {
            mCloseSession?.invoke(s, token)
        } catch (e: Throwable) {
            DebugLog.w(TAG, "closeSession failed, will retry: ${e.cause?.message ?: e.message}")
            unclosedTokens[token] = 0
        } finally {
            isSessionOpen = false
        }
    }

    private fun retryUnclosed(s: Any) {
        val iterator = unclosedTokens.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                mCloseSession?.invoke(s, entry.key)
                iterator.remove()
                DebugLog.i(TAG, "Closed a session left open by an earlier failure")
            } catch (e: Throwable) {
                entry.setValue(entry.value + 1)
                if (entry.value >= MAX_CLOSE_RETRIES) {
                    // Most likely it never opened. If it did, only restarting this process clears it.
                    DebugLog.w(TAG, "Giving up closing a session after ${entry.value} tries: ${e.cause?.message ?: e.message}")
                    iterator.remove()
                }
            }
        }
    }

    fun pushFrame(colors: IntArray) {
        val s = service ?: return
        if (!isSessionOpen || ledIds.isEmpty() || colors.isEmpty()) return

        try {
            val states = Array(ledIds.size) { i ->
                val color = colors[i % colors.size]
                LightState.Builder().setColor(color).build()
            }
            val startMs = SystemClock.uptimeMillis()
            mSetLightStates?.invoke(s, token, ledIds, states)
            lastFrame = IntArray(ledIds.size) { i -> colors[i % colors.size] }
            val tookMs = SystemClock.uptimeMillis() - startMs
            if (tookMs > SLOW_FRAME_LOG_MS) DebugLog.w(TAG, "setLightStates took ${tookMs}ms")
        } catch (e: Throwable) {
            DebugLog.w(TAG, "pushFrame failed: ${e.message}")
            closeSession()
        }
    }

    /**
     * Reads back what the lights service is showing on each LED and compares it with what we
     * expect: our last frame while our session is open, otherwise dark. A mismatch means another
     * session outranks ours, which on these phones is one of our own that was never closed.
     * Returns a description of the mismatch, or null when it matches or can't be read.
     */
    fun ringMismatch(): String? {
        val s = service ?: return null
        val read = mGetLightState ?: return null
        if (ledIds.isEmpty()) return null
        val expected = if (isSessionOpen && lastFrame.size == ledIds.size) lastFrame else IntArray(ledIds.size)
        val actual = try {
            IntArray(ledIds.size) { i -> (read.invoke(s, ledIds[i]) as? LightState)?.color ?: return null }
        } catch (e: Throwable) {
            DebugLog.w(TAG, "getLightState failed: ${e.cause?.message ?: e.message}")
            return null
        }
        // Compare colour only; alpha isn't something the ring shows.
        val matches = expected.indices.all { (expected[it] and 0xFFFFFF) == (actual[it] and 0xFFFFFF) }
        if (matches) return null
        fun hex(frame: IntArray) = frame.joinToString(" ") { "%06X".format(it and 0xFFFFFF) }
        return "sessionOpen=$isSessionOpen, expected [${hex(expected)}], showing [${hex(actual)}]"
    }

    fun blank() {
        if (ledIds.isEmpty()) {
            closeSession()
            return
        }
        if (!isSessionOpen && !openSession()) {
            return
        }
        val off = IntArray(ledIds.size) { 0x00000000 }
        pushFrame(off)
        try {
            Thread.sleep(BLANK_FRAME_GAP_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (isSessionOpen) {
            pushFrame(off)
        }
        closeSession()
    }

    companion object {
        private const val TAG = "PixelLightsManager"
        private const val BLANK_FRAME_GAP_MS = 20L
        private const val SLOW_FRAME_LOG_MS = 250L
        private const val MAX_CLOSE_RETRIES = 5
    }
}
