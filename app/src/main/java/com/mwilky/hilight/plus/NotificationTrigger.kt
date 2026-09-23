package com.mwilky.hilight.plus

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
                    Log.e(TAG, "Listener event failed", t)
                }
            }
        }
        scope.launch {
            applicationContext.dataStore.data.collect {
                enqueue(ListenerEvent.SettingsChanged)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        enqueue(ListenerEvent.Reconnect(currentShadeKeys()))
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        isListenerConnected = false
        job.cancel()
        DeviceOrientationDetector.stopMonitoring()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
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
        if (posted?.category == Notification.CATEGORY_CALL) {
            if (VoipCallDetector.isIncomingCall(posted)) {
                IncomingCallProcessor.submitVoipRinging(applicationContext, sbn.key, extractSenderName(posted))
            } else {
                IncomingCallProcessor.submitVoipEnded(applicationContext, sbn.key)
            }
            return
        }

        if (!isEligiblePostedNotification(sbn)) return

        val notification = sbn.notification ?: return
        enqueue(ListenerEvent.Posted(sbn.key, pkg, notification))
    }

    private fun enqueue(event: ListenerEvent) {
        if (!events.trySend(event).isSuccess) {
            Log.w(TAG, "Dropped listener event after shutdown")
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
        }
    }

    private suspend fun handlePosted(event: ListenerEvent.Posted) {
        val pkg = event.pkg
        val notification = event.notification
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)

        val snapshot = store.snapshot()
        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled) return

        val resolved = resolveAlert(snapshot, pkg, notification) ?: return
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
            Log.i(TAG, "Notifications disabled -> stopping queued lights")
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
            val resolved = resolveAlert(snapshot, sbn.packageName, sbn.notification)
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

    private fun isEligiblePostedNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing ||
            (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (sbn.notification.flags and Notification.FLAG_NO_CLEAR) != 0 ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            Log.d(TAG, "Ignoring ongoing/summary notification from ${sbn.packageName}")
            return false
        }

        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true) {
            if (ranking.isAmbient || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                Log.d(TAG, "Ignoring silent/ambient notification from ${sbn.packageName} (importance=${ranking.importance})")
                return false
            }
        }
        return true
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

    private fun resolveAlert(snapshot: SettingsSnapshot, pkg: String, notification: Notification): ResolvedAlert? {
        val senderName = extractSenderName(notification)
        val contactRule = if (senderName.isNotBlank()) snapshot.findMessageRuleForSender(senderName) else null
        if (contactRule != null) {
            if (contactRule.pattern == PatternMode.OFF) {
                Log.i(TAG, "Priority 1 Match: Contact '${contactRule.name}' is OFF -> NO LIGHT")
                return null
            }
            Log.i(TAG, "Priority 1 Match: Contact '${contactRule.name}'")
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
                Log.i(TAG, "Favourite Match: '$senderName' but Favourite Contacts is OFF -> NO LIGHT")
                return null
            }
            Log.i(TAG, "Favourite Match: '$senderName'")
            return ResolvedAlert(
                "favourite_${senderName.trim().lowercase()}",
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
                Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' is OFF -> NO LIGHT")
                return null
            }
            val color = if (appRule.isAutoColor) {
                AppIconColorExtractor.extractColorForPackage(applicationContext, appRule.packageName, appRule.color)
            } else {
                appRule.color
            }
            Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' ($pkg)")
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
            Log.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
            return null
        }
        val defaultPattern = snapshot.defaultNotifPattern
        if (defaultPattern == PatternMode.OFF) {
            Log.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
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
        Log.i(TAG, "Priority 3 Match: General Default ($pkg)")
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
            Log.i(TAG, "Slot ${added.emptiedSlotId} emptied after rule change -> removing from cycle")
            controller.removeNotificationAlert(added.emptiedSlotId)
        }
        if (added.changed && (isCycle || added.slot.id == tracker.latestSlot()?.id)) {
            dispatchSlot(controller, snapshot, added.slot, isCycle)
        } else if (!added.changed) {
            Log.d(TAG, "Ignoring update for existing slot ${added.slot.id}")
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
                Log.i(TAG, "Slot ${removal.slotId} has no remaining sources -> removing from cycle")
                controller.removeNotificationAlert(removal.slotId!!)
            }
        } else if (removals.any { it.wasLatest }) {
            Log.i(TAG, "Latest standard-mode notification dismissed -> stopping lights")
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
    }

    companion object {
        private const val TAG = "NotificationTrigger"

        @Volatile
        var isListenerConnected: Boolean = false
            private set
    }
}

