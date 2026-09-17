package com.mwilky.hilight.plus.core

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
        val frame = renderer.renderSplitFrame(longArrayOf(red, green), brightness = 1f, elapsedTimeMs = 1200L)
        assertEquals(0, frame[2])
        assertEquals(0, frame[3])
        assertEquals(0, frame[6])
        assertEquals(0, frame[7])
        assertTrue(isShadeOf(frame[0], red))
        assertTrue(isShadeOf(frame[4], green))
    }

    @Test
    fun onlyTheNewestFourColoursAreShown() {
        val frame = renderer.renderSplitFrame(longArrayOf(red, green, blue, white, amber), brightness = 1f, elapsedTimeMs = 1200L)
        assertTrue(isShadeOf(frame[0], red))
        assertTrue(isShadeOf(frame[2], green))
        assertTrue(isShadeOf(frame[4], blue))
        assertTrue(isShadeOf(frame[6], white))
    }

    @Test
    fun breathingNeverDropsAnArcLowEnoughToReadAsBleed() {
        var minChannel = 255
        for (t in 0L until 2400L step 40L) {
            val frame = renderer.renderSplitFrame(longArrayOf(white, white), brightness = 1f, elapsedTimeMs = t)
            minChannel = minOf(minChannel, frame[0] and 0xFF)
        }
        assertTrue("dimmest point was $minChannel/255", minChannel >= (0.45 * 255).toInt() - 1)
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
