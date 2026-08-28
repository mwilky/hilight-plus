package com.mwilky.hilight.plus

import android.app.Notification
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * Supports auto-dismissing lights when:
 * 1. The triggering notification is dismissed/removed by the user.
 * 2. The device is unlocked (ACTION_USER_PRESENT).
 */
class NotificationTrigger : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastActiveNotificationKey: String? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                scope.launch {
                    val store = AppStore.get(applicationContext)
                    if (store.isStopOnUnlock.first()) {
                        Log.i(TAG, "Device unlocked -> stopping notification lights")
                        LightController.get(applicationContext).clearAlert()
                        lastActiveNotificationKey = null
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(unlockReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(unlockReceiver) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val removedKey = sbn.key
        if (removedKey == lastActiveNotificationKey) {
            scope.launch {
                val store = AppStore.get(applicationContext)
                if (store.isStopOnDismiss.first()) {
                    Log.i(TAG, "Notification dismissed ($removedKey) -> stopping notification lights")
                    LightController.get(applicationContext).clearAlert()
                    lastActiveNotificationKey = null
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Ignore our own notifications or ongoing system alerts
        if (pkg == packageName || sbn.isOngoing) return

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

            // --- PRIORITY 1: Contact Specific Message Rule (Highest) ---
            var matchedContactRule: MessageContactRule? = null
            if (senderName.isNotBlank()) {
                matchedContactRule = store.findMessageRuleForSender(senderName)
            }

            if (matchedContactRule != null) {
                if (matchedContactRule.isEnabled && matchedContactRule.pattern != PatternMode.OFF) {
                    if (!isOrientationAllowed(applicationContext, store, matchedContactRule.faceDownMode)) {
                        Log.i(TAG, "Suppressed '${matchedContactRule.name}' message lights: phone is not face down")
                        return@launch
                    }
                    val pattern = matchedContactRule.pattern
                    val color = matchedContactRule.color
                    Log.i(TAG, "Priority 1 Match: Contact '${matchedContactRule.name}' -> pattern=$pattern, color=$color")
                    lastActiveNotificationKey = sbn.key
                    controller.triggerAlertEffect(pattern = pattern, color = color, durationMs = durationMs)
                } else {
                    Log.i(TAG, "Priority 1 Match: Contact '${matchedContactRule.name}' is OFF or disabled -> NO LIGHT")
                }
                return@launch
            }

            // --- PRIORITY 2: Per-App Notification Rule ---
            val appRule = store.findRuleForPackage(pkg)
            if (appRule != null) {
                if (appRule.isEnabled && appRule.pattern != PatternMode.OFF) {
                    if (!isOrientationAllowed(applicationContext, store, appRule.faceDownMode)) {
                        Log.i(TAG, "Suppressed '${appRule.appName}' notification lights: phone is not face down")
                        return@launch
                    }
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
                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' ($pkg) -> pattern=$pattern, color=$color (auto=${appRule.isAutoColor})")
                    lastActiveNotificationKey = sbn.key
                    controller.triggerAlertEffect(pattern = pattern, color = color, durationMs = durationMs)
                } else {
                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' is OFF or disabled -> NO LIGHT")
                }
                return@launch
            }

            // --- PRIORITY 3: General Fallback Notification Rule ---
            val isDefaultEnabled = store.isDefaultNotifEnabled.first()
            if (isDefaultEnabled) {
                val defaultFaceDown = store.defaultNotifFaceDownMode.first()
                if (!isOrientationAllowed(applicationContext, store, defaultFaceDown)) {
                    Log.i(TAG, "Suppressed General Default notification lights: phone is not face down")
                    return@launch
                }
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
                    Log.i(TAG, "Priority 3 Match: General Default ($pkg) -> pattern=$defaultPattern, color=$defaultColor (auto=$isDefaultAutoColor)")
                    lastActiveNotificationKey = sbn.key
                    controller.triggerAlertEffect(pattern = defaultPattern, color = defaultColor, durationMs = durationMs)
                } else {
                    Log.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
                }
            } else {
                Log.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
            }
        }
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
