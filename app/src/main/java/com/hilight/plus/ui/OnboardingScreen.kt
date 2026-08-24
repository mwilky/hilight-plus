package com.hilight.plus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    val ledCount by controller.shizuku.ledCount.collectAsStateWithLifecycle()

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
                    isPositive = false,
                    isWarning = true,
                    trailingAction = {
                        Button(
                            onClick = { NativeHiLightDetector.openHiLightSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Fix Settings")
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
                    ShizukuBridge.State.CONNECTED -> "Active session holding privileged control over $ledCount Pixel 11 rear LEDs."
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' to grant privileged LED access."
                    ShizukuBridge.State.NOT_RUNNING -> "Shizuku daemon is stopped. Start via Wireless Debugging or ADB."
                    ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed on this device."
                    ShizukuBridge.State.CONNECTING -> "Connecting to local Shizuku binder daemon..."
                    else -> controller.shizuku.errorText() ?: "Could not establish binder connection to Shizuku."
                },
                icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                statusText = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Connected ($ledCount LEDs)"
                    ShizukuBridge.State.CONNECTING -> "Connecting"
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Needs Permission"
                    ShizukuBridge.State.NOT_RUNNING -> "Not Running"
                    ShizukuBridge.State.NOT_INSTALLED -> "Not Installed"
                    else -> "Disconnected"
                },
                isPositive = isShizukuConnected,
                trailingAction = {
                    when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> {
                            TextButton(onClick = { controller.shizuku.unbind() }) {
                                Text("Disconnect")
                            }
                        }
                        ShizukuBridge.State.NEEDS_PERMISSION -> {
                            Button(onClick = { controller.shizuku.requestPermission() }) {
                                Text("Authorize")
                            }
                        }
                        ShizukuBridge.State.NOT_INSTALLED -> {
                            Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                Text("Install")
                            }
                        }
                        ShizukuBridge.State.NOT_RUNNING -> {
                            Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                Text("Open")
                            }
                        }
                        else -> {
                            Button(onClick = { controller.shizuku.refresh() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            )
        }
    }
}
