@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.DebugLogStore
import com.mwilky.hilight.plus.DebugReport
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.ui.diagnostics.ButtonLabel
import com.mwilky.hilight.plus.ui.diagnostics.CallPermissionsCard
import com.mwilky.hilight.plus.ui.diagnostics.NotificationAccessCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.ConnectionStatusCard
import com.mwilky.hilight.plus.ui.diagnostics.StockConflictCard
import com.mwilky.hilight.plus.ui.diagnostics.rememberCallPermissionLauncher
import com.mwilky.hilight.plus.ui.diagnostics.rememberPermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * About & Diagnostics Screen:
 * Displays App Version, System Health diagnostics (ring connection, stock conflict resolver & permissions inspector).
 */
@Composable
fun AboutScreen(controller: LightController, onSetUpConnection: () -> Unit) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val connectionState by controller.daemon.state.collectAsStateWithLifecycle()
    val connectionMethod by controller.daemon.method.collectAsStateWithLifecycle()

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

    // Collected only while this screen is showing, so the log view updates live while watched.
    val logLines by controller.debugLog.lines.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isSharingLog by remember { mutableStateOf(false) }

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
        connectionState = connectionState,
        connectionMethod = connectionMethod,
        connectionError = controller.daemon.errorText(),
        onPauseConnection = { controller.daemon.unbind() },
        onResumeConnection = { controller.daemon.connectManually() },
        onRequestShizukuPermission = { controller.daemon.requestPermission() },
        onOpenShizukuApp = { controller.daemon.openShizukuApp(context) },
        onRestartApp = { controller.daemon.restartApp(context) },
        onSetUpConnection = onSetUpConnection,
        stockState = stockState,
        permissionState = permissionState,
        onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) },
        onRequestPhonePerms = requestCallPermissions,
        onOpenNotifSettings = { openNotifSettings() },
        onOpenAppSettings = { openAppSettings() },
        logLines = logLines,
        isSharingLog = isSharingLog,
        onShareLog = {
            isSharingLog = true
            scope.launch {
                try {
                    context.startActivity(DebugReport.shareIntent(context, controller))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "Couldn't share the debug log", t)
                    Toast.makeText(context, R.string.about_debug_log_share_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    isSharingLog = false
                }
            }
        },
        onClearLog = { controller.debugLog.clear() },
        onResetLights = {
            scope.launch { controller.daemon.resetDaemon() }
            Toast.makeText(context, R.string.about_debug_log_reset_started, Toast.LENGTH_SHORT).show()
        }
    )
}

private const val TAG = "AboutScreen"

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
    connectionState: DaemonBridge.State,
    connectionMethod: DaemonBridge.Method,
    onTitleCardTap: () -> Unit = {},
    connectionError: String?,
    onPauseConnection: () -> Unit,
    onResumeConnection: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
    onSetUpConnection: () -> Unit,
    stockState: StockHiLightState,
    permissionState: PermissionState,
    onOpenSettings: () -> Unit,
    onRequestPhonePerms: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    logLines: List<DebugLogStore.Line>,
    isSharingLog: Boolean,
    onShareLog: () -> Unit,
    onClearLog: () -> Unit,
    onResetLights: () -> Unit
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

            ConnectionStatusCard(
                connectionState = connectionState,
                connectionMethod = connectionMethod,
                connectionError = connectionError,
                onDisconnect = onPauseConnection,
                onConnect = onResumeConnection,
                onRequestPermission = onRequestShizukuPermission,
                onOpenShizukuApp = onOpenShizukuApp,
                onRestartApp = onRestartApp,
                onSetUp = onSetUpConnection
            )

            if (connectionMethod == DaemonBridge.Method.SHIZUKU) {
                ListItem(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable(onClick = onSetUpConnection),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    },
                    supportingContent = { Text(stringResource(R.string.about_switch_builtin_desc)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge)
                ) {
                    Text(stringResource(R.string.about_switch_builtin_title))
                }
            }

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

            RuleGroupHeader(stringResource(R.string.about_debug_log_header))

            DebugLogCard(
                lines = logLines,
                isSharing = isSharingLog,
                canResetLights = connectionState == DaemonBridge.State.CONNECTED,
                onShare = onShareLog,
                onClear = onClearLog,
                onResetLights = onResetLights
            )
        }
    }
}

@Composable
private fun DebugLogCard(
    lines: List<DebugLogStore.Line>,
    isSharing: Boolean,
    canResetLights: Boolean,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onResetLights: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = lines.lastIndex.coerceAtLeast(0))
    // Follow new lines while the view is at the bottom; stop once the user scrolls up to read.
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followTail = !listState.canScrollForward
        }
    }
    LaunchedEffect(lines.lastOrNull()?.id) {
        if (followTail && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    // Same card as the status cards above it: icon, title and pill, description, then the log.
    ExpressiveStatusCard(
        title = stringResource(R.string.about_debug_log_title),
        subtitle = stringResource(R.string.about_debug_log_desc),
        icon = Icons.Rounded.BugReport,
        statusText = pluralStringResource(R.plurals.about_debug_log_entry_count, lines.size, lines.size),
        accentColor = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            stringResource(R.string.about_debug_log_empty),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                DebugLogEntry(line.text)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    ButtonLabel(Icons.Rounded.DeleteSweep, stringResource(R.string.about_debug_log_clear))
                }
                Button(
                    onClick = onShare,
                    enabled = !isSharing,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    ButtonLabel(Icons.Rounded.Share, stringResource(R.string.about_debug_log_share))
                }
            }
            Text(
                stringResource(R.string.about_debug_log_reset_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onResetLights,
                enabled = canResetLights,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                ButtonLabel(Icons.Rounded.RestartAlt, stringResource(R.string.about_debug_log_reset))
            }
        }
    }
}

/** One log entry: time, process and tag on a small header line, the message underneath. */
@Composable
private fun DebugLogEntry(text: String) {
    val parsed = remember(text) { DebugLog.parse(text) }
    val messageStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (parsed == null) {
            Text(text, style = messageStyle, color = MaterialTheme.colorScheme.onSurface)
            return@Column
        }
        val levelColor = when (parsed.level) {
            'E' -> MaterialTheme.colorScheme.error
            'W' -> MaterialTheme.colorScheme.tertiary
            'D' -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                parsed.level.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .background(levelColor, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 5.dp)
            )
            Text(
                "${parsed.time} · ${parsed.source} · ${parsed.tag}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            parsed.message,
            style = messageStyle,
            color = when (parsed.level) {
                'E' -> MaterialTheme.colorScheme.error
                'W' -> MaterialTheme.colorScheme.tertiary
                'D' -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Preview(name = "About Screen Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AboutScreenPreview() {
    HiLightPlusTheme {
        AboutContent(
            connectionState = DaemonBridge.State.CONNECTED,
            connectionMethod = DaemonBridge.Method.SHIZUKU,
            connectionError = null,
            onPauseConnection = {},
            onResumeConnection = {},
            onRequestShizukuPermission = {},
            onOpenShizukuApp = {},
            onRestartApp = {},
            onSetUpConnection = {},
            stockState = StockHiLightState(favoriteCallsActive = false, known = true),
            permissionState = PermissionState(
                context = LocalContext.current,
                isContactsGranted = true,
                isNotifAccessGranted = true,
                isNotifListenerRunning = true
            ),
            onOpenSettings = {},
            onRequestPhonePerms = {},
            onOpenNotifSettings = {},
            onOpenAppSettings = {},
            logLines = listOf(
                DebugLogStore.Line(1, "09-24 18:02:11.420 I app    NotificationTrigger: Priority 2 Match: App 'WhatsApp' (com.whatsapp)"),
                DebugLogStore.Line(2, "09-24 18:02:11.431 I daemon LightEngine: Ring -> queue [app_com.whatsapp]"),
                DebugLogStore.Line(3, "09-24 18:02:40.007 W app    HiLightPlus: removeAlert dropped: daemon not connected (NOT_RUNNING)"),
                DebugLogStore.Line(4, "09-24 18:02:44.915 I daemon LightEngine: Ring -> off")
            ),
            isSharingLog = false,
            onShareLog = {},
            onClearLog = {},
            onResetLights = {}
        )
    }
}
