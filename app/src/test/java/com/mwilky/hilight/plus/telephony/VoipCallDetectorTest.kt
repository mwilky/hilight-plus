package com.mwilky.hilight.plus.telephony

import android.app.Notification
import com.mwilky.hilight.plus.telephony.VoipCallDetector.CallState
import org.junit.Assert.assertEquals
import org.junit.Test

class VoipCallDetectorTest {

    private fun state(category: String?, callType: Int?, fullScreen: Boolean = false, chrono: Boolean = false) =
        VoipCallDetector.callState(category, callType, fullScreen, chrono)

    @Test
    fun callStyleIncomingIsRinging() {
        assertEquals(CallState.RINGING, state(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_INCOMING))
    }

    @Test
    fun callStyleOngoingAndScreeningAreInProgress() {
        assertEquals(CallState.IN_PROGRESS, state(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_ONGOING, fullScreen = true))
        assertEquals(CallState.IN_PROGRESS, state(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_SCREENING, fullScreen = true))
    }

    @Test
    fun legacyCallWithFullScreenIntentIsRinging() {
        assertEquals(CallState.RINGING, state(Notification.CATEGORY_CALL, null, fullScreen = true))
    }

    @Test
    fun legacyCallWithChronometerIsInProgress() {
        assertEquals(CallState.IN_PROGRESS, state(Notification.CATEGORY_CALL, null, chrono = true))
    }

    @Test
    fun whatsAppSilentRepostIsUnclearNotAnswered() {
        // WhatsApp re-posts its ringing notification like this ~0.5s in, while still ringing.
        assertEquals(CallState.UNCLEAR, state(Notification.CATEGORY_CALL, null))
    }

    @Test
    fun otherCategoriesAreNeverCalls() {
        assertEquals(CallState.NOT_A_CALL, state(Notification.CATEGORY_MESSAGE, Notification.CallStyle.CALL_TYPE_INCOMING, fullScreen = true))
        assertEquals(CallState.NOT_A_CALL, state(null, null, fullScreen = true))
    }
}
