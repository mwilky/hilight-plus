package com.mwilky.hilight.plus.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.mwilky.hilight.plus.DebugLog

/** The system switches the built-in connection depends on. */
object DevSettings {

    private const val TAG = "DevSettings"

    /** Settings.Global.ADB_WIFI_ENABLED, which is hidden from the SDK. */
    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    /**
     * Settings.Global.ADB_ALLOWED_CONNECTION_TIME, hidden from the SDK: how long the phone keeps
     * trusting a debugging key that hasn't connected, in milliseconds (7 days unless changed). 0 is
     * Developer options' "Disable adb authorization timeout".
     */
    const val ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"

    /**
     * Whether Developer options is on, or null when the phone won't say. Never false: from Android
     * 17 QPR1, apps usually read 0 for it whatever the switch says (though not always, so no build
     * can be trusted to tell the truth). Wireless debugging being on proves it's on.
     */
    fun isDevOptionsOn(context: Context): Boolean? = devOptionsState(
        reportsOn = readsOn(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        wirelessDebuggingOn = isWirelessDebuggingOn(context)
    )

    /** [isDevOptionsOn] from what can be read; pure, for tests. */
    internal fun devOptionsState(reportsOn: Boolean, wirelessDebuggingOn: Boolean?): Boolean? =
        if (reportsOn || wirelessDebuggingOn == true) true else null

    /** Null when the setting can't be read, so callers can carry on rather than get stuck. */
    fun isWirelessDebuggingOn(context: Context): Boolean? =
        runCatching { Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1 }
            .onFailure { DebugLog.w(TAG, "Can't read $ADB_WIFI_ENABLED: ${it.message}") }
            .getOrNull()

    /**
     * USB debugging keeps the phone's debugging service (adbd) running on its own. With it off,
     * turning Wireless debugging off stops that service, and everything started through it,
     * including the lights daemon. Null when it reads off, which the phone may say whatever the
     * switch says, as for [isDevOptionsOn].
     */
    fun isUsbDebuggingOn(context: Context): Boolean? =
        if (readsOn(context, Settings.Global.ADB_ENABLED)) true else null

    /**
     * Whether the phone itself says Developer options is on, ignoring Wireless debugging: the app
     * may have switched that on itself, which proves nothing.
     */
    fun reportsDevOptionsOn(context: Context): Boolean = readsOn(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)

    private fun readsOn(context: Context, key: String): Boolean =
        Settings.Global.getInt(context.contentResolver, key, 0) == 1

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /**
     * Whether the phone keeps trusting our pairing however long it goes unused, from the raw
     * value of [ADB_ALLOWED_CONNECTION_TIME] (null when unset, the 7-day default).
     */
    fun isAdbAuthTimeoutDisabled(rawValue: String?): Boolean = rawValue?.trim()?.toLongOrNull() == 0L

    /** The raw value, or the reason it can't be read: hidden settings aren't readable everywhere. */
    fun readAdbAllowedConnectionTime(context: Context): Result<String?> =
        runCatching { Settings.Global.getString(context.contentResolver, ADB_ALLOWED_CONNECTION_TIME) }

    /** Granted by the daemon after its first successful start (see `grantWriteSecureSettings`). */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Turns Wireless debugging on, if the app has been allowed to. When the phone won't say whether
     * Developer options is on ([isDevOptionsOn] is null), only with [whenUnsure]: for a paired
     * user's start, who had it on to pair, or the lights could never come back by themselves after
     * a restart. The lights service runs with Developer options off, as long as this stays on.
     */
    fun enableWirelessDebugging(context: Context, whenUnsure: Boolean = false): Boolean {
        if (isDevOptionsOn(context) == null && !whenUnsure) return false
        return setWirelessDebugging(context, true)
    }

    /** After a start: the daemon doesn't need Wireless debugging to keep running. */
    fun disableWirelessDebugging(context: Context): Boolean = setWirelessDebugging(context, false)

    private fun setWirelessDebugging(context: Context, on: Boolean): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching { Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED, if (on) 1 else 0) }
            .onFailure { DebugLog.w(TAG, "Couldn't turn Wireless debugging ${if (on) "on" else "off"}: ${it.message}") }
            .getOrDefault(false)
    }
}
