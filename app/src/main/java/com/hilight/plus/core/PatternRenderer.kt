package com.hilight.plus.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates 8-LED color frames based on active pattern mode, elapsed time, and color parameters.
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
        val speed = maxOf(60L, speedMs)

        val frame = IntArray(count)

        when (pattern.lowercase()) {
            "off" -> {
                // Empty frame (all 0)
            }

            "solid" -> {
                val c = scaleColor(baseColor, clampedBrightness.toDouble())
                for (i in 0 until count) {
                    frame[i] = c
                }
            }

            "breathe" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1 - cos(phase * 2 * PI)) / 2.0
                val intensity = (0.05 + 0.95 * k) * clampedBrightness
                val c = scaleColor(baseColor, intensity)
                for (i in 0 until count) {
                    frame[i] = c
                }
            }

            "pulse" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = if (phase < 0.15) phase / 0.15 else Math.exp(-(phase - 0.15) * 5.0)
                val c = scaleColor(baseColor, k * clampedBrightness)
                for (i in 0 until count) {
                    frame[i] = c
                }
            }

            "wave" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val k = (1.0 + sin(2 * PI * (phase + i.toDouble() / count))) / 2.0
                    frame[i] = scaleColor(baseColor, (0.1 + 0.9 * k) * clampedBrightness)
                }
            }

            "comet" -> {
                val headPos = (elapsedTimeMs % speed) / speed.toDouble() * count
                for (i in 0 until count) {
                    var dist = headPos - i
                    if (dist < 0) dist += count
                    val k = maxOf(0.0, 1.0 - dist / 3.0)
                    frame[i] = scaleColor(baseColor, k * clampedBrightness)
                }
            }

            "rainbow" -> {
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                for (i in 0 until count) {
                    val hue = ((phase + i.toDouble() / count) * 360.0) % 360.0
                    frame[i] = scaleColor(hsvToRgb(hue, 1f, 1f), clampedBrightness.toDouble())
                }
            }

            // --- Future Effect Placeholders ---
            "gemini_listening" -> {
                // Future Placeholder: Soft pulsing cyan wave
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val c = scaleColor(0xFF00E5FF.toInt(), clampedBrightness.toDouble())
                for (i in 0 until count) {
                    val k = (1.0 + sin(2 * PI * (phase + i.toDouble() / count))) / 2.0
                    frame[i] = scaleColor(c, 0.2 + 0.8 * k)
                }
            }

            "gemini_thinking" -> {
                // Future Placeholder: Orbiting purple comet
                val headPos = (elapsedTimeMs % speed) / speed.toDouble() * count
                val c = scaleColor(0xFF7C4DFF.toInt(), clampedBrightness.toDouble())
                for (i in 0 until count) {
                    var dist = headPos - i
                    if (dist < 0) dist += count
                    val k = maxOf(0.0, 1.0 - dist / 3.0)
                    frame[i] = scaleColor(c, k)
                }
            }

            "gemini_responding" -> {
                // Future Placeholder: Dynamic speaking pulse
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = (1 - cos(phase * 2 * PI)) / 2.0
                frame.fill(scaleColor(baseColor, k * clampedBrightness))
            }

            "contact_call_alert" -> {
                // Future Placeholder: High-visibility rhythmic ring pulse
                val phase = (elapsedTimeMs % speed) / speed.toDouble()
                val k = if (phase < 0.2) phase / 0.2 else Math.exp(-(phase - 0.2) * 4.0)
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
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * k).toInt()
        val g = (((color ushr 8) and 0xFF) * k).toInt()
        val b = ((color and 0xFF) * k).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hsvToRgb(hue: Double, sat: Float, value: Float): Int {
        val h = (hue % 360 + 360) % 360
        val c = value * sat
        val x = c * (1 - kotlin.math.abs((h / 60.0) % 2 - 1)).toFloat()
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
        return 0xFF000000.toInt() or (ri shl 16) or (gi shl 8) or bi
    }
}
