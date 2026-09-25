package com.mwilky.hilight.plus.core

import android.content.AttributionSource
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.DebugLog
import kotlin.system.exitProcess

/**
 * Entry point when the app starts the daemon itself over Wireless debugging (`app_process`, shell
 * UID), instead of Shizuku. Hosts a [HiLightDaemonService] and hands its binder to the app through
 * [DaemonBinderContract.AUTHORITY]'s provider.
 *
 * The app process can die and come back while this keeps running (and keeps the ring's alerts),
 * so the binder is handed over again every time the app's side of the link dies. When the app
 * stays uninstalled, the daemon stops, which clears the ring.
 */
object DaemonMain {

    private const val TAG = "DaemonMain"
    private const val RESEND_DELAY_MS = 1_000L
    private const val MAX_RESEND_DELAY_MS = 30_000L
    // An app update briefly takes the provider away too, so only a lasting absence means uninstalled.
    private const val APP_GONE_GRACE_MS = 2 * 60_000L

    private lateinit var service: HiLightDaemonService
    private lateinit var handler: Handler
    private var appUid = -1
    private var appGoneSinceMs = 0L

    // Held for as long as it's linked: a proxy that gets garbage collected takes its death
    // notification with it, and the daemon would never hear that the app restarted.
    private var appToken: IBinder? = null

    private val appDeathRecipient = IBinder.DeathRecipient {
        handler.post {
            DebugLog.i(TAG, "App process died; handing the binder over again")
            appToken = null
        }
        handler.postDelayed({ sendBinder(1) }, RESEND_DELAY_MS)
    }

    /** The app's provider doesn't exist: the app isn't installed (for now, at least). */
    private class AppGoneException : Exception("HiLight Plus provider not found")

    @JvmStatic
    fun main(args: Array<String>) {
        appUid = args.getOrNull(0)?.toIntOrNull() ?: run {
            System.err.println("usage: DaemonMain <app uid>")
            exitProcess(1)
        }
        @Suppress("DEPRECATION")
        Looper.prepareMainLooper()
        handler = Handler(Looper.getMainLooper())
        DebugLog.source = "daemon"
        service = HiLightDaemonService(appUid)
        sendBinder(attempt = 1)
        Looper.loop()
    }

    /**
     * Keeps trying until the app takes the binder. A cached app process can be frozen, and calls
     * into it fail until it thaws, so a failure only means "later" - the ring keeps running
     * meanwhile. Only an app that stays uninstalled ends the daemon.
     */
    private fun sendBinder(attempt: Int) {
        val result = runCatching { deliver() }
        val clientToken = result.getOrNull()
        if (clientToken != null) {
            appGoneSinceMs = 0L
            DebugLog.i(TAG, "Binder handed to the app (attempt $attempt)")
            appToken = clientToken
            val linked = runCatching {
                clientToken.linkToDeath(appDeathRecipient, 0)
            }.isSuccess
            if (linked) return
            appToken = null
            // The app died between the reply and linking: treat it like any other death.
        }
        val error = result.exceptionOrNull()
        if (error is AppGoneException) {
            val now = SystemClock.uptimeMillis()
            if (appGoneSinceMs == 0L) appGoneSinceMs = now
            if (now - appGoneSinceMs > APP_GONE_GRACE_MS) {
                DebugLog.w(TAG, "App uninstalled; stopping")
                service.destroy()
                return
            }
        } else {
            appGoneSinceMs = 0L
        }
        if (attempt == 1 || attempt % 10 == 0) {
            DebugLog.w(TAG, "Handing the binder to the app failed (attempt $attempt): ${error?.message}")
        }
        handler.postDelayed({ sendBinder(attempt + 1) }, (RESEND_DELAY_MS * attempt).coerceAtMost(MAX_RESEND_DELAY_MS))
    }

    /**
     * Calls the app's provider as the shell user, starting the app's process if it isn't running.
     * Returns the token the app replied with, whose death means the app process is gone.
     * Hidden APIs are fine here: an `app_process` process isn't subject to the app restrictions.
     */
    private fun deliver(): IBinder? {
        val authority = DaemonBinderContract.AUTHORITY
        val activityManager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
            ?: return null
        val amClass = Class.forName("android.app.IActivityManager")
        val userId = appUid / 100_000
        val token = Binder()
        val holder = amClass.getMethod(
            "getContentProviderExternal",
            String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java
        ).invoke(activityManager, authority, userId, token, authority) ?: throw AppGoneException()
        try {
            val provider = holder.javaClass.getField("provider").get(holder) ?: throw AppGoneException()
            val extras = Bundle().apply {
                putBinder(DaemonBinderContract.EXTRA_BINDER, service)
                putInt(DaemonBinderContract.EXTRA_VERSION, BuildConfig.VERSION_CODE)
                // Set from CLASSPATH at launch. Every install gets a new path, so this also tells
                // apart two builds that share a version code.
                putString(DaemonBinderContract.EXTRA_APK_PATH, System.getProperty("java.class.path"))
            }
            val attribution = AttributionSource.Builder(Process.myUid())
                .setPackageName(SHELL_PACKAGE)
                .build()
            val reply = Class.forName("android.content.IContentProvider").getMethod(
                "call",
                AttributionSource::class.java, String::class.java, String::class.java,
                String::class.java, Bundle::class.java
            ).invoke(provider, attribution, authority, DaemonBinderContract.METHOD_ATTACH, null, extras) as Bundle?
            return reply?.getBinder(DaemonBinderContract.EXTRA_CLIENT_TOKEN)
        } finally {
            runCatching {
                amClass.getMethod(
                    "removeContentProviderExternalAsUser",
                    String::class.java, IBinder::class.java, Int::class.javaPrimitiveType
                ).invoke(activityManager, authority, token, userId)
            }
        }
    }

    private const val SHELL_PACKAGE = "com.android.shell"
}

/** The handshake between [DaemonMain] and the app's `DaemonBinderProvider`. */
object DaemonBinderContract {
    const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.daemon"
    const val METHOD_ATTACH = "attach"
    const val EXTRA_BINDER = "binder"
    const val EXTRA_VERSION = "version"
    const val EXTRA_APK_PATH = "apk_path"
    const val EXTRA_CLIENT_TOKEN = "client"
}
