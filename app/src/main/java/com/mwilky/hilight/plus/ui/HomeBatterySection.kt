@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.BatteryFullTimeout
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.BatterySettings
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.ShizukuStatusCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Battery indicator page. A single global config rather than a list of per-target rules, so its
 * settings sit in segmented list groups (like the Notifications page's additional settings)
 * instead of rule rows opening the rule editor dialog.
 *
 * Grouped as: Charging (what shows while plugged in), Low battery, Look (colour), and When (the
 * same conditions a rule has, in the same order as the rule editor).
 */
@Composable
fun HomeBatteryPage(
    shizukuState: ShizukuBridge.State,
    shizukuError: String?,
    onDisconnectShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    battery: BatterySettings,
    globalQuietStartMinutes: Int,
    globalQuietEndMinutes: Int,
    onBatteryChange: (BatterySettings) -> Unit,
    renderer: PatternRenderer
) {
    var editingQuietStart by remember { mutableStateOf(false) }
    var editingQuietEnd by remember { mutableStateOf(false) }
    // Own window if set, otherwise the Conditions page window, same as a rule shows.
    val quietStart = battery.quietHoursStartMinutes ?: globalQuietStartMinutes
    val quietEnd = battery.quietHoursEndMinutes ?: globalQuietEndMinutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (shizukuState != ShizukuBridge.State.CONNECTED) {
            ShizukuStatusCard(
                shizukuState = shizukuState,
                shizukuError = shizukuError,
                onDisconnect = onDisconnectShizuku,
                onConnect = onConnectShizuku,
                onRequestPermission = onRequestShizukuPermission,
                onOpenShizukuApp = onOpenShizukuApp
            )
        }

        SectionHero(
            title = stringResource(R.string.hero_battery_title),
            statusText = if (battery.enabled) {
                stringResource(R.string.hero_status_battery_on)
            } else {
                stringResource(R.string.hero_status_off)
            },
            checked = battery.enabled,
            onCheckedChange = { onBatteryChange(battery.copy(enabled = it)) }
        )

        SectionBody(enabled = battery.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BatteryPreviewCard(battery = battery, renderer = renderer)

                RuleGroupHeader(stringResource(R.string.battery_group_charging))
                ChargingGroup(battery = battery, onBatteryChange = onBatteryChange, renderer = renderer)

                RuleGroupHeader(stringResource(R.string.battery_group_low))
                LowBatteryGroup(battery = battery, onBatteryChange = onBatteryChange)

                RuleGroupHeader(stringResource(R.string.dialog_section_look))
                LookGroup(battery = battery, onBatteryChange = onBatteryChange)

                RuleGroupHeader(stringResource(R.string.dialog_section_when))
                WhenGroup(
                    battery = battery,
                    quietStart = quietStart,
                    quietEnd = quietEnd,
                    onEditQuietStart = { editingQuietStart = true },
                    onEditQuietEnd = { editingQuietEnd = true },
                    onBatteryChange = onBatteryChange
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (editingQuietStart) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_start),
            initialMinutes = quietStart,
            onDismiss = { editingQuietStart = false },
            onConfirm = { minutes ->
                onBatteryChange(battery.copy(quietHoursStartMinutes = minutes))
                editingQuietStart = false
            }
        )
    }
    if (editingQuietEnd) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_end),
            initialMinutes = quietEnd,
            onDismiss = { editingQuietEnd = false },
            onConfirm = { minutes ->
                onBatteryChange(battery.copy(quietHoursEndMinutes = minutes))
                editingQuietEnd = false
            }
        )
    }
}

/** Whether to light while plugged in (with the pattern to use), and how long "full" stays on. */
@Composable
private fun ChargingGroup(
    battery: BatterySettings,
    onBatteryChange: (BatterySettings) -> Unit,
    renderer: PatternRenderer
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SwitchRow(
            index = 0,
            count = 2,
            title = stringResource(R.string.battery_show_charging_title),
            description = stringResource(R.string.battery_show_charging_desc),
            checked = battery.showCharging,
            onCheckedChange = { onBatteryChange(battery.copy(showCharging = it)) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                BatteryPattern.entries.forEach { pattern ->
                    BatteryPatternCard(
                        pattern = pattern,
                        autoColor = battery.autoColor,
                        color = battery.color,
                        selected = battery.chargingPattern == pattern,
                        renderer = renderer,
                        onClick = { onBatteryChange(battery.copy(chargingPattern = pattern)) }
                    )
                }
            }
        }

        // The full-charge display is the tail of charging, so it has nothing to time out when
        // charging isn't shown: disabled rather than hidden, so the group keeps its shape.
        OptionsRow(
            index = 1,
            count = 2,
            title = stringResource(R.string.battery_section_full_timeout_title),
            description = stringResource(R.string.battery_section_full_timeout_desc),
            options = BatteryFullTimeout.entries.map { it to stringResource(it.titleRes) },
            selected = battery.fullTimeout,
            onSelect = { onBatteryChange(battery.copy(fullTimeout = it)) },
            enabled = battery.showCharging
        )
    }
}

@Composable
private fun LowBatteryGroup(
    battery: BatterySettings,
    onBatteryChange: (BatterySettings) -> Unit
) {
    SwitchRow(
        index = 0,
        count = 1,
        title = stringResource(R.string.battery_section_low_title),
        description = stringResource(R.string.battery_section_low_desc),
        checked = battery.lowWarningEnabled,
        onCheckedChange = { onBatteryChange(battery.copy(lowWarningEnabled = it)) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.battery_threshold_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.battery_threshold_value, battery.lowThresholdPercent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = battery.lowThresholdPercent.toFloat(),
            onValueChange = { value ->
                val rounded = (value / 5f).roundToInt() * 5
                onBatteryChange(battery.copy(lowThresholdPercent = rounded.coerceIn(5, 50)))
            },
            valueRange = 5f..50f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LookGroup(
    battery: BatterySettings,
    onBatteryChange: (BatterySettings) -> Unit
) {
    SwitchRow(
        index = 0,
        count = 1,
        title = stringResource(R.string.battery_section_auto_color),
        description = stringResource(R.string.battery_section_auto_color_desc),
        checked = battery.autoColor,
        onCheckedChange = { onBatteryChange(battery.copy(autoColor = it)) },
        expandedVisible = !battery.autoColor
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PALETTE.forEach { swatch ->
                ColorSwatch(
                    color = swatch,
                    selected = battery.color == swatch,
                    enabled = true,
                    onClick = { onBatteryChange(battery.copy(color = swatch)) }
                )
            }
        }
    }
}

/** Phone position, Do Not Disturb, Quiet hours, then priority - the rule editor's order. */
@Composable
private fun WhenGroup(
    battery: BatterySettings,
    quietStart: Int,
    quietEnd: Int,
    onEditQuietStart: () -> Unit,
    onEditQuietEnd: () -> Unit,
    onBatteryChange: (BatterySettings) -> Unit
) {
    val rowCount = 4
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        OptionsRow(
            index = 0,
            count = rowCount,
            title = stringResource(R.string.dialog_orientation_title),
            description = stringResource(
                when (battery.faceDownMode) {
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
            selected = battery.faceDownMode,
            onSelect = { onBatteryChange(battery.copy(faceDownMode = it)) }
        )

        OptionsRow(
            index = 1,
            count = rowCount,
            title = stringResource(R.string.dialog_dnd_title),
            description = stringResource(
                when (battery.dndMode) {
                    DndMode.INHERIT -> R.string.dialog_dnd_desc_default
                    DndMode.ALWAYS -> R.string.dialog_dnd_desc_always
                    DndMode.SKIP -> R.string.dialog_dnd_desc_skip
                }
            ),
            options = DndMode.entries.map { mode ->
                mode to stringResource(
                    when (mode) {
                        DndMode.INHERIT -> R.string.dialog_mode_default
                        DndMode.ALWAYS -> R.string.dialog_mode_always
                        DndMode.SKIP -> R.string.dialog_mode_skip
                    }
                )
            },
            selected = battery.dndMode,
            onSelect = { onBatteryChange(battery.copy(dndMode = it)) }
        )

        OptionsRow(
            index = 2,
            count = rowCount,
            title = stringResource(R.string.dialog_quiet_hours_title),
            description = stringResource(
                when (battery.quietHoursMode) {
                    QuietHoursMode.INHERIT -> R.string.dialog_quiet_hours_desc_default
                    QuietHoursMode.ALWAYS -> R.string.dialog_quiet_hours_desc_always
                    QuietHoursMode.SKIP -> R.string.dialog_quiet_hours_desc_skip
                }
            ),
            options = QuietHoursMode.entries.map { mode ->
                mode to stringResource(
                    when (mode) {
                        QuietHoursMode.INHERIT -> R.string.dialog_mode_default
                        QuietHoursMode.ALWAYS -> R.string.dialog_mode_always
                        QuietHoursMode.SKIP -> R.string.dialog_mode_skip
                    }
                )
            },
            selected = battery.quietHoursMode,
            onSelect = { onBatteryChange(battery.copy(quietHoursMode = it)) },
            expandedVisible = battery.quietHoursMode == QuietHoursMode.SKIP
        ) {
            // Same start/end buttons the rule editor shows for a rule's own quiet window.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditQuietStart,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, quietStart)}")
                }
                OutlinedButton(
                    onClick = onEditQuietEnd,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, quietEnd)}")
                }
            }
        }

        SwitchRow(
            index = 3,
            count = rowCount,
            title = stringResource(R.string.battery_section_overrides_notifications),
            description = stringResource(R.string.battery_section_overrides_notifications_desc),
            checked = battery.overridesNotifications,
            onCheckedChange = { onBatteryChange(battery.copy(overridesNotifications = it)) }
        )
    }
}

/**
 * A segmented row with a switch, and optionally a control that expands inside the same row
 * while the setting is on (the threshold slider, the pattern cards, the fixed colour swatches).
 *
 * The text and switch are laid out directly rather than through the list item's headline and
 * trailing slots: a description long enough to wrap makes Material treat the row as a
 * three-line list item, which top-aligns the trailing slot instead of centring it.
 */
@Composable
private fun SwitchRow(
    index: Int,
    count: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expandedVisible: Boolean = checked,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(title)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            ExpandingContent(visible = expandedVisible, content = expandedContent)
        }
    }
}

/**
 * A segmented row whose choice is a connected button group, for settings with more than two
 * states, optionally with content that expands beneath the buttons for a given choice (the
 * quiet-hours window when "stay dark" is chosen).
 */
@Composable
private fun <T> OptionsRow(
    index: Int,
    count: Int,
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    expandedVisible: Boolean = false,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title)
            AnimatedText(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConnectedChoice(
                options = options,
                selected = selected,
                onSelect = onSelect,
                enabled = enabled,
                modifier = Modifier.padding(top = 10.dp)
            )
            ExpandingContent(visible = expandedVisible, content = expandedContent)
        }
    }
}

/** The connected toggle-button strip shared by the choice rows and the preview card. */
@Composable
private fun <T> ConnectedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { optionIndex, (option, label) ->
            ToggleButton(
                checked = selected == option,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                // The surrounding container is surfaceContainer, which the default unchecked
                // button colour matches, leaving unselected options invisible.
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shapes = when (optionIndex) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
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

private const val DISABLED_ALPHA = 0.38f

/** Fade-and-expand reveal used inside a row, with the same motion the section bodies use. */
@Composable
private fun ExpandingContent(
    visible: Boolean,
    content: (@Composable ColumnScope.() -> Unit)?
) {
    if (content == null) return
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(effects) + expandVertically(spatial),
        exit = fadeOut(effects) + shrinkVertically(spatial)
    ) {
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

/** The three states the battery layer can be showing, so each is one tap away in the preview. */
private enum class PreviewState { CHARGING, FULL, LOW }

/**
 * Live ring preview: pick which state to preview, then fine-tune the level. Mirrors the
 * engine's gating, so a state the settings wouldn't light (charging display off, low warning
 * off, level above the threshold) previews dark rather than pretending.
 */
@Composable
private fun BatteryPreviewCard(battery: BatterySettings, renderer: PatternRenderer) {
    var previewLevel by remember { mutableFloatStateOf(65f) }
    var previewState by remember { mutableStateOf(PreviewState.CHARGING) }
    var frames by remember { mutableStateOf(IntArray(8)) }

    LaunchedEffect(battery, previewLevel, previewState) {
        val level = previewLevel.roundToInt()
        val startMs = System.currentTimeMillis()
        while (isActive) {
            val pluggedIn = previewState != PreviewState.LOW
            frames = if (pluggedIn && !battery.showCharging) {
                IntArray(8)
            } else {
                renderer.renderBatteryFrame(
                    pattern = battery.chargingPattern,
                    levelPercent = level,
                    charging = previewState == PreviewState.CHARGING,
                    full = previewState == PreviewState.FULL,
                    low = previewState == PreviewState.LOW &&
                        battery.lowWarningEnabled && level <= battery.lowThresholdPercent,
                    autoColor = battery.autoColor,
                    fixedColor = battery.color,
                    brightness = 1.0f,
                    elapsedTimeMs = System.currentTimeMillis() - startMs,
                    ledCount = 8
                )
            }
            delay(33)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = HiLightTheme.CardShape
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiffusedRingPreview(
                frames = frames,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                size = 96.dp
            )
            ConnectedChoice(
                options = listOf(
                    PreviewState.CHARGING to stringResource(R.string.battery_preview_state_charging),
                    PreviewState.FULL to stringResource(R.string.battery_preview_state_full),
                    PreviewState.LOW to stringResource(R.string.battery_preview_state_low)
                ),
                selected = previewState,
                onSelect = { state ->
                    previewState = state
                    // Snap the level somewhere the chosen state actually shows.
                    previewLevel = when (state) {
                        PreviewState.CHARGING -> if (previewLevel >= 100f) 65f else previewLevel
                        PreviewState.FULL -> 100f
                        PreviewState.LOW -> minOf(previewLevel, battery.lowThresholdPercent.toFloat())
                    }
                }
            )
            Slider(
                value = previewLevel,
                onValueChange = { previewLevel = it },
                valueRange = 0f..100f,
                // "Full" is always 100%, so there's nothing to fine-tune.
                enabled = previewState != PreviewState.FULL,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.battery_preview_level_format, previewLevel.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryPatternCard(
    pattern: BatteryPattern,
    autoColor: Boolean,
    color: Long,
    selected: Boolean,
    renderer: PatternRenderer,
    onClick: () -> Unit
) {
    var frames by remember { mutableStateOf(IntArray(8)) }

    LaunchedEffect(pattern, autoColor, color) {
        val startMs = System.currentTimeMillis()
        while (isActive) {
            frames = renderer.renderBatteryFrame(
                pattern = pattern,
                levelPercent = 65,
                charging = true,
                full = false,
                low = false,
                autoColor = autoColor,
                fixedColor = color,
                brightness = 1.0f,
                elapsedTimeMs = System.currentTimeMillis() - startMs,
                ledCount = 8
            )
            delay(33)
        }
    }

    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "batteryPatternCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "batteryPatternContainer"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DiffusedRingPreview(
                frames = frames,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                size = 48.dp
            )
            Text(
                text = stringResource(pattern.titleRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
