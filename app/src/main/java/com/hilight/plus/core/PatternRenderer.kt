package com.hilight.plus.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates smooth 8-LED color frames based on active pattern mode, elapsed time, and color parameters.
 */
class PatternRenderer {

    // Google Quad-Color Palette: Blue, Red, Yellow, Green
    private val googleQuadColors = intArrayOf(
        0xFF4285F4.toInt(), 0xFF4285F4.toInt(), // LEDs 0, 1: Google Blue
        0xFFEA4335.toInt(), 0xFFEA4335.toInt(), // LEDs 2, 3: Google Red
        0xFFFBBC05.toInt(), 0xFFFBBC05.toInt(), // LEDs 4, 5: Google Yellow
        0xFF34A853.toInt(), 0xFF34A853.toInt()  // LEDs 6, 7: Google Green
    )

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

            "rainbow" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val hue = ((phase + (i.toDouble() / count)) * 360.0) % 360.0
                    frame[i] = scaleColor(hsvToRgb(hue, 1f, 1f), clampedBrightness.toDouble())
                }
            }

            // --- Authentic Stock Pixel 11 Gemini Assistant Effects ---

            "google_quad", "gemini_listening" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1.0 - cos(phase * 2.0 * PI)) / 2.0
                val intensity = (0.10 + 0.90 * k) * clampedBrightness
                for (i in 0 until count) {
                    val quadCol = googleQuadColors[i % googleQuadColors.size]
                    frame[i] = scaleColor(quadCol, intensity)
                }
            }

            "gemini_thinking", "gemini_comet" -> {
                val headPos = ((elapsedTimeMs % speed) / speed.toDouble()) * count
                val tailLength = 3.5
                for (i in 0 until count) {
                    val diff1 = ((headPos - i) % count + count) % count
                    val diff2 = ((headPos + (count / 2.0) - i) % count + count) % count
                    val k1 = if (diff1 <= tailLength) (1.0 - (diff1 / tailLength)).coerceIn(0.0, 1.0) else 0.0
                    val k2 = if (diff2 <= tailLength) (1.0 - (diff2 / tailLength)).coerceIn(0.0, 1.0) else 0.0
                    val k = maxOf(k1 * k1, k2 * k2)

                    frame[i] = if (k > 0.01) scaleColor(baseColor, k * clampedBrightness) else 0x00000000
                }
            }

            "gemini_responding", "gemini_glow" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1.0 - cos(phase * 2.0 * PI)) / 2.0
                val intensity = (0.15 + 0.85 * k) * clampedBrightness
                frame.fill(scaleColor(baseColor, intensity))
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
