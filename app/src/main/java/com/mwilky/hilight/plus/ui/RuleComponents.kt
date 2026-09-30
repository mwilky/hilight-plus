@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.ClipData
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.MIN_BRIGHTNESS
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.RuleSort
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Large section header with the one prominent toggle for a whole feature (calls / notifications).
 */
@Composable
fun SectionHero(
    title: String,
    statusText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContainer"
    )
    val content = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HiLightTheme.CardShape,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@Composable
fun SectionOffHint(text: String) {
    ListItem(
        leadingContent = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
    ) {
        Text(text)
    }
}

/**
 * One rule in a segmented group. Tap edits, the trailing switch toggles, long-press offers delete.
 */
@Composable
fun RuleListItem(
    index: Int,
    count: Int,
    title: String,
    pattern: PatternMode,
    color: Long,
    faceDownMode: FaceDownMode,
    renderer: PatternRenderer,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    dndMode: DndMode = DndMode.INHERIT,
    quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        SegmentedListItem(
            onClick = onEdit,
            onLongClick = if (onDelete != null) ({ menuOpen = true }) else null,
            onLongClickLabel = if (onDelete != null) stringResource(R.string.rule_delete_cd) else null,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = {
                AnimatedRingBadge(pattern = pattern, color = color, renderer = renderer, size = 36.dp)
            },
            supportingContent = {
                // Pattern on its own line; only conditions overridden from the Conditions page get a chip.
                val chips = buildList {
                    when (faceDownMode) {
                        FaceDownMode.INHERIT -> Unit
                        FaceDownMode.ALWAYS -> add(ConditionChip(Icons.Rounded.ScreenRotation, R.string.rule_trigger_always, lights = true))
                        FaceDownMode.ONLY_FACE_DOWN -> add(ConditionChip(Icons.Rounded.ScreenRotation, R.string.rule_trigger_face_down, lights = false))
                    }
                    when (dndMode) {
                        DndMode.INHERIT -> Unit
                        DndMode.ALWAYS -> add(ConditionChip(Icons.Rounded.DoNotDisturbOn, R.string.rule_dnd_always, lights = true))
                        DndMode.SKIP -> add(ConditionChip(Icons.Rounded.DoNotDisturbOn, R.string.rule_dnd_skip, lights = false))
                    }
                    when (quietHoursMode) {
                        QuietHoursMode.INHERIT -> Unit
                        QuietHoursMode.ALWAYS -> add(ConditionChip(Icons.Rounded.Bedtime, R.string.rule_quiet_hours_always, lights = true))
                        QuietHoursMode.SKIP -> add(ConditionChip(Icons.Rounded.Bedtime, R.string.rule_quiet_hours_skip, lights = false))
                    }
                }
                Column(
                    modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedText(text = stringResource(pattern.titleRes))
                    if (chips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chips.forEach { ConditionChipView(it) }
                        }
                    }
                }
            },
            trailingContent = {
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
        ) {
            Text(title)
        }
        if (onDelete != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rule_delete_cd)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private data class ConditionChip(val icon: ImageVector, @StringRes val labelRes: Int, val lights: Boolean)

/**
 * Small read-only badge for one overridden condition. Tertiary when the rule lights anyway,
 * muted when it stays dark; the label spells the direction out too so colour isn't the only cue.
 */
@Composable
private fun ConditionChipView(chip: ConditionChip) {
    Surface(
        shape = CircleShape,
        color = if (chip.lights) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (chip.lights) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(chip.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = stringResource(chip.labelRes), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Text that cross-fades and resizes with the expressive motion scheme when its content changes.
 */
@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            fadeIn(effects).togetherWith(fadeOut(effects))
                .using(SizeTransform(clip = false) { _, _ -> spatial })
        },
        label = "animatedText"
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

/** Group title, with an optional trailing control such as [RuleSortButton]. */
@Composable
fun RuleGroupHeader(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/**
 * Shows a custom-rule list's current order; tapping opens the choices, current one ticked.
 * [forContacts] labels name sorts as first name, beside the last-name options.
 */
@Composable
fun RuleSortButton(
    sort: RuleSort,
    options: List<RuleSort>,
    forContacts: Boolean,
    onSortChange: (RuleSort) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun RuleSort.label() = if (forContacts) contactTitleRes else titleRes
    Box {
        TextButton(onClick = { menuOpen = true }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(sort.label()), maxLines = 1)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        menuOpen = false
                        onSortChange(option)
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyRuleHint(text: String, icon: ImageVector) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
    ) {
        Text(text)
    }
}

@Composable
fun AddRuleButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
fun AnimatedRingBadge(
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    size: Dp,
    animate: Boolean = true
) {
    var miniFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }
    val shouldAnimate = animate && pattern != PatternMode.OFF && pattern != PatternMode.SOLID

    LaunchedEffect(pattern, color, shouldAnimate) {
        val speed = pattern.speedMs()
        fun frame(elapsed: Long) = renderer.renderFrame(
            pattern = pattern.id,
            colorLong = color,
            brightness = 1.0f,
            speedMs = speed,
            elapsedTimeMs = elapsed,
            ledCount = 8
        )
        if (!shouldAnimate) {
            miniFrames = frame(0L)
            return@LaunchedEffect
        }
        // One clock shared by every badge, so a rule added later runs in step with the rest
        // instead of starting its cycle from zero.
        while (isActive) {
            miniFrames = frame(SystemClock.uptimeMillis())
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

/**
 * Brightness in [stepPercent] steps from [MIN_BRIGHTNESS] to full, with the current value on the
 * right. [titleStyle] lets the title match the labels around it.
 */
@Composable
internal fun BrightnessSlider(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = LocalTextStyle.current,
    stepPercent: Int = 10
) {
    Column(modifier = modifier) {
        BrightnessHeader(
            value = stringResource(R.string.brightness_value, (brightness * 100).roundToInt()),
            titleStyle = titleStyle
        )
        Slider(
            value = brightness,
            onValueChange = { onBrightnessChange(snapBrightness(it, stepPercent)) },
            valueRange = MIN_BRIGHTNESS..1f,
            steps = brightnessSteps(stepPercent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BrightnessHeader(value: String, titleStyle: TextStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(R.string.brightness_title), style = titleStyle)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Stops between the slider's ends, one per [stepPercent]. */
private fun brightnessSteps(stepPercent: Int): Int =
    ((1f - MIN_BRIGHTNESS) * 100).roundToInt() / stepPercent - 1

private fun snapBrightness(value: Float, stepPercent: Int): Float =
    (value * 100 / stepPercent).roundToInt() * stepPercent / 100f

private const val SWATCHES_PER_ROW = 5

/**
 * The preset [PALETTE] plus a custom swatch that opens [ColorPickerDialog]. The custom swatch
 * takes on the chosen colour whenever it isn't one of the presets. Selection is only shown
 * while [enabled], so an auto colour never looks like a manual pick.
 */
@Composable
internal fun ColorSwatchGrid(
    selectedColor: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val isCustom = selectedColor !in PALETTE
    val slots = PALETTE.size + 1
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (rowStart in 0 until slots step SWATCHES_PER_ROW) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in rowStart until rowStart + SWATCHES_PER_ROW) {
                    when {
                        i < PALETTE.size -> {
                            val c = PALETTE[i]
                            ColorSwatch(
                                color = c,
                                selected = enabled && selectedColor == c,
                                enabled = enabled,
                                onClick = { onSelect(c) }
                            )
                        }
                        i == PALETTE.size -> CustomColorSwatch(
                            customColor = if (enabled && isCustom) selectedColor else null,
                            enabled = enabled,
                            onClick = { showPicker = true }
                        )
                        // Keeps the last row's swatches in the same columns as the rows above.
                        else -> Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showPicker = false },
            onConfirm = {
                showPicker = false
                onSelect(it)
            }
        )
    }
}

/** A full hue sweep, so the custom swatch reads as "any colour" rather than one more preset. */
private val HUE_SWEEP = Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })

/**
 * Sits after the presets with a hue ring and palette icon, so it always reads as "pick your own".
 * Filled with [customColor] once one is chosen (and selected, like a preset), grey until then.
 */
@Composable
private fun CustomColorSwatch(
    customColor: Long?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val selected = customColor != null
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "customSwatchScale"
    )
    val fill = customColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .border(3.dp, HUE_SWEEP, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Palette,
            contentDescription = stringResource(R.string.dialog_custom_color),
            modifier = Modifier.size(20.dp),
            tint = when {
                customColor == null -> MaterialTheme.colorScheme.onSurfaceVariant
                fill.luminance() > 0.5f -> Color.Black
                else -> Color.White
            }
        )
    }
}

private fun toHex(color: Long): String = "%06X".format(color and 0xFFFFFFL)

/**
 * Keeps only hex digits from typed or pasted text, so "#ff8800", "FF8800" and an ARGB
 * "#FFFF8800" all end up as "FF8800".
 */
internal fun parseHexInput(raw: String): String {
    val digits = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
    return if (digits.length == 8) digits.drop(2) else digits.take(6)
}

@Composable
private fun ColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val controller = rememberColorPickerController()
    var picked by rememberSaveable { mutableLongStateOf(initialColor) }
    // What's in the hex field, which may be half-typed; [picked] only follows complete codes.
    var hexText by rememberSaveable { mutableStateOf(toHex(initialColor)) }
    // Read once so the wheel reopens where it was after a rotation or fold.
    val startColor = remember { Color(picked) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.dialog_custom_color)
    // A square wheel as wide as the dialog allows, but short enough for landscape and the cover screen.
    val wheelSize = (LocalConfiguration.current.screenHeightDp.dp * 0.45f).coerceAtMost(280.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_custom_color)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HsvColorPicker(
                    modifier = Modifier.size(wheelSize),
                    controller = controller,
                    initialColor = startColor,
                    onColorChanged = { envelope ->
                        // The wheel reports the initial colour too; only a touch changes the pick,
                        // so confirming untouched keeps a preset exactly.
                        if (envelope.fromUser) {
                            picked = (envelope.color.toArgb().toLong() and 0xFFFFFFL) or 0xFF000000L
                            hexText = toHex(picked)
                        }
                    }
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        hexText = parseHexInput(raw)
                        if (hexText.length == 6) {
                            picked = hexText.toLong(16) or 0xFF000000L
                            // Not from the user, so the wheel's echo doesn't overwrite the exact code.
                            controller.selectByColor(Color(picked), fromUser = false)
                        }
                    },
                    label = { Text(stringResource(R.string.dialog_custom_color_hex)) },
                    prefix = { Text("#") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(picked))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText(copyLabel, "#${toHex(picked)}"))
                                )
                            }
                        }) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.dialog_custom_color_copy)
                            )
                        }
                    },
                    isError = hexText.length != 6,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = HiLightTheme.DialogCardShape
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }, enabled = hexText.length == 6) {
                Text(stringResource(R.string.dialog_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}

@Preview(name = "Rule components", showBackground = true, widthDp = 390)
@Composable
private fun RuleComponentsPreview() {
    val renderer = PatternRenderer()
    HiLightPlusTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHero(title = "Call lights", statusText = "On · 3 rules", checked = true, onCheckedChange = {})
            SectionHero(title = "Notification lights", statusText = "Off", checked = false, onCheckedChange = {})
            SectionOffHint(stringResource(R.string.section_off_hint))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                RuleListItem(0, 3, "Sarah Connor", PatternMode.PULSE, 0xFFEA4335, FaceDownMode.INHERIT, renderer, true, {}, {}, {})
                RuleListItem(1, 3, "Mom", PatternMode.BREATHE, 0xFFFF007F, FaceDownMode.ONLY_FACE_DOWN, renderer, false, {}, {}, {})
                RuleListItem(
                    2, 3, "Unknown numbers", PatternMode.WAVE, 0xFFFBBC05, FaceDownMode.ALWAYS, renderer, true, {}, {},
                    dndMode = DndMode.SKIP,
                    quietHoursMode = QuietHoursMode.ALWAYS
                )
            }
            EmptyRuleHint("No custom caller rules.", Icons.Rounded.Lightbulb)
            AddRuleButton("Add contact", Icons.Rounded.Lightbulb, onClick = {})
        }
    }
}
