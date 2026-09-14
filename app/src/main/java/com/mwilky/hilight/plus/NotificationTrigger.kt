package com.mwilky.hilight.plus

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mwilky.hilight.plus.core.AppIconColorExtractor
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.core.NotificationSlotTracker
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
    @Volatile
    private var lastUnlockBehavior = UnlockBehavior.NONE

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            enqueue(ListenerEvent.Screen(action))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        registerReceiver(screenStateReceiver, filter)

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
            AppStore.get(applicationContext).isCycleNotifications.collect { cycling ->
                enqueue(ListenerEvent.CycleMode(cycling))
            }
        }
        scope.launch {
            AppStore.get(applicationContext).unlockBehavior.collect { behavior ->
                lastUnlockBehavior = behavior
                syncNotificationMonitor()
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
        runCatching { unregisterReceiver(screenStateReceiver) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        enqueue(ListenerEvent.Removed(sbn.key))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!isEligiblePostedNotification(sbn)) return

        val notification = sbn.notification ?: return
        enqueue(ListenerEvent.Posted(sbn.key, pkg, notification))
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        super.onInterruptionFilterChanged(interruptionFilter)
        LightController.get(this).setDndActive(isSystemDndActive(interruptionFilter))
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
            is ListenerEvent.Screen -> handleScreen(event.action)
            is ListenerEvent.Reconnect -> {
                val shadeKeys = event.shadeKeys ?: return
                applyRemovals(
                    tracker.pruneMissing(shadeKeys),
                    AppStore.get(applicationContext).snapshot().isCycleNotifications
                )
            }
            is ListenerEvent.CycleMode -> {
                val previous = lastKnownCycling
                lastKnownCycling = event.cycling
                if (previous != null && previous != event.cycling) {
                    applyModeChange(event.cycling)
                }
            }
            is ListenerEvent.SettingsChanged -> handleSettingsChanged()
        }
    }

    private suspend fun handleScreen(action: String) {
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)
        val behavior = store.snapshot().unlockBehavior
        lastUnlockBehavior = behavior
        when (action) {
            Intent.ACTION_USER_PRESENT -> {
                when (behavior) {
                    UnlockBehavior.NONE -> {
                        Log.d(TAG, "Screen unlocked -> UnlockBehavior.NONE: lights continue until timeout")
                    }
                    UnlockBehavior.PAUSE -> {
                        val faceDown = DeviceOrientationDetector.isDeviceFaceDown(applicationContext)
                        controller.setDeviceFaceDown(faceDown)
                        if (DevicePresence.isActivelyUsing(applicationContext, faceDown)) {
                            Log.i(TAG, "Screen unlocked -> UnlockBehavior.PAUSE: pausing notification lights")
                            controller.pauseAlerts()
                        } else {
                            Log.i(TAG, "Screen unlocked face down or idle -> leaving notification lights running")
                            controller.resumeAlerts()
                        }
                        syncNotificationMonitor()
                    }
                    UnlockBehavior.CLEAR -> {
                        Log.i(TAG, "Screen unlocked -> UnlockBehavior.CLEAR: stopping lights and clearing alerts")
                        tracker.clear()
                        controller.clearAlert()
                        syncNotificationMonitor()
                    }
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                DevicePresence.dreaming = false
                if (behavior == UnlockBehavior.PAUSE) {
                    Log.i(TAG, "Screen turned off -> clearing unlock pause")
                    controller.resumeAlerts()
                }
            }
            Intent.ACTION_DREAMING_STARTED -> {
                DevicePresence.dreaming = true
                if (behavior == UnlockBehavior.PAUSE) {
                    Log.i(TAG, "Screensaver started -> clearing unlock pause")
                    controller.resumeAlerts()
                }
            }
            Intent.ACTION_DREAMING_STOPPED -> {
                DevicePresence.dreaming = false
                if (behavior == UnlockBehavior.PAUSE &&
                    DevicePresence.isActivelyUsing(
                        applicationContext,
                        DeviceOrientationDetector.lastKnownFaceDown
                    )
                ) {
                    Log.i(TAG, "Screensaver stopped -> restoring unlock pause")
                    controller.pauseAlerts()
                }
            }
        }
    }

    private suspend fun handlePosted(event: ListenerEvent.Posted) {
        val pkg = event.pkg
        val notification = event.notification
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)

        val snapshot = store.snapshot()
        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled) return

        val unlockBehavior = snapshot.unlockBehavior
        lastUnlockBehavior = unlockBehavior
        val resolved = resolveAlert(snapshot, pkg, notification) ?: return
        val dndActive = isSystemDndActive(
            getSystemService(NotificationManager::class.java).currentInterruptionFilter
        )
        controller.setDndActive(dndActive)
        val isCycle = snapshot.isCycleNotifications
        val requiresFaceDown = resolved.faceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown)

        val watchOrientation = requiresFaceDown || unlockBehavior == UnlockBehavior.PAUSE
        if (watchOrientation) {
            DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
        }
        val faceDown = if (watchOrientation || unlockBehavior != UnlockBehavior.NONE) {
            DeviceOrientationDetector.isDeviceFaceDown(applicationContext)
        } else {
            DeviceOrientationDetector.lastKnownFaceDown
        }
        if (watchOrientation) {
            controller.setDeviceFaceDown(faceDown)
        }

        val usingDevice = DevicePresence.isActivelyUsing(applicationContext, faceDown)
        if (usingDevice && unlockBehavior == UnlockBehavior.CLEAR) {
            Log.i(TAG, "Ignoring notification from $pkg: unlocked with UnlockBehavior.CLEAR")
            if (requiresFaceDown) syncNotificationMonitor()
            return
        }
        if (unlockBehavior == UnlockBehavior.PAUSE) {
            if (usingDevice) {
                controller.pauseAlerts()
            } else {
                controller.resumeAlerts()
            }
        }

        val added = bindResolved(event.key, resolved, snapshot, becomeLatest = true)
        applyBoundSlot(controller, snapshot, added, isCycle)
        syncNotificationMonitor()
    }

    private suspend fun handleSettingsChanged() {
        val snapshot = AppStore.get(applicationContext).snapshot()
        lastUnlockBehavior = snapshot.unlockBehavior
        if (!snapshot.isEnabled || !snapshot.isNotificationsEnabled) {
            Log.i(TAG, "Notifications disabled -> stopping queued lights")
            tracker.clear()
            LightController.get(applicationContext).clearAlert()
            syncNotificationMonitor()
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

    private suspend fun applyModeChange(cycling: Boolean) {
        val controller = LightController.get(applicationContext)
        val snapshot = AppStore.get(applicationContext).snapshot()
        lastUnlockBehavior = snapshot.unlockBehavior
        val faceDown = DeviceOrientationDetector.isDeviceFaceDown(applicationContext)
        if (snapshot.unlockBehavior == UnlockBehavior.PAUSE) {
            controller.setDeviceFaceDown(faceDown)
            if (DevicePresence.isActivelyUsing(applicationContext, faceDown)) {
                controller.pauseAlerts()
            } else {
                controller.resumeAlerts()
            }
        } else if (tracker.hasRestrictedSlot()) {
            controller.setDeviceFaceDown(faceDown)
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
        val watchOrientation = tracker.hasRestrictedSlot() ||
            (tracker.sourceCount > 0 && lastUnlockBehavior == UnlockBehavior.PAUSE)
        if (watchOrientation) {
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
        data class Screen(val action: String) : ListenerEvent
        data class Reconnect(val shadeKeys: Set<String>?) : ListenerEvent
        data class CycleMode(val cycling: Boolean) : ListenerEvent
        data object SettingsChanged : ListenerEvent
    }

    companion object {
        private const val TAG = "NotificationTrigger"

        @Volatile
        var isListenerConnected: Boolean = false
            private set
    }
}

internal object DevicePresence {
    @Volatile
    var dreaming = false

    fun isActivelyUsing(context: Context, faceDown: Boolean = false): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return isActivelyUsing(
            dreaming = dreaming,
            faceDown = faceDown,
            interactive = pm?.isInteractive == true,
            keyguardLocked = km?.isKeyguardLocked == true
        )
    }

    fun isActivelyUsing(
        dreaming: Boolean,
        faceDown: Boolean,
        interactive: Boolean,
        keyguardLocked: Boolean
    ): Boolean {
        if (faceDown || dreaming) return false
        return interactive && !keyguardLocked
    }
}
