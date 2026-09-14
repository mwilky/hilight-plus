package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.ui.diagnostics.CallPermissionsCard
import com.mwilky.hilight.plus.ui.diagnostics.NotificationAccessCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.StockConflictCard
import com.mwilky.hilight.plus.ui.diagnostics.rememberCallPermissionLauncher
import com.mwilky.hilight.plus.ui.diagnostics.rememberPermissionState

/**
 * About & Diagnostics Screen:
 * Displays App Version, System Health diagnostics (Stock conflict resolver & Permissions inspector).
 */
@Composable
fun AboutScreen(controller: LightController) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState()
    val requestCallPermissions = rememberCallPermissionLauncher(permissionState)

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
        permissionState = permissionState,
        onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) },
        onRequestPhonePerms = requestCallPermissions,
        onOpenNotifSettings = { openNotifSettings() },
        onOpenAppSettings = { openAppSettings() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    stockState: StockHiLightState,
    permissionState: PermissionState,
    onOpenSettings: () -> Unit,
    onRequestPhonePerms: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                scrollBehavior = scrollBehavior
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
                            text = stringResource(R.string.main_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.about_app_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.about_version_format, stringResource(R.string.about_version_label), BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.about_status_section_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            StockConflictCard(
                stockState = stockState,
                onOpenSettings = onOpenSettings
            )

            CallPermissionsCard(
                state = permissionState,
                onRequestPermissions = onRequestPhonePerms,
                onOpenAppSettings = onOpenAppSettings
            )

            NotificationAccessCard(
                state = permissionState,
                onOpenNotifSettings = onOpenNotifSettings
            )
        }
    }
}

@Preview(name = "About Screen Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AboutScreenPreview() {
    HiLightPlusTheme {
        AboutContent(
            stockState = StockHiLightState(favoriteCallsActive = false, known = true),
            permissionState = PermissionState(
                context = LocalContext.current,
                isPhoneGranted = true,
                isCallLogGranted = true,
                isContactsGranted = true,
                isNotifAccessGranted = true,
                isNotifListenerRunning = true
            ),
            onOpenSettings = {},
            onRequestPhonePerms = {},
            onOpenNotifSettings = {},
            onOpenAppSettings = {}
        )
    }
}
