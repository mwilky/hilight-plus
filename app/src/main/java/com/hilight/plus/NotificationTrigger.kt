package com.hilight.plus

import android.app.Notification
import android.app.Person
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens for incoming system notifications (messages, chats, apps) and triggers
 * the appropriate rear LED animation according to the 3-Tier Priority System
 * with user-configurable duration (in seconds/intervals).
 */
class NotificationTrigger : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

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
                    val pattern = matchedContactRule.pattern
                    val color = matchedContactRule.color
                    Log.i(TAG, "Priority 1 Match: Contact '${matchedContactRule.name}' -> pattern=$pattern, color=$color")
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
                    val pattern = appRule.pattern
                    val color = appRule.color
                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' ($pkg) -> pattern=$pattern, color=$color")
                    controller.triggerAlertEffect(pattern = pattern, color = color, durationMs = durationMs)
                } else {
                    Log.i(TAG, "Priority 2 Match: App '${appRule.appName}' is OFF or disabled -> NO LIGHT")
                }
                return@launch
            }

            // --- PRIORITY 3: General Fallback Notification Rule ---
            val isDefaultEnabled = store.isDefaultNotifEnabled.first()
            if (isDefaultEnabled) {
                val defaultPattern = store.defaultNotifPattern.first()
                val defaultColor = store.defaultNotifColor.first()
                if (defaultPattern != PatternMode.OFF) {
                    Log.i(TAG, "Priority 3 Match: General Default -> pattern=$defaultPattern, color=$defaultColor")
                    controller.triggerAlertEffect(pattern = defaultPattern, color = defaultColor, durationMs = durationMs)
                } else {
                    Log.i(TAG, "Priority 3 Match: General Default is OFF -> NO LIGHT")
                }
            } else {
                Log.i(TAG, "Priority 3 Match: General Default is disabled -> NO LIGHT")
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
