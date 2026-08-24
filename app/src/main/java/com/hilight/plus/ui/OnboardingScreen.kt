package com.hilight.plus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
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

            // Conflict Warning Card (if stock features are active)
            if (stockState.anyActive) {
                val warningDesc = when {
                    stockState.bothActive -> stringResource(R.string.stock_hilight_warning_both)
                    stockState.favoriteCallsActive -> stringResource(R.string.stock_hilight_warning_calls)
                    else -> stringResource(R.string.stock_hilight_warning_assistant)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.stock_hilight_warning_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = androidx.core.text.HtmlCompat.fromHtml(
                                warningDesc,
                                androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
                            ).toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { NativeHiLightDetector.openHiLightSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.stock_hilight_open_settings))
                        }
                    }
                }
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

            // Shizuku Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_shizuku_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when (shizukuState) {
                            ShizukuBridge.State.CONNECTED -> "Renderer running in Shizuku's shell-UID process."
                            ShizukuBridge.State.NEEDS_PERMISSION -> "Running. Approve this app to use it."
                            ShizukuBridge.State.NOT_INSTALLED -> "No computer needed — start it via Wireless debugging."
                            ShizukuBridge.State.NOT_RUNNING -> "Start it under Wireless debugging. Needed again after each reboot."
                            ShizukuBridge.State.CONNECTING -> "Connecting to Shizuku daemon…"
                            else -> controller.shizuku.errorText() ?: "Could not reach Shizuku."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (shizukuState == ShizukuBridge.State.CONNECTED) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = when (shizukuState) {
                                    ShizukuBridge.State.CONNECTED -> "connected ($ledCount LEDs)"
                                    ShizukuBridge.State.CONNECTING -> "connecting"
                                    ShizukuBridge.State.NEEDS_PERMISSION -> "approve it"
                                    ShizukuBridge.State.NOT_INSTALLED -> "not installed"
                                    ShizukuBridge.State.NOT_RUNNING -> "not running"
                                    else -> "failed"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (shizukuState == ShizukuBridge.State.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        when (shizukuState) {
                            ShizukuBridge.State.CONNECTED -> {
                                TextButton(onClick = { controller.shizuku.unbind() }) {
                                    Text("Disconnect")
                                }
                            }
                            ShizukuBridge.State.NEEDS_PERMISSION -> {
                                Button(onClick = { controller.shizuku.requestPermission() }) {
                                    Text("Request access")
                                }
                            }
                            ShizukuBridge.State.NOT_INSTALLED -> {
                                Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                    Text("Get Shizuku")
                                }
                            }
                            ShizukuBridge.State.NOT_RUNNING -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                        Text("Open Shizuku")
                                    }
                                    TextButton(onClick = { controller.shizuku.refresh() }) {
                                        Text("Check again")
                                    }
                                }
                            }
                            else -> {
                                Button(onClick = { controller.shizuku.refresh() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
