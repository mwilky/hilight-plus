package com.mwilky.hilight.plus.adb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * Settings screens the setup steps send the user to, opened at the exact row and highlighted where
 * Settings supports it. On a large screen (the unfolded Fold) Settings opens beside the app, so the
 * checklist stays in view.
 */
object SetupIntents {

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    fun aboutPhone(context: Context): Intent =
        highlighted(context, Settings.ACTION_DEVICE_INFO_SETTINGS, "build_number")

    fun developerOptions(context: Context): Intent =
        highlighted(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "toggle_adb_wireless")

    fun wifi(context: Context): Intent =
        Intent(Settings.Panel.ACTION_WIFI).addFlags(flags(context))

    fun forStep(context: Context, step: SetupStep): Intent? = when (step) {
        SetupStep.DEV_OPTIONS -> aboutPhone(context)
        SetupStep.WIFI -> wifi(context)
        SetupStep.WIRELESS_DEBUGGING, SetupStep.PAIR -> developerOptions(context)
        SetupStep.DONE -> null
    }

    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure {
                // Highlighting is a nicety; fall back to the plain screen.
                runCatching { context.startActivity(Intent(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
    }

    private fun highlighted(context: Context, action: String, key: String): Intent =
        Intent(action)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
            .addFlags(flags(context))

    private fun flags(context: Context): Int {
        val wide = context.resources.configuration.screenWidthDp >= 600
        return Intent.FLAG_ACTIVITY_NEW_TASK or (if (wide) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0)
    }
}
