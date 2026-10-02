package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.LowBatteryPattern
import com.mwilky.hilight.plus.MAX_SPEED
import com.mwilky.hilight.plus.MIN_SPEED
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.SplitAnimation
import com.mwilky.hilight.plus.SplitLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * The hardware effect route replays each pattern from keyframes, so these check the conversion
 * reproduces the frame renderer closely, fits the keyframe budget and keeps hardware-safe timing.
 */
class RingEffectTest {

    private val renderer = PatternRenderer()

    private fun patternEffect(pattern: PatternMode, periodMs: Long = 10L) = RingEffect.sample(pattern.speedMs(), periodMs, 8) { t ->
        renderer.renderFrame(pattern.id, 0xFF4285F4L, 1f, pattern.speedMs(), t)
    }

    /** A turning look's loop can be stretched to fit whole hardware frames; map back to pattern time. */
    private fun patternTime(effect: RingEffect, pattern: PatternMode, t: Long): Long =
        if (effect.durationMs == 0L) t else t * pattern.speedMs() / effect.durationMs

    /** The colour the hardware would show at [t]: a fade between keyframes, or a hold for steps. */
    private fun colorAt(seq: LedSequence, t: Long, linear: Boolean = true): Int {
        var start = 0L
        for (i in 1 until seq.colors.size) {
            val end = start + seq.delaysMs[i]
            if (!linear && t < end) return seq.colors[i - 1] and 0xFFFFFF
            if (t <= end) {
                val f = (t - start).toDouble() / (end - start)
                fun ch(shift: Int): Int {
                    val a = (seq.colors[i - 1] ushr shift) and 0xFF
                    val b = (seq.colors[i] ushr shift) and 0xFF
                    return (a + (b - a) * f).toInt()
                }
                return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            start = end
        }
        return seq.colors.last() and 0xFFFFFF
    }

    private fun maxError(a: Int, b: Int): Int =
        intArrayOf(16, 8, 0).maxOf { abs(((a ushr it) and 0xFF) - ((b ushr it) and 0xFF)) }

    @Test
    fun everyRulePatternFitsTheKeyframeBudgetAndTracksTheRenderer() {
        for (pattern in PatternMode.entries.filter { it != PatternMode.OFF }) {
            val effect = patternEffect(pattern)
            var worst = 0
            for (t in 0L..effect.durationMs step 10L) {
                // A hard edge (a comet head arriving, a beacon flash) becomes a fade over one
                // sample, so match against the renderer within that much time either side.
                val nearby = (-20L..20L step 10L).map { dt ->
                    renderer.renderFrame(pattern.id, 0xFF4285F4L, 1f, pattern.speedMs(), patternTime(effect, pattern, (t + dt).coerceAtLeast(0L)))
                }
                for (led in 0 until 8) {
                    val shown = colorAt(effect.leds[led], t, effect.linear)
                    // Anything between the colours the renderer passes through in that window.
                    val error = intArrayOf(16, 8, 0).maxOf { shift ->
                        val values = nearby.map { (it[led] ushr shift) and 0xFF }
                        val v = (shown ushr shift) and 0xFF
                        maxOf(0, values.min() - v, v - values.max())
                    }
                    worst = max(worst, error)
                }
            }
            assertTrue("$pattern keyframes", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
            assertTrue("$pattern worst error $worst", worst <= 24)
        }
    }

    @Test
    fun smoothPatternsStayWithinAFewLevels() {
        for (pattern in listOf(PatternMode.BREATHE, PatternMode.WAVE, PatternMode.RAINBOW, PatternMode.GEMINI_LISTENING)) {
            val effect = patternEffect(pattern)
            var worst = 0
            for (t in 0L..effect.durationMs step 10L) {
                val expected = renderer.renderFrame(pattern.id, 0xFF4285F4L, 1f, pattern.speedMs(), patternTime(effect, pattern, t))
                for (led in 0 until 8) worst = max(worst, maxError(colorAt(effect.leds[led], t, effect.linear), expected[led]))
            }
            assertTrue("$pattern worst error $worst", worst <= 16)
        }
    }

    @Test
    fun beaconIsAStrobeSoItGoesOutAsExactSteps() {
        val effect = patternEffect(PatternMode.BEACON)
        assertFalse(effect.linear)
        effect.leds.forEach { assertTrue("${it.colors.size} keyframes", it.colors.size <= 6) }
    }

    @Test
    fun cometIsOneLedCopiedRoundTheRingInEvenSteps() {
        val effect = patternEffect(PatternMode.COMET, periodMs = 33L)
        val loop = effect.durationMs
        assertEquals("loop fits whole frames per LED", 0L, loop % (33L * 8))
        // Every LED shows LED 0's colour one eighth of a turn later per place round the ring.
        for (t in 0L until loop step 11L) {
            val lead = colorAt(effect.leds[0], t)
            for (led in 1 until 8) {
                val later = colorAt(effect.leds[led], (t + led * loop / 8) % loop)
                assertTrue("LED $led at $t", maxError(lead, later) <= 2)
            }
        }
        effect.leds.forEach { assertTrue(it.colors.size <= RingEffect.MAX_KEYFRAMES) }
    }

    @Test
    fun allLedsLastOneLoopSoTheyStayInStep() {
        val effect = patternEffect(PatternMode.COMET)
        effect.leds.forEach { assertEquals(800L, it.durationMs) }
    }

    @Test
    fun delaysAreWholeHardwareFrames() {
        val effect = patternEffect(PatternMode.PULSE, periodMs = 33L)
        effect.leds.forEach { led -> led.delaysMs.drop(1).forEach { assertEquals("delay $it", 0L, it % 33L) } }
        effect.leds.forEach { assertEquals(0L, it.delaysMs[0]) }
    }

    @Test
    fun solidIsStaticSoItGoesOutAsOneFrame() {
        assertTrue(patternEffect(PatternMode.SOLID).isStatic)
        assertFalse(patternEffect(PatternMode.BREATHE).isStatic)
    }

    @Test
    fun loopEndsOnItsOpeningColoursSoItRestartsWithoutAJump() {
        val effect = patternEffect(PatternMode.RAINBOW)
        effect.leds.forEach { assertEquals(it.colors.first(), it.colors.last()) }
    }

    @Test
    fun splitRotateUsesHardStepsOnlyWhereTheLayoutMoves() {
        val colors = longArrayOf(0xFFFF0000L, 0xFF00FF00L)
        val loop = PatternRenderer.splitLoopMs(SplitAnimation.ROTATE, colors.size, 8)
        val effect = RingEffect.sample(loop, 10L, 8) { t ->
            renderer.renderSplitFrame(colors, SplitLook(), t, 8, SplitAnimation.ROTATE)
        }
        assertFalse(effect.linear)
        effect.leds.forEach { led ->
            var t = 0L
            led.delaysMs.forEach { t += it; assertEquals("keyframe at $t", 0L, t % 800L) }
        }
    }

    @Test
    fun everyPatternAtEverySpeedFitsTheHardwareBudget() {
        // Over budget the lights HAL aborts, and speed changes how much happens per frame.
        for (pattern in PatternMode.entries.filter { it.moves }) {
            for (speed in listOf(MIN_SPEED, 0.75f, 1.25f, 1.5f, MAX_SPEED)) {
                val loop = pattern.speedMs(speed)
                val effect = RingEffect.sample(loop, 33L, 8) { t ->
                    renderer.renderFrame(pattern.id, 0xFF4285F4L, 1f, loop, t)
                }
                assertTrue("$pattern x$speed", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
            }
        }
    }

    @Test
    fun everySplitBrightnessChoiceFitsTheHardwareBudget() {
        // The sliders reach any level from 10% to 100%, and over budget the lights HAL aborts.
        val palette = longArrayOf(0xFFFF0000L, 0xFF00FF00L, 0xFF0000FFL, 0xFFFFAA00L)
        val levels = listOf(0.1f, 0.25f, 0.45f, 0.7f, 1f)
        for (animation in SplitAnimation.entries) {
            for (arcs in 2..4) {
                for (dimmest in levels) for (brightest in levels) for (steady in levels) {
                    if (brightest < dimmest) continue
                    val look = SplitLook(dimmest, brightest, steady)
                    val effect = RingEffect.sample(PatternRenderer.splitLoopMs(animation, arcs, 8), 33L, 8) { t ->
                        renderer.renderSplitFrame(palette.copyOf(arcs), look, t, 8, animation)
                    }
                    assertTrue("$animation x$arcs $look", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
                }
            }
        }
    }

    @Test
    fun everySplitAndBatteryLookFitsTheHardwareBudget() {
        // Over budget doesn't fail gracefully: the lights HAL aborts. So check every look.
        val palette = longArrayOf(0xFFFF0000L, 0xFF00FF00L, 0xFF0000FFL, 0xFFFFAA00L)
        for (animation in SplitAnimation.entries) {
            for (arcs in 2..4) {
                val colors = palette.copyOf(arcs)
                val effect = RingEffect.sample(PatternRenderer.splitLoopMs(animation, arcs, 8), 33L, 8) { t ->
                    renderer.renderSplitFrame(colors, SplitLook(), t, 8, animation)
                }
                assertTrue("$animation x$arcs", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
            }
        }
        for (pattern in BatteryPattern.entries) {
            for (lowPattern in LowBatteryPattern.entries) {
                for ((charging, full, low) in listOf(Triple(true, false, false), Triple(false, true, false), Triple(false, false, true))) {
                    val loop = PatternRenderer.batteryLoopMs(pattern, charging, full, low, lowPattern)
                    val effect = RingEffect.sample(loop, 33L, 8) { t ->
                        renderer.renderBatteryFrame(pattern, 40, charging, full, low, true, 0xFF34A853L, 1f, t, lowPattern)
                    }
                    assertTrue("$pattern $lowPattern $charging/$full/$low", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
                }
            }
        }
    }
}
