package com.mwilky.hilight.plus.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
}
