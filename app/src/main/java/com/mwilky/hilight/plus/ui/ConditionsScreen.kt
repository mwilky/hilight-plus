@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mwilky.hilight.plus.R

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
        quietHoursEnabled = state.quietHoursEnabled,
        onToggleQuietHours = viewModel::setQuietHoursEnabled,
        quietHoursStartMinutes = state.quietHoursStartMinutes,
        quietHoursEndMinutes = state.quietHoursEndMinutes,
        onChangeQuietHoursWindow = viewModel::setQuietHoursWindow
    )
}

@Composable
fun ConditionsContent(
    isOnlyWhenFaceDown: Boolean,
    onToggleFaceDown: (Boolean) -> Unit,
    suppressDuringDnd: Boolean,
    onToggleDnd: (Boolean) -> Unit,
    quietHoursEnabled: Boolean,
    onToggleQuietHours: (Boolean) -> Unit,
    quietHoursStartMinutes: Int,
    quietHoursEndMinutes: Int,
    onChangeQuietHoursWindow: (Int, Int) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingStart = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, quietHoursStartMinutes)}")
                    }
                    OutlinedButton(
                        onClick = { editingEnd = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, quietHoursEndMinutes)}")
                    }
                }
            }
        }
    }

    if (editingStart) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_start),
            initialMinutes = quietHoursStartMinutes,
            onDismiss = { editingStart = false },
            onConfirm = { minutes ->
                onChangeQuietHoursWindow(minutes, quietHoursEndMinutes)
                editingStart = false
            }
        )
    }
    if (editingEnd) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_end),
            initialMinutes = quietHoursEndMinutes,
            onDismiss = { editingEnd = false },
            onConfirm = { minutes ->
                onChangeQuietHoursWindow(quietHoursStartMinutes, minutes)
                editingEnd = false
            }
        )
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
            quietHoursEnabled = true,
            onToggleQuietHours = {},
            quietHoursStartMinutes = 22 * 60,
            quietHoursEndMinutes = 7 * 60,
            onChangeQuietHoursWindow = { _, _ -> }
        )
    }
}
