package com.mwilky.hilight.plus.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mwilky.hilight.plus.NotificationTrigger

/**
 * Snapshot of the runtime permissions HiLight Plus needs: phone/call-log/contacts
 * for caller identification, and notification listener access for notifications & chats.
 */
@Stable
class PermissionState internal constructor(
    private val context: Context,
    isPhoneGranted: Boolean = false,
    isCallLogGranted: Boolean = false,
    isContactsGranted: Boolean = false,
    isNotifAccessGranted: Boolean = false,
    isNotifListenerRunning: Boolean = false
) {
    var isPhoneGranted by mutableStateOf(isPhoneGranted)
        private set
    var isCallLogGranted by mutableStateOf(isCallLogGranted)
        private set
    var isContactsGranted by mutableStateOf(isContactsGranted)
        private set
    var isNotifAccessGranted by mutableStateOf(isNotifAccessGranted)
        private set
    var isNotifListenerRunning by mutableStateOf(isNotifListenerRunning)
        private set

    val hasAllCallPermissions: Boolean
        get() = isPhoneGranted && isCallLogGranted && isContactsGranted

    val hasNotifAccess: Boolean
        get() = isNotifAccessGranted && isNotifListenerRunning

    fun refresh() {
        isPhoneGranted = context.hasPermission(Manifest.permission.READ_PHONE_STATE)
        isCallLogGranted = context.hasPermission(Manifest.permission.READ_CALL_LOG)
        isContactsGranted = context.hasPermission(Manifest.permission.READ_CONTACTS)
        isNotifAccessGranted = isNotificationListenerEnabled(context)
        isNotifListenerRunning = isNotificationListenerRunning()
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(context.packageName)
}

fun isNotificationListenerRunning(): Boolean = NotificationTrigger.isListenerConnected

/**
 * Tracks call/contacts/notification-listener permission state, refreshing whenever the
 * screen resumes (e.g. returning from Settings after granting a permission there).
 */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val state = remember(context) { PermissionState(context).apply { refresh() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.refresh()
        }
    }
    return state
}

/** Launches the system permission dialog for whichever call/contacts permissions are still missing. */
@Composable
fun rememberCallPermissionLauncher(state: PermissionState): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        state.refresh()
    }
    return {
        val missing = buildList {
            if (!state.isPhoneGranted) add(Manifest.permission.READ_PHONE_STATE)
            if (!state.isCallLogGranted) add(Manifest.permission.READ_CALL_LOG)
            if (!state.isContactsGranted) add(Manifest.permission.READ_CONTACTS)
        }.toTypedArray()
        launcher.launch(missing)
    }
}
