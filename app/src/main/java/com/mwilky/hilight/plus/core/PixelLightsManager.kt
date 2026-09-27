package com.mwilky.hilight.plus.core

import android.hardware.lights.ColorSequence
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.MultiLightEffect
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
    private var mSetLightEffect: Method? = null
    private var mGetLightSequence: Method? = null

    private var leds: List<Light> = emptyList()
    private var ledIds: IntArray = intArrayOf()

    /**
     * Whether the ring can play a looping [RingEffect] by itself (Android 17 light effects), so
     * nothing has to push frames while it runs.
     */
    var supportsEffects: Boolean = false
        private set

    /** The hardware's frame length: the lights service truncates every effect delay to a multiple of it. */
    var effectPeriodMs: Long = 1L
        private set
    var isSessionOpen: Boolean = false
        private set

    // Sessions whose close failed, with how many retries they've had. The lights service keeps
    // such a session, and its last frame, for as long as this process lives, and it can outrank
    // every session opened after it, so the ring would stay frozen on it whatever we push.
    private val unclosedTokens = linkedMapOf<IBinder, Int>()

    val unclosedSessionCount: Int
        get() = unclosedTokens.size

    // What our open session last set, per LED: a frame or an effect, never both. Only meaningful
    // while the session is open.
    private var lastFrame: IntArray = intArrayOf()
    private var lastEffect: RingEffect? = null

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
            // Light effects are new in Android 17; without them the engine pushes frames instead.
            mSetLightEffect = runCatching {
                ifaceClass.getMethod("setLightEffect", IBinder::class.java, MultiLightEffect::class.java)
            }.getOrNull()
            mGetLightSequence = runCatching { ifaceClass.getMethod("getLightSequence", Int::class.javaPrimitiveType) }.getOrNull()

            @Suppress("UNCHECKED_CAST")
            val allLights = mGetLights?.invoke(service) as? List<Light> ?: emptyList()
            leds = allLights.filter { it.type == Light.LIGHT_TYPE_APPLICATION }
            ledIds = leds.map { it.id }.toIntArray()
            supportsEffects = mSetLightEffect != null && leds.isNotEmpty() && leds.all { it.hasAnimationControl() }
            effectPeriodMs = leds.maxOfOrNull { it.minUpdatePeriodMillis }?.coerceAtLeast(1L) ?: 1L

            DebugLog.i(TAG, "Connected to ${ledIds.size} Pixel 11 rear LEDs (IDs: ${ledIds.joinToString()}), effects=$supportsEffects, frame period ${effectPeriodMs}ms")
            if (supportsEffects && openSession()) {
                // A daemon that died mid-effect can leave the hardware still playing it; replace it.
                lastEffect = offEffect()
                closeSession()
            }
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
        lastEffect = null
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
        stopEffect()
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
        stopEffect()

        try {
            val states = Array(ledIds.size) { i ->
                LightState.Builder().setColor(offAsZero(colors[i % colors.size])).build()
            }
            val startMs = SystemClock.uptimeMillis()
            mSetLightStates?.invoke(s, token, ledIds, states)
            lastFrame = IntArray(ledIds.size) { i -> colors[i % colors.size] }
            // A frame on an LED replaces any effect our session had playing on it.
            lastEffect = null
            val tookMs = SystemClock.uptimeMillis() - startMs
            if (tookMs > SLOW_FRAME_LOG_MS) DebugLog.w(TAG, "setLightStates took ${tookMs}ms")
        } catch (e: Throwable) {
            DebugLog.w(TAG, "pushFrame failed: ${e.message}")
            closeSession()
        }
    }

    /**
     * Hands [effect] to the lights hardware, which loops it by itself until something replaces
     * it: another effect, a frame, or the session closing. [iterations] of 0 loops until replaced;
     * more than that stops after that many loops, leaving the ring dark. Returns false if the
     * service refused it, so the caller can fall back to frames.
     */
    fun playEffect(effect: RingEffect, iterations: Int = 0): Boolean {
        val s = service ?: return false
        val method = mSetLightEffect ?: return false
        if (!isSessionOpen || !supportsEffects || effect.leds.isEmpty()) return false
        return try {
            val mode = if (effect.linear) ColorSequence.INTERPOLATION_MODE_LINEAR else ColorSequence.INTERPOLATION_MODE_NONE
            val builder = MultiLightEffect.Builder()
                .setIterations(iterations)
                .setPreemptive(true)
            leds.forEachIndexed { i, light ->
                val seq = effect.leds[i % effect.leds.size]
                builder.addLightSequence(
                    light,
                    ColorSequence.Builder()
                        .setInterpolationMode(mode)
                        .addControlPoints(seq.delaysMs, seq.colors)
                        .build()
                )
            }
            method.invoke(s, token, builder.build())
            lastEffect = effect
            lastFrame = intArrayOf()
            true
        } catch (e: Throwable) {
            DebugLog.w(TAG, "setLightEffect failed: ${e.cause?.message ?: e.message}")
            false
        }
    }

    /**
     * Ends the effect our session is playing, if any, by replacing it with one that shows black
     * once and finishes. A plain frame or closing the session doesn't reliably stop an effect the
     * hardware is already playing: the ring was seen carrying on with an old effect after both.
     * Once this one finishes the hardware is idle and takes frames again.
     */
    private fun stopEffect() {
        if (lastEffect == null || !isSessionOpen) return
        lastEffect = null
        playEffect(offEffect(), iterations = 1)
        lastEffect = null
    }

    private fun offEffect(): RingEffect = RingEffect(
        List(ledIds.size) { LedSequence(longArrayOf(0L, effectPeriodMs), intArrayOf(OPAQUE_BLACK, OPAQUE_BLACK)) },
        linear = true
    )

    /**
     * Reads back what the lights service is showing on each LED and compares it with what we
     * expect: our last frame or effect while our session is open, otherwise dark. A mismatch
     * means another session outranks ours, which on these phones is one of our own that was
     * never closed. Returns a description of the mismatch, or null when it matches or can't be read.
     */
    fun ringMismatch(): String? {
        val s = service ?: return null
        if (ledIds.isEmpty()) return null
        val effect = lastEffect
        // While an effect plays, the service reports its LEDs as black, so check the sequence instead.
        if (isSessionOpen && effect != null) return effectMismatch(s, effect)
        val read = mGetLightState ?: return null
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

    private fun effectMismatch(s: Any, effect: RingEffect): String? {
        val read = mGetLightSequence ?: return null
        for (i in ledIds.indices) {
            val showing = try {
                read.invoke(s, ledIds[i]) as? ColorSequence
            } catch (e: Throwable) {
                // Can't tell either way, which mustn't count as stuck.
                DebugLog.w(TAG, "getLightSequence failed: ${e.cause?.message ?: e.message}")
                return null
            }
            val expected = effect.leds[i % effect.leds.size].colors
            val actual = showing?.colors
            if (actual == null || !actual.map { it and 0xFFFFFF }.toIntArray().contentEquals(expected.map { it and 0xFFFFFF }.toIntArray())) {
                return "sessionOpen=true, LED ${ledIds[i]} expected our effect (${expected.size} keyframes), showing ${actual?.size ?: "no"} keyframes"
            }
        }
        return null
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
        private const val OPAQUE_BLACK = 0xFF000000.toInt()

        /**
         * While an effect plays, the lights service records each of its LEDs as opaque black and
         * skips passing on any colour equal to what it recorded. An opaque black frame after an
         * effect would never reach the hardware, which would keep playing the old effect on those
         * LEDs, so black always goes out as 0.
         */
        private fun offAsZero(color: Int): Int = if (color and 0xFFFFFF == 0) 0 else color
        private const val BLANK_FRAME_GAP_MS = 20L
        private const val SLOW_FRAME_LOG_MS = 250L
        private const val MAX_CLOSE_RETRIES = 5
    }
}
