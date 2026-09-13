package com.mwilky.hilight.plus.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.R
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Conditions Screen:
 * Smart suppression and trigger rules for hardware lights.
 */
@Composable
fun ConditionsScreen(controller: LightController) {
    val scope = rememberCoroutineScope()
    val isOnlyWhenFaceDown by controller.store.isOnlyWhenFaceDown.collectAsStateWithLifecycle(initialValue = false)
    val suppressDuringDnd by controller.store.suppressDuringDnd.collectAsStateWithLifecycle(initialValue = false)
    val quietHoursEnabled by controller.store.quietHoursEnabled.collectAsStateWithLifecycle(initialValue = false)
    val quietHoursStart by controller.store.quietHoursStartMinutes.collectAsStateWithLifecycle(initialValue = 22 * 60)
    val quietHoursEnd by controller.store.quietHoursEndMinutes.collectAsStateWithLifecycle(initialValue = 7 * 60)

    ConditionsContent(
        isOnlyWhenFaceDown = isOnlyWhenFaceDown,
        onToggleFaceDown = { enabled ->
            scope.launch { controller.store.setOnlyWhenFaceDown(enabled) }
        },
        suppressDuringDnd = suppressDuringDnd,
        onToggleDnd = { enabled ->
            scope.launch { controller.store.setSuppressDuringDnd(enabled) }
        },
        quietHoursEnabled = quietHoursEnabled,
        onToggleQuietHours = { enabled ->
            scope.launch { controller.store.setQuietHoursEnabled(enabled) }
        },
        quietHoursStartMinutes = quietHoursStart,
        quietHoursEndMinutes = quietHoursEnd,
        onChangeQuietHoursWindow = { start, end ->
            scope.launch { controller.store.setQuietHoursWindow(start, end) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
            Text(
                text = stringResource(R.string.conditions_defaults_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ConditionCard(
                title = stringResource(R.string.conditions_face_down_title),
                subtitle = stringResource(R.string.conditions_face_down_desc),
                icon = Icons.Rounded.ScreenRotation,
                checked = isOnlyWhenFaceDown,
                onCheckedChange = onToggleFaceDown
            )

            ConditionCard(
                title = stringResource(R.string.conditions_dnd_title),
                subtitle = stringResource(R.string.conditions_dnd_desc),
                icon = Icons.Rounded.DoNotDisturbOn,
                checked = suppressDuringDnd,
                onCheckedChange = onToggleDnd
            )

            QuietHoursCard(
                enabled = quietHoursEnabled,
                onToggle = onToggleQuietHours,
                startMinutes = quietHoursStartMinutes,
                endMinutes = quietHoursEndMinutes,
                onEditStart = { editingStart = true },
                onEditEnd = { editingEnd = true }
            )
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
private fun ConditionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HiLightTheme.CardShape,
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
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun QuietHoursCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    startMinutes: Int,
    endMinutes: Int,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HiLightTheme.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.conditions_quiet_hours_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.conditions_quiet_hours_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditStart,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, startMinutes)}")
                }
                OutlinedButton(
                    onClick = onEditEnd,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, endMinutes)}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).mod(24),
        initialMinute = initialMinutes.mod(60),
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
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

private fun formatClockMinutes(context: android.content.Context, minutes: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, (minutes / 60).mod(24))
        set(Calendar.MINUTE, minutes.mod(60))
        set(Calendar.SECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
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
