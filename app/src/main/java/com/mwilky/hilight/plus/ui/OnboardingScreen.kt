package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
                        OnboardingStepDots(currentStep)
                    }

                    val isNextEnabled = when (currentStep) {
                        OnboardingStep.WELCOME -> true
                        OnboardingStep.SHIZUKU -> shizukuState == ShizukuBridge.State.CONNECTED
                        OnboardingStep.STOCK_CONFLICT -> !stockState.favoriteCallsActive
                        OnboardingStep.PERMISSIONS -> permissionState.hasAllCallPermissions && permissionState.isNotifAccessGranted
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
    stockState: StockHiLightState,
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

@Composable
private fun OnboardingStepDots(currentStep: OnboardingStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnboardingStep.entries.forEach { step ->
            val isSelected = step == currentStep
            Box(
                modifier = Modifier
                    .size(if (isSelected) HiLightTheme.OnboardingDotSelected else HiLightTheme.OnboardingDot)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
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

@Preview(name = "Step 2 - Shizuku Access", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep2Preview() {
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
                    onOpenShizuku = {}
                )
            }
        }
    }
}

@Preview(name = "Step 3 - Native HiLight", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep3Preview() {
    HiLightPlusTheme {
        Scaffold(
            bottomBar = {
                OnboardingBottomBarPreview(currentStep = OnboardingStep.STOCK_CONFLICT)
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                StockConflictStepContent(
                    stockState = StockHiLightState(favoriteCallsActive = true, known = true),
                    onOpenSettings = {}
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
                OnboardingStepDots(currentStep)
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
