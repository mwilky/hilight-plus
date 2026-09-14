package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.QuietHoursMode
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
}
