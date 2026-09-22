package com.mwilky.hilight.plus.telephony

import android.content.Context
import android.provider.ContactsContract
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
 * Lights the ring for a ringing call and resolves the caller through three tiers:
 * - Specific enabled Contact Rule (highest priority)
 * - All Other Contacts (saved in address book, including a disabled custom rule)
 * - Unknown & Private Numbers (unsaved callers)
 *
 * Every call, cellular or app (WhatsApp, Teams, ...), arrives from the notification listener as
 * its ringing call-style notification, so no phone-state or call-log permission is needed. The
 * caller is the notification's display name: the dialer shows the contact name for saved
 * callers and the bare number (or "Unknown") otherwise. Events are serialized so a late
 * "ringing" can never restart lights after the call has ended.
 */
internal object IncomingCallProcessor {
    private val events = Channel<CallEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var settingsWatchStarted = false

    // The one call currently ringing, by notification key.
    private var ringingKey: String? = null
    private var ringingCallerName: String = ""

    init {
        scope.launch {
            for (event in events) {
                try {
                    lock.withLock { handle(event) }
                } catch (t: Throwable) {
                    Log.e(TAG, "Call event failed", t)
                }
            }
        }
    }

    /** A ringing call notification was posted (or re-posted) under [key]. */
    fun submitVoipRinging(appContext: Context, key: String, callerName: String) {
        ensureSettingsWatch(appContext)
        events.trySend(CallEvent.Ringing(appContext, key, callerName))
    }

    /** The notification under [key] was removed or is no longer a ringing call. */
    fun submitVoipEnded(appContext: Context, key: String) {
        events.trySend(CallEvent.Ended(appContext, key))
    }

    /** Listener reconnected: a tracked call whose notification is gone has ended. */
    fun submitVoipShadeSync(appContext: Context, shadeKeys: Set<String>) {
        events.trySend(CallEvent.ShadeSync(appContext, shadeKeys))
    }

    private fun ensureSettingsWatch(appContext: Context) {
        if (settingsWatchStarted) return
        settingsWatchStarted = true
        scope.launch {
            appContext.dataStore.data.collect {
                lock.withLock {
                    if (ringingKey != null) {
                        startResolvedCall(appContext, AppStore.get(appContext), LightController.get(appContext), ringingCallerName)
                    }
                }
            }
        }
    }

    private suspend fun handle(event: CallEvent) {
        val controller = LightController.get(event.appContext)
        val store = AppStore.get(event.appContext)
        when (event) {
            is CallEvent.Ringing -> {
                ringingKey = event.key
                ringingCallerName = event.callerName
                startResolvedCall(event.appContext, store, controller, event.callerName)
            }
            is CallEvent.Ended -> if (event.key == ringingKey) endCall(controller)
            is CallEvent.ShadeSync -> {
                val key = ringingKey
                if (key != null && key !in event.shadeKeys) endCall(controller)
            }
        }
    }

    private fun endCall(controller: LightController) {
        Log.i(TAG, "Call ended or answered, stopping call lights")
        ringingKey = null
        ringingCallerName = ""
        stopCallLights(controller)
    }

    /**
     * A custom rule matches on the caller's display name; otherwise the address book decides
     * between All Other Contacts and Unknown.
     */
    private suspend fun startResolvedCall(
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

        data class Ringing(override val appContext: Context, val key: String, val callerName: String) : CallEvent
        data class Ended(override val appContext: Context, val key: String) : CallEvent
        data class ShadeSync(override val appContext: Context, val shadeKeys: Set<String>) : CallEvent
    }

    private const val TAG = "IncomingCallProcessor"
}
