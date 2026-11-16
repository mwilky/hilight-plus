package com.mwilky.hilight.plus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressFillTest {

    private val blue = 0xFF4285F4L

    private fun lit(frame: IntArray) = frame.count { it and 0xFFFFFF != 0 }

    @Test
    fun `one LED from the start, a whole LED per eighth, all only when done`() {
        assertEquals(1, ProgressFill.litCount(0f, 8))
        assertEquals(1, ProgressFill.litCount(0.125f, 8))
        assertEquals(2, ProgressFill.litCount(0.13f, 8))
        assertEquals(4, ProgressFill.litCount(0.5f, 8))
        assertEquals(7, ProgressFill.litCount(0.87f, 8))
        assertEquals(8, ProgressFill.litCount(0.99f, 8))
        assertEquals(8, ProgressFill.litCount(1f, 8))
        assertEquals(1, ProgressFill.litCount(Float.NaN, 8))
    }

    @Test
    fun `fills clockwise from the top in the rule's colour`() {
        val frame = ProgressFill.frame(0.4f, blue, 1f, 0L, 8)
        assertEquals(4, lit(frame))
        assertTrue((0 until 4).all { frame[it] and 0xFFFFFF != 0 })
        assertTrue((4 until 8).all { frame[it] and 0xFFFFFF == 0 })
        // At the bottom of its breath the arc is still the same hue, a little dimmer.
        assertEquals((0x42 * 0.8).toInt(), (frame[0] ushr 16) and 0xFF)
    }

    @Test
    fun `breathes shallowly, never dark`() {
        val levels = (0L until ProgressFill.LOOP_MS step 50).map { t -> ProgressFill.frame(0.5f, blue, 1f, t, 8)[0] and 0xFF }
        assertEquals(0xF4, levels.max())
        assertTrue("dimmest ${levels.min()}", levels.min() >= (0xF4 * 0.79).toInt())
    }

    @Test
    fun `goes out as a hardware effect within the keyframe budget`() {
        val effect = RingEffect.sample(ProgressFill.LOOP_MS, 33L, 8) { t -> ProgressFill.frame(0.6f, blue, 0.8f, t, 8) }
        assertTrue(effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
        assertEquals(8, effect.leds.size)
    }
}
