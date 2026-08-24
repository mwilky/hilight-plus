package com.hilight.plus.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hilight.plus.LightController
import com.hilight.plus.NativeHiLightDetector
import com.hilight.plus.R
import com.hilight.plus.ShizukuBridge

@Composable
fun OnboardingScreen(controller: LightController, onComplete: () -> Unit) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_complete_btn),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }

            // Conflict Warning Card
            if (stockState.anyActive) {
                ExpressiveStatusCard(
                    title = "Stock Settings Conflict",
                    subtitle = when {
                        stockState.bothActive -> "Both Favorite Calls and Assistant Feedback are active in System Settings. These will override HiLight Plus animations."
                        stockState.favoriteCallsActive -> "Stock Favorite Calls is active in System Settings and will conflict with custom caller lighting."
                        else -> "Stock Assistant Feedback is active in System Settings and will conflict with custom AI lighting."
                    },
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
                            Text("Open System Settings to Resolve")
                        }
                    }
                )
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.onboarding_info_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.onboarding_info_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Shizuku Connection Status
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            ExpressiveStatusCard(
                title = "Shizuku Privileged Access",
                subtitle = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Active session holding privileged control over your Pixel's rear light array."
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' below to grant privileged LED access."
                    ShizukuBridge.State.NOT_RUNNING -> "Shizuku daemon is stopped. Start via Wireless Debugging or ADB."
                    ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed on this device."
                    ShizukuBridge.State.CONNECTING -> "Connecting to local Shizuku binder daemon..."
                    else -> controller.shizuku.errorText() ?: "Could not establish binder connection to Shizuku."
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
                                    onClick = { controller.shizuku.refresh() },
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
        }
    }
}
