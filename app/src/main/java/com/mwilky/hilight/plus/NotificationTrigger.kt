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
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens for incoming system notifications (messages, chats, apps) and triggers
 * the appropriate rear LED animation according to the 3-Tier Priority System.
 *
 * Automatically filters out:
 * - Ongoing/persistent background alerts (media player, step counters, downloads, persistent foreground services).
 * - Silent/minimized low-priority alerts (IMPORTANCE_LOW / IMPORTANCE_MIN).
 * - Deduplicates multiple notifications per application/rule so each app has exactly 1 slot in the cycle rotation.
 * - Manages Unlock Behavior (NONE, PAUSE, CLEAR) & Screen Lock / Face-Down resumption.
 */
class NotificationTrigger : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val controller = LightController.get(applicationContext)
            val store = AppStore.get(applicationContext)

            scope.launch {
                val behavior = store.unlockBehavior.first()
                when (action) {
                    Intent.ACTION_USER_PRESENT -> {
                        DeviceOrientationDetector.stopMonitoring()
                        when (behavior) {
                            UnlockBehavior.NONE -> {
                                Log.d(TAG, "Screen unlocked -> UnlockBehavior.NONE: lights continue until timeout")
                            }
                            UnlockBehavior.PAUSE -> {
                                Log.i(TAG, "Screen unlocked -> UnlockBehavior.PAUSE: pausing active lights")
                                controller.pauseAlerts()
                            }
                            UnlockBehavior.CLEAR -> {
                                Log.i(TAG, "Screen unlocked -> UnlockBehavior.CLEAR: stopping lights and clearing alerts")
                                controller.clearAlert()
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        if (behavior == UnlockBehavior.PAUSE) {
                            val globalOnlyFaceDown = store.isOnlyWhenFaceDown.first()
                            if (globalOnlyFaceDown) {
                                // Check current orientation immediately
                                val isFaceDown = DeviceOrientationDetector.isDeviceFaceDown(applicationContext)
                                if (isFaceDown) {
                                    Log.i(TAG, "Screen turned off (already face-down) -> resuming queued alerts")
                                    controller.resumeAlerts()
                                } else {
                                    Log.i(TAG, "Screen turned off (face-up) -> listening for face-down flip...")
                                    // Start monitoring so when phone is placed face-down on table, lights engage!
                                    DeviceOrientationDetector.startMonitoring(applicationContext)
                                }
                            } else {
                                Log.i(TAG, "Screen turned off -> resuming queued alerts")
                                controller.resumeAlerts()
                            }
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
        }
        registerReceiver(screenStateReceiver, filter)

        // Callback whenever phone is flipped while locked with pending alerts
        DeviceOrientationDetector.onOrientationChanged = { isFaceDown ->
            scope.launch {
                val controller = LightController.get(applicationContext)

                if (isFaceDown) {
                    Log.i(TAG, "Phone flipped face-down while locked -> starting/resuming rear lights")
                    controller.resumeAlerts()
                    // Stop monitoring once triggered so lights keep running continuously until timeout
                    DeviceOrientationDetector.stopMonitoring()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DeviceOrientationDetector.stopMonitoring()
        runCatching { unregisterReceiver(screenStateReceiver) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        scope.launch {
            // Check if there are any remaining notifications for this package in the active shade
            val hasRemainingForPkg = try {
                activeNotifications?.any { it.packageName == pkg && !it.isOngoing } == true
            } catch (_: Throwable) {
                false
            }

            if (!hasRemainingForPkg) {
                Log.i(TAG, "All notifications for $pkg dismissed -> removing from alert queue")
                LightController.get(applicationContext).removeNotificationAlert(pkg)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // 1. Ignore our own notifications
        if (pkg == packageName) return

        // 2. Ignore ongoing / persistent system alerts (e.g. Media Player, downloads, Tesla persistent connect)
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 || (sbn.notification.flags and Notification.FLAG_NO_CLEAR) != 0) {
            Log.d(TAG, "Ignoring ongoing notification from $pkg")
            return
        }

        // 3. Ignore Silent / Ambient / Minimized notifications in the shade
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true) {
            if (ranking.isAmbient || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                Log.d(TAG, "Ignoring silent/ambient notification from $pkg (importance=${ranking.importance})")
                return
            }
        }

        val notification = sbn.notification ?: return

        val controller = LightController.get(applicationContext)
        val store = AppStore.get(applicationContext)

        scope.launch {
            val masterEnabled = store.isEnabled.first()
            val notifsEnabled = store.isNotificationsEnabled.first()

            if (!masterEnabled || !notifsEnabled) return@launch

            val durationSeconds = store.notificationDurationSeconds.first().coerceIn(5, 300)
            val durationMs = durationSeconds * 1000L

            // Extract sender name / contact title
            val senderName = extractSenderName(notification)
            Log.i(TAG, "Notification received from pkg: $pkg, sender: '$senderName' (duration=${durationSeconds}s)")

            // Check if device is currently unlocked with PAUSE behavior
            val isUnlocked = isDeviceUnlocked(applicationContext)
            val unlockBehavior = store.unlockBehavior.first()
            val shouldPauseOnArrival = isUnlocked && unlockBehavior == UnlockBehavior.PAUSE

            // --- PRIORITY 1: Contact Specific Message Rule (Highest) ---
            var matchedContactRule: MessageContactRule? = null
            if (senderName.isNotBlank()) {
                matchedContactRule = store.findMessageRuleForSender(senderName)
            }

            if (matchedContactRule != null) {
                if (matchedContactRule.isEnabled && matchedContactRule.pattern != PatternMode.OFF) {
                    val isOrientationOk = isOrientationAllowed(applicationContext, store, matchedContactRule.faceDownMode)
                    val pattern = matchedContactRule.pattern
                    val color = matchedContactRule.color
                    val alertKey = "contact_${matchedContactRule.id}"
                    val isCycle = store.isCycleNotifications.first()

                    Log.i(TAG, "Priority 1 Match: Contact '${matchedContactRule.name}' -> pattern=$pattern, color=$color (unlocked=$isUnlocked, orientationOk=$isOrientationOk)")

                    // Always post to memory queue
                    if (isCycle) {
                        controller.postNotificationAlert(
                            key = alertKey,
                            pattern = pattern,
                            color = color,
                            durationMs = durationMs
                        )
                    } else {
                        controller.triggerAlertEffect(
                            pattern = pattern,
                            color = color,
                            durationMs = durationMs
                        )
                    }

                    // If screen is active/unlocked or orientation is not yet met, pause the output
                    if (shouldPauseOnArrival || !isOrientationOk) {
                        controller.pauseAlerts()
                        if (!isUnlocked && !isOrientationOk) {
                            DeviceOrientationDetector.startMonitoring(applicationContext)
                        }
                    }
                } else {
                    Log.i(TAG, "Priority 1 Match: Contact '${matchedContactRule.name}' is OFF or disabled -> NO LIGHT")
                }
                return@launch
            }

            // --- PRIORITY 2: Per-App Notification Rule ---
            val appRule = store.findRuleForPackage(pkg)
            if (appRule != null) {
                if (appRule.isEnabled && appRule.pattern != PatternMode.OFF) {
                    val isOrientationOk = isOrientationAllowed(applicationContext, store, appRule.faceDownMode)
                    val pattern = appRule.pattern
                    val color = if (appRule.isAutoColor) {
                        com.mwilky.hilight.plus.core.AppIconColorExtractor.extractColorForPackage(
                            context = applicationContext,
                            packageName = appRule.packageName,
                            fallbackColor = appRule.color
                        )
                    } else {
                        appRule.color
                    }
                    val isCycle = store.isCycleNotifications.first()

                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' ($pkg) -> pattern=$pattern, color=$color (auto=${appRule.isAutoColor}, unlocked=$isUnlocked, orientationOk=$isOrientationOk)")

                    // Always post to memory queue
                    if (isCycle) {
                        controller.postNotificationAlert(
                            key = pkg,
                            pattern = pattern,
                            color = color,
                            durationMs = durationMs
                        )
                    } else {
                        controller.triggerAlertEffect(
                            pattern = pattern,
                            color = color,
                            durationMs = durationMs
                        )
                    }

                    // If screen is active/unlocked or orientation is not yet met, pause the output
                    if (shouldPauseOnArrival || !isOrientationOk) {
                        controller.pauseAlerts()
                        if (!isUnlocked && !isOrientationOk) {
                            DeviceOrientationDetector.startMonitoring(applicationContext)
                        }
                    }
                } else {
                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' is OFF or disabled -> NO LIGHT")
                }
                return@launch
            }

            // --- PRIORITY 3: General Fallback Notification Rule ---
            val isDefaultEnabled = store.isDefaultNotifEnabled.first()
            if (isDefaultEnabled) {
                val defaultFaceDown = store.defaultNotifFaceDownMode.first()
                val isOrientationOk = isOrientationAllowed(applicationContext, store, defaultFaceDown)
                val defaultPattern = store.defaultNotifPattern.first()
                val isDefaultAutoColor = store.isDefaultNotifAutoColor.first()
                val defaultColor = if (isDefaultAutoColor) {
                    com.mwilky.hilight.plus.core.AppIconColorExtractor.extractColorForPackage(
                        context = applicationContext,
                        packageName = pkg,
                        fallbackColor = store.defaultNotifColor.first()
                    )
                } else {
                    store.defaultNotifColor.first()
                }
                if (defaultPattern != PatternMode.OFF) {
                    val isCycle = store.isCycleNotifications.first()
                    Log.i(TAG, "Priority 3 Match: General Default ($pkg) -> pattern=$defaultPattern, color=$defaultColor (auto=$isDefaultAutoColor, unlocked=$isUnlocked, orientationOk=$isOrientationOk)")

                    // Always post to memory queue
                    if (isCycle) {
                        controller.postNotificationAlert(
                            key = pkg,
                            pattern = defaultPattern,
                            color = defaultColor,
                            durationMs = durationMs
                        )
                    } else {
                        controller.triggerAlertEffect(
                            pattern = defaultPattern,
                            color = defaultColor,
                            durationMs = durationMs
                        )
                    }

                    // If screen is active/unlocked or orientation is not yet met, pause the output
                    if (shouldPauseOnArrival || !isOrientationOk) {
                        controller.pauseAlerts()
                        if (!isUnlocked && !isOrientationOk) {
                            DeviceOrientationDetector.startMonitoring(applicationContext)
                        }
                    }
                } else {
                    Log.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
                }
            } else {
                Log.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
            }
        }
    }

    private fun isDeviceUnlocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isInteractive = pm?.isInteractive == true
        val isLocked = km?.isKeyguardLocked == true

        return isInteractive && !isLocked
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

    @Suppress("DEPRECATION")
    private fun extractSenderName(notification: Notification): String {
        val extras = notification.extras ?: return ""

        val people = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
        val personName = people?.firstOrNull()?.name?.toString()
        if (!personName.isNullOrBlank()) return personName

        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) return title

        val convoTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (!convoTitle.isNullOrBlank()) return convoTitle

        return ""
    }

    companion object {
        private const val TAG = "NotificationTrigger"
    }
}
