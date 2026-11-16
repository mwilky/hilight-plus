package com.mwilky.hilight.plus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownRingTest {

    private val green = 0xFF00FF00.toInt()
    private val amber = 0xFFFFAA00.toInt()
    private val red = 0xFFFF0000.toInt()

    private val minute = 60_000L
    private val total = 40 * minute

    private fun lit(frame: IntArray) = frame.count { it != 0 }

    @Test
    fun `starts full and green`() {
        val frame = CountdownRing.frame(total, total, 8)
        assertEquals(8, lit(frame))
        assertTrue(frame.all { it == green })
    }

    @Test
    fun `loses a whole led for each eighth, from the far end`() {
        // Just into the second eighth: seven left, and it is the last LED that went out.
        val frame = CountdownRing.frame(total, 35 * minute, 8)
        assertEquals(7, lit(frame))
        assertEquals(0, frame[7])
        assertEquals(8, lit(CountdownRing.frame(total, 35 * minute + 1, 8)))
    }

    @Test
    fun `turns amber from halfway`() {
        assertEquals(green, CountdownRing.frame(total, 20 * minute + 1, 8)[0])
        val half = CountdownRing.frame(total, 20 * minute, 8)
        assertEquals(amber, half[0])
        assertEquals(4, lit(half))
    }

    @Test
    fun `last eighth is a full red lap that drains again`() {
        assertEquals(2, lit(CountdownRing.frame(total, 5 * minute + 1, 8)))
        val lapStart = CountdownRing.frame(total, 5 * minute, 8)
        assertEquals(8, lit(lapStart))
        assertTrue(lapStart.all { it == red })
        // The lap is 5 minutes, so each LED is 37.5 seconds.
        assertEquals(7, lit(CountdownRing.frame(total, 5 * minute - 37_500, 8)))
        assertEquals(1, lit(CountdownRing.frame(total, 1, 8)))
    }

    @Test
    fun `a short timer has no lap and ends on one red led`() {
        val short = 60_000L
        assertEquals(0L, CountdownRing.lapMs(short, 8))
        val last = CountdownRing.frame(short, 7_000, 8)
        assertEquals(1, lit(last))
        assertEquals(red, last[0])
        assertEquals(amber, CountdownRing.frame(short, 8_000, 8)[0])
    }

    @Test
    fun `dark once it has run out`() {
        assertEquals(0, lit(CountdownRing.frame(total, 0, 8)))
        assertEquals(0, lit(CountdownRing.frame(total, -5_000, 8)))
    }

    @Test
    fun `changes ahead are soonest first and match where the frame changes`() {
        val changes = CountdownRing.changesAhead(total, total, 8)
        // Seven main steps (halfway is one of them) and seven inside the lap.
        assertEquals(14, changes.size)
        assertEquals(changes.sortedDescending(), changes)
        changes.forEach { left ->
            assertFalse(
                "frame should change at $left",
                CountdownRing.frame(total, left, 8).contentEquals(CountdownRing.frame(total, left + 1, 8))
            )
        }
        assertEquals(listOf(35 * minute), CountdownRing.changesAhead(total, 36 * minute, 8).take(1))
        assertTrue(CountdownRing.changesAhead(total, 1_000, 8).isEmpty())
    }

    /** The colour the hardware holds at [t] for a stepped sequence. */
    private fun colorAt(seq: LedSequence, t: Long): Int {
        var at = 0L
        var color = seq.colors[0]
        for (i in 1 until seq.colors.size) {
            at += seq.delaysMs[i]
            if (t < at) return color
            color = seq.colors[i]
        }
        return color
    }

    /** What the hardware may be given: steps on whole frames, in budget, no hold too long, LEDs in step. */
    private fun assertHardwareSafe(effect: RingEffect, period: Long) {
        assertFalse("hard steps, not fades", effect.linear)
        assertEquals(8, effect.leds.size)
        effect.leds.forEach { led ->
            assertTrue("${led.colors.size} keyframes", led.colors.size <= RingEffect.MAX_KEYFRAMES)
            assertEquals(effect.durationMs, led.durationMs)
            assertEquals(0L, led.delaysMs[0])
            led.delaysMs.drop(1).forEach {
                assertTrue("delay $it", it in period..CountdownRing.MAX_HOLD_MS)
                assertEquals(0L, it % period)
            }
        }
    }

    /** A second into each stretch between changes, before [untilMs], the effect shows what the frame would. */
    private fun assertShowsCountdown(effect: RingEffect, totalMs: Long, remaining: Long, untilMs: Long) {
        val checks = listOf(0L) + CountdownRing.changesAhead(totalMs, remaining, 8).map { remaining - it }
        checks.map { it + 1_000 }.filter { it < untilMs }.forEach { t ->
            val expected = CountdownRing.frame(totalMs, remaining - t, 8)
            for (led in 0 until 8) {
                assertEquals("led $led at ${t}ms", expected[led] and 0xFFFFFF, colorAt(effect.leds[led], t) and 0xFFFFFF)
            }
        }
    }

    @Test
    fun `a five minute timer goes out whole, with no hold over a minute`() {
        // The timer from the Pixel log: 5 minutes, sent with 296.9 seconds left.
        val five = 298_755L
        val remaining = 296_868L
        val effect = CountdownRing.effect(five, remaining, 33L, 8)
        assertHardwareSafe(effect, 33L)
        assertTrue(kotlin.math.abs(effect.durationMs - remaining) <= 33L)
        assertShowsCountdown(effect, five, remaining, effect.durationMs)
        // Green until halfway: no amber before then, which is what went wrong on the ring.
        assertEquals(green, colorAt(effect.leds[0], remaining - five / 2 - 2_000) or 0xFF000000.toInt())
        effect.leds.forEach { assertEquals(0, it.colors.last() and 0xFFFFFF) }
    }

    @Test
    fun `a long timer goes out in parts that stop on the colours showing`() {
        val remaining = 36 * minute
        val effect = CountdownRing.effect(total, remaining, 33L, 8)
        assertHardwareSafe(effect, 33L)
        assertTrue("stops short of ${remaining}ms: ${effect.durationMs}ms", effect.durationMs < remaining)
        assertTrue("covers several minutes: ${effect.durationMs}ms", effect.durationMs >= 6 * minute)
        assertShowsCountdown(effect, total, remaining, effect.durationMs)
        // Not dark at the stop: each LED ends on the colour it was showing.
        val atStop = CountdownRing.frame(total, remaining - effect.durationMs + 1, 8)
        for (led in 0 until 8) assertEquals(atStop[led] and 0xFFFFFF, effect.leds[led].colors.last() and 0xFFFFFF)
    }

    @Test
    fun `effect is hardware safe from any point in any timer`() {
        for (length in listOf(30_000L, 3 * minute, 5 * minute, total, 99 * 60 * minute)) {
            for (sixtyFourths in 1..64) {
                val remaining = length * sixtyFourths / 64
                val effect = CountdownRing.effect(length, remaining, 33L, 8)
                assertHardwareSafe(effect, 33L)
                assertShowsCountdown(effect, length, remaining, effect.durationMs)
            }
        }
    }
}
