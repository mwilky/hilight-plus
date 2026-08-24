package com.hilight.plus

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationTrigger : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing) return
        // Notification rules stripped for baseline setup
    }
}
