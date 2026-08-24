package com.hilight.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.core.PatternRenderer
import com.hilight.plus.ui.HiLightPlusTheme
import com.hilight.plus.ui.OnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
                        MainScreen(
                            controller = controller,
                            onResetOnboarding = {
                                scope.launch {
                                    store.setOnboardingCompleted(false)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(controller: LightController, onResetOnboarding: () -> Unit) {
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFF16181C)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val inactiveColor = Color(0xFF282B30)
                            val frostedLensColor = Color(0x33FFFFFF)

                            Canvas(modifier = Modifier.size(120.dp)) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val ringRadius = size.minDimension * 0.35f
                                val spotRadius = size.minDimension * 0.18f

                                // Base unlit translucent channel ring
                                drawCircle(
                                    color = inactiveColor,
                                    radius = ringRadius + 6.dp.toPx(),
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx())
                                )

                                // Render diffused radial glow spots for each LED
                                for (i in 0 until 8) {
                                    val angle = (i * (2 * PI / 8.0) - (PI / 2.0))
                                    val spotCenter = Offset(
                                        x = center.x + (ringRadius * cos(angle)).toFloat(),
                                        y = center.y + (ringRadius * sin(angle)).toFloat()
                                    )

                                    val c = screenPreviewFrames.getOrElse(i) { 0x00000000 }
                                    val isLit = (c ushr 24) > 0 && ((c and 0x00FFFFFF) != 0)

                                    if (isLit) {
                                        val ledColor = Color(c)
                                        // Soft outer diffusion glow blending adjacent LEDs
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    ledColor.copy(alpha = 0.95f),
                                                    ledColor.copy(alpha = 0.65f),
                                                    ledColor.copy(alpha = 0.25f),
                                                    Color.Transparent
                                                ),
                                                center = spotCenter,
                                                radius = spotRadius * 1.5f
                                            ),
                                            radius = spotRadius * 1.5f,
                                            center = spotCenter
                                        )

                                        // Bright core emitter
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.85f),
                                                    ledColor.copy(alpha = 0.95f)
                                                ),
                                                center = spotCenter,
                                                radius = spotRadius * 0.45f
                                            ),
                                            radius = spotRadius * 0.45f,
                                            center = spotCenter
                                        )
                                    }
                                }

                                // Frosted glass diffusion overlay ring on top
                                drawCircle(
                                    color = frostedLensColor,
                                    radius = ringRadius + 6.dp.toPx(),
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx())
                                )
                            }
                        }
                    }

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

                    // Action Buttons: Test on Display & Test on Device
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Test on Display Button
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

                        // Test on Device Hardware Button
                        Button(
                            onClick = {
                                controller.testAlert(
                                    pattern = PatternMode.SOLID,
                                    color = selectedColor,
                                    durationMs = 3000
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
                onClick = onResetOnboarding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.main_reset_onboarding))
            }
        }
    }
}
