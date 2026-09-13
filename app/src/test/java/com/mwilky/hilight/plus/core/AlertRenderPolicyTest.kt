package com.mwilky.hilight.plus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun unlockPauseAndCallHideNotificationsIndependentlyOfOrientation() {
        assertFalse(
            AlertRenderPolicy.canShowNotification(
                unlockPaused = true,
                callActive = false,
                requiresFaceDown = false,
                deviceFaceDown = true
            )
        )
        assertFalse(
            AlertRenderPolicy.canShowNotification(
                unlockPaused = false,
                callActive = true,
                requiresFaceDown = false,
                deviceFaceDown = true
            )
        )
        assertFalse(
            AlertRenderPolicy.canShowNotification(
                unlockPaused = false,
                callActive = false,
                requiresFaceDown = true,
                deviceFaceDown = false
            )
        )
        assertTrue(
            AlertRenderPolicy.canShowNotification(
                unlockPaused = false,
                callActive = false,
                requiresFaceDown = false,
                deviceFaceDown = false
            )
        )
    }

    @Test
    fun cyclingSkipsRestrictedSlotsWhileFaceUp() {
        val flags = listOf(true, false, true)
        assertEquals(1, AlertRenderPolicy.firstEligibleIndex(flags, deviceFaceDown = false, startIndex = 0))
        assertEquals(0, AlertRenderPolicy.firstEligibleIndex(flags, deviceFaceDown = true, startIndex = 0))
        assertNull(AlertRenderPolicy.firstEligibleIndex(listOf(true, true), deviceFaceDown = false, startIndex = 0))
    }
}
