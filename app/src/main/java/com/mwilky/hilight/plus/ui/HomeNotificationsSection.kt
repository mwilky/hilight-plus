package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.UnlockBehavior
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlin.math.roundToInt

@Composable
fun HomeNotifsMasterCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HiLightTheme.PillShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notifs_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.notifs_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun HomeNotifsSettingsCard(
    enabled: Boolean,
    alpha: Float,
    scale: Float,
    isDefaultNotifEnabled: Boolean,
    defaultNotifColor: Long,
    defaultNotifPattern: PatternMode,
    defaultNotifFaceDownMode: FaceDownMode,
    onToggleDefaultNotif: (Boolean) -> Unit,
    onEditDefaultNotif: () -> Unit,
    messageContactRules: List<MessageContactRule>,
    onToggleMessageRule: (MessageContactRule, Boolean) -> Unit,
    onEditMessageRule: (MessageContactRule) -> Unit,
    onDeleteMessageRule: (String) -> Unit,
    onAddMessageContact: () -> Unit,
    appRules: List<AppNotificationRule>,
    onToggleAppRule: (AppNotificationRule, Boolean) -> Unit,
    onEditAppRule: (AppNotificationRule) -> Unit,
    onDeleteAppRule: (String) -> Unit,
    onAddApp: () -> Unit,
    notifDurationSec: Int,
    onChangeDuration: (Int) -> Unit,
    unlockBehavior: UnlockBehavior,
    onChangeUnlockBehavior: (UnlockBehavior) -> Unit,
    isCycleNotifications: Boolean,
    onToggleCycleNotifications: (Boolean) -> Unit,
    renderer: PatternRenderer
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha),
        shape = HiLightTheme.SectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TonalRuleCard(
                title = stringResource(R.string.notifs_default_title),
                pattern = defaultNotifPattern,
                color = defaultNotifColor,
                renderer = renderer,
                isEnabled = isDefaultNotifEnabled,
                controlsEnabled = enabled,
                faceDownMode = defaultNotifFaceDownMode,
                onToggle = onToggleDefaultNotif,
                onEdit = onEditDefaultNotif
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.notifs_contact_rules_header, messageContactRules.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (messageContactRules.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Message,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.notifs_no_contact_rules),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                messageContactRules.forEach { rule ->
                    TonalRuleCard(
                        title = rule.name,
                        pattern = rule.pattern,
                        color = rule.color,
                        renderer = renderer,
                        isEnabled = rule.isEnabled,
                        controlsEnabled = enabled,
                        faceDownMode = rule.faceDownMode,
                        onToggle = { isEnabled -> onToggleMessageRule(rule, isEnabled) },
                        onEdit = { onEditMessageRule(rule) },
                        onDelete = { onDeleteMessageRule(rule.id) }
                    )
                }
            }

            OutlinedButton(
                onClick = { if (enabled) onAddMessageContact() },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calls_add_contact_btn))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.notifs_app_rules_header, appRules.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (appRules.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.notifs_no_app_rules),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                appRules.forEach { rule ->
                    TonalRuleCard(
                        title = rule.appName,
                        pattern = rule.pattern,
                        color = rule.color,
                        renderer = renderer,
                        isEnabled = rule.isEnabled,
                        controlsEnabled = enabled,
                        faceDownMode = rule.faceDownMode,
                        onToggle = { isEnabled -> onToggleAppRule(rule, isEnabled) },
                        onEdit = { onEditAppRule(rule) },
                        onDelete = { onDeleteAppRule(rule.packageName) }
                    )
                }
            }

            OutlinedButton(
                onClick = { if (enabled) onAddApp() },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notifs_add_app_btn))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_additional_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = HiLightTheme.CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_duration_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isCycleNotifications) {
                                    stringResource(R.string.settings_duration_cycling_desc)
                                } else {
                                    stringResource(R.string.settings_duration_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SuggestionChip(
                            onClick = {},
                            enabled = enabled && !isCycleNotifications,
                            label = {
                                Text(
                                    text = formatDurationLabel(notifDurationSec),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            border = null
                        )
                    }

                    Slider(
                        value = notifDurationSec.toFloat(),
                        enabled = enabled && !isCycleNotifications,
                        onValueChange = { value ->
                            val rounded = (value / 30f).roundToInt() * 30
                            onChangeDuration(rounded.coerceIn(30, 300))
                        },
                        valueRange = 30f..300f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = HiLightTheme.CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_unlock_behavior_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedContent(
                            targetState = unlockBehavior,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "UnlockBehaviorSubtitle"
                        ) { behavior ->
                            Text(
                                text = when (behavior) {
                                    UnlockBehavior.NONE -> stringResource(R.string.settings_unlock_none_desc)
                                    UnlockBehavior.PAUSE -> stringResource(R.string.settings_unlock_pause_desc)
                                    UnlockBehavior.CLEAR -> stringResource(R.string.settings_unlock_clear_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val behaviors = UnlockBehavior.entries
                        behaviors.forEachIndexed { index, b ->
                            val isSelected = unlockBehavior == b
                            SegmentedButton(
                                selected = isSelected,
                                onClick = { if (enabled) onChangeUnlockBehavior(b) },
                                enabled = enabled,
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = behaviors.size),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                label = {
                                    Text(
                                        text = when (b) {
                                            UnlockBehavior.NONE -> stringResource(R.string.settings_unlock_none)
                                            UnlockBehavior.PAUSE -> stringResource(R.string.settings_unlock_pause)
                                            UnlockBehavior.CLEAR -> stringResource(R.string.settings_unlock_clear)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = HiLightTheme.CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_cycle_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_cycle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCycleNotifications,
                        onCheckedChange = onToggleCycleNotifications,
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun formatDurationLabel(seconds: Int): String {
    return if (seconds < 60) {
        stringResource(R.string.duration_seconds, seconds)
    } else {
        val minutes = seconds / 60
        val remain = seconds % 60
        if (remain == 0) {
            stringResource(R.string.duration_minutes, minutes)
        } else {
            stringResource(R.string.duration_minutes_seconds, minutes, remain)
        }
    }
}
