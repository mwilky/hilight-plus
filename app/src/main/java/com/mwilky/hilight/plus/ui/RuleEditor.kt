@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TEST_DURATION_MS = 4000L

data class RuleEditorResult(
    val pattern: PatternMode,
    val color: Long,
    val faceDown: FaceDownMode,
    val autoColor: Boolean,
    val dndMode: DndMode,
    val quietHoursMode: QuietHoursMode,
    val quietHoursStartMinutes: Int,
    val quietHoursEndMinutes: Int
)

internal val PALETTE = listOf(
    0xFF4285F4, // Google Blue
    0xFFEA4335, // Google Red
    0xFFFBBC05, // Google Yellow
    0xFF34A853, // Google Green
    0xFFFF007F, // Neon Pink
    0xFF8A2BE2, // Purple
    0xFF00E5FF, // Cyan
    0xFFFFFFFF  // Pure White
)

/**
 * Full-screen Look / When editor for a call or notification rule.
 */
@Composable
fun CustomRuleDialog(
    title: String,
    initialColor: Long,
    initialPattern: PatternMode,
    renderer: PatternRenderer,
    controller: LightController,
    onDismiss: () -> Unit,
    onSave: (RuleEditorResult) -> Unit,
    initialFaceDown: FaceDownMode = FaceDownMode.INHERIT,
    initialDnd: DndMode = DndMode.INHERIT,
    initialQuietHours: QuietHoursMode = QuietHoursMode.INHERIT,
    initialQuietStart: Int = 22 * 60,
    initialQuietEnd: Int = 7 * 60,
    showAutoColorToggle: Boolean = false,
    initialAutoColor: Boolean = true,
    autoExtractedColor: Long? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The dialog has its own window, so the Activity's edge-to-edge bar appearance does not apply.
        val view = LocalView.current
        val isDark = isSystemInDarkTheme()
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
        RuleEditorContent(
            title = title,
            initialColor = initialColor,
            initialPattern = initialPattern,
            renderer = renderer,
            controller = controller,
            onDismiss = onDismiss,
            onSave = onSave,
            initialFaceDown = initialFaceDown,
            initialDnd = initialDnd,
            initialQuietHours = initialQuietHours,
            initialQuietStart = initialQuietStart,
            initialQuietEnd = initialQuietEnd,
            showAutoColorToggle = showAutoColorToggle,
            initialAutoColor = initialAutoColor,
            autoExtractedColor = autoExtractedColor
        )
    }
}

@Composable
private fun RuleEditorContent(
    title: String,
    initialColor: Long,
    initialPattern: PatternMode,
    renderer: PatternRenderer,
    controller: LightController,
    onDismiss: () -> Unit,
    onSave: (RuleEditorResult) -> Unit,
    initialFaceDown: FaceDownMode,
    initialDnd: DndMode,
    initialQuietHours: QuietHoursMode,
    initialQuietStart: Int,
    initialQuietEnd: Int,
    showAutoColorToggle: Boolean,
    initialAutoColor: Boolean,
    autoExtractedColor: Long?
) {
    var isAutoColor by remember(initialAutoColor) { mutableStateOf(initialAutoColor) }
    var selectedColor by remember(initialColor) {
        mutableLongStateOf(
            if (showAutoColorToggle && initialAutoColor && autoExtractedColor != null) autoExtractedColor else initialColor
        )
    }
    var selectedPattern by remember(initialPattern) { mutableStateOf(initialPattern) }
    var selectedFaceDown by remember(initialFaceDown) { mutableStateOf(initialFaceDown) }
    var selectedDnd by remember(initialDnd) { mutableStateOf(initialDnd) }
    var selectedQuietHours by remember(initialQuietHours) { mutableStateOf(initialQuietHours) }
    var quietStartMinutes by remember(initialQuietStart) { mutableIntStateOf(initialQuietStart) }
    var quietEndMinutes by remember(initialQuietEnd) { mutableIntStateOf(initialQuietEnd) }
    var editingQuietStart by remember { mutableStateOf(false) }
    var editingQuietEnd by remember { mutableStateOf(false) }

    // One shared clock drives every ring preview in the editor.
    var clockMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (isActive) {
            clockMs = System.currentTimeMillis() - start
            delay(33)
        }
    }

    // Test-on-LEDs: previews the current pattern/color on the physical lights.
    val connectionState by controller.daemon.state.collectAsStateWithLifecycle()
    var isTesting by remember { mutableStateOf(false) }
    LaunchedEffect(isTesting) {
        if (isTesting) {
            delay(TEST_DURATION_MS)
            isTesting = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.cancelTestPattern() }
    }
    fun toggleTest() {
        if (isTesting) {
            isTesting = false
            controller.cancelTestPattern()
        } else {
            isTesting = true
            controller.testPattern(selectedPattern, selectedColor, TEST_DURATION_MS)
        }
    }

    val patterns = remember { PatternMode.entries.filter { it != PatternMode.OFF } }
    val isColorEnabled = selectedPattern != PatternMode.RAINBOW
    val canPickManualColor = isColorEnabled && (!showAutoColorToggle || !isAutoColor)
    val manualColorAlpha by animateFloatAsState(
        targetValue = if (canPickManualColor) 1.0f else 0.35f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "manualColorAlpha"
    )

    fun save() {
        onSave(
            RuleEditorResult(
                pattern = selectedPattern,
                color = selectedColor,
                faceDown = selectedFaceDown,
                autoColor = isAutoColor,
                dndMode = selectedDnd,
                quietHoursMode = selectedQuietHours,
                quietHoursStartMinutes = quietStartMinutes,
                quietHoursEndMinutes = quietEndMinutes
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dialog_btn_close))
                        }
                    },
                    actions = {
                        Button(
                            onClick = { save() },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.dialog_btn_save))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
                PreviewHeader(
                    pattern = selectedPattern,
                    color = selectedColor,
                    elapsedMs = clockMs,
                    renderer = renderer,
                    isTesting = isTesting,
                    testEnabled = connectionState == DaemonBridge.State.CONNECTED,
                    onToggleTest = ::toggleTest
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EditorSection(title = stringResource(R.string.dialog_section_look)) {
                Text(
                    text = stringResource(R.string.dialog_pattern_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (patterns.indexOf(selectedPattern) - 1).coerceAtLeast(0)
                )
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(patterns, key = { it.id }) { p ->
                        PatternCard(
                            pattern = p,
                            color = selectedColor,
                            selected = selectedPattern == p,
                            elapsedMs = clockMs,
                            renderer = renderer,
                            onClick = { selectedPattern = p }
                        )
                    }
                }

                if (showAutoColorToggle) {
                    val setAutoColor: (Boolean) -> Unit = { auto ->
                        isAutoColor = auto
                        if (auto && autoExtractedColor != null) {
                            selectedColor = autoExtractedColor
                        }
                    }
                    ListItem(
                        onClick = { setAutoColor(!isAutoColor) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shapes = ListItemDefaults.shapes(),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        supportingContent = { Text(stringResource(R.string.dialog_auto_color_desc)) },
                        trailingContent = { Switch(checked = isAutoColor, onCheckedChange = setAutoColor) }
                    ) {
                        Text(stringResource(R.string.dialog_auto_color_title))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .alpha(manualColorAlpha),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (showAutoColorToggle && isAutoColor) {
                            stringResource(R.string.dialog_color_auto_label)
                        } else {
                            stringResource(R.string.dialog_color_label)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 4,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PALETTE.forEach { c ->
                            ColorSwatch(
                                color = c,
                                selected = selectedColor == c && canPickManualColor,
                                enabled = canPickManualColor,
                                onClick = { selectedColor = c }
                            )
                        }
                    }
                }
            }

            EditorSection(title = stringResource(R.string.dialog_section_when)) {
                ChoiceGroup(
                    title = stringResource(R.string.dialog_orientation_title),
                    description = stringResource(
                        when (selectedFaceDown) {
                            FaceDownMode.INHERIT -> R.string.dialog_orientation_desc_default
                            FaceDownMode.ALWAYS -> R.string.dialog_orientation_desc_always
                            FaceDownMode.ONLY_FACE_DOWN -> R.string.dialog_orientation_desc_face_down
                        }
                    ),
                    options = FaceDownMode.entries.map { mode ->
                        mode to stringResource(
                            when (mode) {
                                FaceDownMode.INHERIT -> R.string.dialog_orientation_default
                                FaceDownMode.ALWAYS -> R.string.dialog_orientation_always
                                FaceDownMode.ONLY_FACE_DOWN -> R.string.dialog_orientation_face_down
                            }
                        )
                    },
                    selected = selectedFaceDown,
                    onSelect = { selectedFaceDown = it }
                )
                ChoiceGroup(
                    title = stringResource(R.string.dialog_dnd_title),
                    description = when (selectedDnd) {
                        DndMode.INHERIT -> stringResource(R.string.dialog_dnd_desc_default)
                        DndMode.ALWAYS -> stringResource(R.string.dialog_dnd_desc_always)
                        DndMode.SKIP -> stringResource(R.string.dialog_dnd_desc_skip)
                    },
                    options = DndMode.entries.map { mode ->
                        mode to when (mode) {
                            DndMode.INHERIT -> stringResource(R.string.dialog_mode_default)
                            DndMode.ALWAYS -> stringResource(R.string.dialog_mode_always)
                            DndMode.SKIP -> stringResource(R.string.dialog_mode_skip)
                        }
                    },
                    selected = selectedDnd,
                    onSelect = { selectedDnd = it }
                )
                ChoiceGroup(
                    title = stringResource(R.string.dialog_quiet_hours_title),
                    description = when (selectedQuietHours) {
                        QuietHoursMode.INHERIT -> stringResource(R.string.dialog_quiet_hours_desc_default)
                        QuietHoursMode.ALWAYS -> stringResource(R.string.dialog_quiet_hours_desc_always)
                        QuietHoursMode.SKIP -> stringResource(R.string.dialog_quiet_hours_desc_skip)
                    },
                    options = QuietHoursMode.entries.map { mode ->
                        mode to when (mode) {
                            QuietHoursMode.INHERIT -> stringResource(R.string.dialog_mode_default)
                            QuietHoursMode.ALWAYS -> stringResource(R.string.dialog_mode_always)
                            QuietHoursMode.SKIP -> stringResource(R.string.dialog_mode_skip)
                        }
                    },
                    selected = selectedQuietHours,
                    onSelect = { selectedQuietHours = it }
                )
                AnimatedVisibility(
                    visible = selectedQuietHours == QuietHoursMode.SKIP,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
                ) {
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingQuietStart = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, quietStartMinutes)}")
                        }
                        OutlinedButton(
                            onClick = { editingQuietEnd = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, quietEndMinutes)}")
                        }
                    }
                }
            }
        }
    }

    if (editingQuietStart) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_start),
            initialMinutes = quietStartMinutes,
            onDismiss = { editingQuietStart = false },
            onConfirm = { minutes ->
                quietStartMinutes = minutes
                editingQuietStart = false
            }
        )
    }
    if (editingQuietEnd) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_end),
            initialMinutes = quietEndMinutes,
            onDismiss = { editingQuietEnd = false },
            onConfirm = { minutes ->
                quietEndMinutes = minutes
                editingQuietEnd = false
            }
        )
    }
}

@Composable
private fun PatternRing(
    pattern: PatternMode,
    color: Long,
    elapsedMs: Long,
    renderer: PatternRenderer,
    size: Dp
) {
    val frames = renderer.renderFrame(
        pattern = pattern.id,
        colorLong = color,
        brightness = 1.0f,
        speedMs = pattern.speedMs(),
        elapsedTimeMs = elapsedMs,
        ledCount = 8
    )
    DiffusedRingPreview(
        frames = frames,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        size = size
    )
}

@Composable
private fun PreviewHeader(
    pattern: PatternMode,
    color: Long,
    elapsedMs: Long,
    renderer: PatternRenderer,
    isTesting: Boolean,
    testEnabled: Boolean,
    onToggleTest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PatternRing(pattern = pattern, color = color, elapsedMs = elapsedMs, renderer = renderer, size = 96.dp)
            Text(
                text = stringResource(pattern.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val testContainerColor by animateColorAsState(
                targetValue = if (isTesting) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "testButtonContainer"
            )
            val testContentColor by animateColorAsState(
                targetValue = if (isTesting) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "testButtonContent"
            )
            FilledTonalButton(
                onClick = onToggleTest,
                enabled = testEnabled,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = testContainerColor,
                    contentColor = testContentColor
                )
            ) {
                Icon(
                    if (isTesting) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        if (isTesting) R.string.rule_editor_test_leds_stop else R.string.rule_editor_test_leds
                    )
                )
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            content()
        }
    }
}

@Composable
private fun PatternCard(
    pattern: PatternMode,
    color: Long,
    selected: Boolean,
    elapsedMs: Long,
    renderer: PatternRenderer,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "patternCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "patternContainer"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = Modifier.width(88.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PatternRing(pattern = pattern, color = color, elapsedMs = elapsedMs, renderer = renderer, size = 48.dp)
            Text(
                text = stringResource(pattern.titleRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ColorSwatch(
    color: Long,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "swatchScale"
    )
    val swatch = Color(color)
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge
        )
        AnimatedText(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            options.forEachIndexed { index, (mode, label) ->
                ToggleButton(
                    checked = selected == mode,
                    onCheckedChange = { onSelect(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(name = "Rule editor", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RuleEditorPreview() {
    HiLightPlusTheme {
        RuleEditorContent(
            title = "Configure WhatsApp",
            initialColor = 0xFF4285F4,
            initialPattern = PatternMode.PULSE,
            renderer = PatternRenderer(),
            controller = LightController.get(LocalContext.current),
            onDismiss = {},
            onSave = {},
            initialFaceDown = FaceDownMode.INHERIT,
            initialDnd = DndMode.INHERIT,
            initialQuietHours = QuietHoursMode.SKIP,
            initialQuietStart = 22 * 60,
            initialQuietEnd = 7 * 60,
            showAutoColorToggle = true,
            initialAutoColor = false,
            autoExtractedColor = 0xFF25D366
        )
    }
}
