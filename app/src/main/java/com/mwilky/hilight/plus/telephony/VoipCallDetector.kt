package com.mwilky.hilight.plus.telephony

import android.app.Notification

/**
 * Recognises a ringing app call (WhatsApp, Teams, Meet, ...) from its notification.
 *
 * CallStyle notifications say so directly via EXTRA_CALL_TYPE. Older call notifications
 * only carry CATEGORY_CALL; for those a full-screen intent is what separates "ringing"
 * from "call in progress".
 */
internal object VoipCallDetector {

    fun isIncomingCall(notification: Notification): Boolean {
        val extras = notification.extras
        val callType = if (extras != null && extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            extras.getInt(Notification.EXTRA_CALL_TYPE)
        } else {
            null
        }
        return isIncomingCall(notification.category, callType, notification.fullScreenIntent != null)
    }

    fun isIncomingCall(category: String?, callType: Int?, hasFullScreenIntent: Boolean): Boolean {
        if (category != Notification.CATEGORY_CALL) return false
        return if (callType != null) {
            callType == Notification.CallStyle.CALL_TYPE_INCOMING
        } else {
            hasFullScreenIntent
        }
    }
}
