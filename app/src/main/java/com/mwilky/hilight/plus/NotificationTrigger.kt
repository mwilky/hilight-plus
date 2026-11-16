package com.mwilky.hilight.plus

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.os.Parcelable
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mwilky.hilight.plus.core.AppIconColorExtractor
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.core.NotificationSlotTracker
import com.mwilky.hilight.plus.core.PocketDetector
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

    /**
     * A notification showing a timer or progress bar, as of when it was posted. [totalMs] is a
     * timer's length: what the notification said, or the most time left it has shown.
     */
    private data class ProgressItem(
        val key: String,
        val pkg: String,
        val channelId: String?,
        val reading: ProgressReading,
        val postedAtElapsedMs: Long,
        val totalMs: Long
    )

    // Every notification under way, oldest first; the newest one its app rule shows is on the
    // ring. Only touched while handling events, like the tracker.
    private val progress = LinkedHashMap<String, ProgressItem>()
    private var shownProgress: ProgressShow? = null

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
        // So an app's channel list already has whatever is in the shade, before anything new arrives.
        try {
            activeNotifications
        } catch (_: Throwable) {
            null
        }?.filter { !isBubbleOnly(it) && isEligiblePostedNotification(it, log = false) }?.forEach(::rememberChannel)
        // A timer or download already running when the listener (re)connects.
        enqueue(ListenerEvent.ProgressScan)
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
        PocketDetector.release(PocketDetector.TOKEN_NOTIFICATIONS)
        // The tracker dies with this instance, so nothing could ever remove the alerts it posted,
        // and no removal will arrive to end a ringing call: e.g. notification access was revoked
        // mid-alert. Clear both rather than leave the ring lit.
        DebugLog.w(TAG, "Notification listener destroyed -> clearing notification and call lights")
        LightController.get(applicationContext).clearAlert()
        LightController.get(applicationContext).clearProgress()
        IncomingCallProcessor.submitListenerGone(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        DebugLog.d(TAG, "Removed pkg=${sbn.packageName} key=${DebugLog.redactKey(sbn.key)}")
        IncomingCallProcessor.submitVoipRemoved(applicationContext, sbn.key)
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
                "chrono=${posted?.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)} silent=${isSilent(sbn.key)} " +
                "bubbleOnly=${isBubbleOnly(sbn)}"
        )
        val callState = posted?.let(VoipCallDetector::callState) ?: VoipCallDetector.CallState.NOT_A_CALL
        if (callState != VoipCallDetector.CallState.NOT_A_CALL) {
            when (callState) {
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

        // A timer or progress bar may show as progress on the ring instead of an ordinary alert,
        // if its app rule says so; that is decided against the settings, off this thread.
        val reading = posted?.let { readProgress(sbn, it) }
        if (posted != null && reading != null) {
            val eligible = !isBubbleOnly(sbn) && isEligiblePostedNotification(sbn, log = false)
            if (eligible) rememberChannel(sbn)
            enqueue(ListenerEvent.Progress(sbn.key, pkg, posted, reading, postedAtElapsed(sbn), eligible))
            return
        }
        // Whatever this key showed before, it shows no progress now (a download that finished).
        enqueue(ListenerEvent.NoProgress(sbn.key))

        // Swiping away a bubbled conversation (Google Messages) doesn't remove it: it is re-posted
        // with its shade entry suppressed and only the bubble left. To the user it is dismissed.
        if (isBubbleOnly(sbn)) {
            DebugLog.d(TAG, "Bubble-only notification from $pkg -> treating as dismissed")
            enqueue(ListenerEvent.Removed(sbn.key))
            return
        }

        if (!isEligiblePostedNotification(sbn)) return
        rememberChannel(sbn)

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
            is ListenerEvent.Progress -> handleProgress(event)
            is ListenerEvent.NoProgress -> if (progress.remove(event.key) != null) pushProgress()
            is ListenerEvent.ProgressScan -> scanForProgress()
            is ListenerEvent.Removed -> {
                if (progress.remove(event.key) != null) pushProgress()
                applyRemovals(
                    listOf(tracker.remove(event.key)),
                    AppStore.get(applicationContext).snapshot().isCycleNotifications
                )
            }
            is ListenerEvent.Reconnect -> {
                val shadeKeys = event.shadeKeys ?: return
                if (progress.keys.retainAll(shadeKeys)) pushProgress()
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
        if (!snapshot.isNotificationsEnabled) return

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

        // A rule may have just started or stopped showing progress, or changed its look: read the
        // shade again, which also takes progress out of the alerts below.
        scanForProgress()

        if (!snapshot.isNotificationsEnabled) {
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
            activeNotifications?.filterNot(::isBubbleOnly)?.associateBy { it.key }
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
     * Makes the daemon's alerts match the waiting slots. A daemon that kept running while this
     * process was away still holds its alerts, and any removal made meanwhile was dropped, so it
     * is cleared first rather than trusted. Then every slot still waiting is sent. Newest-only is
     * left out: its alert was timed, and the tracker still names the latest source long after
     * that time ran out, so replaying it could light something that has already finished.
     */
    private suspend fun handleDaemonConnected() {
        val snapshot = AppStore.get(applicationContext).snapshot()
        LightController.get(applicationContext).clearAlert()
        // A daemon that restarted has lost the progress; one that kept running takes it again.
        pushProgress(force = true)
        if (!snapshot.isNotificationsEnabled || !snapshot.isCycleNotifications) return
        val slots = tracker.slotsInOrder()
        if (slots.isEmpty()) return
        DebugLog.i(TAG, "Lights service connected -> replaying ${slots.size} waiting alert(s)")
        val controller = LightController.get(applicationContext)
        if (tracker.hasRestrictedSlot()) {
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
        }
        slots.forEach { dispatchSlot(controller, snapshot, it, isCycle = true) }
    }

    private fun isEligiblePostedNotification(sbn: StatusBarNotification, log: Boolean = true): Boolean {
        if (sbn.isOngoing ||
            (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (sbn.notification.flags and Notification.FLAG_NO_CLEAR) != 0 ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            if (log) DebugLog.d(TAG, "Ignoring ongoing/summary notification from ${sbn.packageName}")
            return false
        }

        // Missed calls pass even when silent (WhatsApp posts them on a low-importance channel);
        // resolveAlert only lets a silent one light through the Missed Calls rule.
        if (sbn.notification.category != Notification.CATEGORY_MISSED_CALL && isSilent(sbn.key)) {
            if (log) DebugLog.d(TAG, "Ignoring silent/ambient notification from ${sbn.packageName}")
            return false
        }
        return true
    }

    /** Notes the channel a notification that could light came on, for its app rule's channel list. */
    private fun rememberChannel(sbn: StatusBarNotification) {
        val channelId = sbn.notification?.channelId ?: return
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) != true) return
        val name = ranking.channel?.name?.toString()?.takeIf { it.isNotBlank() } ?: return
        val pkg = sbn.packageName
        scope.launch { SeenChannels.get(applicationContext).record(pkg, channelId, name) }
    }

    private fun isBubbleOnly(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        return (notification.flags and Notification.FLAG_BUBBLE) != 0 &&
            notification.bubbleMetadata?.isNotificationSuppressed == true
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
        val quietEndMinutes: Int? = null,
        val brightness: Float = DEFAULT_BRIGHTNESS,
        val speed: Float = DEFAULT_SPEED
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
                snapshot.missedCallsQuietHoursEndMinutes,
                snapshot.missedCallsBrightness,
                snapshot.missedCallsSpeed
            )
        }
        if (notification.category == Notification.CATEGORY_MISSED_CALL && isSilent(key)) {
            DebugLog.i(TAG, "Silent missed call ($pkg) with Missed Calls off -> NO LIGHT")
            return null
        }

        // Text rules are the most specific thing a user can ask for, so they sit above contacts and apps.
        val textRule = snapshot.findTextRule(pkg, extractTitle(notification), extractMessages(notification))
        if (textRule != null) {
            if (textRule.pattern == PatternMode.OFF) {
                DebugLog.i(TAG, "Text rule ${textRule.id} matched ($pkg) but is OFF -> NO LIGHT")
                return null
            }
            DebugLog.i(TAG, "Text rule ${textRule.id} matched ($pkg)")
            return ResolvedAlert(
                "text_${textRule.id}",
                textRule.pattern,
                textRule.color,
                textRule.faceDownMode,
                textRule.dndMode,
                textRule.quietHoursMode,
                textRule.quietHoursStartMinutes,
                textRule.quietHoursEndMinutes,
                textRule.brightness,
                textRule.speed
            )
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
                contactRule.quietHoursEndMinutes,
                contactRule.brightness,
                contactRule.speed
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
                snapshot.favouriteNotifQuietHoursEndMinutes,
                snapshot.favouriteNotifBrightness,
                snapshot.favouriteNotifSpeed
            )
        }

        val appRule = snapshot.findRuleForPackage(pkg)
        if (appRule != null) {
            if (!appRule.lightsChannel(notification.channelId)) {
                DebugLog.i(TAG, "Priority 2 Match: App '${appRule.appName}' but its channel is switched off -> NO LIGHT")
                return null
            }
            if (!appRule.lightsNotifications) {
                DebugLog.i(TAG, "Priority 2 Match: App '${appRule.appName}' lights for progress only -> NO LIGHT")
                return null
            }
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
                appRule.quietHoursEndMinutes,
                appRule.brightness,
                appRule.speed
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
            quietEndMinutes = snapshot.defaultNotifQuietHoursEndMinutes,
            brightness = snapshot.defaultNotifBrightness,
            speed = snapshot.defaultNotifSpeed
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
            becomeLatest,
            resolved.brightness,
            resolved.speed
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
                brightness = slot.brightness,
                speed = slot.speed,
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
                brightness = slot.brightness,
                speed = slot.speed,
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

    /** When [sbn] was posted, on the elapsed-realtime clock the daemon counts on. */
    private fun postedAtElapsed(sbn: StatusBarNotification): Long =
        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - sbn.postTime).coerceAtLeast(0L)

    /**
     * What [notification] says is under way, as of when it was posted, or null for most
     * notifications. A timer wins over a progress bar: a timer's notification can carry an empty
     * bar too.
     */
    private fun readProgress(sbn: StatusBarNotification, notification: Notification): ProgressReading? {
        val extras = notification.extras ?: return null
        val timer = (if (sbn.packageName == ProgressReader.CLOCK_PACKAGE) ProgressReader.fromSortKey(notification.sortKey) else null)
            ?: metricTimerReading(notification, postedAtElapsed(sbn), sbn.postTime)
            ?: ProgressReader.fromChronometer(
                showsChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
                countsDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                whenMs = notification.`when`,
                postedAtMs = sbn.postTime
            )
        val reading = timer ?: ProgressReader.fromProgressBar(
            progress = extras.getInt(Notification.EXTRA_PROGRESS),
            max = extras.getInt(Notification.EXTRA_PROGRESS_MAX),
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        )
        if (reading != null) DebugLog.d(TAG, "Progress in ${sbn.packageName} key=${DebugLog.redactKey(sbn.key)}: $reading")
        return reading
    }

    /**
     * A timer posted as an Android 17 metric, as Clock does: the moment it reaches zero while
     * running, or the time left while paused. It doesn't say how long the timer was set for.
     */
    private fun metricTimerReading(notification: Notification, postedAtElapsedMs: Long, postedAtMs: Long): TimerReading? {
        // Rebuilding the style is the only way to read its metrics; only worth it for this style.
        if (notification.extras?.getString(Notification.EXTRA_TEMPLATE) != Notification.MetricStyle::class.java.name) return null
        val style = runCatching {
            Notification.Builder.recoverBuilder(applicationContext, notification).style as? Notification.MetricStyle
        }.getOrNull() ?: return null
        fun timer(metric: Notification.Metric?) =
            (metric?.value as? Notification.Metric.TimeDifference)?.takeIf { it.isTimer }
        val value = timer(style.criticalMetric) ?: style.metrics.firstNotNullOfOrNull(::timer) ?: return null
        value.pausedDuration?.let { return TimerReading(running = false, remainingMs = it.toMillis()) }
        val remaining = value.zeroElapsedRealtime?.let { it - postedAtElapsedMs }
            ?: value.zeroTime?.let { it.toEpochMilli() - postedAtMs }
            ?: return null
        return TimerReading(running = true, remainingMs = remaining)
    }

    /** The app rule that shows [pkg]'s progress on [channelId], if any. */
    private fun progressRule(snapshot: SettingsSnapshot, pkg: String, channelId: String?): AppNotificationRule? =
        snapshot.findRuleForPackage(pkg)?.takeIf {
            snapshot.isNotificationsEnabled && it.showsProgress && it.lightsChannel(channelId)
        }

    private suspend fun handleProgress(event: ListenerEvent.Progress) {
        val snapshot = AppStore.get(applicationContext).snapshot()
        if (progressRule(snapshot, event.pkg, event.notification.channelId) == null) {
            // Its app doesn't show progress: an ordinary notification, as it always was.
            if (progress.remove(event.key) != null) pushProgress()
            if (event.eligible) handlePosted(ListenerEvent.Posted(event.key, event.pkg, event.notification))
            return
        }
        // It is progress now, so it stops being an alert if it was one.
        applyRemovals(listOf(tracker.remove(event.key)), snapshot.isCycleNotifications)
        remember(event.key, event.pkg, event.notification.channelId, event.reading, event.postedAtElapsedMs)
        pushProgress()
    }

    /**
     * Keeps [reading] for [key], or drops the key when it isn't under way any more: a paused or
     * finished timer. Another notification's reading says nothing about the ones kept.
     */
    private fun remember(key: String, pkg: String, channelId: String?, reading: ProgressReading, postedAtElapsedMs: Long) {
        if (reading is TimerReading && (!reading.running || postedAtElapsedMs + reading.remainingMs <= SystemClock.elapsedRealtime())) {
            progress.remove(key)
            return
        }
        // Not every timer says how long it was set for; then the most time left seen stands in.
        val seen = progress[key]?.totalMs ?: 0L
        val total = (reading as? TimerReading)?.let { maxOf(it.totalMs ?: 0L, seen, it.remainingMs) } ?: 0L
        // Replacing a key keeps its place, so an update doesn't make it the newest.
        progress[key] = ProgressItem(key, pkg, channelId, reading, postedAtElapsedMs, total)
    }

    /**
     * Reads every timer and progress bar in the shade again, for a rule that changed or a listener
     * that (re)connected. Lengths already worked out for timers are kept.
     */
    private suspend fun scanForProgress() {
        if (!isListenerConnected) return
        val shade = try {
            activeNotifications
        } catch (_: Throwable) {
            null
        } ?: return
        val snapshot = AppStore.get(applicationContext).snapshot()
        val shown = mutableSetOf<String>()
        for (sbn in shade) {
            val notification = sbn.notification ?: continue
            if (progressRule(snapshot, sbn.packageName, notification.channelId) == null) continue
            val reading = readProgress(sbn, notification) ?: continue
            remember(sbn.key, sbn.packageName, notification.channelId, reading, postedAtElapsed(sbn))
            if (progress.containsKey(sbn.key)) shown += sbn.key
        }
        progress.keys.retainAll(shown)
        if (shown.isNotEmpty()) applyRemovals(shown.map { tracker.remove(it) }, snapshot.isCycleNotifications)
        pushProgress()
    }

    /** How [item] shows through its app rule now, or null if it doesn't. */
    private fun showFor(item: ProgressItem, snapshot: SettingsSnapshot): ProgressShow? {
        val rule = progressRule(snapshot, item.pkg, item.channelId) ?: return null
        val reading = item.reading
        val endsAt = (reading as? TimerReading)?.let { item.postedAtElapsedMs + it.remainingMs } ?: 0L
        if (reading is TimerReading && endsAt <= SystemClock.elapsedRealtime()) return null
        // A progress-only rule has no colour of its own to pick, so a bar fills in the app's.
        val color = if (rule.isAutoColor || !rule.lightsNotifications) {
            AppIconColorExtractor.extractColorForPackage(applicationContext, rule.packageName, rule.color)
        } else {
            rule.color
        }
        val ownQuiet = rule.quietHoursMode == QuietHoursMode.SKIP
        return ProgressShow(
            key = item.key,
            totalMs = if (reading is TimerReading) item.totalMs else 0L,
            endsAtElapsedMs = endsAt,
            fraction = (reading as? BarReading)?.fraction ?: 0f,
            color = color,
            brightness = rule.brightness,
            requiresFaceDown = rule.faceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown),
            dndMode = rule.dndMode,
            quietHoursMode = rule.quietHoursMode,
            quietStartMinutes = if (ownQuiet) rule.quietHoursStartMinutes else null,
            quietEndMinutes = if (ownQuiet) rule.quietHoursEndMinutes else null
        )
    }

    /**
     * Puts the newest progress its rule shows on the ring, or clears the slot. Only sends when
     * that changes, unless [force]d for a daemon that may have lost it.
     */
    private suspend fun pushProgress(force: Boolean = false) {
        val snapshot = AppStore.get(applicationContext).snapshot()
        val show = progress.values.reversed().firstNotNullOfOrNull { showFor(it, snapshot) }
        val controller = LightController.get(applicationContext)
        if (show == null) {
            if (shownProgress != null || force) {
                DebugLog.i(TAG, "Progress cleared: nothing under way that a rule shows")
                controller.clearProgress()
            }
            shownProgress = null
        } else if (show != shownProgress || force) {
            if (show.requiresFaceDown) {
                DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
                controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(applicationContext))
            }
            controller.showProgress(show)
            shownProgress = show
        }
        syncNotificationMonitor()
    }

    private fun syncNotificationMonitor() {
        // A timer that has run out lights nothing, whether or not its app has removed it yet.
        val activeProgress = shownProgress?.takeIf { !it.isTimer || it.endsAtElapsedMs > SystemClock.elapsedRealtime() }
        if (tracker.hasRestrictedSlot() || activeProgress?.requiresFaceDown == true) {
            DeviceOrientationDetector.retainMonitoring(applicationContext, DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_NOTIFICATIONS)
        }
        if (tracker.sourceCount > 0 || activeProgress != null) {
            PocketDetector.retain(applicationContext, PocketDetector.TOKEN_NOTIFICATIONS)
        } else {
            PocketDetector.release(PocketDetector.TOKEN_NOTIFICATIONS)
        }
    }

    private fun currentShadeKeys(): Set<String>? {
        return try {
            activeNotifications?.filterNot(::isBubbleOnly)?.map { it.key }?.toSet()
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

    /** The bold first line; a conversation that has no title of its own uses the conversation's. */
    private fun extractTitle(notification: Notification): String? {
        val extras = notification.extras ?: return null
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Every place an app may have put the message: the collapsed text, the expanded text, an
     * inbox's lines and a conversation's newest message. A text rule matches if any of them does.
     */
    private fun extractMessages(notification: Notification): List<String> {
        val extras = notification.extras ?: return emptyList()
        return buildList {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
                ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
                ?.lastOrNull()?.text?.let { add(it.toString()) }
        }.filter { it.isNotBlank() }.distinct()
    }

    private sealed interface ListenerEvent {
        data class Posted(val key: String, val pkg: String, val notification: Notification) : ListenerEvent
        data class Removed(val key: String) : ListenerEvent
        data class Progress(
            val key: String,
            val pkg: String,
            val notification: Notification,
            val reading: ProgressReading,
            val postedAtElapsedMs: Long,
            val eligible: Boolean
        ) : ListenerEvent
        data class NoProgress(val key: String) : ListenerEvent
        data object ProgressScan : ListenerEvent
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

