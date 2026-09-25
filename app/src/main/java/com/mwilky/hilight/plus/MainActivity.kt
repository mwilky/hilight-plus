@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.ui.AboutScreen
import com.mwilky.hilight.plus.ui.ConditionsScreen
import com.mwilky.hilight.plus.ui.ConnectMigrationSheet
import com.mwilky.hilight.plus.ui.ConnectScreen
import com.mwilky.hilight.plus.ui.HiLightPlusTheme
import com.mwilky.hilight.plus.ui.HomeScreen
import com.mwilky.hilight.plus.ui.OnboardingScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            controller = controller
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        LightController.get(this).refreshStatus()
        NativeHiLightDetector.check(this)
    }
}

private enum class NavTab(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    CONDITIONS(R.string.nav_conditions, Icons.Rounded.Tune),
    ABOUT(R.string.nav_about, Icons.Rounded.Info)
}

@Composable
private fun MainAppNavigation(controller: LightController) {
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var showConnect by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val promptShown by controller.store.isConnectPromptShown.collectAsStateWithLifecycle(initialValue = true)
    val paired = remember(showConnect) { controller.daemon.wireless.isPaired() }

    if (showConnect) {
        ConnectScreen(controller = controller, onClose = { showConnect = false })
        return
    }

    // People updating from a Shizuku-only version hear about the built-in connection once.
    if (!promptShown && !paired) {
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
