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
                        // 1. Look up contact display name from phonebook
                        val contactName = lookupContactName(context, incomingNumber)

                        // 2. Check if contact has a custom Call Rule configured in HiLight Plus
                        val matchedRule = if (contactName != null) store.findRuleForContactName(contactName) else null

                        if (matchedRule != null) {
                            if (matchedRule.isEnabled) {
                                Log.i(TAG, "Matched custom rule for '${matchedRule.name}': ${matchedRule.pattern}")
                                controller.startIncomingCallAlert(pattern = matchedRule.pattern, color = matchedRule.color)
                            } else {
                                Log.i(TAG, "Custom rule for '${matchedRule.name}' is disabled")
                            }
                        } else {
                            // 3. If contact is in address book (no custom rule) -> All Other Contacts
                            if (contactName != null) {
                                Log.i(TAG, "Caller '$contactName' is a saved contact (no custom rule) -> using 'All Other Contacts'")
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

    private fun lookupContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error looking up contact in address book: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "IncomingCallWatcher"
    }
}
