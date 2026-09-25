package com.mwilky.hilight.plus

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mwilky.hilight.plus.core.AppIconColorExtractor
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.core.NotificationSlotTracker
import com.mwilky.hilight.plus.telephony.IncomingCallProcessor
import com.mwilky.hilight.plus.telephony.VoipCallDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Listens for incoming system notifications and drives rear LED alerts.
 *
 * Source notification keys are tracked separately from display slots so cycling
 * can group repeats while dismissal only removes a slot when its last contributor is gone.
 */
class NotificationTrigger : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val events = Channel<ListenerEvent>(Channel.UNLIMITED)
    private val tracker = NotificationSlotTracker()

    @Volatile
    private var lastKnownCycling: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        LightController.get(applicationContext)

        scope.launch {
            for (event in events) {
                try {
                    handle(event)
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "Listener event failed", t)
                }
            }
        }
        scope.launch {
            applicationContext.dataStore.data.collect {
                enqueue(ListenerEvent.SettingsChanged)
            }
        }
        scope.launch {
            LightController.get(applicationContext).daemon.connections.collect {
                enqueue(ListenerEvent.DaemonConnected)
                IncomingCallProcessor.submitDaemonConnected(applicationContext)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DebugLog.i(TAG, "Notification listener connected")
        isListenerConnected = true
        enqueue(ListenerEvent.Reconnect(currentShadeKeys()))
    }

    override fun onListenerDisconnected() {
        DebugLog.w(TAG, "Notification listener disconnected")
        isListenerConnected = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        isListenerConnected = false
        job.cancel()
        DeviceOrientationDetector.stopMonitoring()
        // The tracker dies with this instance, so nothing could ever remove the alerts it posted,
        // and no removal will arrive to end a ringing call: e.g. notification access was revoked
        // mid-alert. Clear both rather than leave the ring lit.
        DebugLog.w(TAG, "Notification listener destroyed -> clearing notification and call lights")
        LightController.get(applicationContext).clearAlert()
        IncomingCallProcessor.submitListenerGone(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        DebugLog.d(TAG, "Removed pkg=${sbn.packageName} key=${DebugLog.redactKey(sbn.key)}")
        IncomingCallProcessor.submitVoipEnded(applicationContext, sbn.key)
        enqueue(ListenerEvent.Removed(sbn.key))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        // A ringing call (dialer or app call) is a call, not a message: it lights until answered
        // or ended. The same key re-posted as an in-progress call is how "answered" is signalled.
        val posted = sbn.notification
        DebugLog.d(
            TAG,
            "Posted pkg=$pkg category=${posted?.category} channel=${posted?.channelId} key=${DebugLog.redactKey(sbn.key)} " +
                "callType=${posted?.extras?.takeIf { it.containsKey(Notification.EXTRA_CALL_TYPE) }?.getInt(Notification.EXTRA_CALL_TYPE)} " +
                "fullScreen=${posted?.fullScreenIntent != null} ongoing=${sbn.isOngoing} " +
                "chrono=${posted?.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)} silent=${isSilent(sbn.key)}"
        )
        if (posted?.category == Notification.CATEGORY_CALL) {
            when (VoipCallDetector.callState(posted)) {
                VoipCallDetector.CallState.RINGING ->
                    IncomingCallProcessor.submitVoipRinging(applicationContext, sbn.key, extractSenderName(posted))
                VoipCallDetector.CallState.IN_PROGRESS ->
                    IncomingCallProcessor.submitVoipEnded(applicationContext, sbn.key)
                // Keep whatever state the key is in; removal still ends a ringing call.
                else -> Unit
            }
            return
        }
        // A ringing call's key re-posted as anything but a call (WhatsApp can turn it into its
        // missed-call notification in place) means it is no longer ringing. No-op for other keys.
        IncomingCallProcessor.submitVoipEnded(applicationContext, sbn.key)

        if (!isEligiblePostedNotification(sbn)) return

        val notification = sbn.notification ?: return
        enqueue(ListenerEvent.Posted(sbn.key, pkg, notification))
    }

    private fun enqueue(event: ListenerEvent) {
        if (!events.trySend(event).isSuccess) {
            DebugLog.w(TAG, "Dropped listener event after shutdown")
        }
    }

    private suspend fun handle(event: ListenerEvent) {
        when (event) {
            is ListenerEvent.Posted -> handlePosted(event)
            is ListenerEvent.Removed -> {
                applyRemovals(
                    listOf(tracker.remove(event.key)),
                    AppStore.get(applicationContext).snapshot().isCycleNotifications
                )
            }
            is ListenerEvent.Reconnect -> {
                val shadeKeys = event.shadeKeys ?: return
                IncomingCallProcessor.submitVoipShadeSync(applicationContext, shadeKeys)
                applyRemovals(
                    tracker.pruneMissing(shadeKeys),
                    AppStore.get(applicationContext).snapshot().isCycleNotifications
                )
            }
            is ListenerEvent.SettingsChanged -> handleSettingsChanged()
            is ListenerEvent.DaemonConnected -> handleDaemonConnected()
        }
    }

    private suspend fun handlePosted(event: ListenerEvent.Posted) {
        val pkg = event.pkg
        val notification = event.notification
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)

        val snapshot = store.snapshot()
        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled) return

        val resolved = resolveAlert(snapshot, event.key, pkg, notification) ?: return
        val isCycle = snapshot.isCycleNotifications
        val requiresFaceDown = resolved.faceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown)
        if (requiresFaceDown) {
            DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
        }

        val added = bindResolved(event.key, resolved, snapshot, becomeLatest = true)
        applyBoundSlot(controller, snapshot, added, isCycle)
        syncNotificationMonitor()
    }

    private suspend fun handleSettingsChanged() {
        val snapshot = AppStore.get(applicationContext).snapshot()
        val previousCycling = lastKnownCycling
        lastKnownCycling = snapshot.isCycleNotifications

        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled) {
            DebugLog.i(TAG, "Notifications disabled -> stopping queued lights")
            tracker.clear()
            LightController.get(applicationContext).clearAlert()
            syncNotificationMonitor()
            return
        }

        if (previousCycling != null && previousCycling != snapshot.isCycleNotifications) {
            applyModeChange(snapshot.isCycleNotifications, snapshot)
            return
        }

        if (!isListenerConnected || tracker.sourceCount == 0) return

        val shade = try {
            activeNotifications?.associateBy { it.key }
        } catch (_: Throwable) {
            return
        } ?: return

        val controller = LightController.get(applicationContext)
        val isCycle = snapshot.isCycleNotifications
        for (key in tracker.sourceKeys()) {
            val sbn = shade[key]
            if (sbn == null) {
                applyRemovals(listOf(tracker.remove(key)), isCycle)
                continue
            }
            val resolved = resolveAlert(snapshot, key, sbn.packageName, sbn.notification)
            if (resolved == null) {
                applyRemovals(listOf(tracker.remove(key)), isCycle)
                continue
            }
            applyBoundSlot(
                controller,
                snapshot,
                bindResolved(key, resolved, snapshot, becomeLatest = false),
                isCycle
            )
        }
        syncNotificationMonitor()
    }

    /**
     * A (re)started daemon holds no alerts, so send it every slot still waiting. Newest-only is
     * left out: its alert was timed, and the tracker still names the latest source long after
     * that time ran out, so replaying it could light something that has already finished.
     */
    private suspend fun handleDaemonConnected() {
        val snapshot = AppStore.get(applicationContext).snapshot()
        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled || !snapshot.isCycleNotifications) return
        val slots = tracker.slotsInOrder()
        if (slots.isEmpty()) return
        DebugLog.i(TAG, "Lights service connected -> replaying ${slots.size} waiting alert(s)")
        val controller = LightController.get(applicationContext)
        if (tracker.hasRestrictedSlot()) {
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
        }
        slots.forEach { dispatchSlot(controller, snapshot, it, isCycle = true) }
    }

    private fun isEligiblePostedNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing ||
            (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (sbn.notification.flags and Notification.FLAG_NO_CLEAR) != 0 ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            DebugLog.d(TAG, "Ignoring ongoing/summary notification from ${sbn.packageName}")
            return false
        }

        // Missed calls pass even when silent (WhatsApp posts them on a low-importance channel);
        // resolveAlert only lets a silent one light through the Missed Calls rule.
        if (sbn.notification.category != Notification.CATEGORY_MISSED_CALL && isSilent(sbn.key)) {
            DebugLog.d(TAG, "Ignoring silent/ambient notification from ${sbn.packageName}")
            return false
        }
        return true
    }

    private fun isSilent(key: String): Boolean {
        val ranking = Ranking()
        if (currentRanking?.getRanking(key, ranking) != true) return false
        return ranking.isAmbient || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    private data class ResolvedAlert(
        val slotId: String,
        val pattern: PatternMode,
        val color: Long,
        val faceDownMode: FaceDownMode,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartMinutes: Int? = null,
        val quietEndMinutes: Int? = null
    )

    private fun resolveAlert(snapshot: SettingsSnapshot, key: String, pkg: String, notification: Notification): ResolvedAlert? {
        // A missed call has one style whoever called, so it is resolved before any contact or app rule.
        // When the rule is off, missed-call notifications fall through to the rules below as before.
        if (notification.category == Notification.CATEGORY_MISSED_CALL &&
            snapshot.isCallLightsEnabled && snapshot.isMissedCallsEnabled
        ) {
            if (snapshot.missedCallsPattern == PatternMode.OFF) {
                DebugLog.i(TAG, "Missed Call Match ($pkg) but Missed Calls is OFF -> NO LIGHT")
                return null
            }
            DebugLog.i(TAG, "Missed Call Match ($pkg)")
            return ResolvedAlert(
                "missed_call",
                snapshot.missedCallsPattern,
                snapshot.missedCallsColor,
                snapshot.missedCallsFaceDownMode,
                snapshot.missedCallsDndMode,
                snapshot.missedCallsQuietHoursMode,
                snapshot.missedCallsQuietHoursStartMinutes,
                snapshot.missedCallsQuietHoursEndMinutes
            )
        }
        if (notification.category == Notification.CATEGORY_MISSED_CALL && isSilent(key)) {
            DebugLog.i(TAG, "Silent missed call ($pkg) with Missed Calls off -> NO LIGHT")
            return null
        }

        val senderName = extractSenderName(notification)
        val contactRule = if (senderName.isNotBlank()) snapshot.findMessageRuleForSender(senderName) else null
        if (contactRule != null) {
            if (contactRule.pattern == PatternMode.OFF) {
                DebugLog.i(TAG, "Priority 1 Match: Contact rule ${contactRule.id} is OFF -> NO LIGHT")
                return null
            }
            DebugLog.i(TAG, "Priority 1 Match: Contact rule ${contactRule.id}")
            return ResolvedAlert(
                "contact_${contactRule.id}",
                contactRule.pattern,
                contactRule.color,
                contactRule.faceDownMode,
                contactRule.dndMode,
                contactRule.quietHoursMode,
                contactRule.quietHoursStartMinutes,
                contactRule.quietHoursEndMinutes
            )
        }

        // Favourites sit above app rules: a starred contact is more specific than the app they message from.
        if (snapshot.isFavouriteNotifEnabled && isFavouriteContactName(applicationContext, senderName)) {
            if (snapshot.favouriteNotifPattern == PatternMode.OFF) {
                DebugLog.i(TAG, "Favourite Match ($pkg) but Favourite Contacts is OFF -> NO LIGHT")
                return null
            }
            DebugLog.i(TAG, "Favourite Match ($pkg)")
            return ResolvedAlert(
                "favourite_${Integer.toHexString(senderName.trim().lowercase().hashCode())}",
                snapshot.favouriteNotifPattern,
                snapshot.favouriteNotifColor,
                snapshot.favouriteNotifFaceDownMode,
                snapshot.favouriteNotifDndMode,
                snapshot.favouriteNotifQuietHoursMode,
                snapshot.favouriteNotifQuietHoursStartMinutes,
                snapshot.favouriteNotifQuietHoursEndMinutes
            )
        }

        val appRule = snapshot.findRuleForPackage(pkg)
        if (appRule != null) {
            if (appRule.pattern == PatternMode.OFF) {
                DebugLog.i(TAG, "Priority 2 Match: App '${appRule.appName}' is OFF -> NO LIGHT")
                return null
            }
            val color = if (appRule.isAutoColor) {
                AppIconColorExtractor.extractColorForPackage(applicationContext, appRule.packageName, appRule.color)
            } else {
                appRule.color
            }
            DebugLog.i(TAG, "Priority 2 Match: App '${appRule.appName}' ($pkg)")
            return ResolvedAlert(
                "app_${appRule.packageName}",
                appRule.pattern,
                color,
                appRule.faceDownMode,
                appRule.dndMode,
                appRule.quietHoursMode,
                appRule.quietHoursStartMinutes,
                appRule.quietHoursEndMinutes
            )
        }

        if (!snapshot.isDefaultNotifEnabled) {
            DebugLog.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
            return null
        }
        val defaultPattern = snapshot.defaultNotifPattern
        if (defaultPattern == PatternMode.OFF) {
            DebugLog.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
            return null
        }
        val defaultColor = if (snapshot.isDefaultNotifAutoColor) {
            AppIconColorExtractor.extractColorForPackage(
                applicationContext,
                pkg,
                snapshot.defaultNotifColor
            )
        } else {
            snapshot.defaultNotifColor
        }
        DebugLog.i(TAG, "Priority 3 Match: General Default ($pkg)")
        return ResolvedAlert(
            slotId = "fallback_$pkg",
            pattern = defaultPattern,
            color = defaultColor,
            faceDownMode = snapshot.defaultNotifFaceDownMode,
            dndMode = snapshot.defaultNotifDndMode,
            quietHoursMode = snapshot.defaultNotifQuietHoursMode,
            quietStartMinutes = snapshot.defaultNotifQuietHoursStartMinutes,
            quietEndMinutes = snapshot.defaultNotifQuietHoursEndMinutes
        )
    }

    private fun bindResolved(
        sourceKey: String,
        resolved: ResolvedAlert,
        snapshot: SettingsSnapshot,
        becomeLatest: Boolean
    ): NotificationSlotTracker.AddResult {
        val quietStartOverride = if (resolved.quietHoursMode == QuietHoursMode.SKIP) {
            resolved.quietStartMinutes
        } else {
            null
        }
        val quietEndOverride = if (resolved.quietHoursMode == QuietHoursMode.SKIP) {
            resolved.quietEndMinutes
        } else {
            null
        }
        return tracker.add(
            sourceKey,
            resolved.slotId,
            resolved.pattern,
            resolved.color,
            resolved.faceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown),
            resolved.dndMode,
            resolved.quietHoursMode,
            quietStartOverride,
            quietEndOverride,
            becomeLatest
        )
    }

    private fun applyBoundSlot(
        controller: LightController,
        snapshot: SettingsSnapshot,
        added: NotificationSlotTracker.AddResult,
        isCycle: Boolean
    ) {
        if (added.emptiedSlotId != null && isCycle) {
            DebugLog.i(TAG, "Slot ${added.emptiedSlotId} emptied after rule change -> removing from cycle")
            controller.removeNotificationAlert(added.emptiedSlotId)
        }
        if (added.changed && (isCycle || added.slot.id == tracker.latestSlot()?.id)) {
            dispatchSlot(controller, snapshot, added.slot, isCycle)
        } else if (!added.changed) {
            DebugLog.d(TAG, "Ignoring update for existing slot ${added.slot.id}")
        }
    }

    private fun dispatchSlot(
        controller: LightController,
        snapshot: SettingsSnapshot,
        slot: NotificationSlotTracker.Slot,
        isCycle: Boolean
    ) {
        if (isCycle) {
            controller.postNotificationAlert(
                key = slot.id,
                pattern = slot.pattern,
                color = slot.color,
                durationMs = 0L,
                requiresFaceDown = slot.requiresFaceDown,
                dndMode = slot.dndMode,
                quietHoursMode = slot.quietHoursMode,
                quietStartMinutes = slot.quietStartMinutes,
                quietEndMinutes = slot.quietEndMinutes
            )
        } else {
            val durationMs = snapshot.notificationDurationSeconds.coerceIn(5, 300) * 1000L
            controller.triggerAlertEffect(
                pattern = slot.pattern,
                color = slot.color,
                durationMs = durationMs,
                requiresFaceDown = slot.requiresFaceDown,
                dndMode = slot.dndMode,
                quietHoursMode = slot.quietHoursMode,
                quietStartMinutes = slot.quietStartMinutes,
                quietEndMinutes = slot.quietEndMinutes
            )
        }
    }

    private suspend fun applyModeChange(cycling: Boolean, snapshot: SettingsSnapshot) {
        val controller = LightController.get(applicationContext)
        if (tracker.hasRestrictedSlot()) {
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
        }
        controller.clearAlert()
        if (cycling) {
            tracker.slotsInOrder().forEach { slot ->
                dispatchSlot(controller, snapshot, slot, isCycle = true)
            }
        } else {
            tracker.latestSlot()?.let { latest ->
                dispatchSlot(controller, snapshot, latest, isCycle = false)
            }
        }
        syncNotificationMonitor()
    }

    private fun applyRemovals(removals: List<NotificationSlotTracker.Removal>, isCycle: Boolean) {
        if (removals.isEmpty()) {
            syncNotificationMonitor()
            return
        }
        val controller = LightController.get(applicationContext)
        if (isCycle) {
            removals.filter { it.slotEmptied && it.slotId != null }.forEach { removal ->
                DebugLog.i(TAG, "Slot ${removal.slotId} has no remaining sources -> removing from cycle")
                controller.removeNotificationAlert(removal.slotId!!)
            }
        } else if (removals.any { it.wasLatest }) {
            DebugLog.i(TAG, "Latest standard-mode notification dismissed -> stopping lights")
            controller.clearAlert()
        }
        syncNotificationMonitor()
    }

    private fun syncNotificationMonitor() {
        if (tracker.hasRestrictedSlot()) {
            DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
        }
    }

    private fun currentShadeKeys(): Set<String>? {
        return try {
            activeNotifications?.map { it.key }?.toSet()
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSenderName(notification: Notification): String {
        val extras = notification.extras ?: return ""

        val people = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
        val personName = people?.firstOrNull { !it.name.isNullOrBlank() }?.name?.toString()
        if (!personName.isNullOrBlank()) return personName

        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) return title

        val convoTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (!convoTitle.isNullOrBlank()) return convoTitle

        return ""
    }

    private sealed interface ListenerEvent {
        data class Posted(val key: String, val pkg: String, val notification: Notification) : ListenerEvent
        data class Removed(val key: String) : ListenerEvent
        data class Reconnect(val shadeKeys: Set<String>?) : ListenerEvent
        data object SettingsChanged : ListenerEvent
        data object DaemonConnected : ListenerEvent
    }

    companion object {
        private const val TAG = "NotificationTrigger"

        @Volatile
        var isListenerConnected: Boolean = false
            private set
    }
}

