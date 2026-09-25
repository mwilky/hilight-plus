package com.mwilky.hilight.plus.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.R

/**
 * Keeps the app process running while it sets up, pairs and starts the daemon. Without it, the app
 * is frozen a few seconds after going to the background (the user is in Settings, or it was woken
 * by a notification reply or boot): setting changes stop reaching it, and the daemon's binder
 * hand-over waits until it's opened.
 *
 * During setup its notification is the setup guide itself ([useNotification]), so keeping the app
 * awake adds nothing to the shade. Otherwise it shows a quiet "Connecting" one.
 *
 * A short service: Android allows at most a few minutes, far more than a start needs.
 */
class ConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyForeground()
        instance = this
        // Everything may have finished before the service got here.
        synchronized(holds) { if (holds.isEmpty()) stopNow() }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        DebugLog.w(TAG, "Connection keep-alive timed out")
        synchronized(holds) { holds.clear() }
        stopNow()
        customForeground = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun applyForeground() {
        val (id, notification) = customForeground ?: (NOTIFICATION_ID to notification(this))
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    }

    private fun stopNow() {
        // The setup guide stays behind as an ordinary notification; the quiet one goes.
        stopForeground(if (customForeground != null) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "ConnectionService"
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 4102

        /** Held for a whole setup session, while the user is in Settings. */
        const val HOLD_SETUP = "setup"

        private val holds = mutableSetOf<String>()

        @Volatile
        private var instance: ConnectionService? = null

        @Volatile
        private var customForeground: Pair<Int, Notification>? = null

        fun isHeld(reason: String): Boolean = synchronized(holds) { reason in holds }

        /** Shows [notification] as the service's own (null for the default) while it runs. */
        fun useNotification(id: Int, notification: Notification?) {
            customForeground = notification?.let { id to it }
            instance?.applyForeground()
        }

        /** Keeps the process awake for [reason] until [release]. */
        fun hold(context: Context, reason: String) {
            val first = synchronized(holds) { holds.add(reason) && holds.size == 1 }
            if (!first) return
            runCatching {
                context.startForegroundService(Intent(context, ConnectionService::class.java))
            }.onFailure {
                // Not allowed from the background right now; carry on and hope not to be frozen.
                DebugLog.w(TAG, "Couldn't start the keep-alive: ${it.message}")
                synchronized(holds) { holds.remove(reason) }
            }
        }

        fun release(reason: String) {
            val empty = synchronized(holds) { holds.remove(reason) && holds.isEmpty() }
            if (reason == HOLD_SETUP && customForeground != null) {
                customForeground = null
                if (!empty) instance?.applyForeground()
            }
            if (empty) instance?.stopNow()
        }

        private fun notification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.connection_notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = context.getString(R.string.connection_notif_channel_desc) }
                )
            }
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ring)
                .setContentTitle(context.getString(R.string.connection_notif_title))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build()
        }
    }
}
