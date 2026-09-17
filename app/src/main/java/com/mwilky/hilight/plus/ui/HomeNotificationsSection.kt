@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.MultiAlertMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.SettingsSnapshot
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.NotificationAccessCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.ShizukuStatusCard
import kotlin.math.roundToInt

@Composable
fun HomeNotifsPage(
    shizukuState: ShizukuBridge.State,
    shizukuError: String?,
    onDisconnectShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    permissionState: PermissionState,
    onOpenNotifSettings: () -> Unit,
    state: SettingsSnapshot,
    onToggleNotifs: (Boolean) -> Unit,
    onToggleDefaultNotif: (Boolean) -> Unit,
    onEditDefaultNotif: () -> Unit,
    onToggleMessageRule: (MessageContactRule, Boolean) -> Unit,
    onEditMessageRule: (MessageContactRule) -> Unit,
    onDeleteMessageRule: (String) -> Unit,
    onAddMessageContact: () -> Unit,
    onToggleAppRule: (AppNotificationRule, Boolean) -> Unit,
    onEditAppRule: (AppNotificationRule) -> Unit,
    onDeleteAppRule: (String) -> Unit,
    onAddApp: () -> Unit,
    onChangeDuration: (Int) -> Unit,
    onChangeMultiAlertMode: (MultiAlertMode) -> Unit,
    renderer: PatternRenderer
) {
    val messageRules = state.messageContactRules
    val appRules = state.appRules
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
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
        if (!permissionState.hasNotifAccess) {
            NotificationAccessCard(state = permissionState, onOpenNotifSettings = onOpenNotifSettings)
        }

        SectionHero(
            title = stringResource(R.string.hero_notifs_title),
            statusText = heroStatusText(
                enabled = state.isNotificationsEnabled,
                ruleCount = messageRules.size + appRules.size
            ),
            checked = state.isNotificationsEnabled,
            onCheckedChange = onToggleNotifs
        )

        SectionBody(enabled = state.isNotificationsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RuleListItem(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.notifs_default_title),
                    pattern = state.defaultNotifPattern,
                    color = state.defaultNotifColor,
                    faceDownMode = state.defaultNotifFaceDownMode,
                    dndMode = state.defaultNotifDndMode,
                    quietHoursMode = state.defaultNotifQuietHoursMode,
                    renderer = renderer,
                    isEnabled = state.isDefaultNotifEnabled,
                    onToggle = onToggleDefaultNotif,
                    onEdit = onEditDefaultNotif
                )

                RuleGroupHeader(stringResource(R.string.notifs_contact_rules_header, messageRules.size))
                if (messageRules.isEmpty()) {
                    EmptyRuleHint(stringResource(R.string.notifs_no_contact_rules), Icons.AutoMirrored.Rounded.Message)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        messageRules.forEachIndexed { index, rule ->
                            RuleListItem(
                                index = index,
                                count = messageRules.size,
                                title = rule.name,
                                pattern = rule.pattern,
                                color = rule.color,
                                faceDownMode = rule.faceDownMode,
                                dndMode = rule.dndMode,
                                quietHoursMode = rule.quietHoursMode,
                                renderer = renderer,
                                isEnabled = rule.isEnabled,
                                onToggle = { isEnabled -> onToggleMessageRule(rule, isEnabled) },
                                onEdit = { onEditMessageRule(rule) },
                                onDelete = { onDeleteMessageRule(rule.id) }
                            )
                        }
                    }
                }
                AddRuleButton(stringResource(R.string.calls_add_contact_btn), Icons.Rounded.PersonAdd, onAddMessageContact)

                RuleGroupHeader(stringResource(R.string.notifs_app_rules_header, appRules.size))
                if (appRules.isEmpty()) {
                    EmptyRuleHint(stringResource(R.string.notifs_no_app_rules), Icons.Rounded.Apps)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        appRules.forEachIndexed { index, rule ->
                            RuleListItem(
                                index = index,
                                count = appRules.size,
                                title = rule.appName,
                                pattern = rule.pattern,
                                color = rule.color,
                                faceDownMode = rule.faceDownMode,
                                dndMode = rule.dndMode,
                                quietHoursMode = rule.quietHoursMode,
                                renderer = renderer,
                                isEnabled = rule.isEnabled,
                                onToggle = { isEnabled -> onToggleAppRule(rule, isEnabled) },
                                onEdit = { onEditAppRule(rule) },
                                onDelete = { onDeleteAppRule(rule.packageName) }
                            )
                        }
                    }
                }
                AddRuleButton(stringResource(R.string.notifs_add_app_btn), Icons.Rounded.Add, onAddApp)

                RuleGroupHeader(stringResource(R.string.settings_additional_header))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    val mode = state.multiAlertMode
                    val isCycle = mode.keepsQueue
                    // Duration only applies to Newest only; in the queue modes lights last until dismissal,
                    // so the whole row fades rather than just the slider's thumb.
                    val durationAlpha by animateFloatAsState(
                        targetValue = if (isCycle) DISABLED_ALPHA else 1f,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "durationAlpha"
                    )
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        trailingContent = {
                            Text(
                                text = formatDurationLabel(state.notificationDurationSeconds),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.alpha(durationAlpha)
                            )
                        },
                        supportingContent = {
                            Column(modifier = Modifier.alpha(durationAlpha)) {
                                AnimatedText(
                                    if (isCycle) stringResource(R.string.settings_duration_cycling_desc)
                                    else stringResource(R.string.settings_duration_desc)
                                )
                                Slider(
                                    value = state.notificationDurationSeconds.toFloat(),
                                    enabled = !isCycle,
                                    onValueChange = { value ->
                                        val rounded = (value / 30f).roundToInt() * 30
                                        onChangeDuration(rounded.coerceIn(30, 300))
                                    },
                                    valueRange = 30f..300f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_duration_title), modifier = Modifier.alpha(durationAlpha))
                    }
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ConnectedChoice(
                                    options = listOf(
                                        MultiAlertMode.LATEST to stringResource(R.string.settings_multi_latest),
                                        MultiAlertMode.CYCLE to stringResource(R.string.settings_multi_cycle),
                                        MultiAlertMode.SPLIT to stringResource(R.string.settings_multi_split)
                                    ),
                                    selected = mode,
                                    onSelect = onChangeMultiAlertMode
                                )
                                AnimatedText(
                                    when (mode) {
                                        MultiAlertMode.LATEST -> stringResource(R.string.settings_multi_latest_desc)
                                        MultiAlertMode.CYCLE -> stringResource(R.string.settings_multi_cycle_desc)
                                        MultiAlertMode.SPLIT -> stringResource(R.string.settings_multi_split_desc)
                                    }
                                )
                                AnimatedVisibility(
                                    visible = mode == MultiAlertMode.SPLIT,
                                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
                                ) {
                                    SplitRingExample(renderer)
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_multi_title))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** A still of three waiting notifications, so the split layout is visible before any arrive. */
@Composable
private fun SplitRingExample(renderer: PatternRenderer) {
    val frame = remember(renderer) {
        renderer.renderSplitFrame(
            colors = longArrayOf(0xFF00E5FF, 0xFF34A853, 0xFFFF6D00),
            brightness = 1f,
            elapsedTimeMs = 1200L
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiffusedRingPreview(
            frames = frame,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            size = 56.dp
        )
        Text(
            text = stringResource(R.string.settings_multi_split_example),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

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
