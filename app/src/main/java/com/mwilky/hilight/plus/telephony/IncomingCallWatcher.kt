package com.mwilky.hilight.plus.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.mwilky.hilight.plus.AppStore
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens for incoming phone calls and activates custom rear LED lighting:
 * - Specific Contact Rule (highest priority)
 * - All Other Contacts (saved in address book)
 * - Unknown & Private Numbers (unsaved callers)
 * - Orientation check: verifies Face-Down condition (per-rule or global setting)
 */
class IncomingCallWatcher : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val controller = LightController.get(context)
        val store = AppStore.get(context)

        scope.launch {
            val masterEnabled = store.isEnabled.first()
            val callLightsEnabled = store.isCallLightsEnabled.first()

            if (!masterEnabled || !callLightsEnabled) {
                Log.d(TAG, "Call lights are disabled by master switch")
                return@launch
            }

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val contactName = if (incomingNumber.isNotBlank()) lookupContactName(context, incomingNumber) else null
                    val matchedRule = if (contactName != null) store.findRuleForContactName(contactName) else null

                    if (matchedRule != null) {
                        if (matchedRule.isEnabled) {
                            if (!isOrientationAllowed(context, store, matchedRule.faceDownMode)) {
                                Log.i(TAG, "Suppressed '${matchedRule.name}' call lights: phone is not face down")
                                return@launch
                            }
                            Log.i(TAG, "Matched custom rule for '${matchedRule.name}': ${matchedRule.pattern}")
                            controller.startIncomingCallAlert(pattern = matchedRule.pattern, color = matchedRule.color)
                        } else {
                            Log.i(TAG, "Custom rule for '${matchedRule.name}' is disabled")
                        }
                    } else {
                        // 3. If contact is in address book (no custom rule) -> All Other Contacts
                        if (contactName != null) {
                            val otherFaceDown = store.otherContactsFaceDownMode.first()
                            if (!isOrientationAllowed(context, store, otherFaceDown)) {
                                Log.i(TAG, "Suppressed Other Contacts call lights: phone is not face down")
                                return@launch
                            }
                            Log.i(TAG, "Caller '$contactName' is a saved contact (no custom rule) -> using 'All Other Contacts'")
                            triggerOtherContactsAlert(store, controller)
                        } else {
                            val unknownFaceDown = store.unknownNumbersFaceDownMode.first()
                            if (!isOrientationAllowed(context, store, unknownFaceDown)) {
                                Log.i(TAG, "Suppressed Unknown Numbers call lights: phone is not face down")
                                return@launch
                            }
                            Log.i(TAG, "Caller is unsaved / not in contacts -> using 'Unknown & Private Numbers'")
                            triggerUnknownAlert(store, controller)
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

    private suspend fun isOrientationAllowed(context: Context, store: AppStore, ruleMode: FaceDownMode): Boolean {
        return when (ruleMode) {
            FaceDownMode.ALWAYS -> true
            FaceDownMode.ONLY_FACE_DOWN -> DeviceOrientationDetector.isDeviceFaceDown(context)
            FaceDownMode.INHERIT -> {
                val globalOnlyFaceDown = store.isOnlyWhenFaceDown.first()
                if (globalOnlyFaceDown) {
                    DeviceOrientationDetector.isDeviceFaceDown(context)
                } else {
                    true
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
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx != -1) cursor.getString(nameIdx) else null
                } else null
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "IncomingCallWatcher"
    }
}
