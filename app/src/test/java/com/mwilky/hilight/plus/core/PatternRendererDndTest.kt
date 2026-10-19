package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.PatternMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Do Not Disturb looks and the flash played as it turns on and off. */
class PatternRendererDndTest {

    private val renderer = PatternRenderer()
    private val color = 0xFF8A2BE2L

    private fun frame(pattern: String, t: Long, speedMs: Long) =
        renderer.renderFrame(pattern, color, 1f, speedMs, t)

    private fun frame(pattern: PatternMode, t: Long) = frame(pattern.id, t, pattern.speedMs())

    private fun lit(frame: IntArray): Set<Int> = frame.indices.filter { frame[it] and 0xFFFFFF != 0 }.toSet()

    private fun level(c: Int): Int = maxOf((c ushr 16) and 0xFF, (c ushr 8) and 0xFF, c and 0xFF)

    @Test
    fun dndSignLightsOnlyTheThreeAndNineOClockLeds() {
        for (t in listOf(0L, 500L, 5_000L)) assertEquals(setOf(2, 6), lit(frame(PatternMode.DND_MINUS, t)))
    }

    @Test
    fun crescentIsBrightestOnTheLeftAndDarkOnTheRight() {
        val f = frame(PatternMode.DND_CRESCENT, 0)
        assertTrue(level(f[6]) > level(f[5]))
        assertEquals(level(f[5]), level(f[7]))
        assertEquals(0, level(f[2]))
    }

    @Test
    fun snoozeBlinksThreeTimesEachFainterThenRests() {
        val loop = PatternMode.DND_SNOOZE.speedMs()
        val peaks = listOf(0.05, 0.15, 0.25).map { level(frame(PatternMode.DND_SNOOZE, (it * loop).toLong())[0]) }
        assertTrue("$peaks", peaks[0] > peaks[1] && peaks[1] > peaks[2] && peaks[2] > 0)
        assertTrue(lit(frame(PatternMode.DND_SNOOZE, loop / 2)).isEmpty())
    }

    @Test
    fun glanceIsDarkExceptForItsOneGlowOnTheDndSign() {
        val loop = PatternMode.DND_GLANCE.speedMs()
        assertTrue(lit(frame(PatternMode.DND_GLANCE, 0)).isEmpty())
        assertEquals(setOf(2, 6), lit(frame(PatternMode.DND_GLANCE, (0.06 * loop).toLong())))
        assertTrue(lit(frame(PatternMode.DND_GLANCE, loop / 2)).isEmpty())
    }

    @Test
    fun driftLightsOneLedAtATimeAndHandsOverToTheNext() {
        val loop = PatternMode.DND_DRIFT.speedMs()
        assertEquals(setOf(0), lit(frame(PatternMode.DND_DRIFT, 0)))
        assertEquals(setOf(3), lit(frame(PatternMode.DND_DRIFT, 3 * loop / 8)))
        assertEquals(setOf(3, 4), lit(frame(PatternMode.DND_DRIFT, 3 * loop / 8 + loop / 16)))
    }

    @Test
    fun sleepNeverQuiteGoesOut() {
        val loop = PatternMode.DND_SLEEP.speedMs()
        for (t in 0L until loop step 250L) assertEquals(8, lit(frame(PatternMode.DND_SLEEP, t)).size)
    }

    @Test
    fun theStillLooksTakeNoSpeed() {
        assertTrue(!PatternMode.DND_MINUS.moves && !PatternMode.DND_CRESCENT.moves)
        assertTrue(PatternMode.DND_SLEEP.moves && PatternMode.DND_DRIFT.moves)
    }

    @Test
    fun flashOnSweepsClockwiseFromTheTopThenEndsDark() {
        val ms = PatternRenderer.DND_FLASH_MS
        val on = PatternRenderer.DND_FLASH_ON
        assertTrue(lit(frame(on, 0, ms)).isEmpty())
        // A quarter of the way through, only the first LEDs clockwise from the top are lit.
        val early = lit(frame(on, ms / 4, ms))
        assertTrue("$early", 0 in early && 1 in early && 7 !in early)
        assertEquals(8, lit(frame(on, ms * 6 / 10, ms)).size)
        assertTrue(lit(frame(on, ms, ms)).isEmpty())
    }

    @Test
    fun flashOffEmptiesAnticlockwiseFromTheTopThenEndsDark() {
        val ms = PatternRenderer.DND_FLASH_MS
        val off = PatternRenderer.DND_FLASH_OFF
        assertEquals(8, lit(frame(off, ms / 4, ms)).size)
        val draining = lit(frame(off, ms / 2, ms))
        assertTrue("$draining", 0 !in draining && 7 !in draining && 1 in draining)
        assertTrue(lit(frame(off, ms, ms)).isEmpty())
    }

    @Test
    fun bothFlashesFitTheHardwareBudget() {
        // Over budget the lights HAL aborts, so check the flash as the engine sends it.
        for (pattern in listOf(PatternRenderer.DND_FLASH_ON, PatternRenderer.DND_FLASH_OFF)) {
            for (period in listOf(10L, 33L)) {
                val effect = RingEffect.sample(PatternRenderer.DND_FLASH_MS, period, 8) { t ->
                    renderer.renderFrame(pattern, color, 1f, PatternRenderer.DND_FLASH_MS, t)
                }
                assertTrue("$pattern at $period", effect.leds.all { it.colors.size <= RingEffect.MAX_KEYFRAMES })
                // Played once and left as it ends, so it has to end dark.
                effect.leds.forEach { assertEquals(0, it.colors.last() and 0xFFFFFF) }
            }
        }
    }
}
