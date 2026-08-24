package com.hilight.plus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.core.PatternRenderer
import com.hilight.plus.ui.ContactsScreen
import com.hilight.plus.ui.DiffusedRingPreview
import com.hilight.plus.ui.HiLightPlusTheme
import com.hilight.plus.ui.OnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                                    // 1. Reset DataStore onboarding flag
                                    store.setOnboardingCompleted(false)

                                    // 2. Unbind Shizuku
                                    controller.shizuku.unbind()

                                    // 3. Revoke runtime permissions
                                    runCatching {
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_PHONE_STATE)
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_CONTACTS)
                                        revokeSelfPermissionOnKill(Manifest.permission.READ_CALL_LOG)
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
    DASHBOARD("Dashboard", Icons.Rounded.Lightbulb),
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
                NavTab.CONTACTS -> {
                    CheckTelephonyPermissions()
                    ContactsScreen(controller = controller)
                }
            }
        }
    }
}

@Composable
private fun CheckTelephonyPermissions() {
    val context = LocalContext.current
    val permissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    )

    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissions)
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

    val scope = rememberCoroutineScope()
    val renderer = remember { PatternRenderer() }

    var selectedColor by remember { mutableLongStateOf(0xFF4285F4) }
    var screenPreviewFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }
    var isScreenTesting by remember { mutableStateOf(false) }

    val colorPalette = listOf(
        0xFF4285F4, // Google Blue
        0xFFEA4335, // Google Red
        0xFFFBBC05, // Google Yellow
        0xFF34A853, // Google Green
        0xFFFF007F, // Neon Pink
        0xFF8A2BE2, // Purple
        0xFF00E5FF, // Cyan
        0xFFFFFFFF  // Pure White
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent Stock HiLight Warning Banner
            if (stockState.anyActive) {
                val warningDesc = when {
                    stockState.bothActive -> stringResource(R.string.stock_hilight_warning_both)
                    stockState.favoriteCallsActive -> stringResource(R.string.stock_hilight_warning_calls)
                    else -> stringResource(R.string.stock_hilight_warning_assistant)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.stock_hilight_warning_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = androidx.core.text.HtmlCompat.fromHtml(
                                warningDesc,
                                androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
                            ).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { NativeHiLightDetector.openHiLightSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.stock_hilight_open_settings))
                        }
                    }
                }
            }

            // Shizuku Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Shizuku Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (shizukuState) {
                                ShizukuBridge.State.CONNECTED -> "connected ($ledCount LEDs)"
                                ShizukuBridge.State.CONNECTING -> "connecting"
                                ShizukuBridge.State.NEEDS_PERMISSION -> "approve it"
                                ShizukuBridge.State.NOT_INSTALLED -> "not installed"
                                ShizukuBridge.State.NOT_RUNNING -> "not running"
                                else -> "failed"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (shizukuState == ShizukuBridge.State.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    if (shizukuState != ShizukuBridge.State.CONNECTED) {
                        Button(
                            onClick = {
                                controller.shizuku.requestPermission()
                            }
                        ) {
                            Text(if (shizukuState == ShizukuBridge.State.NEEDS_PERMISSION) "Request access" else "Connect")
                        }
                    }
                }
            }

            // Color Picker & Testing Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.main_test_card_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Diffused Pixel 11 Glass Camera Ring Preview
                    DiffusedRingPreview(
                        frames = screenPreviewFrames,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        size = 110.dp
                    )

                    Text(
                        text = stringResource(R.string.main_test_card_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Color Palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        colorPalette.forEach { c ->
                            val isSelected = selectedColor == c
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                        }
                                    )
                                    .clickable { selectedColor = c }
                            )
                        }
                    }

                    // Action Buttons: Preview on Display & Test on Device
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isScreenTesting) return@OutlinedButton
                                isScreenTesting = true
                                scope.launch {
                                    val startMs = System.currentTimeMillis()
                                    val durationMs = 3000L
                                    while (isActive && System.currentTimeMillis() - startMs < durationMs) {
                                        val elapsed = System.currentTimeMillis() - startMs
                                        screenPreviewFrames = renderer.renderFrame(
                                            pattern = "solid",
                                            colorLong = selectedColor,
                                            brightness = 1.0f,
                                            speedMs = 800L,
                                            elapsedTimeMs = elapsed,
                                            ledCount = 8
                                        )
                                        delay(33)
                                    }
                                    screenPreviewFrames = IntArray(8) { 0x00000000 }
                                    isScreenTesting = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Smartphone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Preview")
                        }

                        Button(
                            onClick = {
                                controller.previewEffect(
                                    pattern = PatternMode.SOLID,
                                    color = selectedColor,
                                    durationMs = 3000L
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = (shizukuState == ShizukuBridge.State.CONNECTED)
                        ) {
                            Icon(
                                Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Test Device")
                        }
                    }
                }
            }

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
