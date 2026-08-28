package com.mwilky.hilight.plus

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mwilky.hilight.plus.ui.AboutScreen
import com.mwilky.hilight.plus.ui.ConditionsScreen
import com.mwilky.hilight.plus.ui.HiLightPlusTheme
import com.mwilky.hilight.plus.ui.HomeScreen
import com.mwilky.hilight.plus.ui.OnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = LightController.get(this)
        NativeHiLightDetector.check(this)
        val store = AppStore.get(this)

        setContent {
            HiLightPlusTheme {
                val isOnboardingCompleted by store.isOnboardingCompleted.collectAsStateWithLifecycle(
                    initialValue = null
                )
                val scope = rememberCoroutineScope()

                val owner = LocalLifecycleOwner.current
                LaunchedEffect(owner) {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            controller.refreshStatus()
                            NativeHiLightDetector.check(this@MainActivity)
                            delay(1500)
                        }
                    }
                }

                when (isOnboardingCompleted) {
                    null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    false -> {
                        OnboardingScreen(
                            controller = controller,
                            onComplete = {
                                scope.launch {
                                    store.setOnboardingCompleted(true)
                                }
                            }
                        )
                    }

                    true -> {
                        MainAppNavigation(
                            controller = controller,
                            onResetAll = {
                                scope.launch {
                                    store.setOnboardingCompleted(false)
                                    controller.shizuku.unbind()
                                    runCatching {
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_PHONE_STATE)
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_CALL_LOG)
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_CONTACTS)
                                        revokeSelfPermissionOnKill(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LightController.get(this).refreshStatus()
        NativeHiLightDetector.check(this)
    }
}

private enum class NavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    CONDITIONS("Conditions", Icons.Rounded.Tune),
    ABOUT("About", Icons.Rounded.Info)
}

@Composable
private fun MainAppNavigation(controller: LightController, onResetAll: () -> Unit) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
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
                NavTab.HOME -> HomeScreen(controller = controller)
                NavTab.CONDITIONS -> ConditionsScreen(controller = controller)
                NavTab.ABOUT -> AboutScreen(controller = controller, onResetAll = onResetAll)
            }
        }
    }
}
