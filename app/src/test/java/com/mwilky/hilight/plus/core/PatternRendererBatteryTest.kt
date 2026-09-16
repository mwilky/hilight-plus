package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.LowBatteryPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [PatternRenderer.renderBatteryFrame]: the gauge fill math, the full/low overrides
 * that always win regardless of the chosen charging pattern, and the auto colour gradient.
 */
class PatternRendererBatteryTest {

    private val renderer = PatternRenderer()

    private fun litCount(frame: IntArray) = frame.count { (it ushr 24) and 0xFF > 0 && (it and 0x00FFFFFF) != 0 }

    @Test
    fun zeroPercentLightsNothing() {
        val frame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE,
            levelPercent = 0,
            charging = true,
            full = false,
            low = false,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        assertEquals(0, litCount(frame))
    }

    @Test
    fun hundredPercentLightsEveryLed() {
        val frame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE,
            levelPercent = 100,
            charging = true,
            full = false,
            low = false,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        assertEquals(8, litCount(frame))
    }

    @Test
    fun sixtyTwoPercentSnapsToTheNearestWholeLedWithNoPartialBrightness() {
        // 62% of 8 LEDs = 4.96 -> rounds to 5 whole LEDs, none of them partial: a dim boundary
        // LED next to a full-brightness one just reads as bleed on the diffused ring, not a edge.
        val frame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE,
            levelPercent = 62,
            charging = false,
            full = false,
            low = false,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        assertEquals(5, litCount(frame))
        // Every lit LED is at full brightness (auto colour at s=1,v=1 always peaks one channel at 255) -
        // no dim partial LED at the boundary.
        for (i in 0 until 5) {
            val c = frame[i]
            val maxChannel = maxOf((c ushr 16) and 0xFF, (c ushr 8) and 0xFF, c and 0xFF)
            assertEquals(255, maxChannel)
        }
    }

    @Test
    fun fullOverridesTheChosenChargingPatternWithAllLedsLit() {
        val frame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE, // would normally only light 1-2 whole LEDs at 20%
            levelPercent = 20,
            charging = true,
            full = true,
            low = false,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        assertEquals(8, litCount(frame))
    }

    @Test
    fun lowAndUnpluggedShowsANonEmptyHeartbeat() {
        val frame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.CHARGE_FILL,
            levelPercent = 12,
            charging = false,
            full = false,
            low = true,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        assertTrue(litCount(frame) > 0)
    }

    @Test
    fun solidLowPatternIsStaticAndLightsTheRemainingLeds() {
        fun frameAt(elapsed: Long) = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE, levelPercent = 25, charging = false, full = false, low = true,
            autoColor = true, fixedColor = 0, brightness = 1f, elapsedTimeMs = elapsed,
            lowPattern = LowBatteryPattern.SOLID
        )
        // 25% of 8 LEDs = 2, lit identically whenever it's sampled.
        assertEquals(2, litCount(frameAt(0L)))
        assertTrue(frameAt(0L).contentEquals(frameAt(777L)))
    }

    @Test
    fun breatheLowPatternIsNeverFullyDark() {
        for (elapsed in 0L..2200L step 100L) {
            val frame = renderer.renderBatteryFrame(
                pattern = BatteryPattern.GAUGE, levelPercent = 12, charging = false, full = false, low = true,
                autoColor = true, fixedColor = 0, brightness = 1f, elapsedTimeMs = elapsed,
                lowPattern = LowBatteryPattern.BREATHE
            )
            assertTrue("dark at $elapsed", litCount(frame) >= 1)
        }
    }

    @Test
    fun lowIsIgnoredWhileCharging() {
        // Charging always takes priority over the low-battery heartbeat, even below the threshold.
        val chargingFrame = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GAUGE,
            levelPercent = 10,
            charging = true,
            full = false,
            low = true,
            autoColor = true,
            fixedColor = 0xFF34A853,
            brightness = 1f,
            elapsedTimeMs = 0L
        )
        // At 10% with GAUGE, 0.8 LEDs rounds to 1 whole LED lit, not the full-ring heartbeat treatment.
        assertEquals(1, litCount(chargingFrame))
    }

    @Test
    fun autoColorGoesFromRedAtZeroToGreenAtFull() {
        // Compare channel dominance rather than absolute brightness, since the gradient ring
        // breathes while charging and elapsedTimeMs=0 isn't necessarily its peak brightness.
        fun dominantChannelIsGreater(frame: IntArray, channelShift: Int, otherShift: Int): Boolean {
            val c = frame.first { (it ushr 24) and 0xFF > 0 }
            val channel = (c ushr channelShift) and 0xFF
            val other = (c ushr otherShift) and 0xFF
            return channel > other
        }

        val red = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GRADIENT_RING, levelPercent = 0, charging = true, full = false, low = false,
            autoColor = true, fixedColor = 0, brightness = 1f, elapsedTimeMs = 0L
        )
        val green = renderer.renderBatteryFrame(
            pattern = BatteryPattern.GRADIENT_RING, levelPercent = 100, charging = true, full = false, low = false,
            autoColor = true, fixedColor = 0, brightness = 1f, elapsedTimeMs = 0L
        )
        // Red dominates at 0%: red channel (shift 16) outweighs green (shift 8).
        assertTrue(dominantChannelIsGreater(red, 16, 8))
        // Green dominates at 100%: green channel outweighs red.
        assertTrue(dominantChannelIsGreater(green, 8, 16))
    }
}
