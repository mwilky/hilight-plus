package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.SplitAnimation
import com.mwilky.hilight.plus.SplitLook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the split-ring layer: arc layout on the 8-LED ring, the one-LED gap that keeps
 * neighbouring colours apart on the diffused surface, the four-arc cap, and the brightness floor.
 */
class PatternRendererSplitTest {

    private val renderer = PatternRenderer()

    private val red = 0xFFFF0000L
    private val green = 0xFF00FF00L
    private val blue = 0xFF0000FFL
    private val white = 0xFFFFFFFFL
    private val amber = 0xFFFFAA00L

    @Test
    fun twoArcsAreTwoLedsEachWithTwoDarkLedsBetween() {
        assertArrayEquals(intArrayOf(0, 0, -1, -1, 1, 1, -1, -1), PatternRenderer.splitLayout(2, 8))
    }

    @Test
    fun threeArcsFillThreeOfTheFourQuarterSlotsAndLeaveTheFourthDark() {
        assertArrayEquals(intArrayOf(0, -1, 1, -1, 2, -1, -1, -1), PatternRenderer.splitLayout(3, 8))
    }

    @Test
    fun arcsAreAlwaysTheSameSizeAsEachOther() {
        for (segments in 2..4) {
            val layout = PatternRenderer.splitLayout(segments, 8)
            val sizes = (0 until segments).map { seg -> layout.count { it == seg } }
            assertEquals("arcs $segments: sizes $sizes", 1, sizes.toSet().size)
        }
    }

    @Test
    fun fourArcsAreSingleLedsEverySecondPosition() {
        assertArrayEquals(intArrayOf(0, -1, 1, -1, 2, -1, 3, -1), PatternRenderer.splitLayout(4, 8))
    }

    @Test
    fun moreThanFourArcsAreCappedAtFour() {
        assertArrayEquals(PatternRenderer.splitLayout(4, 8), PatternRenderer.splitLayout(7, 8))
    }

    @Test
    fun everyArcIsSeparatedFromTheNextByADarkLed() {
        for (segments in 2..4) {
            val layout = PatternRenderer.splitLayout(segments, 8)
            for (i in layout.indices) {
                val next = layout[(i + 1) % layout.size]
                val bothLit = layout[i] >= 0 && next >= 0
                assertTrue("arcs $segments: LEDs $i and ${(i + 1) % 8} touch", !bothLit || layout[i] == next)
            }
        }
    }

    @Test
    fun frameUsesEachArcsOwnColourAndLeavesGapsDark() {
        val frame = renderer.renderSplitFrame(longArrayOf(red, green), look = SplitLook(), elapsedTimeMs = 1200L)
        assertEquals(0, frame[2])
        assertEquals(0, frame[3])
        assertEquals(0, frame[6])
        assertEquals(0, frame[7])
        assertTrue(isShadeOf(frame[0], red))
        assertTrue(isShadeOf(frame[4], green))
    }

    @Test
    fun onlyTheNewestFourColoursAreShown() {
        val frame = renderer.renderSplitFrame(longArrayOf(red, green, blue, white, amber), look = SplitLook(), elapsedTimeMs = 1200L)
        assertTrue(isShadeOf(frame[0], red))
        assertTrue(isShadeOf(frame[2], green))
        assertTrue(isShadeOf(frame[4], blue))
        assertTrue(isShadeOf(frame[6], white))
    }

    @Test
    fun breathingNeverDropsAnArcLowEnoughToReadAsBleed() {
        var minChannel = 255
        for (t in 0L until 2400L step 40L) {
            val frame = renderer.renderSplitFrame(longArrayOf(white, white), look = SplitLook(), elapsedTimeMs = t)
            minChannel = minOf(minChannel, frame[0] and 0xFF)
        }
        assertTrue("dimmest point was $minChannel/255", minChannel >= (0.45 * 255).toInt() - 1)
    }

    @Test
    fun everyAnimationKeepsTheArcCountAndGapsOnEveryFrame() {
        for (animation in SplitAnimation.entries) {
            for (segments in 2..4) {
                val colors = LongArray(segments) { white }
                val litPerFrame = PatternRenderer.splitLayout(segments, 8).count { it >= 0 }
                for (t in 0L until 10_000L step 50L) {
                    val frame = renderer.renderSplitFrame(colors, look = SplitLook(), elapsedTimeMs = t, animation = animation)
                    assertEquals("$animation arcs $segments t=$t", litPerFrame, frame.count { it != 0 })
                    for (i in frame.indices) {
                        val next = (i + 1) % frame.size
                        if (segments > 2) {
                            assertTrue("$animation arcs $segments t=$t: LEDs $i and $next touch", frame[i] == 0 || frame[next] == 0)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun noAnimationDimsAnArcLowEnoughToReadAsBleed() {
        for (animation in SplitAnimation.entries) {
            for (t in 0L until 10_000L step 40L) {
                val frame = renderer.renderSplitFrame(longArrayOf(white, white, white), look = SplitLook(), elapsedTimeMs = t, animation = animation)
                frame.filter { it != 0 }.forEach {
                    assertTrue("$animation t=$t: ${it and 0xFF}/255", (it and 0xFF) >= (0.45 * 255).toInt() - 1)
                }
            }
        }
    }

    @Test
    fun breatheAndSpotlightMoveBetweenTheChosenDimmestAndBrightest() {
        val look = SplitLook(dimmest = 0.3f, brightest = 0.8f)
        for (animation in listOf(SplitAnimation.BREATHE, SplitAnimation.SPOTLIGHT)) {
            val levels = (0L until 10_000L step 20L).map {
                renderer.renderSplitFrame(longArrayOf(white, white), look, it, animation = animation)[0] and 0xFF
            }
            assertEquals("$animation dimmest", 0.3 * 255, levels.min().toDouble(), 1.0)
            assertEquals("$animation brightest", 0.8 * 255, levels.max().toDouble(), 1.0)
        }
    }

    @Test
    fun spotlightStillMovesWithItsBrightestAtFullPower() {
        val levels = (0L until 10_000L step 20L).map {
            renderer.renderSplitFrame(longArrayOf(white, white), SplitLook(brightest = 1f), it, animation = SplitAnimation.SPOTLIGHT)[0] and 0xFF
        }
        // The newest arc peaks halfway through its 900ms turn.
        val peak = renderer.renderSplitFrame(longArrayOf(white, white), SplitLook(brightest = 1f), 450L, animation = SplitAnimation.SPOTLIGHT)
        assertEquals(255, peak[0] and 0xFF)
        assertTrue("dimmest was ${levels.min()}", levels.min() < 128)
    }

    @Test
    fun solidAndRotateHoldTheSteadyLevel() {
        for (animation in listOf(SplitAnimation.SOLID, SplitAnimation.ROTATE)) {
            val frame = renderer.renderSplitFrame(longArrayOf(white, white), SplitLook(steady = 1f), 0L, animation = animation)
            assertEquals(animation.name, 255, frame[0] and 0xFF)
        }
    }

    @Test
    fun fullPowerKeepsTheColourInsteadOfWashingOut() {
        val googleBlue = 0xFF4285F4L
        val frame = renderer.renderSplitFrame(longArrayOf(googleBlue, googleBlue), SplitLook(steady = 1f), 0L, animation = SplitAnimation.SOLID)
        assertEquals(0x4285F4, frame[0] and 0xFFFFFF)
    }

    @Test
    fun twiceTheSpeedPlaysTheSameFramesInHalfTheTime() {
        val colors = longArrayOf(white, red, green)
        for (animation in SplitAnimation.entries) {
            for (t in 0L until 6_000L step 100L) {
                val normal = renderer.renderSplitFrame(colors, SplitLook(), t * 2, animation = animation)
                val fast = renderer.renderSplitFrame(colors, SplitLook(), t, animation = animation, speed = 2f)
                assertArrayEquals("$animation t=$t", normal, fast)
            }
        }
        assertEquals(1200L, PatternRenderer.splitLoopMs(SplitAnimation.BREATHE, 3, 8, 2f))
    }

    @Test
    fun aDimmestAboveTheBrightestIsTreatedAsOneLevel() {
        val look = SplitLook(dimmest = 0.9f, brightest = 0.4f).clamped()
        assertEquals(0.9f, look.dimmest)
        assertEquals(0.9f, look.brightest)
    }

    @Test
    fun solidIsSteadyAndBelowFullPower() {
        val first = renderer.renderSplitFrame(longArrayOf(white, white), SplitLook(), 0L, animation = SplitAnimation.SOLID)
        val later = renderer.renderSplitFrame(longArrayOf(white, white), SplitLook(), 3_700L, animation = SplitAnimation.SOLID)
        assertArrayEquals(first, later)
        assertTrue((first[0] and 0xFF) in 1 until 255)
    }

    @Test
    fun spotlightLightsTheNewestArcFirstThenMovesClockwise() {
        val colors = longArrayOf(white, white, white)
        val first = renderer.renderSplitFrame(colors, SplitLook(), 450L, animation = SplitAnimation.SPOTLIGHT)
        assertTrue((first[0] and 0xFF) > (first[2] and 0xFF))
        assertTrue((first[0] and 0xFF) > (first[4] and 0xFF))
        val second = renderer.renderSplitFrame(colors, SplitLook(), 900L + 450L, animation = SplitAnimation.SPOTLIGHT)
        assertTrue((second[2] and 0xFF) > (second[0] and 0xFF))
        assertTrue((second[2] and 0xFF) > (second[4] and 0xFF))
    }

    @Test
    fun rotateStartsWithTheNewestAtTheTopThenStepsClockwiseOneLedAtATime() {
        val colors = longArrayOf(red, green)
        val start = renderer.renderSplitFrame(colors, SplitLook(), 0L, animation = SplitAnimation.ROTATE)
        assertTrue(isShadeOf(start[0], red))
        assertEquals(0, start[7])
        val oneStep = renderer.renderSplitFrame(colors, SplitLook(), 800L, animation = SplitAnimation.ROTATE)
        assertEquals(0, oneStep[0])
        assertTrue(isShadeOf(oneStep[1], red))
        assertTrue(isShadeOf(oneStep[2], red))
        assertTrue(isShadeOf(oneStep[5], green))
    }

    private fun isShadeOf(actual: Int, base: Long): Boolean {
        val b = base.toInt()
        fun ch(c: Int, shift: Int) = (c ushr shift) and 0xFF
        return (0..2).all { i ->
            val shift = i * 8
            if (ch(b, shift) == 0) ch(actual, shift) == 0 else ch(actual, shift) > 0
        }
    }
}
