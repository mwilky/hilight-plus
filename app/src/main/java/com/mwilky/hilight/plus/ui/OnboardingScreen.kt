package com.mwilky.hilight.plus.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge

@Composable
fun OnboardingScreen(
    controller: LightController,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isCallLogGranted by remember { mutableStateOf(hasCallLogPermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isCallLogGranted = hasCallLogPermission()
            isContactsGranted = hasContactsPermission()
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1: Shizuku Privileged Access Card
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            ExpressiveStatusCard(
                title = "Step 1: Shizuku Hardware Control",
                subtitle = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Shizuku privileged session is active and ready."
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' to grant LED hardware control."
                    ShizukuBridge.State.NOT_RUNNING -> "Shizuku is not running. Start via Wireless Debugging or ADB."
                    ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed."
                    ShizukuBridge.State.CONNECTING -> "Connecting to Shizuku daemon..."
                    else -> controller.shizuku.errorText() ?: "Could not connect to Shizuku."
                },
                icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                statusText = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Connected"
                    ShizukuBridge.State.CONNECTING -> "Connecting"
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Needs Permission"
                    ShizukuBridge.State.NOT_RUNNING -> "Not Running"
                    ShizukuBridge.State.NOT_INSTALLED -> "Not Installed"
                    else -> "Disconnected"
                },
                accentColor = if (isShizukuConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                containerColor = if (isShizukuConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                contentColor = if (isShizukuConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                bottomAction = {
                    when (shizukuState) {
                        ShizukuBridge.State.NEEDS_PERMISSION -> {
                            Button(
                                onClick = { controller.shizuku.requestPermission() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Authorize Shizuku")
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
                                    onClick = { controller.shizuku.refresh() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check Again")
                                }
                            }
                        }
                        ShizukuBridge.State.CONNECTING, ShizukuBridge.State.CONNECTED -> null
                        else -> {
                            Button(
                                onClick = { controller.shizuku.refresh() },
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

            // Step 2: Android Call Permissions Card
            val hasAllPerms = isPhoneGranted && isCallLogGranted && isContactsGranted
            val permsStatusText = when {
                hasAllPerms -> "Granted"
                !isPhoneGranted || !isCallLogGranted -> "Missing Phone/Call Permissions"
                else -> "Missing Contacts"
            }
            val permsDesc = when {
                hasAllPerms -> "Phone State, Call Log, and Contacts permissions are active. Caller identification is ready."
                !isPhoneGranted || !isCallLogGranted -> "Phone State & Call Log permissions are required to detect and identify incoming caller numbers."
                else -> "Contacts permission is missing. The app cannot match names and custom caller rules."
            }

            val permsAccent = if (hasAllPerms) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            val permsContainer = if (hasAllPerms) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            val permsContent = if (hasAllPerms) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer

            ExpressiveStatusCard(
                title = "Step 2: Phone, Call Log & Contacts",
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
                            if (!isCallLogGranted) add(Manifest.permission.READ_CALL_LOG)
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

            // Step 3: Stock Conflict Warning Card (Favorite Calls)
            if (stockState.favoriteCallsActive) {
                ExpressiveStatusCard(
                    title = "Stock Settings Conflict",
                    subtitle = "Stock Favorite Calls is active in System Settings. This will conflict with custom caller lighting in HiLight Plus.",
                    icon = Icons.Rounded.Warning,
                    statusText = "Conflict Detected",
                    accentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    isWarning = true,
                    bottomAction = {
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
                            Text("Open Settings to Disable")
                        }
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                enabled = isShizukuConnected
            ) {
                Text(stringResource(R.string.onboarding_complete_btn))
            }
        }
    }
}
