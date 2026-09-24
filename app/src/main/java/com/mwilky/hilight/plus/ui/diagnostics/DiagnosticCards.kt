@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.ui.ExpressiveStatusCard
import com.mwilky.hilight.plus.ui.StandardDiagnosticCard

@Composable
internal fun ButtonLabel(icon: ImageVector?, text: String) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(text)
}

@Composable
private fun ErrorButton(onClick: () -> Unit, icon: ImageVector?, text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        ButtonLabel(icon, text)
    }
}

/**
 * Shizuku privileged-access status: connection state, why it's not connected, and the
 * one relevant action for that state (authorize, connect, install, retry...).
 */
@Composable
fun ShizukuStatusCard(
    shizukuState: ShizukuBridge.State,
    shizukuError: String?,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit
) {
    val isConnected = shizukuState == ShizukuBridge.State.CONNECTED
    val isExplicitlyDisconnected = shizukuState == ShizukuBridge.State.DISCONNECTED

    ExpressiveStatusCard(
        title = stringResource(R.string.shizuku_card_title),
        subtitle = when (shizukuState) {
            ShizukuBridge.State.CONNECTED -> stringResource(R.string.shizuku_desc_connected)
            ShizukuBridge.State.DISCONNECTED -> stringResource(R.string.shizuku_desc_disconnected)
            ShizukuBridge.State.NEEDS_PERMISSION -> stringResource(R.string.shizuku_desc_needs_permission)
            ShizukuBridge.State.NOT_RUNNING -> stringResource(R.string.shizuku_desc_not_running)
            ShizukuBridge.State.NOT_INSTALLED -> stringResource(R.string.shizuku_desc_not_installed)
            ShizukuBridge.State.CONNECTING -> stringResource(R.string.shizuku_desc_connecting)
            else -> shizukuError ?: stringResource(R.string.shizuku_status_disconnected)
        },
        icon = if (isConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
        statusText = when (shizukuState) {
            ShizukuBridge.State.CONNECTED -> stringResource(R.string.shizuku_status_connected)
            ShizukuBridge.State.DISCONNECTED -> stringResource(R.string.shizuku_status_disconnected_paused)
            ShizukuBridge.State.CONNECTING -> stringResource(R.string.shizuku_status_connecting)
            ShizukuBridge.State.NEEDS_PERMISSION -> stringResource(R.string.shizuku_status_needs_permission)
            ShizukuBridge.State.NOT_RUNNING -> stringResource(R.string.shizuku_status_not_running)
            ShizukuBridge.State.NOT_INSTALLED -> stringResource(R.string.shizuku_status_not_installed)
            else -> stringResource(R.string.shizuku_status_disconnected)
        },
        accentColor = if (isConnected) {
            MaterialTheme.colorScheme.primary
        } else if (isExplicitlyDisconnected) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        containerColor = if (isConnected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isConnected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        bottomAction = {
            when (shizukuState) {
                ShizukuBridge.State.CONNECTED -> {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        ButtonLabel(Icons.Rounded.PowerSettingsNew, stringResource(R.string.shizuku_btn_disconnect))
                    }
                }
                ShizukuBridge.State.DISCONNECTED -> {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        ButtonLabel(Icons.Rounded.PowerSettingsNew, stringResource(R.string.shizuku_btn_connect))
                    }
                }
                ShizukuBridge.State.NEEDS_PERMISSION -> {
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        ButtonLabel(Icons.Rounded.Key, stringResource(R.string.shizuku_btn_authorize))
                    }
                }
                ShizukuBridge.State.NOT_INSTALLED -> {
                    Button(onClick = onOpenShizukuApp, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        ButtonLabel(Icons.Rounded.Download, stringResource(R.string.shizuku_btn_install))
                    }
                }
                ShizukuBridge.State.NOT_RUNNING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onOpenShizukuApp, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                            ButtonLabel(Icons.AutoMirrored.Rounded.Launch, stringResource(R.string.shizuku_btn_open))
                        }
                        OutlinedButton(onClick = onRestartApp, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                            ButtonLabel(Icons.Rounded.Refresh, stringResource(R.string.shizuku_btn_restart_app))
                        }
                    }
                }
                ShizukuBridge.State.CONNECTING -> null
                else -> {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        ButtonLabel(Icons.Rounded.Refresh, stringResource(R.string.shizuku_btn_retry))
                    }
                }
            }
        }
    )
}

/**
 * Warns when Pixel's native "Calls from favourites" HiLight setting is still on, since it
 * fights HiLight Plus for the rear lights during a call.
 */
@Composable
fun StockConflictCard(
    stockState: StockHiLightState,
    onOpenSettings: () -> Unit
) {
    val isConflict = stockState.known && stockState.favoriteCallsActive
    val isKnown = stockState.known

    StandardDiagnosticCard(
        title = stringResource(R.string.onboarding_stock_card_title),
        subtitle = when {
            isConflict -> stringResource(R.string.onboarding_stock_conflict_active_desc)
            !isKnown -> stringResource(R.string.onboarding_stock_unknown_desc)
            else -> stringResource(R.string.about_stock_ok_desc)
        },
        icon = if (isConflict || !isKnown) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        statusText = when {
            isConflict -> stringResource(R.string.onboarding_stock_status_conflict)
            !isKnown -> stringResource(R.string.onboarding_stock_status_unknown)
            else -> stringResource(R.string.onboarding_stock_status_ready)
        },
        isOk = isKnown && !isConflict,
        bottomAction = if (isConflict) {
            {
                ErrorButton(
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.Settings,
                    text = stringResource(R.string.onboarding_stock_btn_open),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else null
    )
}

/**
 * Contacts permission status, needed to tell saved callers and senders from unknown ones and
 * to pick contacts for rules.
 */
@Composable
fun CallPermissionsCard(
    state: PermissionState,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val hasAll = state.hasContactsPermission

    StandardDiagnosticCard(
        title = stringResource(R.string.onboarding_perms_calls_title),
        subtitle = if (hasAll) {
            stringResource(R.string.onboarding_perms_calls_granted_desc)
        } else {
            stringResource(R.string.onboarding_perms_calls_needed_desc)
        },
        icon = if (hasAll) Icons.Rounded.CheckCircle else Icons.Rounded.Contacts,
        statusText = if (hasAll) {
            stringResource(R.string.onboarding_perms_calls_status_granted)
        } else {
            stringResource(R.string.onboarding_perms_calls_status_needed)
        },
        isOk = hasAll,
        bottomAction = if (!hasAll) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ErrorButton(
                        onClick = onRequestPermissions,
                        icon = null,
                        text = stringResource(R.string.onboarding_perms_calls_btn_grant),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.onboarding_perms_calls_btn_app_info))
                    }
                }
            }
        } else null
    )
}

/**
 * Notification listener access status, needed to detect incoming notifications and chats.
 */
@Composable
fun NotificationAccessCard(
    state: PermissionState,
    onOpenNotifSettings: () -> Unit
) {
    val hasAccess = state.hasNotifAccess

    StandardDiagnosticCard(
        title = stringResource(R.string.onboarding_perms_notif_title),
        subtitle = when {
            !state.isNotifAccessGranted -> stringResource(R.string.onboarding_perms_notif_needed_desc)
            !state.isNotifListenerRunning -> stringResource(R.string.onboarding_perms_notif_not_running_desc)
            else -> stringResource(R.string.onboarding_perms_notif_granted_desc)
        },
        icon = if (hasAccess) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationAdd,
        statusText = when {
            !state.isNotifAccessGranted -> stringResource(R.string.onboarding_perms_notif_status_needed)
            !state.isNotifListenerRunning -> stringResource(R.string.onboarding_perms_notif_status_not_running)
            else -> stringResource(R.string.onboarding_perms_calls_status_granted)
        },
        isOk = hasAccess,
        bottomAction = if (!hasAccess) {
            {
                ErrorButton(
                    onClick = onOpenNotifSettings,
                    icon = Icons.Rounded.NotificationsActive,
                    text = stringResource(R.string.onboarding_perms_notif_btn_enable),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else null
    )
}
