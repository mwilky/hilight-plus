@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.adb.ConnectSetup
import com.mwilky.hilight.plus.adb.PairingPhase
import com.mwilky.hilight.plus.adb.SetupIntents
import com.mwilky.hilight.plus.adb.SetupState
import com.mwilky.hilight.plus.adb.SetupStep
import com.mwilky.hilight.plus.ui.diagnostics.ButtonLabel
import com.mwilky.hilight.plus.ui.diagnostics.ConnectionStatusCard

/** The Shizuku card's actions, for the "Already use Shizuku?" option. */
data class ShizukuActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onRequestPermission: () -> Unit = {},
    val onOpenApp: () -> Unit = {},
    val onRestartApp: () -> Unit = {}
)

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * The connect checklist with its setup session: started when shown, stopped when it leaves the
 * screen (but not across a fold or rotation). [allowShizuku] offers Shizuku as an alternative and
 * counts a Shizuku connection as done.
 */
@Composable
internal fun ConnectStep(controller: LightController, allowShizuku: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val setup = remember { ConnectSetup.get(context.applicationContext as Application) }
    val setupState by setup.state.collectAsStateWithLifecycle()
    val connectionState by controller.daemon.state.collectAsStateWithLifecycle()
    val connectionMethod by controller.daemon.method.collectAsStateWithLifecycle()
    var showShizuku by rememberSaveable { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(canNotify(context)) }
    var askedPermissions by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notificationsAllowed = canNotify(context)
        setup.allowDiscovery(true)
    }

    DisposableEffect(Unit) {
        // Ask as the screen opens, before anything scans: the guide notification and finding the
        // pairing dialog both depend on the answer.
        val needed = missingSetupPermissions(context)
        if (needed.isNotEmpty() && !askedPermissions) {
            askedPermissions = true
            setup.allowDiscovery(false)
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            setup.allowDiscovery(true)
        }
        setup.start()
        onDispose {
            if (activity?.isChangingConfigurations != true) setup.stop()
        }
    }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = canNotify(context)
        setup.update()
        onPauseOrDispose { }
    }

    fun openStep(step: SetupStep) {
        SetupIntents.forStep(context, step)?.let { SetupIntents.open(context, it) }
    }

    ConnectStepContent(
        setup = setupState,
        connectionState = connectionState,
        connectionMethod = connectionMethod,
        connectionError = controller.daemon.errorText(),
        notificationsAllowed = notificationsAllowed,
        allowShizuku = allowShizuku,
        showShizuku = showShizuku,
        onToggleShizuku = { showShizuku = !showShizuku },
        onOpenStep = ::openStep,
        onSubmitCode = setup::submitCode,
        onRetry = setup::retry,
        shizukuActions = ShizukuActions(
            onPause = { controller.daemon.unbind() },
            onResume = { controller.daemon.connectManually() },
            onRequestPermission = { controller.daemon.requestPermission() },
            onOpenApp = { controller.daemon.openShizukuApp(context) },
            onRestartApp = { controller.daemon.restartApp(context) }
        )
    )
}

private fun canNotify(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Notifications for the guide, and local network access where the platform has it, since
 * finding the pairing dialog uses mDNS.
 */
private fun missingSetupPermissions(context: Context): List<String> = buildList {
    if (!canNotify(context)) add(Manifest.permission.POST_NOTIFICATIONS)
    val localNetworkExists = runCatching { context.packageManager.getPermissionInfo(LOCAL_NETWORK_PERMISSION, 0) }.isSuccess
    if (localNetworkExists && context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
        add(LOCAL_NETWORK_PERMISSION)
    }
}

@Composable
internal fun ConnectStepContent(
    setup: SetupState,
    connectionState: DaemonBridge.State,
    connectionMethod: DaemonBridge.Method,
    connectionError: String?,
    notificationsAllowed: Boolean,
    allowShizuku: Boolean,
    showShizuku: Boolean,
    onToggleShizuku: () -> Unit,
    onOpenStep: (SetupStep) -> Unit,
    onSubmitCode: (String) -> Unit,
    onRetry: () -> Unit,
    shizukuActions: ShizukuActions
) {
    val done = if (allowShizuku) connectionState == DaemonBridge.State.CONNECTED else setup.connected

    StepColumn(
        stringResource(R.string.connect_title),
        { StepBody(stringResource(R.string.connect_desc)) },
        {
            when {
                done || showShizuku -> ConnectionStatusCard(
                    // Until the user has Shizuku, the bridge is on the built-in path; show Shizuku as missing.
                    connectionState = if (done || connectionMethod == DaemonBridge.Method.SHIZUKU) {
                        connectionState
                    } else {
                        DaemonBridge.State.NOT_INSTALLED
                    },
                    connectionMethod = if (done) connectionMethod else DaemonBridge.Method.SHIZUKU,
                    connectionError = connectionError,
                    onDisconnect = shizukuActions.onPause,
                    onConnect = shizukuActions.onResume,
                    onRequestPermission = shizukuActions.onRequestPermission,
                    onOpenShizukuApp = shizukuActions.onOpenApp,
                    onRestartApp = shizukuActions.onRestartApp,
                    onSetUp = onToggleShizuku
                )
                else -> ConnectChecklist(
                    setup = setup,
                    notificationsAllowed = notificationsAllowed,
                    onOpenStep = onOpenStep,
                    onSubmitCode = onSubmitCode,
                    onRetry = onRetry
                )
            }
        },
        {
            if (allowShizuku && !done) {
                TextButton(onClick = onToggleShizuku, shapes = ButtonDefaults.shapes()) {
                    Text(
                        if (showShizuku) stringResource(R.string.connect_use_builtin) else stringResource(R.string.connect_use_shizuku)
                    )
                }
            }
        }
    )
}

private enum class RowStatus { DONE, CURRENT, PENDING }

@Composable
private fun ConnectChecklist(
    setup: SetupState,
    notificationsAllowed: Boolean,
    onOpenStep: (SetupStep) -> Unit,
    onSubmitCode: (String) -> Unit,
    onRetry: () -> Unit
) {
    val step = setup.step
    val networkReady = setup.devOptionsOn && setup.wifiConnected && setup.wirelessDebuggingOn

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ChecklistRow(
                number = 1,
                status = when {
                    setup.devOptionsOn -> RowStatus.DONE
                    else -> RowStatus.CURRENT
                },
                title = stringResource(R.string.connect_step_dev_title),
                description = if (setup.devOptionsOn) {
                    stringResource(R.string.connect_step_dev_done)
                } else {
                    stringResource(R.string.connect_step_dev_desc)
                }
            ) {
                StepButton(stringResource(R.string.connect_step_dev_btn)) { onOpenStep(SetupStep.DEV_OPTIONS) }
            }

            ChecklistRow(
                number = 2,
                status = when {
                    networkReady -> RowStatus.DONE
                    step == SetupStep.WIFI || step == SetupStep.WIRELESS_DEBUGGING -> RowStatus.CURRENT
                    else -> RowStatus.PENDING
                },
                title = stringResource(R.string.connect_step_wireless_title),
                description = when {
                    networkReady -> stringResource(R.string.connect_step_wireless_done)
                    step == SetupStep.WIFI -> stringResource(R.string.connect_step_wifi_desc)
                    else -> stringResource(R.string.connect_step_wireless_desc)
                }
            ) {
                if (step == SetupStep.WIFI) {
                    StepButton(stringResource(R.string.connect_btn_wifi)) { onOpenStep(SetupStep.WIFI) }
                } else {
                    StepButton(stringResource(R.string.connect_step_wireless_btn)) { onOpenStep(SetupStep.WIRELESS_DEBUGGING) }
                }
            }

            val pairing = setup.pairing
            val codeEntry = setup.codeEntryAvailable &&
                (pairing == PairingPhase.CODE_NEEDED || pairing == PairingPhase.WRONG_CODE)
            ChecklistRow(
                number = 3,
                status = when {
                    setup.connected -> RowStatus.DONE
                    step == SetupStep.PAIR -> RowStatus.CURRENT
                    else -> RowStatus.PENDING
                },
                title = stringResource(R.string.connect_step_pair_title),
                description = when (pairing) {
                    PairingPhase.CODE_NEEDED -> stringResource(R.string.connect_step_pair_code_desc)
                    PairingPhase.PAIRING, PairingPhase.STARTING -> stringResource(R.string.connect_step_pair_connecting)
                    PairingPhase.WRONG_CODE -> stringResource(R.string.connect_step_pair_wrong)
                    PairingPhase.FAILED -> setup.error ?: stringResource(R.string.connect_step_pair_failed)
                    PairingPhase.SEARCHING -> if (notificationsAllowed) {
                        stringResource(R.string.connect_step_pair_desc)
                    } else {
                        stringResource(R.string.connect_step_pair_desc_in_app)
                    }
                }
            ) {
                when {
                    pairing == PairingPhase.PAIRING || pairing == PairingPhase.STARTING ->
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    pairing == PairingPhase.FAILED ->
                        StepButton(stringResource(R.string.connect_step_pair_retry), onClick = onRetry)
                    codeEntry -> CodeEntry(onSubmitCode)
                    else -> StepButton(stringResource(R.string.connect_step_pair_btn)) { onOpenStep(SetupStep.PAIR) }
                }
            }
        }
    }
}

/** One checklist row; [action] only shows while the row is the current step. */
@Composable
private fun ChecklistRow(
    number: Int,
    status: RowStatus,
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
    val (container, content) = when (status) {
        RowStatus.DONE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RowStatus.CURRENT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        RowStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(container),
                    contentAlignment = Alignment.Center
                ) {
                    if (status == RowStatus.DONE) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = content)
                    } else {
                        Text(number.toString(), style = MaterialTheme.typography.titleMedium, color = content)
                    }
                }
            },
            supportingContent = { Text(description) },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = if (status == RowStatus.PENDING) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(title)
        }
        if (status == RowStatus.CURRENT) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                action()
            }
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
        ButtonLabel(Icons.Rounded.Settings, text)
    }
}

/** In-app fallback for the notification's code field (no notification access, or side by side). */
@Composable
private fun CodeEntry(onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.connect_code_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { onSubmit(code) },
            enabled = code.length == 6,
            shapes = ButtonDefaults.shapes()
        ) {
            Text(stringResource(R.string.connect_code_btn))
        }
    }
}

// =========================================================================
// Full-screen connect flow (Home card, About, and the switch-over prompt)
// =========================================================================

@Composable
fun ConnectScreen(controller: LightController, onClose: () -> Unit) {
    val context = LocalContext.current
    // The live connection, not the last setup session's result, which may be out of date.
    val connectionState by controller.daemon.state.collectAsStateWithLifecycle()
    val connectionMethod by controller.daemon.method.collectAsStateWithLifecycle()
    val shizukuInstalled = remember { controller.daemon.isShizukuInstalled() }

    ConnectScreenContent(
        done = connectionState == DaemonBridge.State.CONNECTED && connectionMethod == DaemonBridge.Method.BUILT_IN,
        shizukuInstalled = shizukuInstalled,
        onOpenShizukuInfo = { controller.daemon.openShizukuAppInfo(context) },
        onClose = onClose
    ) {
        ConnectStep(controller = controller, allowShizuku = false)
    }
}

@Composable
internal fun ConnectScreenContent(
    done: Boolean,
    shizukuInstalled: Boolean,
    onOpenShizukuInfo: () -> Unit,
    onClose: () -> Unit,
    checklist: @Composable () -> Unit
) {
    BackHandler(onBack = onClose)
    val style = stepStyle(OnboardingStep.CONNECT)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Scaffold(
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (done) Arrangement.End else Arrangement.Start
                ) {
                    if (done) {
                        Button(
                            onClick = onClose,
                            shapes = ButtonDefaults.shapesFor(buttonHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = style.accent, contentColor = style.onAccent),
                            modifier = Modifier.heightIn(buttonHeight)
                        ) {
                            Text(stringResource(R.string.connect_screen_done), style = ButtonDefaults.textStyleFor(buttonHeight))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onClose,
                            shapes = ButtonDefaults.shapesFor(buttonHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                            modifier = Modifier.heightIn(buttonHeight)
                        ) {
                            Text(stringResource(R.string.connect_screen_close), style = ButtonDefaults.textStyleFor(buttonHeight))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StepHero(
                step = OnboardingStep.CONNECT,
                style = style,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 32.dp, bottom = 8.dp)
            )
            AnimatedContent(
                targetState = done,
                transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
                modifier = Modifier.fillMaxSize(),
                label = "ConnectDone"
            ) { isDone ->
                if (isDone) {
                    StepColumn(
                        stringResource(R.string.connect_success_title),
                        { StepBody(stringResource(R.string.connect_success_desc)) },
                        {
                            if (shizukuInstalled) {
                                StandardDiagnosticCard(
                                    title = stringResource(R.string.connect_success_shizuku_title),
                                    subtitle = stringResource(R.string.connect_success_shizuku_desc),
                                    icon = Icons.Rounded.CheckCircle,
                                    statusText = stringResource(R.string.connect_success_shizuku_status),
                                    isOk = true,
                                    bottomAction = {
                                        OutlinedButton(
                                            onClick = onOpenShizukuInfo,
                                            modifier = Modifier.fillMaxWidth(),
                                            shapes = ButtonDefaults.shapes()
                                        ) {
                                            ButtonLabel(Icons.Rounded.Settings, stringResource(R.string.connect_success_shizuku_btn))
                                        }
                                    }
                                )
                            }
                        }
                    )
                } else {
                    checklist()
                }
            }
        }
    }
}

/** Shown once to people who updated from a version that needed Shizuku. */
@Composable
internal fun ConnectMigrationSheet(onSwitch: () -> Unit, onNotNow: () -> Unit) {
    val style = stepStyle(OnboardingStep.CONNECT)
    ModalBottomSheet(onDismissRequest = onNotNow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(style.shape.toShape())
                    .background(style.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = style.onContainer, modifier = Modifier.size(40.dp))
            }
            Text(
                stringResource(R.string.migration_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            StepBody(stringResource(R.string.migration_desc))
            Button(
                onClick = onSwitch,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = style.accent, contentColor = style.onAccent)
            ) {
                Text(stringResource(R.string.migration_switch))
            }
            TextButton(onClick = onNotNow, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.migration_not_now))
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Connect - in progress", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ConnectScreenPreview() {
    HiLightPlusTheme {
        ConnectScreenContent(done = false, shizukuInstalled = true, onOpenShizukuInfo = {}, onClose = {}) {
            ConnectStepContent(
                setup = SetupState(
                    devOptionsOn = true,
                    wifiConnected = true,
                    wirelessDebuggingOn = true,
                    pairing = PairingPhase.CODE_NEEDED,
                    codeEntryAvailable = true
                ),
                connectionState = DaemonBridge.State.CONNECTED,
                connectionMethod = DaemonBridge.Method.SHIZUKU,
                connectionError = null,
                notificationsAllowed = true,
                allowShizuku = false,
                showShizuku = false,
                onToggleShizuku = {},
                onOpenStep = {},
                onSubmitCode = {},
                onRetry = {},
                shizukuActions = ShizukuActions()
            )
        }
    }
}

@Preview(name = "Connect - done", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ConnectScreenDonePreview() {
    HiLightPlusTheme {
        ConnectScreenContent(done = true, shizukuInstalled = true, onOpenShizukuInfo = {}, onClose = {}) { }
    }
}
