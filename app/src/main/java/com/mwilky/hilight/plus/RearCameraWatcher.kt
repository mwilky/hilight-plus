package com.mwilky.hilight.plus

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler

/**
 * Whether any app has a rear camera open. The ring sits round the rear lenses, so it stays dark
 * while one is. Built on the camera service's availability callbacks, which report every app's
 * camera use with no permission; a camera reported unavailable is open somewhere.
 */
internal class RearCameraWatcher(context: Context, private val onChanged: (Boolean) -> Unit) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private val facingBack = mutableMapOf<String, Boolean>()
    private val openRear = mutableSetOf<String>()

    var inUse = false
        private set

    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            if (isRear(cameraId)) update { openRear += cameraId }
        }

        override fun onCameraAvailable(cameraId: String) {
            update { openRear -= cameraId }
        }
    }

    /** Callbacks arrive on [handler]; registering reports every camera's current state at once. */
    fun start(handler: Handler) {
        manager.registerAvailabilityCallback(callback, handler)
    }

    private fun update(change: () -> Unit) {
        change()
        val now = openRear.isNotEmpty()
        if (now == inUse) return
        inUse = now
        DebugLog.i(TAG, if (now) "Rear camera in use: ring held dark" else "Rear camera closed")
        onChanged(now)
    }

    private fun isRear(cameraId: String): Boolean = facingBack.getOrPut(cameraId) {
        runCatching {
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "RearCameraWatcher"
    }
}
