package com.hilight.plus

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.ui.ContactsScreen
import com.hilight.plus.ui.DashboardScreen
import com.hilight.plus.ui.HiLightPlusTheme
import com.hilight.plus.ui.OnboardingScreen
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
                val isOnboardingCompleted by store.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

private enum class NavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    CALLS("Calls", Icons.Rounded.PhoneInTalk)
}

@Composable
private fun MainAppNavigation(controller: LightController, onResetAll: () -> Unit) {
    var selectedTab by remember { mutableStateOf(NavTab.DASHBOARD) }

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
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                NavTab.DASHBOARD -> DashboardScreen(controller = controller, onResetAll = onResetAll)
                NavTab.CALLS -> ContactsScreen(controller = controller)
            }
        }
    }
}
