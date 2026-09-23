package com.mwilky.hilight.plus.telephony

import android.app.Notification

/**
 * Reads a call notification (dialer, WhatsApp, Teams, Meet, ...) as ringing, in progress, or unclear.
 *
 * CallStyle notifications say so directly via EXTRA_CALL_TYPE. Older call notifications
 * only carry CATEGORY_CALL; for those a full-screen intent means ringing and a chronometer
 * means the call is under way. Anything else is unclear: WhatsApp, for one, re-posts its
 * ringing notification as a plain silent one half a second in, while it is still ringing,
 * so an unclear re-post must not be taken as "answered".
 */
internal object VoipCallDetector {

    enum class CallState { NOT_A_CALL, RINGING, IN_PROGRESS, UNCLEAR }

    fun callState(notification: Notification): CallState {
        val extras = notification.extras
        val callType = if (extras != null && extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            extras.getInt(Notification.EXTRA_CALL_TYPE)
        } else {
            null
        }
        return callState(
            notification.category,
            callType,
            hasFullScreenIntent = notification.fullScreenIntent != null,
            showsChronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true
        )
    }

    fun callState(
        category: String?,
        callType: Int?,
        hasFullScreenIntent: Boolean,
        showsChronometer: Boolean
    ): CallState {
        if (category != Notification.CATEGORY_CALL) return CallState.NOT_A_CALL
        if (callType != null) {
            return if (callType == Notification.CallStyle.CALL_TYPE_INCOMING) CallState.RINGING else CallState.IN_PROGRESS
        }
        return when {
            hasFullScreenIntent -> CallState.RINGING
            showsChronometer -> CallState.IN_PROGRESS
            else -> CallState.UNCLEAR
        }
    }
}
