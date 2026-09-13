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
import com.mwilky.hilight.plus.PatternMode
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
 *
 * Face-down-only rules stay queued for the whole ring and follow orientation
 * continuously instead of being decided only on arrival.
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
                            Log.i(TAG, "Matched custom rule for '${matchedRule.name}': ${matchedRule.pattern}")
                            startCallAlert(context, store, controller, matchedRule.faceDownMode, matchedRule.pattern, matchedRule.color)
                        } else {
                            Log.i(TAG, "Custom rule for '${matchedRule.name}' is disabled")
                        }
                    } else if (contactName != null) {
                        Log.i(TAG, "Caller '$contactName' is a saved contact (no custom rule) -> using 'All Other Contacts'")
                        triggerOtherContactsAlert(context, store, controller)
                    } else {
                        Log.i(TAG, "Caller is unsaved / not in contacts -> using 'Unknown & Private Numbers'")
                        triggerUnknownAlert(context, store, controller)
                    }
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK,
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.i(TAG, "Call ended or answered ($stateStr), stopping call lights")
                    DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_CALL)
                    controller.stopIncomingCallAlert()
                }
            }
        }
    }

    private suspend fun startCallAlert(
        context: Context,
        store: AppStore,
        controller: LightController,
        faceDownMode: FaceDownMode,
        pattern: PatternMode,
        color: Long
    ) {
        val requiresFaceDown = faceDownMode.requiresFaceDown(store.isOnlyWhenFaceDown.first())
        if (requiresFaceDown) {
            DeviceOrientationDetector.retainMonitoring(context, DeviceOrientationDetector.TOKEN_CALL)
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(context))
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_CALL)
        }
        controller.startIncomingCallAlert(pattern = pattern, color = color, requiresFaceDown = requiresFaceDown)
    }

    private suspend fun triggerOtherContactsAlert(context: Context, store: AppStore, controller: LightController) {
        val isOtherEnabled = store.isOtherContactsEnabled.first()
        if (isOtherEnabled) {
            val pattern = store.otherContactsPattern.first()
            val color = store.otherContactsColor.first()
            Log.i(TAG, "Triggering Other Contacts lighting: $pattern, color=$color")
            startCallAlert(context, store, controller, store.otherContactsFaceDownMode.first(), pattern, color)
        } else {
            Log.i(TAG, "Other Contacts lights are disabled")
        }
    }

    private suspend fun triggerUnknownAlert(context: Context, store: AppStore, controller: LightController) {
        val isUnknownEnabled = store.isUnknownNumbersEnabled.first()
        if (isUnknownEnabled) {
            val pattern = store.unknownNumbersPattern.first()
            val color = store.unknownNumbersColor.first()
            Log.i(TAG, "Triggering Unknown/Private lighting: $pattern, color=$color")
            startCallAlert(context, store, controller, store.unknownNumbersFaceDownMode.first(), pattern, color)
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
