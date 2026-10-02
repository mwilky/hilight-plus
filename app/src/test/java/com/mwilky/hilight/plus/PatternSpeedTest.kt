package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternSpeedTest {

    @Test
    fun hardwareAndPreviewShareOneTable() {
        assertEquals(2000L, PatternMode.BREATHE.speedMs())
        assertEquals(1200L, PatternMode.WAVE.speedMs())
        assertEquals(800L, PatternMode.COMET.speedMs())
        assertEquals(850L, PatternMode.PULSE.speedMs())
        assertEquals(1000L, PatternMode.SOLID.speedMs())
        assertEquals(400L, PatternMode.OFF.speedMs(400L))
        assertEquals(3200L, PatternMode.GEMINI_LISTENING.speedMs())
        assertEquals(800L, PatternMode.GEMINI_THINKING.speedMs())
        assertEquals(1300L, PatternMode.GEMINI_REPLYING.speedMs())
    }

    @Test
    fun speedScalesThePatternsOwnPace() {
        assertEquals(1000L, PatternMode.BREATHE.speedMs(2f))
        assertEquals(4000L, PatternMode.BREATHE.speedMs(0.5f))
        assertEquals(2000L, PatternMode.BREATHE.speedMs(DEFAULT_SPEED))
        assertEquals(680L, PatternMode.PULSE.speedMs(1.25f))
    }

    @Test
    fun speedIsHeldToTheSlidersRange() {
        assertEquals(PatternMode.BREATHE.speedMs(MAX_SPEED), PatternMode.BREATHE.speedMs(10f))
        assertEquals(PatternMode.BREATHE.speedMs(MIN_SPEED), PatternMode.BREATHE.speedMs(0f))
        assertEquals(DEFAULT_SPEED, clampSpeed(Float.NaN))
    }

    @Test
    fun onlyPatternsThatChangeTakeASpeed() {
        assertFalse(PatternMode.SOLID.moves)
        assertFalse(PatternMode.OFF.moves)
        assertTrue(PatternMode.SPARKLE.moves)
    }
}
