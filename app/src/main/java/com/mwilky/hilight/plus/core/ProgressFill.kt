package com.mwilky.hilight.plus.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * A progress bar drawn on the ring: whole LEDs lit clockwise from the top, as the battery gauge
 * is, since the diffused ring only shows hard edges. One LED stays lit from the start so the ring
 * shows something is under way, and the arc breathes gently so it doesn't read as a notification.
 */
object ProgressFill {

    /** One slow breath. */
    const val LOOP_MS = 2_000L

    /** LEDs lit at [fraction] done: at least one, all of them only when finished. */
    fun litCount(fraction: Float, ledCount: Int): Int {
        if (ledCount <= 0) return 0
        val done = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return ceil(done * ledCount).toInt().coerceIn(1, ledCount)
    }

    fun frame(fraction: Float, color: Long, brightness: Float, elapsedMs: Long, ledCount: Int): IntArray {
        val frame = IntArray(ledCount)
        val phase = (elapsedMs % LOOP_MS) / LOOP_MS.toDouble()
        // Shallow, like Charge fill: dipping lower would make the lit arc look like spill.
        val breathe = 0.8 + 0.2 * (1.0 - cos(phase * 2.0 * PI)) / 2.0
        val lit = scale(color.toInt(), breathe * brightness.coerceIn(0f, 1f))
        for (i in 0 until litCount(fraction, ledCount)) frame[i] = lit
        return frame
    }

    private fun scale(color: Int, factor: Double): Int {
        val k = factor.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = (((color ushr shift) and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
