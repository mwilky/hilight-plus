package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.LowBatteryPattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates smooth 8-LED color frames based on active pattern mode, elapsed time, and color parameters.
 */
class PatternRenderer {

    fun renderFrame(
        pattern: String,
        colorLong: Long,
        brightness: Float,
        speedMs: Long,
        elapsedTimeMs: Long,
        ledCount: Int = 8
    ): IntArray {
        val count = maxOf(1, ledCount)
        val clampedBrightness = brightness.coerceIn(0f, 1f)
        val baseColor = (colorLong.toInt() or 0xFF000000.toInt())
        val speed = maxOf(200L, speedMs)

        val frame = IntArray(count)

        when (pattern.lowercase()) {
            "off" -> {
                frame.fill(0x00000000)
            }

            "solid" -> {
                val c = scaleColor(baseColor, clampedBrightness.toDouble())
                frame.fill(c)
            }

            "breathe" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1.0 - cos(phase * 2.0 * PI)) / 2.0
                val intensity = (0.05 + 0.95 * k) * clampedBrightness
                val c = scaleColor(baseColor, intensity)
                frame.fill(c)
            }

            "pulse" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = if (phase < 0.30) {
                    val t = phase / 0.30
                    t * t
                } else if (phase < 0.85) {
                    val t = (phase - 0.30) / 0.55
                    (1.0 + cos(t * PI)) / 2.0
                } else {
                    0.0
                }
                val c = if (k > 0.001) scaleColor(baseColor, k * clampedBrightness) else 0x00000000
                frame.fill(c)
            }

            "wave" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val angle = 2.0 * PI * (phase - i.toDouble() / count)
                    val k = (1.0 + sin(angle)) / 2.0
                    val intensity = (0.10 + 0.90 * k) * clampedBrightness
                    frame[i] = scaleColor(baseColor, intensity)
                }
            }

            "comet" -> {
                val headPos = ((elapsedTimeMs % speed) / speed.toDouble()) * count
                val tailLength = 3.5
                for (i in 0 until count) {
                    var diff = (headPos - i + count) % count
                    if (diff < 0) diff += count
                    val k = if (diff <= tailLength) {
                        (1.0 - (diff / tailLength)).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }
                    frame[i] = if (k > 0.01) scaleColor(baseColor, k * k * clampedBrightness) else 0x00000000
                }
            }

            "orbit" -> {
                // Dual counter-rotating colliding comets
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val head1 = phase * count
                val head2 = ((1.0 - phase) * count + count) % count
                val tailLength = 3.0

                for (i in 0 until count) {
                    var diff1 = (head1 - i + count) % count
                    if (diff1 < 0) diff1 += count
                    val k1 = if (diff1 <= tailLength) (1.0 - (diff1 / tailLength)).coerceIn(0.0, 1.0) else 0.0

                    var diff2 = (head2 - i + count) % count
                    if (diff2 < 0) diff2 += count
                    val k2 = if (diff2 <= tailLength) (1.0 - (diff2 / tailLength)).coerceIn(0.0, 1.0) else 0.0

                    val combined = (k1 * k1 + k2 * k2).coerceIn(0.0, 1.0)
                    frame[i] = if (combined > 0.01) scaleColor(baseColor, combined * clampedBrightness) else 0x00000000
                }
            }

            "beacon" -> {
                // High-visibility alternating quadrant strobe
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val kTop = when {
                    phase < 0.15 -> 1.0
                    phase < 0.25 -> 0.0
                    phase < 0.40 -> 1.0
                    else -> 0.0
                }
                val kBottom = when {
                    phase >= 0.50 && phase < 0.65 -> 1.0
                    phase >= 0.65 && phase < 0.75 -> 0.0
                    phase >= 0.75 && phase < 0.90 -> 1.0
                    else -> 0.0
                }

                for (i in 0 until count) {
                    val isTopHalf = i == 0 || i == 1 || i == 7 || i == 6
                    val k = if (isTopHalf) kTop else kBottom
                    frame[i] = if (k > 0.01) scaleColor(baseColor, k * clampedBrightness) else 0x00000000
                }
            }

            "ripple" -> {
                // Top-to-bottom split cascade with expansion bounce
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    // Distance from top LED 0: 0, 1, 2, 3, 4, 3, 2, 1
                    val distFromTop = when (i) {
                        0 -> 0.0
                        1, 7 -> 1.0
                        2, 6 -> 2.0
                        3, 5 -> 3.0
                        4 -> 4.0
                        else -> 0.0
                    }
                    val targetPhase = distFromTop / 4.0
                    val diff = abs(phase - targetPhase)
                    val k = if (diff < 0.35) {
                        val t = diff / 0.35
                        (1.0 + cos(t * PI)) / 2.0
                    } else 0.0

                    frame[i] = if (k > 0.01) scaleColor(baseColor, k * clampedBrightness) else 0x00000000
                }
            }

            "sparkle" -> {
                // Organic multi-LED gemstone twinkle
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val seedOffset = doubleArrayOf(0.0, 0.37, 0.71, 0.19, 0.83, 0.53, 0.07, 0.61)

                for (i in 0 until count) {
                    val offset = if (i < seedOffset.size) seedOffset[i] else (i * 0.23) % 1.0
                    val ledPhase = (phase + offset) % 1.0
                    val k = (1.0 - cos(ledPhase * 2.0 * PI)) / 2.0
                    val intensity = (k * k * k) * clampedBrightness // Cubic curve for sharp twinkle glints
                    frame[i] = if (intensity > 0.02) scaleColor(baseColor, intensity) else 0x00000000
                }
            }

            "rainbow" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val hue = ((phase + (i.toDouble() / count)) * 360.0) % 360.0
                    frame[i] = scaleColor(hsvToRgb(hue, 1f, 1f), clampedBrightness.toDouble())
                }
            }

            else -> {
                val c = scaleColor(baseColor, clampedBrightness.toDouble())
                frame.fill(c)
            }
        }

        return frame
    }

    /**
     * Renders the battery indicator layer: a charging pattern, or a fixed "full" / "low battery"
     * look that always overrides the chosen charging pattern.
     */
    fun renderBatteryFrame(
        pattern: BatteryPattern,
        levelPercent: Int,
        charging: Boolean,
        full: Boolean,
        low: Boolean,
        autoColor: Boolean,
        fixedColor: Long,
        brightness: Float,
        elapsedTimeMs: Long,
        lowPattern: LowBatteryPattern = LowBatteryPattern.HEARTBEAT,
        ledCount: Int = 8
    ): IntArray {
        val count = maxOf(1, ledCount)
        val clampedBrightness = brightness.coerceIn(0f, 1f)
        val level = levelPercent.coerceIn(0, 100)
        val baseColor = if (autoColor) {
            batteryLevelColor(level)
        } else {
            fixedColor.toInt() or 0xFF000000.toInt()
        }

        return when {
            full -> renderBatteryFull(baseColor, clampedBrightness, elapsedTimeMs, count)
            low && !charging -> renderBatteryLow(baseColor, clampedBrightness, level, lowPattern, elapsedTimeMs, count)
            pattern == BatteryPattern.GRADIENT_RING -> renderBatteryGradientRing(baseColor, clampedBrightness, elapsedTimeMs, count)
            pattern == BatteryPattern.CHARGE_FILL -> renderBatteryGauge(baseColor, clampedBrightness, level, count, breathe = true, elapsedTimeMs)
            else -> renderBatteryGauge(baseColor, clampedBrightness, level, count, breathe = false, elapsedTimeMs)
        }
    }

    /**
     * Split-ring layer for several waiting notifications: each gets its own arc in its colour,
     * with one dark LED between arcs. On the diffused ring two colours placed side by side just
     * blend into a third, so the gap is what makes "two things" readable rather than "one odd
     * colour". All arcs breathe together, shallowly, so the count stays readable through the
     * whole cycle and there is no per-arc motion to mistake for extra alerts.
     *
     * [colors] is newest first; the newest arc starts at LED 0 (the top) and arcs run clockwise.
     */
    fun renderSplitFrame(
        colors: LongArray,
        brightness: Float,
        elapsedTimeMs: Long,
        ledCount: Int = 8
    ): IntArray {
        val count = maxOf(1, ledCount)
        val layout = splitLayout(colors.size, count)
        val phase = (elapsedTimeMs % SPLIT_BREATHE_MS) / SPLIT_BREATHE_MS.toDouble()
        val k = (SPLIT_BREATHE_FLOOR + (1.0 - SPLIT_BREATHE_FLOOR) * (1.0 - cos(phase * 2.0 * PI)) / 2.0) *
            brightness.coerceIn(0f, 1f)

        val frame = IntArray(count)
        for (i in 0 until count) {
            val segment = layout[i]
            frame[i] = if (segment < 0) 0x00000000 else scaleColor(colors[segment].toInt() or 0xFF000000.toInt(), k)
        }
        return frame
    }

    /** Red (0%) -> amber (50%) -> green (100%), so level reads at a glance without a legend. */
    private fun batteryLevelColor(level: Int): Int {
        val hue = if (level <= 50) {
            40.0 * (level / 50.0)
        } else {
            40.0 + 80.0 * ((level - 50) / 50.0)
        }
        return hsvToRgb(hue, 1f, 1f)
    }

    /**
     * Whole LEDs snapped to the nearest 12.5% of level — no partial-brightness boundary LED.
     * The diffused ring bleeds so heavily that a dim leading LED next to a full-brightness one
     * just reads as glow spilling from its neighbour, not as a boundary; a hard on/off edge is
     * the only thing that survives the diffusion. Breathing (for CHARGE_FILL) stays shallow for
     * the same reason: dipping too low makes "on" indistinguishable from bleed.
     */
    private fun renderBatteryGauge(
        baseColor: Int,
        brightness: Float,
        level: Int,
        count: Int,
        breathe: Boolean,
        elapsedTimeMs: Long
    ): IntArray {
        val litCount = ((level / 100.0) * count).roundToInt().coerceIn(0, count)

        val breatheK = if (breathe) {
            val phase = (elapsedTimeMs % BATTERY_BREATHE_MS) / BATTERY_BREATHE_MS.toDouble()
            0.8 + 0.2 * (1.0 - cos(phase * 2.0 * PI)) / 2.0
        } else {
            1.0
        }
        val c = scaleColor(baseColor, breatheK * brightness)

        val frame = IntArray(count)
        for (i in 0 until litCount) frame[i] = c
        return frame
    }

    /** All LEDs lit in the level colour, breathing slowly since this is only used while charging. */
    private fun renderBatteryGradientRing(baseColor: Int, brightness: Float, elapsedTimeMs: Long, count: Int): IntArray {
        val phase = (elapsedTimeMs % BATTERY_BREATHE_MS) / BATTERY_BREATHE_MS.toDouble()
        val breatheK = 0.55 + 0.45 * (1.0 - cos(phase * 2.0 * PI)) / 2.0
        val c = scaleColor(baseColor, breatheK * brightness)
        return IntArray(count) { c }
    }

    /** All LEDs solid, with a brief brighter highlight sweeping round every 10s so it still looks alive. */
    private fun renderBatteryFull(baseColor: Int, brightness: Float, elapsedTimeMs: Long, count: Int): IntArray {
        val phaseInCycle = elapsedTimeMs % BATTERY_FULL_SWEEP_PERIOD_MS
        val headPos = if (phaseInCycle < BATTERY_FULL_SWEEP_DURATION_MS) {
            (phaseInCycle / BATTERY_FULL_SWEEP_DURATION_MS.toDouble()) * count
        } else {
            -1.0
        }
        val tailLength = 2.0

        val frame = IntArray(count)
        for (i in 0 until count) {
            var k = 0.78
            if (headPos >= 0.0) {
                var diff = abs(headPos - i)
                if (diff > count / 2.0) diff = count - diff
                if (diff <= tailLength) {
                    k = max(k, 1.0 - (diff / tailLength) * 0.22)
                }
            }
            frame[i] = scaleColor(baseColor, k * brightness)
        }
        return frame
    }

    /**
     * Low battery: the remaining LEDs (at least one, so it's never invisible) in the chosen
     * pattern. Heartbeat is a sharp pulse, breathe a slow swell, solid is simply lit.
     */
    private fun renderBatteryLow(
        baseColor: Int,
        brightness: Float,
        level: Int,
        pattern: LowBatteryPattern,
        elapsedTimeMs: Long,
        count: Int
    ): IntArray {
        val litLeds = ((level / 100.0) * count).roundToInt().coerceIn(1, count)

        val k = when (pattern) {
            LowBatteryPattern.SOLID -> 1.0
            LowBatteryPattern.BREATHE -> {
                val phase = (elapsedTimeMs % BATTERY_BREATHE_MS) / BATTERY_BREATHE_MS.toDouble()
                0.35 + 0.65 * (1.0 - cos(phase * 2.0 * PI)) / 2.0
            }
            LowBatteryPattern.HEARTBEAT -> {
                val phase = (elapsedTimeMs % BATTERY_HEARTBEAT_MS) / BATTERY_HEARTBEAT_MS.toDouble()
                val beat = when {
                    phase < 0.20 -> {
                        val t = phase / 0.20
                        t * t
                    }
                    phase < 0.35 -> 1.0
                    phase < 0.55 -> {
                        val t = (phase - 0.35) / 0.20
                        1.0 - t * t
                    }
                    else -> 0.0
                }
                0.15 + 0.85 * beat
            }
        }
        val c = scaleColor(baseColor, k * brightness)

        val frame = IntArray(count)
        for (i in 0 until litLeds) frame[i] = c
        return frame
    }

    private fun scaleColor(color: Int, factor: Double): Int {
        val k = factor.coerceIn(0.0, 1.0)
        val a = 0xFF
        val r = (((color ushr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hsvToRgb(hue: Double, saturation: Float, value: Float): Int {
        val h = ((hue % 360.0) + 360.0) % 360.0
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1.0f - abs(((h / 60.0) % 2.0 - 1.0).toFloat()))
        val m = v - c

        var rPrime = 0f
        var gPrime = 0f
        var bPrime = 0f

        when {
            h < 60.0 -> { rPrime = c; gPrime = x; bPrime = 0f }
            h < 120.0 -> { rPrime = x; gPrime = c; bPrime = 0f }
            h < 180.0 -> { rPrime = 0f; gPrime = c; bPrime = x }
            h < 240.0 -> { rPrime = 0f; gPrime = x; bPrime = c }
            h < 300.0 -> { rPrime = x; gPrime = 0f; bPrime = c }
            else -> { rPrime = c; gPrime = 0f; bPrime = x }
        }

        val r = ((rPrime + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private const val SPLIT_BREATHE_MS = 2400L
        // Never dip low enough that "on" reads as bleed from a neighbour.
        private const val SPLIT_BREATHE_FLOOR = 0.45

        /** Each arc needs at least one lit LED plus its gap, and more than four arcs stop being countable. */
        const val MAX_SPLIT_SEGMENTS = 4

        /**
         * Maps each LED to the arc index it belongs to, or -1 for a gap. Arcs are always the same
         * size as each other. Two arcs split the ring in half, 2 lit + 2 dark each on 8 LEDs: a
         * single dark LED is not enough of a gap on the diffused ring and the two halves merge into
         * one blended circle. Three or more use four fixed single-LED slots at the quarter points,
         * so three alerts read as "three of four slots" with one dark slot, rather than as an
         * uneven 2+2+1: eight LEDs cannot be split three ways evenly, and equal arcs with a
         * consistent grid beat equal gaps.
         */
        fun splitLayout(segments: Int, ledCount: Int): IntArray {
            val count = maxOf(1, ledCount)
            val n = segments.coerceIn(1, minOf(MAX_SPLIT_SEGMENTS, maxOf(1, count / 2)))
            val layout = IntArray(count) { -1 }

            if (n >= 3) {
                val stride = maxOf(2, count / MAX_SPLIT_SEGMENTS)
                for (segment in 0 until n) {
                    val at = segment * stride
                    if (at < count) layout[at] = segment
                }
                return layout
            }

            // Each arc takes half of its share of the ring; the other half is its gap.
            val share = count / n
            val size = maxOf(1, share / 2)
            for (segment in 0 until n) {
                val start = segment * share
                for (offset in 0 until size) layout[start + offset] = segment
            }
            return layout
        }

        private const val BATTERY_BREATHE_MS = 2200L
        private const val BATTERY_HEARTBEAT_MS = 1800L
        private const val BATTERY_FULL_SWEEP_PERIOD_MS = 10_000L
        private const val BATTERY_FULL_SWEEP_DURATION_MS = 900L
    }
}
