package com.hilight.plus.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
                    Log.i(TAG, "Incoming call ringing, number: '$incomingNumber'")

                    val isPrivateOrUnknown = incomingNumber.isBlank() ||
                        incomingNumber.equals("private", ignoreCase = true) ||
                        incomingNumber.equals("unknown", ignoreCase = true) ||
                        incomingNumber.equals("-1") ||
                        incomingNumber.equals("-2")

                    if (isPrivateOrUnknown) {
                        // Truly unknown / private / hidden caller number
                        triggerUnknownAlert(store, controller)
                    } else {
                        // 1. Check if matches a specific custom Contact Rule in HiLight Plus
                        val matchedRule = store.findRuleForPhoneNumber(incomingNumber)
                        if (matchedRule != null) {
                            if (matchedRule.isEnabled) {
                                Log.i(TAG, "Matched custom rule for ${matchedRule.name}: ${matchedRule.pattern}")
                                controller.startIncomingCallAlert(pattern = matchedRule.pattern, color = matchedRule.color)
                            } else {
                                Log.i(TAG, "Custom rule for ${matchedRule.name} is disabled")
                            }
                        } else {
                            // 2. Check if number exists in saved Android Contacts address book
                            val isSaved = isSavedInAddressBook(context, incomingNumber)
                            if (isSaved) {
                                Log.i(TAG, "Caller is a saved contact (no custom rule) -> using 'All Other Contacts'")
                                triggerOtherContactsAlert(store, controller)
                            } else {
                                Log.i(TAG, "Caller is unsaved / not in contacts -> using 'Unknown & Private Numbers'")
                                triggerUnknownAlert(store, controller)
                            }
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

    private suspend fun triggerOtherContactsAlert(store: AppStore, controller: LightController) {
        val isOtherEnabled = store.isOtherContactsEnabled.first()
        if (isOtherEnabled) {
            val pattern = store.otherContactsPattern.first()
            val color = store.otherContactsColor.first()
            Log.i(TAG, "Triggering Other Contacts lighting: $pattern, color=$color")
            controller.startIncomingCallAlert(pattern = pattern, color = color)
        } else {
            Log.i(TAG, "Other Contacts lights are disabled")
        }
    }

    private suspend fun triggerUnknownAlert(store: AppStore, controller: LightController) {
        val isUnknownEnabled = store.isUnknownNumbersEnabled.first()
        if (isUnknownEnabled) {
            val pattern = store.unknownNumbersPattern.first()
            val color = store.unknownNumbersColor.first()
            Log.i(TAG, "Triggering Unknown/Private lighting: $pattern, color=$color")
            controller.startIncomingCallAlert(pattern = pattern, color = color)
        } else {
            Log.i(TAG, "Unknown/Private lights are disabled")
        }
    }

    private fun isSavedInAddressBook(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "Error looking up contact in address book: ${t.message}")
            false
        }
    }

    companion object {
        private const val TAG = "IncomingCallWatcher"
    }
}
