package com.mwilky.hilight.plus

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.mwilky.hilight.plus.ui.diagnostics.isNotificationListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the file a user sends when the lights misbehave: device and permission status, the
 * daemon's live state, every setting and rule, then the whole debug log. Contact rules are listed
 * by id only, so no contact names leave the phone.
 */
internal object DebugReport {

    /** A chooser for sending the report as a text file. */
    suspend fun shareIntent(context: Context, controller: LightController): Intent {
        val file = controller.debugLog.export(header(context, controller))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.debuglog", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_debug_log_share_subject, BuildConfig.VERSION_NAME))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.about_debug_log_share_body))
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.about_debug_log_share_title))
    }

    private suspend fun header(context: Context, controller: LightController): String {
        val snapshot = controller.store.snapshot()
        val daemonState = withContext(Dispatchers.IO) { controller.shizuku.dumpDaemonState() }
        val licence = controller.licensing.status.value
        val stock = NativeHiLightDetector.state.value
        val now = System.currentTimeMillis()

        return buildString {
            appendLine("HiLight Plus debug report")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))} ${TimeZone.getDefault().id}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            appendLine()

            appendLine("== Status ==")
            appendLine("Shizuku: ${controller.shizuku.state.value}${controller.shizuku.errorText()?.let { " ($it)" } ?: ""}")
            appendLine("Notification access: granted=${isNotificationListenerEnabled(context)}, listener connected=${NotificationTrigger.isListenerConnected}")
            appendLine("Contacts permission: ${context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED}")
            appendLine("Stock favourite-calls HiLight: ${if (stock.known) stock.favoriteCallsActive else "unknown"}")
            appendLine("Licence: purchased=${licence.purchased}, trial started=${licence.trialStartMillis?.let { Date(it) }}, expired=${licence.trialExpired}, entitled=${licence.entitled}")
            appendLine()

            appendLine("== Daemon ==")
            appendLine(daemonState ?: "Not reachable")
            appendLine()

            appendLine("== Settings ==")
            appendLine("Master enabled=${snapshot.isEnabled}, only face down=${snapshot.isOnlyWhenFaceDown}, skip in DND=${snapshot.suppressDuringDnd}, quiet hours=${snapshot.quietHoursEnabled} ${time(snapshot.quietHoursStartMinutes)}-${time(snapshot.quietHoursEndMinutes)}")
            appendLine("Calls enabled=${snapshot.isCallLightsEnabled}")
            rule("Other contacts", snapshot.isOtherContactsEnabled, snapshot.otherContactsPattern, snapshot.otherContactsColor, snapshot.otherContactsFaceDownMode, snapshot.otherContactsDndMode, snapshot.otherContactsQuietHoursMode, snapshot.otherContactsQuietHoursStartMinutes, snapshot.otherContactsQuietHoursEndMinutes)
            rule("Favourite calls", snapshot.isFavouriteCallsEnabled, snapshot.favouriteCallsPattern, snapshot.favouriteCallsColor, snapshot.favouriteCallsFaceDownMode, snapshot.favouriteCallsDndMode, snapshot.favouriteCallsQuietHoursMode, snapshot.favouriteCallsQuietHoursStartMinutes, snapshot.favouriteCallsQuietHoursEndMinutes)
            rule("Unknown numbers", snapshot.isUnknownNumbersEnabled, snapshot.unknownNumbersPattern, snapshot.unknownNumbersColor, snapshot.unknownNumbersFaceDownMode, snapshot.unknownNumbersDndMode, snapshot.unknownNumbersQuietHoursMode, snapshot.unknownNumbersQuietHoursStartMinutes, snapshot.unknownNumbersQuietHoursEndMinutes)
            rule("Missed calls", snapshot.isMissedCallsEnabled, snapshot.missedCallsPattern, snapshot.missedCallsColor, snapshot.missedCallsFaceDownMode, snapshot.missedCallsDndMode, snapshot.missedCallsQuietHoursMode, snapshot.missedCallsQuietHoursStartMinutes, snapshot.missedCallsQuietHoursEndMinutes)
            snapshot.contactRules.forEach {
                rule("Call contact ${it.id}", it.isEnabled, it.pattern, it.color, it.faceDownMode, it.dndMode, it.quietHoursMode, it.quietHoursStartMinutes, it.quietHoursEndMinutes)
            }
            appendLine("Notifications enabled=${snapshot.isNotificationsEnabled}, mode=${snapshot.multiAlertMode}, duration=${snapshot.notificationDurationSeconds}s")
            rule("Default", snapshot.isDefaultNotifEnabled, snapshot.defaultNotifPattern, snapshot.defaultNotifColor, snapshot.defaultNotifFaceDownMode, snapshot.defaultNotifDndMode, snapshot.defaultNotifQuietHoursMode, snapshot.defaultNotifQuietHoursStartMinutes, snapshot.defaultNotifQuietHoursEndMinutes, "autoColor=${snapshot.isDefaultNotifAutoColor}")
            rule("Favourite senders", snapshot.isFavouriteNotifEnabled, snapshot.favouriteNotifPattern, snapshot.favouriteNotifColor, snapshot.favouriteNotifFaceDownMode, snapshot.favouriteNotifDndMode, snapshot.favouriteNotifQuietHoursMode, snapshot.favouriteNotifQuietHoursStartMinutes, snapshot.favouriteNotifQuietHoursEndMinutes)
            snapshot.messageContactRules.forEach {
                rule("Sender contact ${it.id}", it.isEnabled, it.pattern, it.color, it.faceDownMode, it.dndMode, it.quietHoursMode, it.quietHoursStartMinutes, it.quietHoursEndMinutes)
            }
            snapshot.appRules.forEach {
                rule("App ${it.packageName}", it.isEnabled, it.pattern, it.color, it.faceDownMode, it.dndMode, it.quietHoursMode, it.quietHoursStartMinutes, it.quietHoursEndMinutes, "autoColor=${it.isAutoColor}")
            }
            appendLine("Battery: ${snapshot.battery}")
            appendLine()
            appendLine("== Log ==")
        }
    }

    private fun StringBuilder.rule(
        label: String,
        enabled: Boolean,
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dnd: DndMode,
        quiet: QuietHoursMode,
        quietStart: Int?,
        quietEnd: Int?,
        extra: String? = null
    ) {
        val window = if (quietStart != null && quietEnd != null) " ${time(quietStart)}-${time(quietEnd)}" else ""
        append("  $label: ${if (enabled) "on" else "off"}, $pattern ").append("#%08X".format(color and 0xFFFFFFFFL))
        append(", faceDown=$faceDown, dnd=$dnd, quiet=$quiet$window")
        if (extra != null) append(", $extra")
        appendLine()
    }

    private fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
