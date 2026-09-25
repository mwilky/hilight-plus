package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.PatternMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Gemini patterns replay keyframes read back from the stock effect, so these pin the frames
 * at the keyframe times to the captured colours.
 */
class PatternRendererGeminiTest {

    private val renderer = PatternRenderer()

    private fun frame(pattern: PatternMode, elapsedMs: Long) = renderer.renderFrame(
        pattern = pattern.id,
        colorLong = 0xFFFF0000L,
        brightness = 1f,
        speedMs = pattern.speedMs(),
        elapsedTimeMs = elapsedMs
    )

    private fun opaque(vararg colors: Int) = IntArray(colors.size) { colors[it] or 0xFF000000.toInt() }

    @Test
    fun listeningStartsOnTheCapturedFirstStep() {
        assertArrayEquals(
            opaque(0x0099FF, 0x00FFFF, 0xC7FCFF, 0xC8FCFF, 0x0099FF, 0x0000FF, 0x0000FF, 0x0000FF),
            frame(PatternMode.GEMINI_LISTENING, 0)
        )
    }

    @Test
    fun listeningStepsOneKeyframeEvery400Ms() {
        assertArrayEquals(
            opaque(0x0000FF, 0x00C8FF, 0x00FFFF, 0xC7FCFF, 0xC7FCFF, 0x0099FF, 0x0000FF, 0x0000FF),
            frame(PatternMode.GEMINI_LISTENING, 400)
        )
    }

    @Test
    fun thinkingIsTheSameCometFourTimesFaster() {
        assertArrayEquals(frame(PatternMode.GEMINI_THINKING, 0), frame(PatternMode.GEMINI_THINKING, 800))
        assertEquals(0xFF0000FF.toInt(), frame(PatternMode.GEMINI_THINKING, 100)[0])
    }

    @Test
    fun replyingIgnoresTheRuleColourAndGoesDarkTogether() {
        assertArrayEquals(
            opaque(0xFFFF00, 0xFF4600, 0x9600FF, 0x0000FF, 0x0000FF, 0x0000FF, 0x0000FF, 0x00FF00),
            frame(PatternMode.GEMINI_REPLYING, 0)
        )
        assertArrayEquals(IntArray(8), frame(PatternMode.GEMINI_REPLYING, 500))
    }

    @Test
    fun replyingMiddleLedsReturnFirst() {
        val f = frame(PatternMode.GEMINI_REPLYING, 1000)
        assertEquals(0xFF0000FF.toInt(), f[4])
        assertEquals(0, f[0])
    }
}
