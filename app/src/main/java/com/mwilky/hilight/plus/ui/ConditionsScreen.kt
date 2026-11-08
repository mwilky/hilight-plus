@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mwilky.hilight.plus.QuietHoursRepeat
import com.mwilky.hilight.plus.QuietHoursSchedule
import com.mwilky.hilight.plus.QuietWindow
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.localeWeekendDays
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Conditions Screen:
 * Smart suppression and trigger rules for hardware lights.
 */
@Composable
fun ConditionsScreen(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ConditionsContent(
        isOnlyWhenFaceDown = state.isOnlyWhenFaceDown,
        onToggleFaceDown = viewModel::setOnlyWhenFaceDown,
        suppressDuringDnd = state.suppressDuringDnd,
        onToggleDnd = viewModel::setSuppressDuringDnd,
        pocketMode = state.pocketMode,
        onTogglePocketMode = viewModel::setPocketMode,
        quietHoursEnabled = state.quietHoursEnabled,
        onToggleQuietHours = viewModel::setQuietHoursEnabled,
        quietHoursStartMinutes = state.quietHoursStartMinutes,
        quietHoursEndMinutes = state.quietHoursEndMinutes,
        onChangeQuietHoursWindow = viewModel::setQuietHoursWindow,
        quietHoursSchedule = state.quietHoursSchedule,
        weekendDays = remember { localeWeekendDays() },
        onChangeQuietHoursRepeat = viewModel::setQuietHoursRepeat,
        onChangeQuietHoursWeekend = viewModel::setQuietHoursWeekend,
        onChangeQuietHoursDay = viewModel::setQuietHoursDay
    )
}

/** Which quiet window a time picker is changing. */
private sealed interface QuietTarget {
    /** Every day's, or the weekdays'. */
    data object Main : QuietTarget
    data object Weekend : QuietTarget
    data class Day(val day: DayOfWeek) : QuietTarget
}

/**
 * A time picker open on [target]'s start or [end]. A day's row asks for both in turn, holding the
 * chosen start in [pendingStart] until the end is picked.
 */
private data class QuietTimeEdit(val target: QuietTarget, val end: Boolean, val pendingStart: Int? = null)

@Composable
fun ConditionsContent(
    isOnlyWhenFaceDown: Boolean,
    onToggleFaceDown: (Boolean) -> Unit,
    suppressDuringDnd: Boolean,
    onToggleDnd: (Boolean) -> Unit,
    pocketMode: Boolean,
    onTogglePocketMode: (Boolean) -> Unit,
    quietHoursEnabled: Boolean,
    onToggleQuietHours: (Boolean) -> Unit,
    quietHoursStartMinutes: Int,
    quietHoursEndMinutes: Int,
    onChangeQuietHoursWindow: (Int, Int) -> Unit,
    quietHoursSchedule: QuietHoursSchedule,
    weekendDays: Set<DayOfWeek>,
    onChangeQuietHoursRepeat: (QuietHoursRepeat) -> Unit,
    onChangeQuietHoursWeekend: (Int, Int) -> Unit,
    onChangeQuietHoursDay: (DayOfWeek, QuietWindow) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editing by remember { mutableStateOf<QuietTimeEdit?>(null) }
    val mainWindow = QuietWindow(quietHoursStartMinutes, quietHoursEndMinutes)
    val weekendWindow = quietHoursSchedule.weekend ?: mainWindow
    val locale = Locale.getDefault()
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    fun windowOf(target: QuietTarget) = when (target) {
        QuietTarget.Main -> mainWindow
        QuietTarget.Weekend -> weekendWindow
        is QuietTarget.Day -> quietHoursSchedule.windowFor(target.day, mainWindow, weekendDays)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conditions_title)) },
                scrollBehavior = scrollBehavior
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
            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
            ) {
                Text(
                    text = stringResource(R.string.conditions_defaults_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // One item on the page, so the gap above the quiet hours controls collapses with them
            // rather than jumping shut after they have gone.
            Column {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    ConditionRow(
                        index = 0,
                        count = 3,
                        title = stringResource(R.string.conditions_face_down_title),
                        subtitle = stringResource(R.string.conditions_face_down_desc),
                        icon = Icons.Rounded.ScreenRotation,
                        checked = isOnlyWhenFaceDown,
                        onCheckedChange = onToggleFaceDown
                    )
                    ConditionRow(
                        index = 1,
                        count = 3,
                        title = stringResource(R.string.conditions_dnd_title),
                        subtitle = stringResource(R.string.conditions_dnd_desc),
                        icon = Icons.Rounded.DoNotDisturbOn,
                        checked = suppressDuringDnd,
                        onCheckedChange = onToggleDnd
                    )
                    ConditionRow(
                        index = 2,
                        count = 3,
                        title = stringResource(R.string.conditions_quiet_hours_title),
                        subtitle = stringResource(R.string.conditions_quiet_hours_desc),
                        icon = Icons.Rounded.Bedtime,
                        checked = quietHoursEnabled,
                        onCheckedChange = onToggleQuietHours
                    )
                }

                AnimatedVisibility(
                    visible = quietHoursEnabled,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ConnectedChoice(
                            options = listOf(
                                QuietHoursRepeat.EVERY_DAY to stringResource(R.string.conditions_quiet_hours_every_day),
                                QuietHoursRepeat.WEEKDAYS_WEEKENDS to stringResource(R.string.conditions_quiet_hours_weekends_differ),
                                QuietHoursRepeat.EACH_DAY to stringResource(R.string.conditions_quiet_hours_custom)
                            ),
                            selected = quietHoursSchedule.repeat,
                            onSelect = onChangeQuietHoursRepeat
                        )
                        // The same fade and resize the Home sections use when their rules come and go.
                        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
                        AnimatedContent(
                            targetState = quietHoursSchedule.repeat,
                            transitionSpec = {
                                (fadeIn(effects) + expandVertically(spatial))
                                    .togetherWith(fadeOut(effects) + shrinkVertically(spatial))
                                    .using(SizeTransform(clip = false) { _, _ -> spatial })
                            },
                            label = "quietHoursRepeat"
                        ) { repeatMode ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (repeatMode) {
                                    QuietHoursRepeat.EVERY_DAY -> QuietWindowButtons(
                                        window = mainWindow,
                                        onEditStart = { editing = QuietTimeEdit(QuietTarget.Main, end = false) },
                                        onEditEnd = { editing = QuietTimeEdit(QuietTarget.Main, end = true) }
                                    )
                                    QuietHoursRepeat.WEEKDAYS_WEEKENDS -> {
                                        QuietWindowGroup(stringResource(R.string.conditions_quiet_hours_weekdays)) {
                                            QuietWindowButtons(
                                                window = mainWindow,
                                                onEditStart = { editing = QuietTimeEdit(QuietTarget.Main, end = false) },
                                                onEditEnd = { editing = QuietTimeEdit(QuietTarget.Main, end = true) }
                                            )
                                        }
                                        QuietWindowGroup(stringResource(R.string.conditions_quiet_hours_weekends)) {
                                            QuietWindowButtons(
                                                window = weekendWindow,
                                                onEditStart = { editing = QuietTimeEdit(QuietTarget.Weekend, end = false) },
                                                onEditEnd = { editing = QuietTimeEdit(QuietTarget.Weekend, end = true) }
                                            )
                                        }
                                    }
                                    QuietHoursRepeat.EACH_DAY -> Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                        repeat(7) { index ->
                                            val day = firstDay.plus(index.toLong())
                                            val window = windowOf(QuietTarget.Day(day))
                                            QuietDayRow(
                                                index = index,
                                                count = 7,
                                                day = day.getDisplayName(TextStyle.FULL, locale),
                                                window = window,
                                                onClick = { editing = QuietTimeEdit(QuietTarget.Day(day), end = false) },
                                                onEnabledChange = { onChangeQuietHoursDay(day, window.copy(enabled = it)) }
                                            )
                                        }
                                    }
                                }
                                if (repeatMode != QuietHoursRepeat.EVERY_DAY) {
                                    Text(
                                        text = stringResource(R.string.conditions_quiet_hours_overnight),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Not a rule default like the ones above, so it sits apart from them.
            ConditionRow(
                index = 0,
                count = 1,
                title = stringResource(R.string.conditions_pocket_title),
                subtitle = stringResource(R.string.conditions_pocket_desc),
                icon = Icons.Rounded.Sensors,
                checked = pocketMode,
                onCheckedChange = onTogglePocketMode
            )
        }
    }

    editing?.let { edit ->
        val window = windowOf(edit.target)
        val field = stringResource(if (edit.end) R.string.conditions_quiet_hours_end else R.string.conditions_quiet_hours_start)
        val targetName = when (val target = edit.target) {
            QuietTarget.Main -> if (quietHoursSchedule.repeat == QuietHoursRepeat.EVERY_DAY) null
                else stringResource(R.string.conditions_quiet_hours_weekdays)
            QuietTarget.Weekend -> stringResource(R.string.conditions_quiet_hours_weekends)
            is QuietTarget.Day -> target.day.getDisplayName(TextStyle.FULL, locale)
        }
        // A day's start and end pickers open one after the other, each fresh.
        key(edit) {
            QuietHoursTimePickerDialog(
                title = targetName?.let { stringResource(R.string.conditions_quiet_hours_dialog_title, it, field) } ?: field,
                initialMinutes = if (edit.end) window.endMinutes else window.startMinutes,
                onDismiss = { editing = null },
                onConfirm = { minutes ->
                    editing = null
                    when (val target = edit.target) {
                        QuietTarget.Main -> if (edit.end) onChangeQuietHoursWindow(window.startMinutes, minutes)
                            else onChangeQuietHoursWindow(minutes, window.endMinutes)
                        QuietTarget.Weekend -> if (edit.end) onChangeQuietHoursWeekend(window.startMinutes, minutes)
                            else onChangeQuietHoursWeekend(minutes, window.endMinutes)
                        // Picking a day's times switches it on.
                        is QuietTarget.Day -> if (edit.end) {
                            onChangeQuietHoursDay(target.day, QuietWindow(edit.pendingStart ?: window.startMinutes, minutes))
                        } else {
                            editing = edit.copy(end = true, pendingStart = minutes)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun QuietWindowGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun QuietWindowButtons(window: QuietWindow, onEditStart: () -> Unit, onEditEnd: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onEditStart,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, window.startMinutes)}")
        }
        OutlinedButton(
            onClick = onEditEnd,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, window.endMinutes)}")
        }
    }
}

/** A day in the "Each day" list: tapping it picks the start, then the end. */
@Composable
private fun QuietDayRow(
    index: Int,
    count: Int,
    day: String,
    window: QuietWindow,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
        supportingContent = {
            Text(
                if (window.enabled) {
                    stringResource(
                        R.string.conditions_quiet_hours_range,
                        formatClockMinutes(context, window.startMinutes),
                        formatClockMinutes(context, window.endMinutes)
                    )
                } else {
                    stringResource(R.string.conditions_quiet_hours_day_off)
                }
            )
        },
        trailingContent = { Switch(checked = window.enabled, onCheckedChange = onEnabledChange) }
    ) {
        Text(day)
    }
}

@Composable
private fun ConditionRow(
    index: Int,
    count: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            val container by animateColorAsState(
                targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "conditionIconContainer"
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    ) {
        Text(title)
    }
}

@Preview(name = "Conditions Screen Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ConditionsScreenPreview() {
    HiLightPlusTheme {
        ConditionsContent(
            isOnlyWhenFaceDown = false,
            onToggleFaceDown = {},
            suppressDuringDnd = false,
            onToggleDnd = {},
            pocketMode = true,
            onTogglePocketMode = {},
            quietHoursEnabled = true,
            onToggleQuietHours = {},
            quietHoursStartMinutes = 22 * 60,
            quietHoursEndMinutes = 7 * 60,
            onChangeQuietHoursWindow = { _, _ -> },
            quietHoursSchedule = QuietHoursSchedule(
                repeat = QuietHoursRepeat.WEEKDAYS_WEEKENDS,
                weekend = QuietWindow(23 * 60, 9 * 60)
            ),
            weekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            onChangeQuietHoursRepeat = {},
            onChangeQuietHoursWeekend = { _, _ -> },
            onChangeQuietHoursDay = { _, _ -> }
        )
    }
}
