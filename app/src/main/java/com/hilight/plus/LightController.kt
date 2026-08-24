package com.hilight.plus

import android.app.Application
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
 * High-level controller coordinating DataStore preferences ([AppStore]) and hardware light execution via [ShizukuBridge].
 */
class LightController private constructor(app: Application) {

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
        shizuku.onAvailabilityChanged = {
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
