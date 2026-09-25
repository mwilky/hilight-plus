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

    fun isDevOptionsOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /** Null when the setting can't be read, so callers can carry on rather than get stuck. */
    fun isWirelessDebuggingOn(context: Context): Boolean? =
        runCatching { Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1 }
            .onFailure { DebugLog.w(TAG, "Can't read $ADB_WIFI_ENABLED: ${it.message}") }
            .getOrNull()

    /**
     * USB debugging keeps the phone's debugging service (adbd) running on its own. With it off,
     * turning Wireless debugging off stops that service, and everything started through it,
     * including the lights daemon.
     */
    fun isUsbDebuggingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** Granted by the daemon after its first successful start (see `grantWriteSecureSettings`). */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Turns Wireless debugging on, if the app has been allowed to. Only when Developer options is
     * already on: the app never switches that back on behind the user's back.
     */
    fun enableWirelessDebugging(context: Context): Boolean = setWirelessDebugging(context, true)

    /** After a start: the daemon doesn't need Wireless debugging to keep running. */
    fun disableWirelessDebugging(context: Context): Boolean = setWirelessDebugging(context, false)

    private fun setWirelessDebugging(context: Context, on: Boolean): Boolean {
        if (!canWriteSecureSettings(context) || !isDevOptionsOn(context)) return false
        return runCatching { Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED, if (on) 1 else 0) }
            .onFailure { DebugLog.w(TAG, "Couldn't turn Wireless debugging ${if (on) "on" else "off"}: ${it.message}") }
            .getOrDefault(false)
    }
}
