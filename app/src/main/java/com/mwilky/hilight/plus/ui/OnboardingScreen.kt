package com.mwilky.hilight.plus.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.mwilky.hilight.plus.ShizukuBridge

enum class OnboardingStep {
    WELCOME,
    SHIZUKU,
    STOCK_CONFLICT,
    PERMISSIONS
}

@Composable
fun OnboardingScreen(
    controller: LightController,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }

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
    var isNotifListenerGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isCallLogGranted = hasCallLogPermission()
            isContactsGranted = hasContactsPermission()
            isNotifListenerGranted = isNotificationListenerEnabled(context)
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

    fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val totalSteps = OnboardingStep.entries.size

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Back Button (Anchored Left)
                    if (currentStep != OnboardingStep.WELCOME) {
                        OutlinedButton(
                            onClick = {
                                val prevIndex = currentStep.ordinal - 1
                                if (prevIndex >= 0) currentStep = OnboardingStep.entries[prevIndex]
                            },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_back))
                        }
                    }

                    // Progress Dots (Strictly Centered on the Screen)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnboardingStep.entries.forEach { step ->
                            val isSelected = step == currentStep
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    val isNextEnabled = when (currentStep) {
                        OnboardingStep.WELCOME -> true
                        OnboardingStep.SHIZUKU -> shizukuState == ShizukuBridge.State.CONNECTED
                        OnboardingStep.STOCK_CONFLICT -> !stockState.favoriteCallsActive
                        OnboardingStep.PERMISSIONS -> isPhoneGranted && isCallLogGranted && isContactsGranted && isNotifListenerGranted
                    }

                    // Next / Get Started Button (Anchored Right)
                    Button(
                        onClick = {
                            if (currentStep == OnboardingStep.PERMISSIONS) {
                                onComplete()
                            } else {
                                val nextIndex = currentStep.ordinal + 1
                                if (nextIndex < totalSteps) currentStep = OnboardingStep.entries[nextIndex]
                            }
                        },
                        enabled = isNextEnabled,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(if (currentStep == OnboardingStep.PERMISSIONS) stringResource(R.string.onboarding_complete_btn) else stringResource(R.string.btn_next))
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (currentStep == OnboardingStep.PERMISSIONS) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350)))
                } else {
                    (slideInHorizontally(tween(350)) { -it } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(tween(350)) { it } + fadeOut(tween(350)))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "OnboardingStepAnimation"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> {
                    WelcomeStepContent()
                }
                OnboardingStep.STOCK_CONFLICT -> {
                    StockConflictStepContent(
                        stockConflictActive = stockState.favoriteCallsActive,
                        onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) }
                    )
                }
                OnboardingStep.SHIZUKU -> {
                    ShizukuStepContent(
                        shizukuState = shizukuState,
                        errorText = controller.shizuku.errorText(),
                        onRequestPermission = { controller.shizuku.requestPermission() },
                        onConnect = { controller.shizuku.connectManually() },
                        onDisconnect = { controller.shizuku.unbind() },
                        onOpenShizuku = { controller.shizuku.openShizukuApp(context) },
                        onRefresh = { controller.shizuku.connectManually() }
                    )
                }
                OnboardingStep.PERMISSIONS -> {
                    PermissionsStepContent(
                        isPhoneGranted = isPhoneGranted,
                        isCallLogGranted = isCallLogGranted,
                        isContactsGranted = isContactsGranted,
                        isNotifListenerGranted = isNotifListenerGranted,
                        onRequestCallPerms = {
                            val perms = arrayOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_CONTACTS
                            )
                            permissionLauncher.launch(perms)
                        },
                        onOpenAppSettings = { openAppSettings() },
                        onOpenNotifListenerSettings = { openNotificationListenerSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        FeaturesHighlightCard()
    }
}

@Composable
private fun StockConflictStepContent(
    stockConflictActive: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_stock_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_stock_desc1),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_stock_desc2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        StandardDiagnosticCard(
            title = stringResource(R.string.onboarding_stock_card_title),
            subtitle = if (stockConflictActive) {
                stringResource(R.string.onboarding_stock_conflict_active_desc)
            } else {
                stringResource(R.string.onboarding_stock_ready_desc)
            },
            icon = if (stockConflictActive) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            statusText = if (stockConflictActive) stringResource(R.string.onboarding_stock_status_conflict) else stringResource(R.string.onboarding_stock_status_ready),
            isOk = !stockConflictActive,
            bottomAction = if (stockConflictActive) {
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
    }
}

@Composable
private fun ShizukuStepContent(
    shizukuState: ShizukuBridge.State,
    errorText: String?,
    onRequestPermission: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit
) {
    val isConnected = shizukuState == ShizukuBridge.State.CONNECTED
    val isExplicitlyDisconnected = shizukuState == ShizukuBridge.State.DISCONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_shizuku_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_shizuku_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        ExpressiveStatusCard(
            title = stringResource(R.string.shizuku_card_title),
            subtitle = when (shizukuState) {
                ShizukuBridge.State.CONNECTED -> stringResource(R.string.shizuku_desc_connected)
                ShizukuBridge.State.DISCONNECTED -> stringResource(R.string.shizuku_desc_disconnected)
                ShizukuBridge.State.NEEDS_PERMISSION -> stringResource(R.string.shizuku_desc_needs_permission)
                ShizukuBridge.State.NOT_RUNNING -> stringResource(R.string.shizuku_desc_not_running)
                ShizukuBridge.State.NOT_INSTALLED -> stringResource(R.string.shizuku_desc_not_installed)
                ShizukuBridge.State.CONNECTING -> stringResource(R.string.shizuku_desc_connecting)
                else -> errorText ?: stringResource(R.string.shizuku_status_disconnected)
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
            accentColor = if (isConnected) MaterialTheme.colorScheme.primary else if (isExplicitlyDisconnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            bottomAction = {
                when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shizuku_btn_disconnect))
                        }
                    }
                    ShizukuBridge.State.DISCONNECTED -> {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shizuku_btn_connect))
                        }
                    }
                    ShizukuBridge.State.NEEDS_PERMISSION -> {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shizuku_btn_authorize))
                        }
                    }
                    ShizukuBridge.State.NOT_INSTALLED -> {
                        Button(
                            onClick = onOpenShizuku,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shizuku_btn_install))
                        }
                    }
                    ShizukuBridge.State.NOT_RUNNING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenShizuku,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.shizuku_btn_open))
                            }
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.shizuku_btn_check_again))
                            }
                        }
                    }
                    ShizukuBridge.State.CONNECTING -> null
                    else -> {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shizuku_btn_retry))
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PermissionsStepContent(
    isPhoneGranted: Boolean,
    isCallLogGranted: Boolean,
    isContactsGranted: Boolean,
    isNotifListenerGranted: Boolean,
    onRequestCallPerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifListenerSettings: () -> Unit
) {
    val hasCallPerms = isPhoneGranted && isCallLogGranted && isContactsGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_perms_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_perms_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // 1. Phone & Contacts Permissions Card
        StandardDiagnosticCard(
            title = stringResource(R.string.onboarding_perms_calls_title),
            subtitle = if (hasCallPerms) {
                stringResource(R.string.onboarding_perms_calls_granted_desc)
            } else {
                stringResource(R.string.onboarding_perms_calls_needed_desc)
            },
            icon = if (hasCallPerms) Icons.Rounded.CheckCircle else Icons.Rounded.PermPhoneMsg,
            statusText = if (hasCallPerms) stringResource(R.string.onboarding_perms_calls_status_granted) else stringResource(R.string.onboarding_perms_calls_status_needed),
            isOk = hasCallPerms,
            bottomAction = if (!hasCallPerms) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestCallPerms,
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

        // 2. Notification Listener Permission Card
        StandardDiagnosticCard(
            title = stringResource(R.string.onboarding_perms_notif_title),
            subtitle = if (isNotifListenerGranted) {
                stringResource(R.string.onboarding_perms_notif_granted_desc)
            } else {
                stringResource(R.string.onboarding_perms_notif_needed_desc)
            },
            icon = if (isNotifListenerGranted) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationAdd,
            statusText = if (isNotifListenerGranted) stringResource(R.string.onboarding_perms_calls_status_granted) else stringResource(R.string.onboarding_perms_notif_status_needed),
            isOk = isNotifListenerGranted,
            bottomAction = if (!isNotifListenerGranted) {
                {
                    Button(
                        onClick = onOpenNotifListenerSettings,
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
    }
}

@Composable
private fun FeaturesHighlightCard(

) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PhoneInTalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_feature_calls_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.onboarding_feature_calls_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_feature_notifs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.onboarding_feature_notifs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_feature_conditions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.onboarding_feature_conditions_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Step 1 - Welcome", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep1Preview() {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBarPreview(currentStep = OnboardingStep.WELCOME)
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                WelcomeStepContent()
            }
        }
    }
}

@Preview(name = "Step 2 - Stock Conflict", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep2Preview() {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBarPreview(currentStep = OnboardingStep.STOCK_CONFLICT)
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                StockConflictStepContent(
                    stockConflictActive = true,
                    onOpenSettings = {}
                )
            }
        }
    }
}

@Preview(name = "Step 3 - Shizuku Access", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep3Preview() {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBarPreview(currentStep = OnboardingStep.SHIZUKU)
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                ShizukuStepContent(
                    shizukuState = ShizukuBridge.State.NEEDS_PERMISSION,
                    errorText = null,
                    onRequestPermission = {},
                    onConnect = {},
                    onDisconnect = {},
                    onOpenShizuku = {},
                    onRefresh = {}
                )
            }
        }
    }
}

@Preview(name = "Step 4 - Permissions", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep4Preview() {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBarPreview(currentStep = OnboardingStep.PERMISSIONS)
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                PermissionsStepContent(
                    isPhoneGranted = true,
                    isCallLogGranted = false,
                    isContactsGranted = false,
                    isNotifListenerGranted = false,
                    onRequestCallPerms = {},
                    onOpenAppSettings = {},
                    onOpenNotifListenerSettings = {}
                )
            }
        }
    }
}

@Composable
private fun OnboardingBottomBarPreview(currentStep: OnboardingStep) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (currentStep != OnboardingStep.WELCOME) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_back))
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingStep.entries.forEach { step ->
                    val isSelected = step == currentStep
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Button(
                onClick = {},
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(if (currentStep == OnboardingStep.PERMISSIONS) stringResource(R.string.onboarding_complete_btn) else stringResource(R.string.btn_next))
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (currentStep == OnboardingStep.PERMISSIONS) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
