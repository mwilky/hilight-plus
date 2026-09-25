package com.mwilky.hilight.plus.adb

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.app.Application
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.MainActivity
import com.mwilky.hilight.plus.R

/**
 * The single notification that walks the user through setup while they're in Settings. It's
 * updated in place, and only offers a code field once the pairing dialog is actually open.
 */
class SetupNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private var lastNotice: GuideNotice? = null
    private var lastAcceptsCode = false

    /** [force] re-posts even when nothing changed, which a reply needs to clear its spinner. */
    fun show(state: SetupState, force: Boolean = false) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notice = state.guideNotice()
        val acceptsCode = state.acceptsCode()
        // Re-posting an unchanged notification would restart its heads-up.
        if (!force && notice == lastNotice && acceptsCode == lastAcceptsCode) return
        lastNotice = notice
        lastAcceptsCode = acceptsCode
        val keepingAwake = ConnectionService.isHeld(ConnectionService.HOLD_SETUP)
        if (notice == GuideNotice.CONNECTING && !keepingAwake) {
            // ConnectionService's own notification shows the progress while it keeps the app awake.
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel()

        val (titleRes, textRes) = when (notice) {
            GuideNotice.TAP_BUILD_NUMBER -> R.string.setup_notif_build_title to R.string.setup_notif_build_text
            GuideNotice.CONNECT_WIFI -> R.string.setup_notif_wifi_title to R.string.setup_notif_wifi_text
            GuideNotice.TURN_ON_WIRELESS_DEBUGGING -> R.string.setup_notif_wireless_title to R.string.setup_notif_wireless_text
            GuideNotice.TAP_PAIR -> R.string.setup_notif_pair_title to R.string.setup_notif_pair_text
            GuideNotice.ENTER_CODE -> R.string.setup_notif_code_title to R.string.setup_notif_code_text
            GuideNotice.CONNECTING -> R.string.setup_notif_connecting_title to R.string.setup_notif_connecting_text
            GuideNotice.WRONG_CODE -> R.string.setup_notif_wrong_title to R.string.setup_notif_wrong_text
            GuideNotice.FAILED -> R.string.setup_notif_failed_title to R.string.setup_notif_failed_text
            GuideNotice.DONE -> R.string.setup_notif_done_title to R.string.setup_notif_done_text
        }

        // Replaces whatever Settings screen the user is on: with Settings already open, Android
        // would otherwise just bring that screen back instead of opening this one.
        val settingsIntent = SetupIntents.forStep(context, state.step)?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = if (settingsIntent != null && notice != GuideNotice.FAILED) {
            PendingIntent.getActivity(context, REQUEST_SETTINGS, settingsIntent, PENDING_FLAGS)
        } else {
            appIntent()
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ring)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(textRes)))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(notice != GuideNotice.DONE && notice != GuideNotice.FAILED)
            .setAutoCancel(notice == GuideNotice.DONE || notice == GuideNotice.FAILED)
            // While it's also the keep-awake service's notification, Android would otherwise hold
            // it back for up to 10 seconds.
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        if (notice == GuideNotice.CONNECTING) builder.setProgress(0, 0, true)
        if (acceptsCode) builder.addAction(codeAction())
        if (notice == GuideNotice.FAILED) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_ring),
                    context.getString(R.string.setup_notif_open_app),
                    appIntent()
                ).build()
            )
        }
        val notification = builder.build()
        if (notice == GuideNotice.DONE) {
            // Its own ordinary notification, so it stays once setup stops keeping the app awake.
            manager.cancel(NOTIFICATION_ID)
            manager.notify(DONE_NOTIFICATION_ID, notification)
            return
        }
        if (keepingAwake) ConnectionService.useNotification(NOTIFICATION_ID, notification)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        lastNotice = null
        lastAcceptsCode = false
        manager.cancel(NOTIFICATION_ID)
        manager.cancel(DONE_NOTIFICATION_ID)
    }

    private fun codeAction(): Notification.Action {
        val input = RemoteInput.Builder(KEY_CODE)
            .setLabel(context.getString(R.string.setup_notif_code_hint))
            .build()
        val intent = Intent(context, PairingCodeReceiver::class.java).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stat_ring),
            context.getString(R.string.setup_notif_code_action),
            pending
        ).addRemoteInput(input).setAllowGeneratedReplies(false).build()
    }

    private fun appIntent(): PendingIntent =
        PendingIntent.getActivity(
            context, REQUEST_APP,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PENDING_FLAGS
        )

    fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.setup_notif_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.setup_notif_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val KEY_CODE = "pairing_code"
        private const val CHANNEL_ID = "setup"
        private const val NOTIFICATION_ID = 4101
        private const val NETWORK_NOTIFICATION_ID = 4103
        private const val DONE_NOTIFICATION_ID = 4104
        private const val REQUEST_NETWORK = 4

        /**
         * After a reboot (or update) on a Wi-Fi network that hasn't been allowed for Wireless
         * debugging: how to allow it. Tapping opens Developer options at the switch.
         */
        fun showNetworkApproval(context: Context) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            SetupNotifier(context).ensureChannel()
            val open = PendingIntent.getBroadcast(
                context, REQUEST_NETWORK,
                Intent(context, AllowNetworkReceiver::class.java).setPackage(context.packageName),
                PENDING_FLAGS
            )
            val text = context.getString(R.string.setup_notif_network_text)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ring)
                .setContentTitle(context.getString(R.string.setup_notif_network_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NETWORK_NOTIFICATION_ID, notification)
        }

        fun cancelNetworkApproval(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NETWORK_NOTIFICATION_ID)
        }
        private const val REQUEST_SETTINGS = 1
        private const val REQUEST_CODE = 2
        private const val REQUEST_APP = 3
        private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}

/** The "Allow Wireless debugging on this Wi-Fi" notification was tapped: have Android ask again. */
class AllowNetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LightController.get(context).daemon.connectManually()
    }
}

/** Receives the code typed into the guide notification. */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(SetupNotifier.KEY_CODE)?.toString() ?: return
        ConnectSetup.get(context.applicationContext as Application).submitCode(code)
    }
}
