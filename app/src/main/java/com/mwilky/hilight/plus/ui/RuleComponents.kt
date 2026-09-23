@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

@Composable
fun RuleGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
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
        val startMs = System.currentTimeMillis()
        while (isActive) {
            miniFrames = frame(System.currentTimeMillis() - startMs)
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
