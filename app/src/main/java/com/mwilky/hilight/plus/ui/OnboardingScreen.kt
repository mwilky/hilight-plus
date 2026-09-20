@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.Licensing
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

enum class OnboardingStep {
    WELCOME,
    SHIZUKU,
    STOCK_CONFLICT,
    PERMISSIONS,
    TRIAL
}

/**
 * Per-step look: hero shape, hero icon and which dynamic colour role tints the step.
 */
private class StepStyle(
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val onAccent: Color
)

@Composable
private fun stepStyle(step: OnboardingStep): StepStyle {
    val c = MaterialTheme.colorScheme
    return when (step) {
        OnboardingStep.WELCOME -> StepStyle(
            MaterialShapes.Cookie9Sided, Icons.Rounded.Lightbulb,
            c.primaryContainer, c.onPrimaryContainer, c.primary, c.onPrimary
        )
        OnboardingStep.SHIZUKU -> StepStyle(
            MaterialShapes.Clover4Leaf, Icons.Rounded.AdminPanelSettings,
            c.secondaryContainer, c.onSecondaryContainer, c.secondary, c.onSecondary
        )
        OnboardingStep.STOCK_CONFLICT -> StepStyle(
            MaterialShapes.Sunny, Icons.Rounded.Warning,
            c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, c.onTertiary
        )
        OnboardingStep.PERMISSIONS -> StepStyle(
            MaterialShapes.Clover8Leaf, Icons.Rounded.VerifiedUser,
            c.primaryContainer, c.onPrimaryContainer, c.primary, c.onPrimary
        )
        OnboardingStep.TRIAL -> StepStyle(
            MaterialShapes.Gem, Icons.Rounded.WorkspacePremium,
            c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, c.onTertiary
        )
    }
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
    val licenseStatus by controller.licensing.status.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

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
        OnboardingStep.TRIAL -> true
    }

    OnboardingScaffold(
        currentStep = currentStep,
        isNextEnabled = isNextEnabled,
        onBack = {
            val prevIndex = currentStep.ordinal - 1
            if (prevIndex >= 0) currentStep = OnboardingStep.entries[prevIndex]
        },
        onNext = {
            if (currentStep == OnboardingStep.TRIAL) {
                onComplete()
            } else {
                val nextIndex = currentStep.ordinal + 1
                if (nextIndex < totalSteps) currentStep = OnboardingStep.entries[nextIndex]
            }
        }
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStepContent()
            OnboardingStep.STOCK_CONFLICT -> StockConflictStepContent(
                stockState = stockState,
                onOpenSettings = { NativeHiLightDetector.openHiLightSettings(context) }
            )
            OnboardingStep.SHIZUKU -> ShizukuStepContent(
                shizukuState = shizukuState,
                errorText = controller.shizuku.errorText(),
                onRequestPermission = { controller.shizuku.requestPermission() },
                onConnect = { controller.shizuku.connectManually() },
                onDisconnect = { controller.shizuku.unbind() },
                onOpenShizuku = { controller.shizuku.openShizukuApp(context) },
                onRestartApp = { controller.shizuku.restartApp(context) }
            )
            OnboardingStep.PERMISSIONS -> PermissionsStepContent(
                permissionState = permissionState,
                onRequestCallPerms = requestCallPermissions,
                onOpenAppSettings = { openAppSettings() },
                onOpenNotifListenerSettings = { openNotificationListenerSettings() }
            )
            OnboardingStep.TRIAL -> TrialStepContent(
                status = licenseStatus,
                onBuy = { activity?.let { controller.licensing.purchase(it) } }
            )
        }
    }
}

/**
 * Shared frame: morphing hero at the top, sliding step content below, tinted bottom bar.
 */
@Composable
private fun OnboardingScaffold(
    currentStep: OnboardingStep,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    content: @Composable (OnboardingStep) -> Unit
) {
    val style = stepStyle(currentStep)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                style = style,
                isNextEnabled = isNextEnabled,
                onBack = onBack,
                onNext = onNext
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StepHero(
                step = currentStep,
                style = style,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 32.dp, bottom = 8.dp)
            )
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
                modifier = Modifier.fillMaxSize(),
                label = "OnboardingStepAnimation"
            ) { step ->
                content(step)
            }
        }
    }
}

/**
 * Large expressive shape that morphs from the previous step's shape into the current one.
 */
@Composable
private fun StepHero(
    step: OnboardingStep,
    style: StepStyle,
    modifier: Modifier = Modifier
) {
    var fromShape by remember { mutableStateOf(style.shape) }
    var toShape by remember { mutableStateOf(style.shape) }
    val progress = remember { Animatable(1f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(step) {
        if (style.shape !== toShape) {
            fromShape = toShape
            toShape = style.shape
            progress.snapTo(0f)
            progress.animateTo(1f, spatial)
        }
    }
    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }
    val shape = remember(morph, progress.value) { MorphShape(morph, progress.value) }

    val container by animateColorAsState(
        targetValue = style.container,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContainer"
    )
    val onContainer by animateColorAsState(
        targetValue = style.onContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContent"
    )

    val iconEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val iconSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Box(
        modifier = modifier
            .size(160.dp)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = style.icon,
            transitionSpec = {
                (fadeIn(iconEffects) + scaleIn(iconSpatial)).togetherWith(fadeOut(iconEffects))
            },
            label = "heroIcon"
        ) { icon ->
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(64.dp))
        }
    }
}

/** A [Shape] sampled from a [Morph] at [progress]; polygons are normalised so we scale to size. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: OnboardingStep,
    style: StepStyle,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val isLast = currentStep == OnboardingStep.TRIAL
    val progress by animateFloatAsState(
        targetValue = (currentStep.ordinal + 1f) / OnboardingStep.entries.size,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "onboardingProgress"
    )
    val accent by animateColorAsState(style.accent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "accent")
    val onAccent by animateColorAsState(style.onAccent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "onAccent")
    val sizeSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = currentStep != OnboardingStep.WELCOME,
                enter = expandHorizontally(sizeSpatial) + fadeIn(effects),
                exit = shrinkHorizontally(sizeSpatial) + fadeOut(effects)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shapes = ButtonDefaults.shapesFor(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                    modifier = Modifier.heightIn(buttonHeight)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.btn_back), style = ButtonDefaults.textStyleFor(buttonHeight))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 96.dp)
                )
            }

            Button(
                onClick = onNext,
                enabled = isNextEnabled,
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                modifier = Modifier
                    .heightIn(buttonHeight)
                    .animateContentSize(sizeSpatial)
            ) {
                Text(
                    if (isLast) stringResource(R.string.onboarding_complete_btn) else stringResource(R.string.btn_next),
                    style = ButtonDefaults.textStyleFor(buttonHeight)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight))
                )
            }
        }
    }
}

/**
 * Fades and lifts a child into place, delayed by its [index] so siblings arrive one after another.
 */
@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(80L * index)
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 32.dp.toPx()
    }
}

/**
 * Scrolling step body. Children are wrapped so each one enters with a stagger.
 */
@Composable
private fun StepColumn(
    title: String,
    vararg items: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(0)
        )
        items.forEachIndexed { i, item ->
            Box(modifier = Modifier.staggeredEntrance(i + 1)) { item() }
        }
    }
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WelcomeStepContent() {
    StepColumn(
        stringResource(R.string.onboarding_title),
        { StepBody(stringResource(R.string.onboarding_subtitle)) },
        { FeaturesHighlightCard() }
    )
}

@Composable
private fun StockConflictStepContent(
    stockState: StockHiLightState,
    onOpenSettings: () -> Unit
) {
    StepColumn(
        stringResource(R.string.onboarding_stock_title),
        { StepBody(stringResource(R.string.onboarding_stock_desc1)) },
        { StepBody(stringResource(R.string.onboarding_stock_desc2)) },
        { StockConflictCard(stockState = stockState, onOpenSettings = onOpenSettings) }
    )
}

@Composable
private fun ShizukuStepContent(
    shizukuState: ShizukuBridge.State,
    errorText: String?,
    onRequestPermission: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRestartApp: () -> Unit
) {
    StepColumn(
        stringResource(R.string.onboarding_shizuku_title),
        { StepBody(stringResource(R.string.onboarding_shizuku_desc)) },
        {
            ShizukuStatusCard(
                shizukuState = shizukuState,
                shizukuError = errorText,
                onDisconnect = onDisconnect,
                onConnect = onConnect,
                onRequestPermission = onRequestPermission,
                onOpenShizukuApp = onOpenShizuku,
                onRestartApp = onRestartApp
            )
        }
    )
}

@Composable
private fun PermissionsStepContent(
    permissionState: PermissionState,
    onRequestCallPerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifListenerSettings: () -> Unit
) {
    StepColumn(
        stringResource(R.string.onboarding_perms_title),
        { StepBody(stringResource(R.string.onboarding_perms_subtitle)) },
        {
            CallPermissionsCard(
                state = permissionState,
                onRequestPermissions = onRequestCallPerms,
                onOpenAppSettings = onOpenAppSettings
            )
        },
        {
            NotificationAccessCard(
                state = permissionState,
                onOpenNotifSettings = onOpenNotifListenerSettings
            )
        }
    )
}

@Composable
private fun TrialStepContent(
    status: Licensing.Status,
    onBuy: () -> Unit
) {
    StepColumn(
        stringResource(R.string.onboarding_trial_title),
        { StepBody(stringResource(R.string.onboarding_trial_desc, Licensing.TRIAL_DAYS)) },
        { LicenseCard(status = status, onBuy = onBuy) }
    )
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
                shape = MaterialShapes.Cookie6Sided,
                title = stringResource(R.string.onboarding_feature_calls_title),
                desc = stringResource(R.string.onboarding_feature_calls_desc)
            )
            FeatureRow(
                icon = Icons.Rounded.NotificationsActive,
                shape = MaterialShapes.Sunny,
                title = stringResource(R.string.onboarding_feature_notifs_title),
                desc = stringResource(R.string.onboarding_feature_notifs_desc)
            )
            FeatureRow(
                icon = Icons.Rounded.ScreenRotation,
                shape = MaterialShapes.Clover4Leaf,
                title = stringResource(R.string.onboarding_feature_conditions_title),
                desc = stringResource(R.string.onboarding_feature_conditions_desc)
            )
            FeatureRow(
                icon = Icons.Rounded.BatteryChargingFull,
                shape = MaterialShapes.Pill,
                title = stringResource(R.string.onboarding_feature_battery_title),
                desc = stringResource(R.string.onboarding_feature_battery_desc)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, shape: RoundedPolygon, title: String, desc: String) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape.toShape())
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
        OnboardingScaffold(currentStep = step, isNextEnabled = true, onBack = {}, onNext = {}) { content() }
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
            onOpenShizuku = {},
            onRestartApp = {}
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

@Preview(name = "Step 5 - Trial", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingStep5Preview() {
    OnboardingStepPreview(OnboardingStep.TRIAL) {
        val now = System.currentTimeMillis()
        TrialStepContent(
            status = Licensing.Status(purchased = false, trialStartMillis = now, now = now, priceText = "\u00a32.99"),
            onBuy = {}
        )
    }
}
