package com.mwilky.hilight.plus.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens for incoming phone calls and activates custom rear LED lighting:
 * - Specific enabled Contact Rule (highest priority)
 * - All Other Contacts (saved in address book, including a disabled custom rule)
 * - Unknown & Private Numbers (unsaved callers)
 *
 * Events are serialized and the receiver is kept alive with [goAsync] so a
 * hangup cannot lose the stop, and a late RINGING cannot restart lights.
 */
class IncomingCallWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val pending = goAsync()
        IncomingCallProcessor.submit(context.applicationContext, stateStr, incomingNumber, pending)
    }

    companion object {
        private const val TAG = "IncomingCallWatcher"
    }
}

internal object IncomingCallProcessor {
    private val session = IncomingCallSession()
    private val events = Channel<Queued>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (queued in events) {
                try {
                    handle(queued)
                } catch (t: Throwable) {
                    Log.e(TAG, "Call event failed", t)
                } finally {
                    queued.pending.finish()
                }
            }
        }
    }

    fun submit(
        appContext: Context,
        state: String,
        number: String,
        pending: BroadcastReceiver.PendingResult
    ) {
        val queued = Queued(appContext, state, number, pending)
        if (!events.trySend(queued).isSuccess) {
            pending.finish()
        }
    }

    private suspend fun handle(queued: Queued) {
        val now = SystemClock.elapsedRealtime()
        val controller = LightController.get(queued.appContext)
        val store = AppStore.get(queued.appContext)

        when (queued.state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                when (val effect = session.onRinging(queued.number, now)) {
                    IncomingCallSession.Effect.Ignore,
                    IncomingCallSession.Effect.Stop -> {
                        Log.d(TAG, "Ignoring stale or duplicate RINGING")
                    }
                    is IncomingCallSession.Effect.Start -> {
                        startResolvedCall(queued.appContext, store, controller, effect.number)
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (session.onEnded(now) == IncomingCallSession.Effect.Stop) {
                    Log.i(TAG, "Call ended or answered (${queued.state}), stopping call lights")
                    DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_CALL)
                    controller.stopIncomingCallAlert()
                }
            }
        }
    }

    private suspend fun startResolvedCall(
        context: Context,
        store: AppStore,
        controller: LightController,
        number: String
    ) {
        val masterEnabled = store.isEnabled.first()
        val callLightsEnabled = store.isCallLightsEnabled.first()
        if (!masterEnabled || !callLightsEnabled) {
            Log.d(TAG, "Call lights are disabled; keeping session but not starting lights")
            return
        }

        val contactName = if (number.isNotBlank()) lookupContactName(context, number) else null
        val matchedRule = if (contactName != null) store.findRuleForContactName(contactName) else null

        if (matchedRule != null && matchedRule.isEnabled) {
            Log.i(TAG, "Matched custom rule for '${matchedRule.name}': ${matchedRule.pattern}")
            startCallAlert(context, store, controller, matchedRule.faceDownMode, matchedRule.pattern, matchedRule.color)
            return
        }

        if (contactName != null) {
            if (matchedRule != null) {
                Log.i(TAG, "Custom rule for '${matchedRule.name}' is disabled -> All Other Contacts")
            } else {
                Log.i(TAG, "Caller '$contactName' is a saved contact (no custom rule) -> All Other Contacts")
            }
            triggerOtherContactsAlert(context, store, controller)
        } else {
            Log.i(TAG, "Caller is unsaved / not in contacts -> Unknown & Private Numbers")
            triggerUnknownAlert(context, store, controller)
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

    private data class Queued(
        val appContext: Context,
        val state: String,
        val number: String,
        val pending: BroadcastReceiver.PendingResult
    )

    private const val TAG = "IncomingCallWatcher"
}
