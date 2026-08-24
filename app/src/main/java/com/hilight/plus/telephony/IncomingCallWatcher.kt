package com.hilight.plus.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.hilight.plus.AppStore
import com.hilight.plus.LightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Monitors incoming phone call state changes to trigger contact-specific lighting patterns.
 */
class IncomingCallWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val controller = LightController.get(context)
        val store = AppStore.get(context)

        CoroutineScope(Dispatchers.IO).launch {
            val callLightsEnabled = store.isCallLightsEnabled.first()
            val masterEnabled = store.isEnabled.first()

            if (!callLightsEnabled || !masterEnabled) return@launch

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    Log.i(TAG, "Incoming call ringing: $incomingNumber")

                    if (incomingNumber.isBlank() || incomingNumber.equals("private", ignoreCase = true) || incomingNumber.equals("unknown", ignoreCase = true)) {
                        // Unknown / Private number
                        val pattern = store.unknownNumbersPattern.first()
                        val color = store.unknownNumbersColor.first()
                        Log.i(TAG, "Triggering unknown/private caller lighting: $pattern")
                        controller.startIncomingCallAlert(pattern = pattern, color = color)
                    } else {
                        val matchedRule = store.findRuleForPhoneNumber(incomingNumber)
                        if (matchedRule != null && matchedRule.isEnabled) {
                            Log.i(TAG, "Matched custom rule for ${matchedRule.name}: ${matchedRule.pattern}")
                            controller.startIncomingCallAlert(pattern = matchedRule.pattern, color = matchedRule.color)
                        } else {
                            val otherContactsPattern = store.otherContactsPattern.first()
                            val otherContactsColor = store.otherContactsColor.first()
                            Log.i(TAG, "Using other contacts default lighting: $otherContactsPattern")
                            controller.startIncomingCallAlert(pattern = otherContactsPattern, color = otherContactsColor)
                        }
                    }
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK,
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.i(TAG, "Call ended or answered ($stateStr), stopping call lights")
                    controller.stopIncomingCallAlert()
                }
            }
        }
    }

    companion object {
        private const val TAG = "IncomingCallWatcher"
    }
}
