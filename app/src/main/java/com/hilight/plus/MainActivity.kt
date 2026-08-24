package com.hilight.plus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.ui.ContactsScreen
import com.hilight.plus.ui.ExpressiveStatusCard
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
    CONTACTS("Calls", Icons.Rounded.PhoneInTalk)
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
                NavTab.CONTACTS -> ContactsScreen(controller = controller)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(controller: LightController, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()
    val ledCount by controller.shizuku.ledCount.collectAsStateWithLifecycle()

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isContactsGranted = hasContactsPermission()
            NativeHiLightDetector.check(context)
            controller.refreshStatus()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isPhoneGranted = hasPhonePermission()
        isContactsGranted = hasContactsPermission()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "System Diagnostics & Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Shizuku Privileged Access Status Card
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            ExpressiveStatusCard(
                title = "Shizuku Privileged Access",
                subtitle = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Active session holding privileged control over $ledCount Pixel 11 rear LEDs."
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' to grant privileged LED access."
                    ShizukuBridge.State.NOT_RUNNING -> "Shizuku daemon is stopped. Start via Wireless Debugging or ADB."
                    ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed on this device."
                    ShizukuBridge.State.CONNECTING -> "Connecting to local Shizuku binder daemon..."
                    else -> controller.shizuku.errorText() ?: "Could not establish binder connection to Shizuku."
                },
                icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                statusText = when (shizukuState) {
                    ShizukuBridge.State.CONNECTED -> "Connected ($ledCount LEDs)"
                    ShizukuBridge.State.CONNECTING -> "Connecting"
                    ShizukuBridge.State.NEEDS_PERMISSION -> "Needs Permission"
                    ShizukuBridge.State.NOT_RUNNING -> "Not Running"
                    ShizukuBridge.State.NOT_INSTALLED -> "Not Installed"
                    else -> "Disconnected"
                },
                isPositive = isShizukuConnected,
                trailingAction = {
                    when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> {
                            TextButton(onClick = { controller.shizuku.unbind() }) {
                                Text("Disconnect")
                            }
                        }
                        ShizukuBridge.State.NEEDS_PERMISSION -> {
                            Button(onClick = { controller.shizuku.requestPermission() }) {
                                Text("Authorize")
                            }
                        }
                        ShizukuBridge.State.NOT_INSTALLED -> {
                            Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                Text("Install")
                            }
                        }
                        ShizukuBridge.State.NOT_RUNNING -> {
                            Button(onClick = { controller.shizuku.openShizukuApp(context) }) {
                                Text("Open")
                            }
                        }
                        else -> {
                            Button(onClick = { controller.shizuku.refresh() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            )

            // 2. Native Pixel HiLight Status & Conflict Card
            val isNativeConflict = stockState.anyActive
            ExpressiveStatusCard(
                title = "Stock HiLight Integration",
                subtitle = when {
                    stockState.bothActive -> "Both Favorite Calls and Assistant Feedback are active in System Settings. These will override HiLight Plus animations."
                    stockState.favoriteCallsActive -> "Stock Favorite Calls is active in System Settings and will conflict with custom caller lighting."
                    stockState.assistantFeedbackActive -> "Stock Assistant Feedback is active in System Settings and will conflict with custom AI lighting."
                    else -> "Stock settings are cleared. HiLight Plus has full, unhindered control of the rear LED array."
                },
                icon = if (isNativeConflict) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                statusText = if (isNativeConflict) "Conflict Active" else "Optimized",
                isPositive = !isNativeConflict,
                isWarning = isNativeConflict,
                trailingAction = {
                    if (isNativeConflict) {
                        Button(
                            onClick = { NativeHiLightDetector.openHiLightSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Fix Settings")
                        }
                    }
                }
            )

            // 3. Android Telephony & Contacts Permissions Card
            val hasAllPerms = isPhoneGranted && isContactsGranted
            val permsStatusText = when {
                hasAllPerms -> "Granted"
                !isPhoneGranted && !isContactsGranted -> "Missing 2 Permissions"
                !isPhoneGranted -> "Missing Phone State"
                else -> "Missing Contacts"
            }
            val permsDesc = when {
                hasAllPerms -> "Phone State and Contacts permissions are active. Incoming call detection is fully operational."
                !isPhoneGranted && !isContactsGranted -> "Phone State (call detection) and Contacts (caller matching) permissions are required for custom call lighting."
                !isPhoneGranted -> "Phone State permission is missing. The app cannot detect incoming ringing calls."
                else -> "Contacts permission is missing. The app cannot look up names and custom caller lighting rules."
            }

            ExpressiveStatusCard(
                title = "Call Telephony & Contacts",
                subtitle = permsDesc,
                icon = if (hasAllPerms) Icons.Rounded.ContactPhone else Icons.Rounded.PermPhoneMsg,
                statusText = permsStatusText,
                isPositive = hasAllPerms,
                isWarning = !hasAllPerms,
                trailingAction = {
                    if (!hasAllPerms) {
                        val missing = mutableListOf<String>().apply {
                            if (!isPhoneGranted) add(Manifest.permission.READ_PHONE_STATE)
                            if (!isContactsGranted) add(Manifest.permission.READ_CONTACTS)
                        }.toTypedArray()

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { permissionLauncher.launch(missing) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Grant")
                            }
                            TextButton(onClick = { openAppSettings() }) {
                                Text("Settings", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.main_reset_onboarding))
            }
        }
    }
}
