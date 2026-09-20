@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.ui.diagnostics.CallPermissionsCard
import com.mwilky.hilight.plus.ui.diagnostics.NotificationAccessCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.ShizukuStatusCard
import com.mwilky.hilight.plus.ui.diagnostics.StockConflictCard
import com.mwilky.hilight.plus.ui.diagnostics.rememberCallPermissionLauncher
import com.mwilky.hilight.plus.ui.diagnostics.rememberPermissionState
import kotlinx.coroutines.delay

/**
 * About & Diagnostics Screen:
 * Displays App Version, System Health diagnostics (Shizuku, stock conflict resolver & permissions inspector).
 */
@Composable
fun AboutScreen(controller: LightController) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

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

    // Hidden LED index walk: ten quick taps on the title card light LEDs 0..7 in turn.
    var titleTaps by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf(0L) }
    var walkingLed by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(walkingLed == null) {
        if (walkingLed == null) return@LaunchedEffect
        repeat(LED_WALK_ROUNDS) {
            for (i in 0 until 8) {
                walkingLed = i
                controller.testSingleLed(i, LED_WALK_STEP_MS + 200L)
                delay(LED_WALK_STEP_MS)
            }
        }
        controller.cancelTestPattern()
        walkingLed = null
    }

    walkingLed?.let { index -> LedWalkDialog(index) }

    AboutContent(
        onTitleCardTap = {
            val now = System.currentTimeMillis()
            titleTaps = if (now - lastTapMs < LED_WALK_TAP_WINDOW_MS) titleTaps + 1 else 1
            lastTapMs = now
            if (titleTaps >= LED_WALK_TAP_COUNT && walkingLed == null) {
                titleTaps = 0
                walkingLed = 0
            }
        },
        shizukuState = shizukuState,
        shizukuError = controller.shizuku.errorText(),
        onDisconnectShizuku = { controller.shizuku.unbind() },
        onConnectShizuku = { controller.shizuku.connectManually() },
        onRequestShizukuPermission = { controller.shizuku.requestPermission() },
        onOpenShizukuApp = { controller.shizuku.openShizukuApp(context) },
        onRestartApp = { controller.shizuku.restartApp(context) },
        stockState = stockState,
        permissionState = permissionState,
        onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) },
        onRequestPhonePerms = requestCallPermissions,
        onOpenNotifSettings = { openNotifSettings() },
        onOpenAppSettings = { openAppSettings() }
    )
}

@Composable
private fun LedWalkDialog(index: Int) {
    val frame = remember(index) { IntArray(8) { if (it == index) 0xFFFF0000.toInt() else 0 } }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(stringResource(R.string.about_led_walk_title, index)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiffusedRingPreview(frames = frame, size = 120.dp)
                Text(stringResource(R.string.about_led_walk_desc))
            }
        }
    )
}

private const val LED_WALK_TAP_COUNT = 10
private const val LED_WALK_TAP_WINDOW_MS = 600L
private const val LED_WALK_STEP_MS = 2000L
private const val LED_WALK_ROUNDS = 2

@Composable
fun AboutContent(
    shizukuState: ShizukuBridge.State,
    onTitleCardTap: () -> Unit = {},
    shizukuError: String?,
    onDisconnectShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
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
            ListItem(
                // No ripple: the tap target is a hidden diagnostic and should not look tappable.
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleCardTap
                ),
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.about_app_desc))
                        Text(
                            stringResource(
                                R.string.about_version_format,
                                stringResource(R.string.about_version_label),
                                BuildConfig.VERSION_NAME
                            )
                        )
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge)
            ) {
                Text(stringResource(R.string.main_title), style = MaterialTheme.typography.titleLarge)
            }

            RuleGroupHeader(stringResource(R.string.about_status_section_header))

            ShizukuStatusCard(
                shizukuState = shizukuState,
                shizukuError = shizukuError,
                onDisconnect = onDisconnectShizuku,
                onConnect = onConnectShizuku,
                onRequestPermission = onRequestShizukuPermission,
                onOpenShizukuApp = onOpenShizukuApp,
                onRestartApp = onRestartApp
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
            shizukuState = ShizukuBridge.State.CONNECTED,
            shizukuError = null,
            onDisconnectShizuku = {},
            onConnectShizuku = {},
            onRequestShizukuPermission = {},
            onOpenShizukuApp = {},
            onRestartApp = {},
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
