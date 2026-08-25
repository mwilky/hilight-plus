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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.LightController
import com.hilight.plus.NativeHiLightDetector
import com.hilight.plus.R
import com.hilight.plus.ShizukuBridge

/**
 * Modern Material 3 Expressive Dashboard displaying live system diagnostics,
 * Shizuku privileged session status, native Pixel conflict detection, and permission states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(controller: LightController, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isContactsGranted = hasContactsPermission()
            NativeHiLightDetector.check(context)
            controller.refreshStatus()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isPhoneGranted = hasPhonePermission()
        isContactsGranted = hasContactsPermission()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            Text(
                text = "System Diagnostics & Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Shizuku Privileged Access Status Card
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            val isExplicitlyDisconnected = shizukuState == ShizukuBridge.State.DISCONNECTED
            ExpressiveStatusCard(
                title = "Shizuku Privileged Access",
                subtitle = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Active session holding privileged control over your Pixel's rear light array."
                    ShizukuBridge.State.DISCONNECTED -> "Session is paused/disconnected. Tap 'Connect' to re-engage hardware lights."
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' below to grant privileged LED access."
                    ShizukuBridge.State.NOT_RUNNING -> "Shizuku daemon is stopped. Start via Wireless Debugging or ADB."
                    ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed on this device."
                    ShizukuBridge.State.CONNECTING -> "Connecting to local Shizuku binder daemon..."
                    else -> controller.shizuku.errorText() ?: "Could not establish binder connection to Shizuku."
                },
                icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                statusText = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Connected"
                    ShizukuBridge.State.DISCONNECTED -> "Disconnected (Paused)"
                    ShizukuBridge.State.CONNECTING -> "Connecting"
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Needs Permission"
                    ShizukuBridge.State.NOT_RUNNING -> "Not Running"
                    ShizukuBridge.State.NOT_INSTALLED -> "Not Installed"
                    else -> "Disconnected"
                },
                accentColor = if (isShizukuConnected) MaterialTheme.colorScheme.primary else if (isExplicitlyDisconnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                containerColor = if (isShizukuConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isShizukuConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                bottomAction = {
                    when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> {
                            OutlinedButton(
                                onClick = { controller.shizuku.unbind() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Disconnect Shizuku Session")
                            }
                        }
                        ShizukuBridge.State.DISCONNECTED -> {
                            Button(
                                onClick = { controller.shizuku.connectManually() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Connect Shizuku Session")
                            }
                        }
                        ShizukuBridge.State.NEEDS_PERMISSION -> {
                            Button(
                                onClick = { controller.shizuku.requestPermission() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Authorize Shizuku Access")
                            }
                        }
                        ShizukuBridge.State.NOT_INSTALLED -> {
                            Button(
                                onClick = { controller.shizuku.openShizukuApp(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Shizuku Manager")
                            }
                        }
                        ShizukuBridge.State.NOT_RUNNING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { controller.shizuku.openShizukuApp(context) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Shizuku")
                                }
                                OutlinedButton(
                                    onClick = { controller.shizuku.connectManually() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check Again")
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = { controller.shizuku.connectManually() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Retry Connection")
                            }
                        }
                    }
                }
            )

            // 2. Native Pixel HiLight Status & Conflict Card
            val isNativeConflict = stockState.anyActive
            val stockAccent = if (isNativeConflict) MaterialTheme.colorScheme.error else Color(0xFF388E3C)
            val stockContainer = if (isNativeConflict) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            val stockContent = if (isNativeConflict) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

            ExpressiveStatusCard(
                title = "Stock HiLight Integration",
                subtitle = when {
                    stockState.bothActive -> "Both Favorite Calls and Assistant Feedback are active in System Settings. These will override HiLight Plus animations."
                    stockState.favoriteCallsActive -> "Stock Favorite Calls is active in System Settings and will conflict with custom caller lighting."
                    stockState.assistantFeedbackActive -> "Stock Assistant Feedback is active in System Settings and will conflict with custom AI lighting."
                    else -> "Stock settings are cleared. HiLight Plus has full, unhindered control of the rear LED array."
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
                            onClick = { NativeHiLightDetector.openHiLightSettings(context) },
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

            // 3. Android Telephony & Contacts Permissions Card
            val hasAllPerms = isPhoneGranted && isContactsGranted
            val permsStatusText = when {
                hasAllPerms -> "Granted"
                !isPhoneGranted && !isContactsGranted -> "Missing 2 Permissions"
                !isPhoneGranted -> "Missing Phone State"
                else -> "Missing Contacts"
            }
            val permsDesc = when {
                hasAllPerms -> "Phone State and Contacts permissions are active. Incoming call detection is fully operational."
                !isPhoneGranted && !isContactsGranted -> "Phone State (call detection) and Contacts (caller matching) permissions are required for custom call lighting."
                !isPhoneGranted -> "Phone State permission is missing. The app cannot detect incoming ringing calls."
                else -> "Contacts permission is missing. The app cannot look up names and custom caller lighting rules."
            }

            val permsAccent = if (hasAllPerms) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            val permsContainer = if (hasAllPerms) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            val permsContent = if (hasAllPerms) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer

            ExpressiveStatusCard(
                title = "Call Telephony & Contacts",
                subtitle = permsDesc,
                icon = if (hasAllPerms) Icons.Rounded.ContactPhone else Icons.Rounded.PermPhoneMsg,
                statusText = permsStatusText,
                accentColor = permsAccent,
                containerColor = permsContainer,
                contentColor = permsContent,
                isWarning = !hasAllPerms,
                bottomAction = if (!hasAllPerms) {
                    {
                        val missing = mutableListOf<String>().apply {
                            if (!isPhoneGranted) add(Manifest.permission.READ_PHONE_STATE)
                            if (!isContactsGranted) add(Manifest.permission.READ_CONTACTS)
                        }.toTypedArray()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { permissionLauncher.launch(missing) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Grant Permission")
                            }
                            OutlinedButton(
                                onClick = { openAppSettings() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("App Info")
                            }
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
