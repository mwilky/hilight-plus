package com.mwilky.hilight.plus

import android.app.Application
import android.content.Context
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Coordinates persistent preferences ([AppStore]) and privileged hardware lighting execution ([ShizukuBridge]).
 */
class LightController private constructor(private val app: Application) {

    val store = AppStore.get(app)
    val shizuku = ShizukuBridge.get(app)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val isEnabled: StateFlow<Boolean> = store.isEnabled
        .stateIn(scope, SharingStarted.Eagerly, true)

    val lightStyle: StateFlow<LightStyle> = store.lightStyle
        .stateIn(scope, SharingStarted.Eagerly, LightStyle())

    val autoOffSeconds: StateFlow<Int> = store.autoOffSeconds
        .stateIn(scope, SharingStarted.Eagerly, 60)

    init {
        DeviceOrientationDetector.onOrientationChanged = { faceDown ->
            shizuku.setDeviceFaceDown(faceDown)
            scope.launch { syncUnlockPauseWithOrientation(faceDown) }
        }

        shizuku.onAvailabilityChanged = {
            shizuku.setDeviceFaceDown(DeviceOrientationDetector.lastKnownFaceDown)
            syncState()
        }

        scope.launch {
            store.isEnabled.collect { syncState() }
        }
        scope.launch {
            store.lightStyle.collect { syncState() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            store.setEnabled(enabled)
        }
    }

    fun setLightStyle(style: LightStyle) {
        scope.launch {
            store.setLightStyle(style)
        }
    }

    fun setAutoOffSeconds(seconds: Int) {
        scope.launch {
            store.setAutoOffSeconds(seconds)
        }
    }

    // --- Production Alert & Event Controls ---

    /**
     * Triggers a transient alert effect for [durationMs] (e.g. notifications, preview effects).
     */
    fun triggerAlertEffect(
        pattern: PatternMode,
        color: Long,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        durationMs: Long = 3000L,
        requiresFaceDown: Boolean = false
    ) {
        val calculatedSpeed = when (pattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 800L
            PatternMode.ORBIT -> 1000L
            PatternMode.BEACON -> 750L
            PatternMode.RIPPLE -> 900L
            PatternMode.SPARKLE -> 1400L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> speedMs
        }
        shizuku.triggerAlert(
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            durationMs = durationMs,
            requiresFaceDown = requiresFaceDown
        )
    }

    /**
     * Enqueues or updates a notification alert in the multi-notification cyclic queue.
     */
    fun postNotificationAlert(
        key: String,
        pattern: PatternMode,
        color: Long,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        durationMs: Long = 0L,
        requiresFaceDown: Boolean = false
    ) {
        val calculatedSpeed = when (pattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 800L
            PatternMode.ORBIT -> 1000L
            PatternMode.BEACON -> 750L
            PatternMode.RIPPLE -> 900L
            PatternMode.SPARKLE -> 1400L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> speedMs
        }
        shizuku.postAlert(
            key = key,
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            durationMs = durationMs,
            requiresFaceDown = requiresFaceDown
        )
    }

    /**
     * Removes an active notification alert by key when dismissed or swiped away.
     */
    fun removeNotificationAlert(key: String) {
        shizuku.removeAlert(key)
    }

    /**
     * Starts an indefinite incoming call ring alert until answered or ended.
     */
    fun startIncomingCallAlert(
        pattern: PatternMode = PatternMode.PULSE,
        color: Long = 0xFF4285F4,
        brightness: Float = 1.0f,
        speedMs: Long = 1000L,
        requiresFaceDown: Boolean = false
    ) {
        val calculatedSpeed = when (pattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 800L
            PatternMode.ORBIT -> 1000L
            PatternMode.BEACON -> 750L
            PatternMode.RIPPLE -> 900L
            PatternMode.SPARKLE -> 1400L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> speedMs
        }
        shizuku.startIncomingCall(
            pattern = pattern.id,
            color = color,
            brightness = brightness,
            speedMs = calculatedSpeed,
            requiresFaceDown = requiresFaceDown
        )
    }

    fun setDeviceFaceDown(faceDown: Boolean) {
        shizuku.setDeviceFaceDown(faceDown)
    }

    private suspend fun syncUnlockPauseWithOrientation(faceDown: Boolean) {
        if (store.unlockBehavior.first() != UnlockBehavior.PAUSE) return
        if (faceDown || !DevicePresence.isActivelyUsing(app, faceDown)) {
            shizuku.resumeAlerts()
        } else {
            shizuku.pauseAlerts()
        }
    }

    /**
     * Stops the call override without deleting pending notification alerts.
     */
    fun stopIncomingCallAlert() {
        shizuku.stopIncomingCall()
    }

    /**
     * Clears notification / transient alerts without interrupting an incoming call.
     */
    fun clearAlert() {
        shizuku.clearAlert()
        syncState()
    }

    /**
     * Pauses alert lighting while retaining queued alerts in memory.
     */
    fun pauseAlerts() {
        shizuku.pauseAlerts()
    }

    /**
     * Resumes queued alert lighting when device is locked again.
     */
    fun resumeAlerts() {
        shizuku.resumeAlerts()
    }

    /**
     * Preview helper for testing a style on the hardware.
     */
    fun previewEffect(pattern: PatternMode, color: Long, durationMs: Long = 3000L) {
        triggerAlertEffect(
            pattern = pattern,
            color = color,
            brightness = 1.0f,
            durationMs = durationMs
        )
    }

    fun syncState() {
        scope.launch {
            val enabled = store.isEnabled.first()
            if (!enabled) {
                shizuku.turnOff()
                return@launch
            }
            val style = store.lightStyle.first()
            shizuku.setAmbient(
                pattern = style.pattern.id,
                color = style.color,
                brightness = style.brightness,
                speedMs = style.speedMs
            )
        }
    }

    fun refreshStatus() {
        shizuku.refresh()
    }

    companion object {
        @Volatile
        private var instance: LightController? = null

        fun get(context: Context): LightController {
            val app = if (context is Application) context else context.applicationContext as Application
            return instance ?: synchronized(this) {
                instance ?: LightController(app).also { instance = it }
            }
        }
    }
}
