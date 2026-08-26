package com.hilight.plus.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.LightController
import com.hilight.plus.NativeHiLightDetector
import com.hilight.plus.R
import com.hilight.plus.StockHiLightState

/**
 * About & Diagnostics Screen:
 * Displays App Version, System Health diagnostics (Stock conflict resolver & Permissions inspector),
 * and the Reset Onboarding action.
 */
@Composable
fun AboutScreen(controller: LightController, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isCallLogGranted by remember { mutableStateOf(hasCallLogPermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }
    var isNotifAccessGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isCallLogGranted = hasCallLogPermission()
            isContactsGranted = hasContactsPermission()
            isNotifAccessGranted = isNotificationListenerEnabled(context)
            NativeHiLightDetector.check(context)
            controller.refreshStatus()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isPhoneGranted = hasPhonePermission()
        isCallLogGranted = hasCallLogPermission()
        isContactsGranted = hasContactsPermission()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openNotifSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    AboutContent(
        stockState = stockState,
        isPhoneGranted = isPhoneGranted,
        isCallLogGranted = isCallLogGranted,
        isContactsGranted = isContactsGranted,
        isNotifAccessGranted = isNotifAccessGranted,
        onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) },
        onRequestPhonePerms = {
            val missing = mutableListOf<String>().apply {
                if (!isPhoneGranted) add(Manifest.permission.READ_PHONE_STATE)
                if (!isCallLogGranted) add(Manifest.permission.READ_CALL_LOG)
                if (!isContactsGranted) add(Manifest.permission.READ_CONTACTS)
            }.toTypedArray()
            permissionLauncher.launch(missing)
        },
        onOpenNotifSettings = { openNotifSettings() },
        onOpenAppSettings = { openAppSettings() },
        onResetAll = onResetAll
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    stockState: StockHiLightState,
    isPhoneGranted: Boolean,
    isCallLogGranted: Boolean,
    isContactsGranted: Boolean,
    isNotifAccessGranted: Boolean,
    onOpenSettings: () -> Unit,
    onRequestPhonePerms: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onResetAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About & System") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            text = "HiLight Plus",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version 1.0.0 • Pixel 11 Pro LED Controller",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "System Diagnostics & Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Stock HiLight Conflict Card
            val isNativeConflict = stockState.favoriteCallsActive
            val stockAccent = if (isNativeConflict) MaterialTheme.colorScheme.error else Color(0xFF388E3C)
            val stockContainer = if (isNativeConflict) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            val stockContent = if (isNativeConflict) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

            ExpressiveStatusCard(
                title = "Stock Favorite Calls",
                subtitle = if (isNativeConflict) {
                    "Stock Favorite Calls is active in System Settings and will conflict with custom caller lighting."
                } else {
                    "Stock Favorite Calls setting is disabled. HiLight Plus has unhindered control over caller lighting."
                },
                icon = if (isNativeConflict) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                statusText = if (isNativeConflict) "Conflict Active" else "Optimized",
                accentColor = stockAccent,
                containerColor = stockContainer,
                contentColor = stockContent,
                isWarning = isNativeConflict,
                bottomAction = if (isNativeConflict) {
                    {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open System Settings to Resolve")
                        }
                    }
                } else null
            )

            // 2. Call Telephony & Contacts Card
            val hasAllPhonePerms = isPhoneGranted && isCallLogGranted && isContactsGranted
            val phoneStatusText = when {
                hasAllPhonePerms -> "Granted"
                !isPhoneGranted || !isCallLogGranted -> "Missing Phone/Call Permissions"
                else -> "Missing Contacts"
            }
            val phoneDesc = when {
                hasAllPhonePerms -> "Phone State, Call Log, and Contacts permissions are active. Caller identification is operational."
                !isPhoneGranted || !isCallLogGranted -> "Phone State & Call Log permissions are required to identify incoming caller numbers."
                else -> "Contacts permission is missing. The app cannot match names and custom caller lighting rules."
            }

            ExpressiveStatusCard(
                title = "Call Telephony & Contacts",
                subtitle = phoneDesc,
                icon = if (hasAllPhonePerms) Icons.Rounded.ContactPhone else Icons.Rounded.PermPhoneMsg,
                statusText = phoneStatusText,
                accentColor = if (hasAllPhonePerms) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                containerColor = if (hasAllPhonePerms) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                contentColor = if (hasAllPhonePerms) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                isWarning = !hasAllPhonePerms,
                bottomAction = if (!hasAllPhonePerms) {
                    {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRequestPhonePerms,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Grant Permission")
                            }
                            OutlinedButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("App Info")
                            }
                        }
                    }
                } else null
            )

            // 3. Notification Listener Access Card
            ExpressiveStatusCard(
                title = "Notification Listener Access",
                subtitle = if (isNotifAccessGranted) {
                    "Notification Listener service is active. Incoming app notifications and chats trigger seamlessly."
                } else {
                    "Notification Listener access is required to detect app notifications and contact messages."
                },
                icon = if (isNotifAccessGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd,
                statusText = if (isNotifAccessGranted) "Granted" else "Access Needed",
                accentColor = if (isNotifAccessGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                containerColor = if (isNotifAccessGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                contentColor = if (isNotifAccessGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                isWarning = !isNotifAccessGranted,
                bottomAction = if (!isNotifAccessGranted) {
                    {
                        Button(
                            onClick = onOpenNotifSettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Rounded.NotificationAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Grant Notification Access")
                        }
                    }
                } else null
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.main_reset_onboarding))
            }
        }
    }
}

@Preview(name = "About Screen Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AboutScreenPreview() {
    HiLightPlusTheme {
        AboutContent(
            stockState = StockHiLightState(favoriteCallsActive = false),
            isPhoneGranted = true,
            isCallLogGranted = true,
            isContactsGranted = true,
            isNotifAccessGranted = true,
            onOpenSettings = {},
            onRequestPhonePerms = {},
            onOpenNotifSettings = {},
            onOpenAppSettings = {},
            onResetAll = {}
        )
    }
}
