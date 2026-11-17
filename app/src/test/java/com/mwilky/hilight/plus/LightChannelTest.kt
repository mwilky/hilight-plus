package com.mwilky.hilight.plus

import com.mwilky.hilight.plus.core.LightChannel
import com.mwilky.hilight.plus.core.LightChannel.BATTERY
import com.mwilky.hilight.plus.core.LightChannel.CALLS
import com.mwilky.hilight.plus.core.LightChannel.DND
import com.mwilky.hilight.plus.core.LightChannel.GEMINI
import com.mwilky.hilight.plus.core.LightChannel.NOTIFICATIONS
import com.mwilky.hilight.plus.core.LightChannel.PROGRESS
import org.junit.Assert.assertEquals
import org.junit.Test

class LightChannelTest {
    @Test
    fun encodeDecodeRoundTrips() {
        val order = listOf(BATTERY, CALLS, DND, GEMINI, PROGRESS, NOTIFICATIONS)
        assertEquals(order, LightChannel.decode(LightChannel.encode(order)))
    }

    @Test
    fun blankDecodesToDefault() {
        assertEquals(LightChannel.DEFAULT_ORDER, LightChannel.decode(null))
        assertEquals(LightChannel.DEFAULT_ORDER, LightChannel.decode(""))
    }

    @Test
    fun unknownIdsAndRepeatsAreDropped() {
        assertEquals(
            listOf(BATTERY, CALLS, GEMINI, NOTIFICATIONS, PROGRESS, DND),
            LightChannel.decode("battery,lasers,calls,battery,gemini,notifications,progress,dnd")
        )
    }

    @Test
    fun missingChannelGoesAfterItsDefaultPredecessor() {
        // Progress missing: it comes after notifications by default.
        assertEquals(
            listOf(BATTERY, NOTIFICATIONS, PROGRESS, CALLS, GEMINI, DND),
            LightChannel.decode("battery,notifications,calls,gemini,dnd")
        )
        // Calls missing: nothing comes before it by default, so it goes first.
        assertEquals(
            listOf(CALLS, DND, BATTERY, PROGRESS, NOTIFICATIONS, GEMINI),
            LightChannel.decode("dnd,battery,progress,notifications,gemini")
        )
    }

    @Test
    fun legacyTogglesKeepTheirOrder() {
        assertEquals(LightChannel.DEFAULT_ORDER, LightChannel.fromLegacy(batteryOverNotifications = false, dndOverBattery = false))
        assertEquals(
            listOf(CALLS, GEMINI, NOTIFICATIONS, PROGRESS, DND, BATTERY),
            LightChannel.fromLegacy(batteryOverNotifications = false, dndOverBattery = true)
        )
        assertEquals(
            listOf(CALLS, GEMINI, PROGRESS, BATTERY, NOTIFICATIONS, DND),
            LightChannel.fromLegacy(batteryOverNotifications = true, dndOverBattery = false)
        )
        assertEquals(
            listOf(CALLS, GEMINI, PROGRESS, DND, BATTERY, NOTIFICATIONS),
            LightChannel.fromLegacy(batteryOverNotifications = true, dndOverBattery = true)
        )
    }

    @Test
    fun everyOrderHasEveryChannelOnce() {
        listOf(true, false).forEach { a ->
            listOf(true, false).forEach { b ->
                assertEquals(LightChannel.entries.toSet(), LightChannel.fromLegacy(a, b).toSet())
                assertEquals(LightChannel.entries.size, LightChannel.fromLegacy(a, b).size)
            }
        }
    }
}
