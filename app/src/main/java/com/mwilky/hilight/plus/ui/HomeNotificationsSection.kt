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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.SPLIT_READABLE_FLOOR
import com.mwilky.hilight.plus.SPLIT_SEPARATE_CEILING
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.MultiAlertMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.RuleSort
import com.mwilky.hilight.plus.SettingsSnapshot
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.SplitAnimation
import com.mwilky.hilight.plus.SplitLook
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.NotificationAccessCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.ConnectionStatusCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun HomeNotifsPage(
    connectionState: DaemonBridge.State,
    connectionMethod: DaemonBridge.Method,
    connectionError: String?,
    onPauseConnection: () -> Unit,
    onResumeConnection: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
    onSetUpConnection: () -> Unit,
    permissionState: PermissionState,
    onOpenNotifSettings: () -> Unit,
    state: SettingsSnapshot,
    onToggleNotifs: (Boolean) -> Unit,
    onToggleFavouriteNotif: (Boolean) -> Unit,
    onEditFavouriteNotif: () -> Unit,
    onToggleDefaultNotif: (Boolean) -> Unit,
    onEditDefaultNotif: () -> Unit,
    onToggleMessageRule: (MessageContactRule, Boolean) -> Unit,
    onEditMessageRule: (MessageContactRule) -> Unit,
    onDeleteMessageRule: (String) -> Unit,
    onChangeMessageRuleSort: (RuleSort) -> Unit,
    onAddMessageContact: () -> Unit,
    onToggleAppRule: (AppNotificationRule, Boolean) -> Unit,
    onEditAppRule: (AppNotificationRule) -> Unit,
    onDeleteAppRule: (String) -> Unit,
    onChangeAppRuleSort: (RuleSort) -> Unit,
    onAddApp: () -> Unit,
    onChangeDuration: (Int) -> Unit,
    onChangeMultiAlertMode: (MultiAlertMode) -> Unit,
    onChangeSplitAnimation: (SplitAnimation) -> Unit,
    onChangeSplitLook: (SplitLook) -> Unit,
    onTestSplit: (colors: LongArray, animation: SplitAnimation, look: SplitLook, durationMs: Long) -> Unit,
    onCancelLedTest: () -> Unit,
    renderer: PatternRenderer
) {
    val messageRules = remember(state.messageContactRules, state.messageRuleSort) {
        state.messageRuleSort.sorted(state.messageContactRules, MessageContactRule::name)
    }
    val appRules = remember(state.appRules, state.appRuleSort) {
        state.appRuleSort.sorted(state.appRules, AppNotificationRule::appName)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (connectionState != DaemonBridge.State.CONNECTED) {
            ConnectionStatusCard(
                connectionState = connectionState,
                connectionMethod = connectionMethod,
                connectionError = connectionError,
                onDisconnect = onPauseConnection,
                onConnect = onResumeConnection,
                onRequestPermission = onRequestShizukuPermission,
                onOpenShizukuApp = onOpenShizukuApp,
                onRestartApp = onRestartApp,
                onSetUp = onSetUpConnection
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
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    RuleListItem(
                        index = 0,
                        count = 2,
                        title = stringResource(R.string.favourite_contacts_title),
                        pattern = state.favouriteNotifPattern,
                        color = state.favouriteNotifColor,
                        faceDownMode = state.favouriteNotifFaceDownMode,
                        dndMode = state.favouriteNotifDndMode,
                        quietHoursMode = state.favouriteNotifQuietHoursMode,
                        renderer = renderer,
                        isEnabled = state.isFavouriteNotifEnabled,
                        onToggle = onToggleFavouriteNotif,
                        onEdit = onEditFavouriteNotif
                    )
                    RuleListItem(
                        index = 1,
                        count = 2,
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
                }

                RuleGroupHeader(
                    text = stringResource(R.string.notifs_contact_rules_header, messageRules.size),
                    action = if (messageRules.size > 1) {
                        { RuleSortButton(state.messageRuleSort, RuleSort.CONTACT_OPTIONS, forContacts = true, onSortChange = onChangeMessageRuleSort) }
                    } else null
                )
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

                RuleGroupHeader(
                    text = stringResource(R.string.notifs_app_rules_header, appRules.size),
                    action = if (appRules.size > 1) {
                        { RuleSortButton(state.appRuleSort, RuleSort.APP_OPTIONS, forContacts = false, onSortChange = onChangeAppRuleSort) }
                    } else null
                )
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
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(stringResource(R.string.settings_split_animation_title))
                                        ConnectedChoice(
                                            options = listOf(
                                                SplitAnimation.BREATHE to stringResource(R.string.settings_split_animation_breathe),
                                                SplitAnimation.SOLID to stringResource(R.string.settings_split_animation_solid),
                                                SplitAnimation.SPOTLIGHT to stringResource(R.string.settings_split_animation_spotlight),
                                                SplitAnimation.ROTATE to stringResource(R.string.settings_split_animation_rotate)
                                            ),
                                            selected = state.splitAnimation,
                                            onSelect = onChangeSplitAnimation
                                        )
                                        SplitBrightnessControls(
                                            animation = state.splitAnimation,
                                            look = state.splitLook,
                                            onLookChange = onChangeSplitLook
                                        )
                                        SplitRingExample(renderer, state.splitAnimation, state.splitLook)
                                        SplitTestButton(
                                            animation = state.splitAnimation,
                                            look = state.splitLook,
                                            enabled = connectionState == DaemonBridge.State.CONNECTED,
                                            onTest = onTestSplit,
                                            onCancel = onCancelLedTest
                                        )
                                    }
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

/** Three waiting notifications in the chosen animation, so it's visible before any arrive. */
@Composable
private fun SplitRingExample(renderer: PatternRenderer, animation: SplitAnimation, look: SplitLook) {
    var frame by remember { mutableStateOf(IntArray(8)) }
    // Restarts with each choice, as the ring does when its arcs change, so Spotlight starts on
    // the newest arc and Rotate with the newest at the top.
    LaunchedEffect(renderer, animation, look) {
        val startMs = System.currentTimeMillis()
        while (isActive) {
            frame = renderer.renderSplitFrame(
                colors = SPLIT_EXAMPLE_COLORS,
                look = look,
                elapsedTimeMs = System.currentTimeMillis() - startMs,
                animation = animation
            )
            delay(33)
        }
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
        AnimatedText(
            text = when (animation) {
                SplitAnimation.BREATHE -> stringResource(R.string.settings_split_example_breathe)
                SplitAnimation.SOLID -> stringResource(R.string.settings_split_example_solid)
                SplitAnimation.SPOTLIGHT -> stringResource(R.string.settings_split_example_spotlight)
                SplitAnimation.ROTATE -> stringResource(R.string.settings_split_example_rotate)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A dimmest-to-brightest range for the animations that move, one steady level for those that
 * don't, and a note once either leaves the levels that keep every arc readable.
 */
@Composable
private fun SplitBrightnessControls(
    animation: SplitAnimation,
    look: SplitLook,
    onLookChange: (SplitLook) -> Unit
) {
    val usesRange = SplitLook.usesRange(animation)
    if (usesRange) {
        BrightnessRangeSlider(
            dimmest = look.dimmest,
            brightest = look.brightest,
            onRangeChange = { dimmest, brightest -> onLookChange(look.copy(dimmest = dimmest, brightest = brightest)) },
            stepPercent = SPLIT_BRIGHTNESS_STEP_PERCENT
        )
    } else {
        BrightnessSlider(
            brightness = look.steady,
            onBrightnessChange = { onLookChange(look.copy(steady = it)) },
            stepPercent = SPLIT_BRIGHTNESS_STEP_PERCENT
        )
    }
    val warning = when {
        usesRange && look.dimmest < SPLIT_READABLE_FLOOR -> R.string.settings_split_dim_warning
        !usesRange && look.steady > SPLIT_SEPARATE_CEILING -> R.string.settings_split_steady_warning
        else -> null
    }
    // Keeps the last note on screen while it animates away.
    var shownWarning by remember { mutableStateOf(warning ?: R.string.settings_split_dim_warning) }
    if (warning != null) shownWarning = warning
    AnimatedVisibility(
        visible = warning != null,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
            AnimatedText(
                text = stringResource(shownWarning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Finer than the rules' 10%, so the default 45% dimmest level sits on a stop.
private const val SPLIT_BRIGHTNESS_STEP_PERCENT = 5

/**
 * Plays the example's three arcs on the physical LEDs. While it plays, a new animation or
 * brightness restarts it with that look, so it can be tuned by eye on the ring itself.
 */
@Composable
private fun SplitTestButton(
    animation: SplitAnimation,
    look: SplitLook,
    enabled: Boolean,
    onTest: (colors: LongArray, animation: SplitAnimation, look: SplitLook, durationMs: Long) -> Unit,
    onCancel: () -> Unit
) {
    var isTesting by remember { mutableStateOf(false) }
    LaunchedEffect(isTesting, animation, look) {
        if (!isTesting) return@LaunchedEffect
        onTest(SPLIT_EXAMPLE_COLORS, animation, look, SPLIT_TEST_DURATION_MS)
        delay(SPLIT_TEST_DURATION_MS)
        isTesting = false
    }
    // Leaving the page or switching mode stops a test still playing, as closing the rule editor does.
    val testing by rememberUpdatedState(isTesting)
    DisposableEffect(Unit) {
        onDispose { if (testing) onCancel() }
    }
    TestOnLedsButton(
        isTesting = isTesting,
        enabled = enabled,
        onClick = {
            if (isTesting) onCancel()
            isTesting = !isTesting
        }
    )
}

private val SPLIT_EXAMPLE_COLORS = longArrayOf(0xFF00E5FF, 0xFF34A853, 0xFFFF6D00)

// Long enough for Rotate, the slowest animation, to go all the way round once.
private const val SPLIT_TEST_DURATION_MS = 8000L

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
