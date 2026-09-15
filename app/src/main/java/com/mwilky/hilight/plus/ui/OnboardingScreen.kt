@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val permissionState = rememberPermissionState()
    val requestCallPermissions = rememberCallPermissionLauncher(permissionState)

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
    val isNextEnabled = when (currentStep) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.SHIZUKU -> shizukuState == ShizukuBridge.State.CONNECTED
        OnboardingStep.STOCK_CONFLICT -> !stockState.favoriteCallsActive
        OnboardingStep.PERMISSIONS -> permissionState.hasAllCallPermissions && permissionState.isNotifAccessGranted
    }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                isNextEnabled = isNextEnabled,
                onBack = {
                    val prevIndex = currentStep.ordinal - 1
                    if (prevIndex >= 0) currentStep = OnboardingStep.entries[prevIndex]
                },
                onNext = {
                    if (currentStep == OnboardingStep.PERMISSIONS) {
                        onComplete()
                    } else {
                        val nextIndex = currentStep.ordinal + 1
                        if (nextIndex < totalSteps) currentStep = OnboardingStep.entries[nextIndex]
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(spatial) { it } + fadeIn(effects))
                        .togetherWith(slideOutHorizontally(spatial) { -it } + fadeOut(effects))
                } else {
                    (slideInHorizontally(spatial) { -it } + fadeIn(effects))
                        .togetherWith(slideOutHorizontally(spatial) { it } + fadeOut(effects))
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
                        stockState = stockState,
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
                        onOpenShizuku = { controller.shizuku.openShizukuApp(context) }
                    )
                }
                OnboardingStep.PERMISSIONS -> {
                    PermissionsStepContent(
                        permissionState = permissionState,
                        onRequestCallPerms = requestCallPermissions,
                        onOpenAppSettings = { openAppSettings() },
                        onOpenNotifListenerSettings = { openNotificationListenerSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: OnboardingStep,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val isLast = currentStep == OnboardingStep.PERMISSIONS
    val progress by animateFloatAsState(
        targetValue = (currentStep.ordinal + 1f) / OnboardingStep.entries.size,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "onboardingProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (currentStep != OnboardingStep.WELCOME) {
                OutlinedButton(
                    onClick = onBack,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.btn_back))
                }
            }

            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(96.dp)
            )

            Button(
                onClick = onNext,
                enabled = isNextEnabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(if (isLast) stringResource(R.string.onboarding_complete_btn) else stringResource(R.string.btn_next))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
        }
    }
}

@Composable
private fun StepColumn(
    title: String,
    spacing: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        content()
    }
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WelcomeStepContent() {
    StepColumn(title = stringResource(R.string.onboarding_title)) {
        StepBody(stringResource(R.string.onboarding_subtitle))
        Spacer(Modifier.height(8.dp))
        FeaturesHighlightCard()
    }
}

@Composable
private fun StockConflictStepContent(
    stockState: StockHiLightState,
    onOpenSettings: () -> Unit
) {
    StepColumn(title = stringResource(R.string.onboarding_stock_title)) {
        StepBody(stringResource(R.string.onboarding_stock_desc1))
        StepBody(stringResource(R.string.onboarding_stock_desc2))
        StockConflictCard(
            stockState = stockState,
            onOpenSettings = onOpenSettings
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
    onOpenShizuku: () -> Unit
) {
    StepColumn(title = stringResource(R.string.onboarding_shizuku_title)) {
        StepBody(stringResource(R.string.onboarding_shizuku_desc))
        ShizukuStatusCard(
            shizukuState = shizukuState,
            shizukuError = errorText,
            onDisconnect = onDisconnect,
            onConnect = onConnect,
            onRequestPermission = onRequestPermission,
            onOpenShizukuApp = onOpenShizuku
        )
    }
}

@Composable
private fun PermissionsStepContent(
    permissionState: PermissionState,
    onRequestCallPerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifListenerSettings: () -> Unit
) {
    StepColumn(title = stringResource(R.string.onboarding_perms_title), spacing = 18.dp) {
        StepBody(stringResource(R.string.onboarding_perms_subtitle))
        CallPermissionsCard(
            state = permissionState,
            onRequestPermissions = onRequestCallPerms,
            onOpenAppSettings = onOpenAppSettings
        )
        NotificationAccessCard(
            state = permissionState,
            onOpenNotifSettings = onOpenNotifListenerSettings
        )
    }
}

@Composable
private fun FeaturesHighlightCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            FeatureRow(
                icon = Icons.Rounded.PhoneInTalk,
                title = stringResource(R.string.onboarding_feature_calls_title),
                desc = stringResource(R.string.onboarding_feature_calls_desc)
            )
            FeatureRow(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.onboarding_feature_notifs_title),
                desc = stringResource(R.string.onboarding_feature_notifs_desc)
            )
            FeatureRow(
                icon = Icons.Rounded.ScreenRotation,
                title = stringResource(R.string.onboarding_feature_conditions_title),
                desc = stringResource(R.string.onboarding_feature_conditions_desc)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        supportingContent = { Text(desc) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
        Text(title)
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Composable
private fun OnboardingStepPreview(step: OnboardingStep, content: @Composable () -> Unit) {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBar(currentStep = step, isNextEnabled = true, onBack = {}, onNext = {})
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) { content() }
        }
    }
}

@Preview(name = "Step 1 - Welcome", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep1Preview() {
    OnboardingStepPreview(OnboardingStep.WELCOME) { WelcomeStepContent() }
}

@Preview(name = "Step 2 - Shizuku Access", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep2Preview() {
    OnboardingStepPreview(OnboardingStep.SHIZUKU) {
        ShizukuStepContent(
            shizukuState = ShizukuBridge.State.NEEDS_PERMISSION,
            errorText = null,
            onRequestPermission = {},
            onConnect = {},
            onDisconnect = {},
            onOpenShizuku = {}
        )
    }
}

@Preview(name = "Step 3 - Native HiLight", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep3Preview() {
    OnboardingStepPreview(OnboardingStep.STOCK_CONFLICT) {
        StockConflictStepContent(
            stockState = StockHiLightState(favoriteCallsActive = true, known = true),
            onOpenSettings = {}
        )
    }
}

@Preview(name = "Step 4 - Permissions", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep4Preview() {
    OnboardingStepPreview(OnboardingStep.PERMISSIONS) {
        PermissionsStepContent(
            permissionState = PermissionState(
                context = LocalContext.current,
                isPhoneGranted = true,
                isCallLogGranted = false,
                isContactsGranted = false,
                isNotifAccessGranted = false,
                isNotifListenerRunning = false
            ),
            onRequestCallPerms = {},
            onOpenAppSettings = {},
            onOpenNotifListenerSettings = {}
        )
    }
}
