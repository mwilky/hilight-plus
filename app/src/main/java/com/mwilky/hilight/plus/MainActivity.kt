@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
    val scope = rememberCoroutineScope()
    val promptShown by controller.store.isConnectPromptShown.collectAsStateWithLifecycle(initialValue = true)
    val paired = remember(showConnect) { controller.daemon.wireless.isPaired() }

    if (showConnect) {
        ConnectScreen(controller = controller, onClose = { showConnect = false })
        return
    }

    // Play's review sheet, once it's due. Below the connect screen's early return so it can't
    // cover that, and held back while the migration sheet is up.
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
                showConnect = true
            },
            onNotNow = { scope.launch { controller.store.setConnectPromptShown() } }
        )
    }

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                NavTab.entries.forEach { tab ->
                    val tabTitle = stringResource(tab.titleRes)
                    ShortNavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tabTitle) },
                        label = { Text(tabTitle) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                NavTab.HOME -> HomeScreen(controller = controller, onSetUpConnection = { showConnect = true })
                NavTab.CONDITIONS -> ConditionsScreen()
                NavTab.ABOUT -> AboutScreen(controller = controller, onSetUpConnection = { showConnect = true })
            }
        }
    }
}
