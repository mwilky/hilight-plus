package com.hilight.plus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.LightController
import com.hilight.plus.PatternMode
import com.hilight.plus.assistant.GeminiAssistantWatcher
import com.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiScreen(controller: LightController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { PatternRenderer() }

    val isGeminiEnabled by controller.store.isGeminiEnabled.collectAsStateWithLifecycle(initialValue = true)

    // 1. Listening State (Default: Google 4-Color Quad)
    val listeningPattern by controller.store.geminiListeningPattern.collectAsStateWithLifecycle(initialValue = PatternMode.GOOGLE_QUAD)
    val listeningColor by controller.store.geminiListeningColor.collectAsStateWithLifecycle(initialValue = 0xFF4285F4)

    // 2. Thinking State (Default: Gemini Comet Cyan)
    val thinkingPattern by controller.store.geminiThinkingPattern.collectAsStateWithLifecycle(initialValue = PatternMode.GEMINI_THINKING)
    val thinkingColor by controller.store.geminiThinkingColor.collectAsStateWithLifecycle(initialValue = 0xFF00E5FF)

    // 3. Responding State (Default: Gemini Glow Blue)
    val respondingPattern by controller.store.geminiRespondingPattern.collectAsStateWithLifecycle(initialValue = PatternMode.GEMINI_RESPONDING)
    val respondingColor by controller.store.geminiRespondingColor.collectAsStateWithLifecycle(initialValue = 0xFF4285F4)

    var isAccessibilityEnabled by remember { mutableStateOf(GeminiAssistantWatcher.isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isAccessibilityEnabled = GeminiAssistantWatcher.isAccessibilityServiceEnabled(context)
        }
    }

    var configuringStage by remember { mutableStateOf<AssistantStage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Assistant") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp, top = 10.dp)
        ) {
            // Master Switch for Gemini Assistant Illumination
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Assistant Illumination",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Light the rear array when Gemini is summoned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isGeminiEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { controller.store.setGeminiEnabled(enabled) }
                            }
                        )
                    }
                }
            }

            // Accessibility Authorization Card
            if (!isAccessibilityEnabled) {
                item {
                    ExpressiveStatusCard(
                        title = "Assistant Observer Required",
                        subtitle = "HiLight Plus needs Accessibility access to detect when Gemini or Google Assistant overlay opens and output voice responses.",
                        icon = Icons.Rounded.Assistant,
                        statusText = "Not Enabled",
                        accentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        isWarning = true,
                        bottomAction = {
                            Button(
                                onClick = { GeminiAssistantWatcher.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.SettingsAccessibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Enable in Accessibility Settings")
                            }
                        }
                    )
                }
            }

            if (isGeminiEnabled) {
                // Section Title
                item {
                    Text(
                        text = "Assistant Lifecycle Stages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // 1. Invoked / Listening Stage Card
                item {
                    AssistantStageCard(
                        title = "1. Listening Stage",
                        subtitle = "When mic is open and listening for speech",
                        pattern = listeningPattern,
                        color = listeningColor,
                        renderer = renderer,
                        onEdit = { configuringStage = AssistantStage.LISTENING }
                    )
                }

                // 2. Thinking / Processing Stage Card
                item {
                    AssistantStageCard(
                        title = "2. Thinking Stage",
                        subtitle = "When processing and generating response",
                        pattern = thinkingPattern,
                        color = thinkingColor,
                        renderer = renderer,
                        onEdit = { configuringStage = AssistantStage.THINKING }
                    )
                }

                // 3. Responding / Speaking Stage Card
                item {
                    AssistantStageCard(
                        title = "3. Responding Stage",
                        subtitle = "When Gemini delivers spoken answer",
                        pattern = respondingPattern,
                        color = respondingColor,
                        renderer = renderer,
                        onEdit = { configuringStage = AssistantStage.RESPONDING }
                    )
                }
            }
        }
    }

    // Stage Customizer Dialog
    if (configuringStage != null) {
        val stage = configuringStage!!
        val currentPattern = when (stage) {
            AssistantStage.LISTENING -> listeningPattern
            AssistantStage.THINKING -> thinkingPattern
            AssistantStage.RESPONDING -> respondingPattern
        }
        val currentColor = when (stage) {
            AssistantStage.LISTENING -> listeningColor
            AssistantStage.THINKING -> thinkingColor
            AssistantStage.RESPONDING -> respondingColor
        }

        AssistantConfigDialog(
            stage = stage,
            initialPattern = currentPattern,
            initialColor = currentColor,
            renderer = renderer,
            onDismiss = { configuringStage = null },
            onSave = { newPattern, newColor ->
                scope.launch {
                    when (stage) {
                        AssistantStage.LISTENING -> {
                            controller.store.setGeminiListeningPattern(newPattern)
                            controller.store.setGeminiListeningColor(newColor)
                        }
                        AssistantStage.THINKING -> {
                            controller.store.setGeminiThinkingPattern(newPattern)
                            controller.store.setGeminiThinkingColor(newColor)
                        }
                        AssistantStage.RESPONDING -> {
                            controller.store.setGeminiRespondingPattern(newPattern)
                            controller.store.setGeminiRespondingColor(newColor)
                        }
                    }
                    configuringStage = null
                }
            }
        )
    }
}

private enum class AssistantStage(val title: String, val desc: String) {
    LISTENING("Configure Listening Stage", "Illumination while Gemini is listening to your voice input."),
    THINKING("Configure Thinking Stage", "Illumination while Gemini processes tokens and generates response."),
    RESPONDING("Configure Responding Stage", "Illumination while Gemini speaks or presents answers.")
}

@Composable
private fun AssistantStageCard(
    title: String,
    subtitle: String,
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MiniGeminiAnimationIcon(pattern = pattern, color = color, renderer = renderer, size = 38.dp)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pattern:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pattern.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit stage")
            }
        }
    }
}

@Composable
private fun MiniGeminiAnimationIcon(
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    size: androidx.compose.ui.unit.Dp
) {
    var miniFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }

    LaunchedEffect(pattern, color) {
        val startMs = System.currentTimeMillis()
        val speed = when (pattern) {
            PatternMode.BREATHE, PatternMode.GOOGLE_QUAD -> 2000L
            PatternMode.WAVE, PatternMode.GEMINI_RESPONDING -> 1200L
            PatternMode.COMET, PatternMode.GEMINI_THINKING -> 800L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> 1000L
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            miniFrames = renderer.renderFrame(
                pattern = pattern.id,
                colorLong = color,
                brightness = 1.0f,
                speedMs = speed,
                elapsedTimeMs = elapsed,
                ledCount = 8
            )
            delay(33)
        }
    }

    DiffusedRingPreview(
        frames = miniFrames,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        size = size
    )
}

@Composable
private fun AssistantConfigDialog(
    stage: AssistantStage,
    initialPattern: PatternMode,
    initialColor: Long,
    renderer: PatternRenderer,
    onDismiss: () -> Unit,
    onSave: (PatternMode, Long) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(initialColor) }
    var selectedPattern by remember { mutableStateOf(initialPattern) }
    var dialogPreviewFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }

    val palette = listOf(
        0xFF4285F4, // Google Blue
        0xFFEA4335, // Google Red
        0xFFFBBC05, // Google Yellow
        0xFF34A853, // Google Green
        0xFF00E5FF, // Gemini Cyan
        0xFFFF007F, // Neon Pink
        0xFF8A2BE2, // Purple
        0xFFFFFFFF  // Pure White
    )

    LaunchedEffect(selectedPattern, selectedColor) {
        val startMs = System.currentTimeMillis()
        val speed = when (selectedPattern) {
            PatternMode.BREATHE, PatternMode.GOOGLE_QUAD -> 2000L
            PatternMode.WAVE, PatternMode.GEMINI_RESPONDING -> 1200L
            PatternMode.COMET, PatternMode.GEMINI_THINKING -> 800L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> 1000L
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            dialogPreviewFrames = renderer.renderFrame(
                pattern = selectedPattern.id,
                colorLong = selectedColor,
                brightness = 1.0f,
                speedMs = speed,
                elapsedTimeMs = elapsed,
                ledCount = 8
            )
            delay(16)
        }
    }

    val isColorEnabled = selectedPattern != PatternMode.RAINBOW && selectedPattern != PatternMode.GOOGLE_QUAD
    val colorAlpha by animateFloatAsState(
        targetValue = if (isColorEnabled) 1.0f else 0.35f,
        animationSpec = tween(durationMillis = 200),
        label = "assistantColorAlpha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stage.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stage.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Ring inside Dialog
                DiffusedRingPreview(
                    frames = dialogPreviewFrames,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    size = 75.dp
                )

                Text(
                    text = "Select Pattern",
                    style = MaterialTheme.typography.labelLarge
                )

                val patterns = listOf(
                    PatternMode.GOOGLE_QUAD,
                    PatternMode.GEMINI_THINKING,
                    PatternMode.GEMINI_RESPONDING,
                    PatternMode.BREATHE,
                    PatternMode.WAVE,
                    PatternMode.COMET,
                    PatternMode.RAINBOW,
                    PatternMode.PULSE
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patterns.forEach { p ->
                        FilterChip(
                            selected = selectedPattern == p,
                            onClick = { selectedPattern = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                // Fixed-height color selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .alpha(colorAlpha),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Alert Color",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        palette.forEach { c ->
                            val isSelected = selectedColor == c && isColorEnabled
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = isColorEnabled) { selectedColor = c }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedPattern, selectedColor) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
