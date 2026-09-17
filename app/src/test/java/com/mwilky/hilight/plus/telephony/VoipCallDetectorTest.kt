package com.mwilky.hilight.plus.telephony

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoipCallDetectorTest {

    @Test
    fun callStyleIncomingIsACall() {
        assertTrue(VoipCallDetector.isIncomingCall(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_INCOMING, false))
    }

    @Test
    fun callStyleOngoingAndScreeningAreNot() {
        assertFalse(VoipCallDetector.isIncomingCall(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_ONGOING, true))
        assertFalse(VoipCallDetector.isIncomingCall(Notification.CATEGORY_CALL, Notification.CallStyle.CALL_TYPE_SCREENING, true))
    }

    @Test
    fun legacyCallCategoryNeedsAFullScreenIntent() {
        assertTrue(VoipCallDetector.isIncomingCall(Notification.CATEGORY_CALL, null, true))
        assertFalse(VoipCallDetector.isIncomingCall(Notification.CATEGORY_CALL, null, false))
    }

    @Test
    fun otherCategoriesAreNeverCalls() {
        assertFalse(VoipCallDetector.isIncomingCall(Notification.CATEGORY_MESSAGE, Notification.CallStyle.CALL_TYPE_INCOMING, true))
        assertFalse(VoipCallDetector.isIncomingCall(null, null, true))
    }
}
