package com.hilight.plus.core

import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.os.Binder
import android.os.IBinder
import android.util.Log
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

    private var ledIds: IntArray = intArrayOf()
    var isSessionOpen: Boolean = false
        private set

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

            @Suppress("UNCHECKED_CAST")
            val allLights = mGetLights?.invoke(service) as? List<Light> ?: emptyList()
            ledIds = allLights
                .filter { it.type == Light.LIGHT_TYPE_APPLICATION }
                .map { it.id }
                .toIntArray()

            Log.i(TAG, "Connected to ${ledIds.size} Pixel 11 rear LEDs (IDs: ${ledIds.joinToString()})")
            return ledIds.isNotEmpty()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize PixelLightsManager: ${e.message}", e)
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
        token = Binder() // Fresh token per session to avoid dead token locks in Android lights manager
        return try {
            mOpenSession?.invoke(s, token, priority)
            isSessionOpen = true
            true
        } catch (e: Throwable) {
            Log.w(TAG, "openSession failed: ${e.message}")
            false
        }
    }

    fun closeSession() {
        val s = service ?: return
        if (!isSessionOpen) return
        try {
            mCloseSession?.invoke(s, token)
        } catch (e: Throwable) {
            Log.w(TAG, "closeSession failed: ${e.message}")
        } finally {
            isSessionOpen = false
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
            mSetLightStates?.invoke(s, token, ledIds, states)
        } catch (e: Throwable) {
            Log.w(TAG, "pushFrame failed: ${e.message}")
            closeSession()
        }
    }

    fun blank() {
        if (isSessionOpen && ledIds.isNotEmpty()) {
            pushFrame(IntArray(ledIds.size) { 0x00000000 })
        }
        closeSession()
    }

    companion object {
        private const val TAG = "PixelLightsManager"
    }
}
