package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.QuietHoursMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRenderPolicyTest {

    @Test
    fun restrictedAlertStaysHiddenUntilFaceDown() {
        assertFalse(AlertRenderPolicy.canShowAlert(requiresFaceDown = true, deviceFaceDown = false))
        assertTrue(AlertRenderPolicy.canShowAlert(requiresFaceDown = true, deviceFaceDown = true))
        assertTrue(AlertRenderPolicy.canShowAlert(requiresFaceDown = false, deviceFaceDown = false))
    }

    @Test
    fun callHidesNotificationsIndependentlyOfOrientation() {
        assertFalse(
            AlertRenderPolicy.canShowNotification(
                callActive = true,
                requiresFaceDown = false,
                deviceFaceDown = true
            )
        )
        assertFalse(
            AlertRenderPolicy.canShowNotification(
                callActive = false,
                requiresFaceDown = true,
                deviceFaceDown = false
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowNotification(
                callActive = false,
                requiresFaceDown = false,
                deviceFaceDown = false
            )
        )
    }

    @Test
    fun dndHidesEvenWhenFaceDown() {
        assertFalse(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = true,
                deviceFaceDown = true,
                dndMode = DndMode.SKIP,
                dndActive = true
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = true,
                deviceFaceDown = true,
                dndMode = DndMode.SKIP,
                dndActive = false
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = false,
                deviceFaceDown = false,
                dndMode = DndMode.ALWAYS,
                dndActive = true
            )
        )
    }

    @Test
    fun inheritQuietHoursFollowTheLiveSwitchAndWindow() {
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = true,
                deviceFaceDown = true,
                quietHoursMode = QuietHoursMode.INHERIT,
                quietHoursEnabled = false,
                quietHoursStartMinutes = 9 * 60,
                quietHoursEndMinutes = 17 * 60,
                nowMinutes = 15 * 60
            )
        )
        assertFalse(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = true,
                deviceFaceDown = true,
                quietHoursMode = QuietHoursMode.INHERIT,
                quietHoursEnabled = true,
                quietHoursStartMinutes = 9 * 60,
                quietHoursEndMinutes = 17 * 60,
                nowMinutes = 15 * 60
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = true,
                deviceFaceDown = true,
                quietHoursMode = QuietHoursMode.INHERIT,
                quietHoursEnabled = true,
                quietHoursStartMinutes = 9 * 60,
                quietHoursEndMinutes = 17 * 60,
                nowMinutes = 18 * 60
            )
        )
    }

    @Test
    fun inheritDndFollowsTheLiveSwitch() {
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = false,
                deviceFaceDown = false,
                dndMode = DndMode.INHERIT,
                dndSuppressEnabled = false,
                dndActive = true
            )
        )
        assertFalse(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = false,
                deviceFaceDown = false,
                dndMode = DndMode.INHERIT,
                dndSuppressEnabled = true,
                dndActive = true
            )
        )
    }

    @Test
    fun quietHoursHideWhileInsideTheWindow() {
        assertFalse(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = false,
                deviceFaceDown = false,
                quietHoursMode = QuietHoursMode.SKIP,
                quietHoursStartMinutes = 22 * 60,
                quietHoursEndMinutes = 7 * 60,
                nowMinutes = 23 * 60
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowAlert(
                requiresFaceDown = false,
                deviceFaceDown = false,
                quietHoursMode = QuietHoursMode.SKIP,
                quietHoursStartMinutes = 22 * 60,
                quietHoursEndMinutes = 7 * 60,
                nowMinutes = 12 * 60
            )
        )
    }

    /** The DND layer one second after the app last reported DND, which turned on at [since]. */
    private fun dndLayer(
        now: Long = 100_000L,
        visible: Boolean = true,
        updatedAt: Long = now - 1_000L,
        dndActive: Boolean = true,
        since: Long = now - 30_000L,
        flashAt: Long? = null,
        showIndicator: Boolean = true,
        showForMs: Long? = null
    ) = AlertRenderPolicy.dndLayer(visible, updatedAt, dndActive, since, flashAt, showIndicator, showForMs, now)

    @Test
    fun dndIndicatorShowsWhileDndIsOn() {
        assertEquals(AlertRenderPolicy.DndLayer.LOOK, dndLayer())
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(dndActive = false))
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(showIndicator = false))
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(visible = false))
    }

    @Test
    fun dndIndicatorGoesDarkWhenTheAppStopsReporting() {
        // An app that died can't say DND ended, so a stale reading must never keep the ring lit.
        val now = 1_000_000L
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(now = now, updatedAt = now - AlertRenderPolicy.DND_STATE_STALE_MS - 1))
        assertEquals(AlertRenderPolicy.DndLayer.LOOK, dndLayer(now = now, updatedAt = now - AlertRenderPolicy.DND_STATE_STALE_MS))
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(updatedAt = 0L))
    }

    @Test
    fun dndIndicatorTimesOutAfterItsShowFor() {
        assertEquals(AlertRenderPolicy.DndLayer.LOOK, dndLayer(since = 100_000L - 9_999L, showForMs = 10_000L))
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(since = 100_000L - 10_000L, showForMs = 10_000L))
    }

    @Test
    fun dndFlashPlaysOutThenGivesWayEvenWithTheLookOff() {
        val now = 100_000L
        assertEquals(AlertRenderPolicy.DndLayer.FLASH, dndLayer(now = now, flashAt = now - 100L, showIndicator = false))
        // DND turning off flashes too.
        assertEquals(AlertRenderPolicy.DndLayer.FLASH, dndLayer(now = now, flashAt = now - 100L, dndActive = false))
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(now = now, flashAt = now - PatternRenderer.DND_FLASH_MS, dndActive = false))
        assertEquals(AlertRenderPolicy.DndLayer.LOOK, dndLayer(now = now, flashAt = now - PatternRenderer.DND_FLASH_MS))
        // Conditions that hide the look hide its flash.
        assertEquals(AlertRenderPolicy.DndLayer.NONE, dndLayer(now = now, flashAt = now - 100L, visible = false))
    }
}
