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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.StockHiLightState

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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    }
                }
            }

            Text(
                text = stringResource(R.string.about_status_section_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Stock HiLight Conflict Card
            val isNativeConflict = stockState.favoriteCallsActive

            StandardDiagnosticCard(
                title = stringResource(R.string.onboarding_stock_card_title),
                subtitle = if (isNativeConflict) {
                    stringResource(R.string.onboarding_stock_conflict_active_desc)
                } else {
                    stringResource(R.string.onboarding_stock_ready_desc)
                },
                icon = if (isNativeConflict) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                statusText = if (isNativeConflict) stringResource(R.string.onboarding_stock_status_conflict) else stringResource(R.string.onboarding_stock_status_ready),
                isOk = !isNativeConflict,
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
                            Text(stringResource(R.string.onboarding_stock_btn_open))
                        }
                    }
                } else null
            )

            // 2. Call Telephony & Contacts Card
            val hasAllPhonePerms = isPhoneGranted && isCallLogGranted && isContactsGranted

            StandardDiagnosticCard(
                title = stringResource(R.string.onboarding_perms_calls_title),
                subtitle = if (hasAllPhonePerms) {
                    stringResource(R.string.onboarding_perms_calls_granted_desc)
                } else {
                    stringResource(R.string.onboarding_perms_calls_needed_desc)
                },
                icon = if (hasAllPhonePerms) Icons.Rounded.CheckCircle else Icons.Rounded.PermPhoneMsg,
                statusText = if (hasAllPhonePerms) stringResource(R.string.onboarding_perms_calls_status_granted) else stringResource(R.string.onboarding_perms_calls_status_needed),
                isOk = hasAllPhonePerms,
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
                                Text(stringResource(R.string.onboarding_perms_calls_btn_grant))
                            }
                            OutlinedButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.onboarding_perms_calls_btn_app_info))
                            }
                        }
                    }
                } else null
            )

            // 3. Notification Listener Access Card
            StandardDiagnosticCard(
                title = stringResource(R.string.onboarding_perms_notif_title),
                subtitle = if (isNotifAccessGranted) {
                    stringResource(R.string.onboarding_perms_notif_granted_desc)
                } else {
                    stringResource(R.string.onboarding_perms_notif_needed_desc)
                },
                icon = if (isNotifAccessGranted) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationAdd,
                statusText = if (isNotifAccessGranted) stringResource(R.string.onboarding_perms_calls_status_granted) else stringResource(R.string.onboarding_perms_notif_status_needed),
                isOk = isNotifAccessGranted,
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
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.onboarding_perms_notif_btn_enable))
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
