package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
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
}
