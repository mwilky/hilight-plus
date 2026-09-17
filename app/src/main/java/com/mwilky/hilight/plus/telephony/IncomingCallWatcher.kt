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
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.SettingsSnapshot
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Listens for incoming phone calls and activates custom rear LED lighting:
 * - Specific enabled Contact Rule (highest priority)
 * - All Other Contacts (saved in address book, including a disabled custom rule)
 * - Unknown & Private Numbers (unsaved callers)
 *
 * Events are serialized and the receiver is kept alive with [goAsync] so a
 * hangup cannot lose the stop, and a late RINGING cannot restart lights.
 *
 * App calls (WhatsApp, Teams, ...) arrive through [IncomingCallProcessor.submitVoipRinging]
 * from the notification listener and resolve through the same three tiers by caller name.
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
    private val events = Channel<CallEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var settingsWatchStarted = false

    // The one app call currently ringing, by notification key. A cellular ring always wins
    // over it while both are live; when the cellular call ends the app call's lights resume.
    private var voipKey: String? = null
    private var voipCallerName: String = ""

    init {
        scope.launch {
            for (event in events) {
                try {
                    lock.withLock { handle(event) }
                } catch (t: Throwable) {
                    Log.e(TAG, "Call event failed", t)
                } finally {
                    (event as? CallEvent.Telephony)?.pending?.finish()
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
        ensureSettingsWatch(appContext)
        val queued = CallEvent.Telephony(appContext, state, number, pending)
        if (!events.trySend(queued).isSuccess) {
            pending.finish()
        }
    }

    /** A ringing app-call notification was posted (or re-posted) under [key]. */
    fun submitVoipRinging(appContext: Context, key: String, callerName: String) {
        ensureSettingsWatch(appContext)
        events.trySend(CallEvent.VoipRinging(appContext, key, callerName))
    }

    /** The notification under [key] was removed or is no longer a ringing call. */
    fun submitVoipEnded(appContext: Context, key: String) {
        events.trySend(CallEvent.VoipEnded(appContext, key))
    }

    /** Listener reconnected: any tracked app call whose notification is gone has ended. */
    fun submitVoipShadeSync(appContext: Context, shadeKeys: Set<String>) {
        events.trySend(CallEvent.VoipShadeSync(appContext, shadeKeys))
    }

    private fun ensureSettingsWatch(appContext: Context) {
        if (settingsWatchStarted) return
        settingsWatchStarted = true
        scope.launch {
            appContext.dataStore.data.collect {
                lock.withLock {
                    val store = AppStore.get(appContext)
                    val controller = LightController.get(appContext)
                    when {
                        session.isRinging -> startResolvedCall(appContext, store, controller, session.number)
                        voipKey != null -> startResolvedVoipCall(appContext, store, controller, voipCallerName)
                    }
                }
            }
        }
    }

    private suspend fun handle(event: CallEvent) {
        val controller = LightController.get(event.appContext)
        val store = AppStore.get(event.appContext)
        when (event) {
            is CallEvent.Telephony -> handleTelephony(event, store, controller)
            is CallEvent.VoipRinging -> {
                voipKey = event.key
                voipCallerName = event.callerName
                if (session.isRinging) {
                    Log.d(TAG, "App call ringing while a phone call rings; phone call keeps the lights")
                } else {
                    startResolvedVoipCall(event.appContext, store, controller, event.callerName)
                }
            }
            is CallEvent.VoipEnded -> if (event.key == voipKey) endVoipCall(controller)
            is CallEvent.VoipShadeSync -> {
                val key = voipKey
                if (key != null && key !in event.shadeKeys) endVoipCall(controller)
            }
        }
    }

    private fun endVoipCall(controller: LightController) {
        Log.i(TAG, "App call ended or answered, stopping call lights")
        voipKey = null
        voipCallerName = ""
        if (!session.isRinging) stopCallLights(controller)
    }

    private suspend fun handleTelephony(queued: CallEvent.Telephony, store: AppStore, controller: LightController) {
        val now = SystemClock.elapsedRealtime()

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
                    if (voipKey != null) {
                        startResolvedVoipCall(queued.appContext, store, controller, voipCallerName)
                    } else {
                        stopCallLights(controller)
                    }
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
        val snapshot = store.snapshot()
        if (!snapshot.isEnabled || !snapshot.isCallLightsEnabled) {
            Log.d(TAG, "Call lights are disabled; keeping session but stopping lights")
            stopCallLights(controller)
            return
        }

        val contactName = if (number.isNotBlank()) lookupContactName(context, number) else null
        startForCaller(context, snapshot, controller, contactName, isSavedContact = contactName != null)
    }

    /**
     * App calls carry a display name rather than a number. A custom rule matches on that name;
     * otherwise the address book decides between All Other Contacts and Unknown.
     */
    private suspend fun startResolvedVoipCall(
        context: Context,
        store: AppStore,
        controller: LightController,
        callerName: String
    ) {
        val snapshot = store.snapshot()
        if (!snapshot.isEnabled || !snapshot.isCallLightsEnabled) {
            Log.d(TAG, "Call lights are disabled; keeping session but stopping lights")
            stopCallLights(controller)
            return
        }

        val name = callerName.takeIf { it.isNotBlank() }
        val isSaved = name != null && isSavedContactName(context, name)
        startForCaller(context, snapshot, controller, name, isSavedContact = isSaved)
    }

    private suspend fun startForCaller(
        context: Context,
        snapshot: SettingsSnapshot,
        controller: LightController,
        contactName: String?,
        isSavedContact: Boolean
    ) {
        val matchedRule = if (contactName != null) snapshot.findRuleForContactName(contactName) else null

        if (matchedRule != null && matchedRule.isEnabled) {
            Log.i(TAG, "Matched custom rule for '${matchedRule.name}': ${matchedRule.pattern}")
            startCallAlert(
                context,
                snapshot,
                controller,
                matchedRule.faceDownMode,
                matchedRule.dndMode,
                matchedRule.quietHoursMode,
                matchedRule.quietHoursStartMinutes,
                matchedRule.quietHoursEndMinutes,
                matchedRule.pattern,
                matchedRule.color
            )
            return
        }

        if (isSavedContact) {
            if (matchedRule != null) {
                Log.i(TAG, "Custom rule for '${matchedRule.name}' is disabled -> All Other Contacts")
            } else {
                Log.i(TAG, "Caller '$contactName' is a saved contact (no custom rule) -> All Other Contacts")
            }
            triggerOtherContactsAlert(context, snapshot, controller)
        } else {
            Log.i(TAG, "Caller is unsaved / not in contacts -> Unknown & Private Numbers")
            triggerUnknownAlert(context, snapshot, controller)
        }
    }

    private fun stopCallLights(controller: LightController) {
        DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_CALL)
        controller.stopIncomingCallAlert()
    }

    private suspend fun startCallAlert(
        context: Context,
        snapshot: SettingsSnapshot,
        controller: LightController,
        faceDownMode: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietStartMinutes: Int?,
        quietEndMinutes: Int?,
        pattern: PatternMode,
        color: Long
    ) {
        val requiresFaceDown = faceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown)
        val quietStartOverride = if (quietHoursMode == QuietHoursMode.SKIP) quietStartMinutes else null
        val quietEndOverride = if (quietHoursMode == QuietHoursMode.SKIP) quietEndMinutes else null
        if (requiresFaceDown) {
            DeviceOrientationDetector.retainMonitoring(context, DeviceOrientationDetector.TOKEN_CALL)
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(context))
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_CALL)
        }
        controller.startIncomingCallAlert(
            pattern = pattern,
            color = color,
            requiresFaceDown = requiresFaceDown,
            dndMode = dndMode,
            quietHoursMode = quietHoursMode,
            quietStartMinutes = quietStartOverride,
            quietEndMinutes = quietEndOverride
        )
    }

    private suspend fun triggerOtherContactsAlert(
        context: Context,
        snapshot: SettingsSnapshot,
        controller: LightController
    ) {
        if (snapshot.isOtherContactsEnabled) {
            val pattern = snapshot.otherContactsPattern
            val color = snapshot.otherContactsColor
            Log.i(TAG, "Triggering Other Contacts lighting: $pattern, color=$color")
            startCallAlert(
                context,
                snapshot,
                controller,
                snapshot.otherContactsFaceDownMode,
                snapshot.otherContactsDndMode,
                snapshot.otherContactsQuietHoursMode,
                snapshot.otherContactsQuietHoursStartMinutes,
                snapshot.otherContactsQuietHoursEndMinutes,
                pattern,
                color
            )
        } else {
            Log.i(TAG, "Other Contacts lights are disabled")
            stopCallLights(controller)
        }
    }

    private suspend fun triggerUnknownAlert(
        context: Context,
        snapshot: SettingsSnapshot,
        controller: LightController
    ) {
        if (snapshot.isUnknownNumbersEnabled) {
            val pattern = snapshot.unknownNumbersPattern
            val color = snapshot.unknownNumbersColor
            Log.i(TAG, "Triggering Unknown/Private lighting: $pattern, color=$color")
            startCallAlert(
                context,
                snapshot,
                controller,
                snapshot.unknownNumbersFaceDownMode,
                snapshot.unknownNumbersDndMode,
                snapshot.unknownNumbersQuietHoursMode,
                snapshot.unknownNumbersQuietHoursStartMinutes,
                snapshot.unknownNumbersQuietHoursEndMinutes,
                pattern,
                color
            )
        } else {
            Log.i(TAG, "Unknown/Private lights are disabled")
            stopCallLights(controller)
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

    private fun isSavedContactName(context: Context, displayName: String): Boolean {
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE",
                arrayOf(displayName.trim()),
                null
            )?.use { it.moveToFirst() }
        }.getOrNull() == true
    }

    private sealed interface CallEvent {
        val appContext: Context

        data class Telephony(
            override val appContext: Context,
            val state: String,
            val number: String,
            val pending: BroadcastReceiver.PendingResult
        ) : CallEvent

        data class VoipRinging(override val appContext: Context, val key: String, val callerName: String) : CallEvent
        data class VoipEnded(override val appContext: Context, val key: String) : CallEvent
        data class VoipShadeSync(override val appContext: Context, val shadeKeys: Set<String>) : CallEvent
    }

    private const val TAG = "IncomingCallWatcher"
}
