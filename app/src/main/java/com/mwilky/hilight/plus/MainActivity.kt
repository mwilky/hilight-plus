@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mwilky.hilight.plus.ui.AboutScreen
import com.mwilky.hilight.plus.ui.ConditionsScreen
import com.mwilky.hilight.plus.ui.ConnectMigrationSheet
import com.mwilky.hilight.plus.ui.ConnectScreen
import com.mwilky.hilight.plus.ui.HiLightPlusTheme
import com.mwilky.hilight.plus.ui.HomeScreen
import com.mwilky.hilight.plus.ui.OnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Set by the "pair again" notification; the connect screen opens and clears it.
    private var openConnect by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openConnect = intent.getBooleanExtra(EXTRA_OPEN_CONNECT, false)

        val controller = LightController.get(this)
        refreshDiagnostics()
        val store = AppStore.get(this)

        setContent {
            HiLightPlusTheme {
                val isOnboardingCompleted by store.isOnboardingCompleted.collectAsStateWithLifecycle(
                    initialValue = null
                )
                val scope = rememberCoroutineScope()

                when (isOnboardingCompleted) {
                    null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }

                    false -> {
                        OnboardingScreen(
                            controller = controller,
                            onComplete = {
                                scope.launch {
                                    // Set up with this version, so the switch-over prompt isn't for them.
                                    store.setConnectPromptShown()
                                    store.setOnboardingCompleted(true)
                                }
                            }
                        )
                    }

                    true -> {
                        MainAppNavigation(
                            controller = controller,
                            openConnect = openConnect,
                            onConnectOpened = { openConnect = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CONNECT, false)) openConnect = true
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        LightController.get(this).refreshStatus()
        NativeHiLightDetector.check(this)
    }

    companion object {
        private const val EXTRA_OPEN_CONNECT = "open_connect"

        /** Opens the app at the connect screen, to pair again. */
        fun connectIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CONNECT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

// Lets the screen settle after opening, and the purchase sheet close, before Play's sheet appears.
private const val REVIEW_SETTLE_DELAY_MS = 2_000L
private const val REVIEW_AFTER_PURCHASE_DELAY_MS = 1_500L

private enum class NavTab(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    CONDITIONS(R.string.nav_conditions, Icons.Rounded.Tune),
    ABOUT(R.string.nav_about, Icons.Rounded.Info)
}

@Composable
private fun MainAppNavigation(
    controller: LightController,
    openConnect: Boolean = false,
    onConnectOpened: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var showConnect by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openConnect) {
        if (openConnect) {
            showConnect = true
            onConnectOpened()
        }
    }

    // The connect screen comes in from the side and goes back out the way it came.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = showConnect,
        transitionSpec = {
            val opening = targetState
            (slideInHorizontally(spatial) { if (opening) it else -it } + fadeIn(effects))
                .togetherWith(slideOutHorizontally(spatial) { if (opening) -it else it } + fadeOut(effects))
        },
        label = "ConnectAnimation"
    ) { connect ->
        if (connect) {
            ConnectScreen(controller = controller, onClose = { showConnect = false })
        } else {
            MainTabs(
                controller = controller,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onSetUpConnection = { showConnect = true }
            )
        }
    }
}

@Composable
private fun MainTabs(
    controller: LightController,
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
    onSetUpConnection: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val promptShown by controller.store.isConnectPromptShown.collectAsStateWithLifecycle(initialValue = true)
    // Checked again each time the tabs come back from the connect screen.
    val paired = remember { controller.daemon.wireless.isPaired() }

    // Play's review sheet, once it's due. Only while the tabs are showing, so it can't cover the
    // connect screen, and held back while the migration sheet is up.
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val migrationSheetShowing = !promptShown && !paired
    LaunchedEffect(lifecycleOwner, migrationSheetShowing) {
        if (activity == null || migrationSheetShowing) return@LaunchedEffect
        launch {
            controller.licensing.purchaseCompleted.collect {
                delay(REVIEW_AFTER_PURCHASE_DELAY_MS)
                ReviewPrompt.maybeAsk(activity, controller, afterPurchase = true)
            }
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(REVIEW_SETTLE_DELAY_MS)
            ReviewPrompt.maybeAsk(activity, controller)
        }
    }

    // People updating from a Shizuku-only version hear about the built-in connection once.
    if (migrationSheetShowing) {
        ConnectMigrationSheet(
            onSwitch = {
                scope.launch { controller.store.setConnectPromptShown() }
                onSetUpConnection()
            },
            onNotNow = { scope.launch { controller.store.setConnectPromptShown() } }
        )
    }

    Scaffold(
        bottomBar = {
            MainNavigationBar(selectedTab = selectedTab, onSelect = onSelectTab)
        }
    ) { padding ->
        // Pages slide the way the bar's pill moves, as the onboarding steps do.
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(spatial) { if (forward) it else -it } + fadeIn(effects))
                    .togetherWith(slideOutHorizontally(spatial) { if (forward) -it else it } + fadeOut(effects))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            label = "NavTabAnimation"
        ) { tab ->
            when (tab) {
                NavTab.HOME -> HomeScreen(controller = controller, onSetUpConnection = onSetUpConnection)
                NavTab.CONDITIONS -> ConditionsScreen()
                NavTab.ABOUT -> AboutScreen(controller = controller, onSetUpConnection = onSetUpConnection)
            }
        }
    }
}

/**
 * The stock bar, but with one pill shared between the tabs that slides to the selected one. The
 * stock items each grow and shrink a pill of their own in place, so theirs is turned off and ours
 * is drawn behind them, sized and placed as theirs would be. It moves like a drop of liquid: the
 * leading edge runs ahead on a quick spring and the trailing edge follows on a looser one, so the
 * pill stretches, thins at the waist and shrinks at its tail on the way, then overshoots, bulges
 * and wobbles back into shape when it lands. See [drawLiquidPill].
 */
@Composable
private fun MainNavigationBar(selectedTab: NavTab, onSelect: (NavTab) -> Unit) {
    val stockColors = ShortNavigationBarItemDefaults.colors()
    val itemColors = ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
    val pillColor = stockColors.selectedIndicatorColor
    val density = LocalDensity.current
    val pillHalfWidth = with(density) { NavPillWidth.toPx() / 2 }
    val pillHalfHeight = with(density) { NavPillHeight.toPx() / 2 }

    // Where each tab's icon sits, relative to the bar, as last laid out.
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    val iconCentres = remember { mutableStateMapOf<NavTab, Offset>() }
    val target = iconCentres[selectedTab]?.minus(barOrigin)

    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var pillTab by remember { mutableStateOf<NavTab?>(null) }
    var movingRight by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        val centre = target ?: return@LaunchedEffect
        val toLeft = centre.x - pillHalfWidth
        val toRight = centre.x + pillHalfWidth
        if (pillTab == null || pillTab == selectedTab) {
            // First layout, or the bar changed size (rotating, folding): jump, don't slide.
            left.snapTo(toLeft)
            right.snapTo(toRight)
        } else {
            movingRight = toLeft > left.value
            launch { left.animateTo(toLeft, if (movingRight) LiquidTrailSpring else LiquidLeadSpring) }
            launch { right.animateTo(toRight, if (movingRight) LiquidLeadSpring else LiquidTrailSpring) }
        }
        pillTab = selectedTab
    }

    Box(
        modifier = Modifier
            .background(ShortNavigationBarDefaults.containerColor)
            .onPlaced { barOrigin = it.positionInRoot() }
            .drawBehind {
                val centre = target ?: return@drawBehind
                if (pillTab == null) return@drawBehind
                drawLiquidPill(
                    color = pillColor,
                    left = left.value,
                    right = right.value,
                    centreY = centre.y,
                    restWidth = pillHalfWidth * 2,
                    restHeight = pillHalfHeight * 2,
                    movingRight = movingRight
                )
            }
    ) {
        // The stock press ripple fills the item's own hidden pill, which flashes on the tapped tab
        // before ours arrives, so ours is the only feedback.
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            ShortNavigationBar(containerColor = Color.Transparent) {
                NavTab.entries.forEach { tab ->
                    val tabTitle = stringResource(tab.titleRes)
                    ShortNavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onSelect(tab) },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tabTitle,
                                modifier = Modifier.onPlaced {
                                    iconCentres[tab] = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                                }
                            )
                        },
                        label = { Text(tabTitle) },
                        colors = itemColors
                    )
                }
            }
        }
    }
}

/**
 * The bar's pill between [left] and [right]. At rest it's the stock pill. Squeezed narrower than
 * that, it bulges taller, as liquid would. Stretched wider, it thins at the waist and its trailing
 * end shrinks, so it reads as a drop pulling away from one tab towards the next.
 */
private fun DrawScope.drawLiquidPill(
    color: Color,
    left: Float,
    right: Float,
    centreY: Float,
    restWidth: Float,
    restHeight: Float,
    movingRight: Boolean
) {
    val width = right - left
    if (width <= 0f) return
    if (width <= restWidth) {
        val height = restHeight * (restWidth / width).coerceAtMost(MAX_BULGE)
        // However hard the tail overshoots, it stays a squashed pill rather than a sliver.
        val shown = width.coerceAtLeast(restWidth * MIN_SQUEEZE)
        drawRoundRect(
            color = color,
            topLeft = Offset((left + right - shown) / 2, centreY - height / 2),
            size = Size(shown, height),
            cornerRadius = CornerRadius(minOf(shown, height) / 2)
        )
        return
    }

    // How far into the stretch it is: 0 at rest, 1 at three times its resting width or more.
    val stretch = ((width - restWidth) / (restWidth * 2)).coerceIn(0f, 1f)
    val leadRadius = restHeight / 2 * lerp(1f, 0.9f, stretch)
    val tailRadius = restHeight / 2 * lerp(1f, 0.6f, stretch)
    val waist = restHeight / 2 * lerp(1f, 0.35f, stretch)
    val leftRadius = if (movingRight) tailRadius else leadRadius
    val rightRadius = if (movingRight) leadRadius else tailRadius
    val leftCx = left + leftRadius
    val rightCx = right - rightRadius
    val midX = (leftCx + rightCx) / 2
    val pull = (rightCx - leftCx) / 4

    val path = Path().apply {
        moveTo(leftCx, centreY - leftRadius)
        cubicTo(leftCx + pull, centreY - leftRadius, midX - pull, centreY - waist, midX, centreY - waist)
        cubicTo(midX + pull, centreY - waist, rightCx - pull, centreY - rightRadius, rightCx, centreY - rightRadius)
        arcTo(Rect(Offset(rightCx, centreY), rightRadius), -90f, 180f, false)
        cubicTo(rightCx - pull, centreY + rightRadius, midX + pull, centreY + waist, midX, centreY + waist)
        cubicTo(midX - pull, centreY + waist, leftCx + pull, centreY + leftRadius, leftCx, centreY + leftRadius)
        arcTo(Rect(Offset(leftCx, centreY), leftRadius), 90f, 180f, false)
        close()
    }
    drawPath(path, color)
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

// The stock item's indicator around a top icon.
private val NavPillWidth = 56.dp
private val NavPillHeight = 32.dp

// How much taller than at rest the pill can bulge when squeezed.
private const val MAX_BULGE = 1.25f

// The narrowest the pill is drawn, as a share of its resting width.
private const val MIN_SQUEEZE = 0.75f

// Bouncier than the motion scheme's springs, for the liquid look: the leading edge gets there
// quickly, and the trailing edge lags, then overshoots into it, so the pill wobbles as it settles.
private val LiquidLeadSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 700f)
private val LiquidTrailSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 260f)
