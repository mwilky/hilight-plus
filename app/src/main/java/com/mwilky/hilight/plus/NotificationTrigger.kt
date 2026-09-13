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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens for incoming system notifications and drives rear LED alerts.
 *
 * Source notification keys are tracked separately from display slots so cycling
 * can group repeats while dismissal only removes a slot when its last contributor is gone.
 */
class NotificationTrigger : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val tracker = NotificationSlotTracker()

    @Volatile
    private var lastKnownCycling: Boolean? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val controller = LightController.get(applicationContext)
            val store = AppStore.get(applicationContext)

            scope.launch {
                val behavior = store.unlockBehavior.first()
                when (action) {
                    Intent.ACTION_USER_PRESENT -> {
                        when (behavior) {
                            UnlockBehavior.NONE -> {
                                Log.d(TAG, "Screen unlocked -> UnlockBehavior.NONE: lights continue until timeout")
                            }
                            UnlockBehavior.PAUSE -> {
                                Log.i(TAG, "Screen unlocked -> UnlockBehavior.PAUSE: pausing notification lights")
                                controller.pauseAlerts()
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
                        if (behavior == UnlockBehavior.PAUSE && DevicePresence.isActivelyUsing(applicationContext)) {
                            Log.i(TAG, "Screensaver stopped -> restoring unlock pause")
                            controller.pauseAlerts()
                        }
                    }
                }
            }
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
            AppStore.get(applicationContext).isCycleNotifications.collect { cycling ->
                val previous = lastKnownCycling
                lastKnownCycling = cycling
                if (previous != null && previous != cycling) {
                    applyModeChange(cycling)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            val shadeKeys = currentShadeKeys() ?: return@launch
            applyRemovals(tracker.pruneMissing(shadeKeys), AppStore.get(applicationContext).isCycleNotifications.first())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DeviceOrientationDetector.stopMonitoring()
        runCatching { unregisterReceiver(screenStateReceiver) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        scope.launch {
            applyRemovals(listOf(tracker.remove(sbn.key)), AppStore.get(applicationContext).isCycleNotifications.first())
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!isEligiblePostedNotification(sbn)) return

        val notification = sbn.notification ?: return
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)

        scope.launch {
            val masterEnabled = store.isEnabled.first()
            val notifsEnabled = store.isNotificationsEnabled.first()
            if (!masterEnabled || !notifsEnabled) return@launch

            val unlockBehavior = store.unlockBehavior.first()
            val resolved = resolveAlert(store, pkg, notification) ?: return@launch
            val isCycle = store.isCycleNotifications.first()
            val requiresFaceDown = resolved.faceDownMode.requiresFaceDown(store.isOnlyWhenFaceDown.first())

            if (requiresFaceDown) {
                DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
            }
            val faceDown = if (requiresFaceDown || unlockBehavior != UnlockBehavior.NONE) {
                DeviceOrientationDetector.isDeviceFaceDown(applicationContext)
            } else {
                DeviceOrientationDetector.lastKnownFaceDown
            }
            if (requiresFaceDown) {
                controller.setDeviceFaceDown(faceDown)
            }

            val usingDevice = DevicePresence.isActivelyUsing(applicationContext, faceDown)
            if (usingDevice && unlockBehavior == UnlockBehavior.CLEAR) {
                Log.i(TAG, "Ignoring notification from $pkg: unlocked with UnlockBehavior.CLEAR")
                if (requiresFaceDown) syncNotificationMonitor()
                return@launch
            }
            if (unlockBehavior == UnlockBehavior.PAUSE) {
                if (usingDevice) {
                    controller.pauseAlerts()
                } else {
                    controller.resumeAlerts()
                }
            }

            val slot = tracker.add(sbn.key, resolved.slotId, resolved.pattern, resolved.color, requiresFaceDown)
            dispatchSlot(controller, store, slot, isCycle)
            syncNotificationMonitor()
        }
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
        val faceDownMode: FaceDownMode
    )

    private suspend fun resolveAlert(store: AppStore, pkg: String, notification: Notification): ResolvedAlert? {
        val senderName = extractSenderName(notification)
        val contactRule = if (senderName.isNotBlank()) store.findMessageRuleForSender(senderName) else null
        if (contactRule != null) {
            if (contactRule.pattern == PatternMode.OFF) {
                Log.i(TAG, "Priority 1 Match: Contact '${contactRule.name}' is OFF -> NO LIGHT")
                return null
            }
            Log.i(TAG, "Priority 1 Match: Contact '${contactRule.name}'")
            return ResolvedAlert("contact_${contactRule.id}", contactRule.pattern, contactRule.color, contactRule.faceDownMode)
        }

        val appRule = store.findRuleForPackage(pkg)
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
            return ResolvedAlert("app_${appRule.packageName}", appRule.pattern, color, appRule.faceDownMode)
        }

        if (!store.isDefaultNotifEnabled.first()) {
            Log.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
            return null
        }
        val defaultPattern = store.defaultNotifPattern.first()
        if (defaultPattern == PatternMode.OFF) {
            Log.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
            return null
        }
        val defaultColor = if (store.isDefaultNotifAutoColor.first()) {
            AppIconColorExtractor.extractColorForPackage(
                applicationContext,
                pkg,
                store.defaultNotifColor.first()
            )
        } else {
            store.defaultNotifColor.first()
        }
        Log.i(TAG, "Priority 3 Match: General Default ($pkg)")
        return ResolvedAlert(
            slotId = "fallback_$pkg",
            pattern = defaultPattern,
            color = defaultColor,
            faceDownMode = store.defaultNotifFaceDownMode.first()
        )
    }

    private suspend fun dispatchSlot(
        controller: LightController,
        store: AppStore,
        slot: NotificationSlotTracker.Slot,
        isCycle: Boolean
    ) {
        if (isCycle) {
            controller.postNotificationAlert(
                key = slot.id,
                pattern = slot.pattern,
                color = slot.color,
                durationMs = 0L,
                requiresFaceDown = slot.requiresFaceDown
            )
        } else {
            val durationMs = store.notificationDurationSeconds.first().coerceIn(5, 300) * 1000L
            controller.triggerAlertEffect(
                pattern = slot.pattern,
                color = slot.color,
                durationMs = durationMs,
                requiresFaceDown = slot.requiresFaceDown
            )
        }
    }

    private suspend fun applyModeChange(cycling: Boolean) {
        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)
        if (store.unlockBehavior.first() == UnlockBehavior.PAUSE &&
            DevicePresence.isActivelyUsing(applicationContext)
        ) {
            controller.pauseAlerts()
        }
        if (tracker.hasRestrictedSlot()) {
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
        }
        controller.clearAlert()
        if (cycling) {
            tracker.slotsInOrder().forEach { slot ->
                dispatchSlot(controller, store, slot, isCycle = true)
            }
        } else {
            tracker.latestSlot()?.let { latest ->
                dispatchSlot(controller, store, latest, isCycle = false)
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
        val personName = people?.firstOrNull()?.name?.toString()
        if (!personName.isNullOrBlank()) return personName

        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) return title

        val convoTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (!convoTitle.isNullOrBlank()) return convoTitle

        return ""
    }

    companion object {
        private const val TAG = "NotificationTrigger"
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
