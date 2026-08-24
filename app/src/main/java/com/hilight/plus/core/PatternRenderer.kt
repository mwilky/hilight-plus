package com.hilight.plus.core

import kotlin.math.PI
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
                // Smooth sine easing from 5% to 100% brightness
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1.0 - cos(phase * 2.0 * PI)) / 2.0
                val intensity = (0.05 + 0.95 * k) * clampedBrightness
                val c = scaleColor(baseColor, intensity)
                frame.fill(c)
            }

            "pulse" -> {
                // Smooth rhythmic pulse that fades completely to 0 at the end of each period
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = if (phase < 0.30) {
                    val t = phase / 0.30
                    t * t
                } else if (phase < 0.85) {
                    val t = (phase - 0.30) / 0.55
                    (1.0 + cos(t * PI)) / 2.0
                } else {
                    0.0 // True dark rest interval between pulses
                }
                val c = if (k > 0.001) scaleColor(baseColor, k * clampedBrightness) else 0x00000000
                frame.fill(c)
            }

            "wave" -> {
                // Continuous traveling sinusoidal wave around the ring
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val angle = 2.0 * PI * (phase - i.toDouble() / count)
                    val k = (1.0 + sin(angle)) / 2.0
                    val intensity = (0.10 + 0.90 * k) * clampedBrightness
                    frame[i] = scaleColor(baseColor, intensity)
                }
            }

            "comet" -> {
                // Smooth circulating comet head with a 3-LED decaying tail wrapped smoothly around modulo 8
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

            "rainbow" -> {
                // Ultra-smooth 360-degree continuous spectrum rotation
                val phase = (elapsedTimeMs % (speed * 2)) / (speed * 2).toDouble()
                for (i in 0 until count) {
                    val hue = ((phase + (i.toDouble() / count)) * 360.0) % 360.0
                    frame[i] = scaleColor(hsvToRgb(hue, 1f, 1f), clampedBrightness.toDouble())
                }
            }

            "contact_call_alert" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = if (phase < 0.2) phase / 0.2 else (1.0 - (phase - 0.2) / 0.8).coerceAtLeast(0.0)
                frame.fill(scaleColor(baseColor, k * clampedBrightness))
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
        val a = (255 * k).toInt().coerceIn(0, 255)
        val r = (((color ushr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hsvToRgb(hue: Double, sat: Float, value: Float): Int {
        val h = (hue % 360.0 + 360.0) % 360.0
        val c = value * sat
        val x = c * (1.0f - kotlin.math.abs(((h / 60.0) % 2.0 - 1.0).toFloat()))
        val m = value - c

        val (r, g, b) = when ((h / 60.0).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val ri = ((r + m) * 255).toInt().coerceIn(0, 255)
        val gi = ((g + m) * 255).toInt().coerceIn(0, 255)
        val bi = ((b + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
