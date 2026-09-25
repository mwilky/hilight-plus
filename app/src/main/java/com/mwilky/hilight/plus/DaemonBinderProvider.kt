package com.mwilky.hilight.plus

import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.mwilky.hilight.plus.core.DaemonBinderContract

/**
 * Where a daemon the app started over Wireless debugging hands over its binder. Exported because
 * the daemon runs as the shell user, and so only accepts calls from that user.
 */
class DaemonBinderProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (Binder.getCallingUid() != Process.SHELL_UID) {
            throw SecurityException("Only the HiLight Plus daemon may call this")
        }
        if (method != DaemonBinderContract.METHOD_ATTACH || extras == null) return null
        val binder = extras.getBinder(DaemonBinderContract.EXTRA_BINDER)
        val version = extras.getInt(DaemonBinderContract.EXTRA_VERSION)
        val apkPath = extras.getString(DaemonBinderContract.EXTRA_APK_PATH)
        val context = context ?: return null
        // Through the controller (built on the main thread) so everything that reacts to a
        // connection is already listening.
        Handler(Looper.getMainLooper()).post {
            LightController.get(context).daemon.onBuiltInBinder(binder, version, apkPath)
        }
        return Bundle().apply { putBinder(DaemonBinderContract.EXTRA_CLIENT_TOKEN, clientToken) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        /** Lives as long as this process; its death tells the daemon to hand the binder over again. */
        val clientToken = Binder()
    }
}

/**
 * After a reboot or an app update, gets the daemon going again without the user opening the app.
 * With the built-in connection that means waiting for Wi-Fi and turning Wireless debugging back on.
 */
class DaemonStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DebugLog.i("DaemonStartReceiver", "${intent.action} -> connecting")
                LightController.get(context).refreshStatus()
            }
        }
    }
}
