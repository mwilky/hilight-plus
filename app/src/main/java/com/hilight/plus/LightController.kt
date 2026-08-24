package com.hilight.plus

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * High-level controller coordinating DataStore preferences and hardware light execution via [ShizukuBridge].
 */
class LightController private constructor(context: Context) {

    private val appContext = context.applicationContext
    val prefs = AppPreferences.get(appContext)
    val shizuku = ShizukuBridge.get(appContext)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val isEnabled: StateFlow<Boolean> = prefs.isEnabled
        .stateIn(scope, SharingStarted.Eagerly, true)

    val lightStyle: StateFlow<LightStyle> = prefs.lightStyle
        .stateIn(scope, SharingStarted.Eagerly, LightStyle())

    val autoOffSeconds: StateFlow<Int> = prefs.autoOffSeconds
        .stateIn(scope, SharingStarted.Eagerly, 60)

    init {
        shizuku.onAvailabilityChanged = {
            syncState()
        }

        scope.launch {
            prefs.isEnabled.collect { syncState() }
        }
        scope.launch {
            prefs.lightStyle.collect { syncState() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            prefs.setEnabled(enabled)
        }
    }

    fun setLightStyle(style: LightStyle) {
        scope.launch {
            prefs.setLightStyle(style)
        }
    }

    fun setAutoOffSeconds(seconds: Int) {
        scope.launch {
            prefs.setAutoOffSeconds(seconds)
        }
    }

    /**
     * Executes a 3-second hardware test alert on the Pixel 11 rear LEDs.
     */
    fun testAlert(pattern: PatternMode, color: Long, durationMs: Long = 3000) {
        shizuku.testAlert(
            pattern = pattern.id,
            color = color,
            brightness = 1.0f,
            speedMs = 800L,
            durationMs = durationMs
        )
    }

    fun syncState() {
        scope.launch {
            val enabled = prefs.isEnabled.first()
            if (!enabled) {
                shizuku.turnOff()
                return@launch
            }
            val style = prefs.lightStyle.first()
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

        fun get(context: Context): LightController =
            instance ?: synchronized(this) {
                instance ?: LightController(context).also { instance = it }
            }
    }
}
