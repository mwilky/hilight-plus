@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.BatteryFullTimeout
import com.mwilky.hilight.plus.BatteryPattern
import com.mwilky.hilight.plus.BatterySettings
import com.mwilky.hilight.plus.BatteryVisibility
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.ShizukuStatusCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Battery indicator page: a single global config (not a per-target rule), so it's edited
 * directly rather than through the rule editor dialog used by calls and notifications.
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
    onBatteryChange: (BatterySettings) -> Unit,
    renderer: PatternRenderer
) {
    val isEnabled = battery.visibility != BatteryVisibility.OFF

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

        SectionHero(
            title = stringResource(R.string.hero_battery_title),
            statusText = if (isEnabled) {
                stringResource(
                    R.string.hero_status_on,
                    stringResource(
                        if (battery.visibility == BatteryVisibility.FACE_DOWN_ONLY) {
                            R.string.battery_visibility_face_down
                        } else {
                            R.string.battery_visibility_always
                        }
                    )
                )
            } else {
                stringResource(R.string.hero_status_off)
            },
            checked = isEnabled,
            onCheckedChange = { on ->
                onBatteryChange(
                    battery.copy(
                        visibility = if (on) BatteryVisibility.ALWAYS else BatteryVisibility.OFF
                    )
                )
            }
        )

        SectionBody(enabled = isEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BatteryPreviewHeader(battery = battery, renderer = renderer)

                SettingCard {
                    ChoiceGroup(
                        title = stringResource(R.string.battery_section_show_title),
                        description = stringResource(R.string.battery_section_show_desc),
                        options = listOf(
                            BatteryVisibility.FACE_DOWN_ONLY to stringResource(R.string.battery_visibility_face_down),
                            BatteryVisibility.ALWAYS to stringResource(R.string.battery_visibility_always)
                        ),
                        selected = battery.visibility,
                        onSelect = { onBatteryChange(battery.copy(visibility = it)) }
                    )
                }

                EditorSection(title = stringResource(R.string.battery_section_pattern_title)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                EditorSection(title = stringResource(R.string.battery_section_color_title)) {
                    ToggleRow(
                        title = stringResource(R.string.battery_section_auto_color),
                        description = stringResource(R.string.battery_section_auto_color_desc),
                        checked = battery.autoColor,
                        onCheckedChange = { onBatteryChange(battery.copy(autoColor = it)) }
                    )
                    AnimatedVisibility(visible = !battery.autoColor) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            maxItemsInEachRow = 4,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PALETTE.forEach { c ->
                                ColorSwatch(
                                    color = c,
                                    selected = battery.color == c,
                                    enabled = true,
                                    onClick = { onBatteryChange(battery.copy(color = c)) }
                                )
                            }
                        }
                    }
                }

                SettingCard {
                    ToggleRow(
                        title = stringResource(R.string.battery_section_low_title),
                        description = stringResource(R.string.battery_section_low_desc),
                        checked = battery.lowWarningEnabled,
                        onCheckedChange = { onBatteryChange(battery.copy(lowWarningEnabled = it)) }
                    )
                    AnimatedVisibility(visible = battery.lowWarningEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.battery_low_threshold_format, battery.lowThresholdPercent),
                                style = MaterialTheme.typography.labelLarge
                            )
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
                }

                SettingCard {
                    ChoiceGroup(
                        title = stringResource(R.string.battery_section_full_timeout_title),
                        description = stringResource(R.string.battery_section_full_timeout_desc),
                        options = BatteryFullTimeout.entries.map { it to stringResource(it.titleRes) },
                        selected = battery.fullTimeout,
                        onSelect = { onBatteryChange(battery.copy(fullTimeout = it)) }
                    )
                }

                SettingCard {
                    ChoiceGroup(
                        title = stringResource(R.string.dialog_quiet_hours_title),
                        description = stringResource(
                            when (battery.quietHoursMode) {
                                QuietHoursMode.INHERIT -> R.string.battery_quiet_desc_default
                                QuietHoursMode.ALWAYS -> R.string.battery_quiet_desc_always
                                QuietHoursMode.SKIP -> R.string.battery_quiet_desc_skip
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
                        onSelect = { onBatteryChange(battery.copy(quietHoursMode = it)) }
                    )
                }

                SettingCard {
                    ToggleRow(
                        title = stringResource(R.string.battery_section_overrides_notifications),
                        description = stringResource(R.string.battery_section_overrides_notifications_desc),
                        checked = battery.overridesNotifications,
                        onCheckedChange = { onBatteryChange(battery.copy(overridesNotifications = it)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Same card look as [EditorSection] but without its own title, for a section whose single
 * [ChoiceGroup] or [ToggleRow] child already carries the only label it needs.
 */
@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/**
 * A titled row with a trailing switch, matching the spacing/typography [ChoiceGroup] uses
 * so toggle rows and choice rows sit consistently inside the same [EditorSection] or [SettingCard].
 */
@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Live ring preview driven by a level slider, so the current settings can be checked at any
 * battery level without waiting for the real battery to get there.
 */
@Composable
private fun BatteryPreviewHeader(battery: BatterySettings, renderer: PatternRenderer) {
    var previewLevel by remember { mutableFloatStateOf(65f) }
    var previewCharging by remember { mutableStateOf(true) }
    var frames by remember { mutableStateOf(IntArray(8)) }

    LaunchedEffect(battery, previewLevel, previewCharging) {
        val level = previewLevel.roundToInt()
        val startMs = System.currentTimeMillis()
        while (isActive) {
            frames = renderer.renderBatteryFrame(
                pattern = battery.chargingPattern,
                levelPercent = level,
                charging = previewCharging,
                full = previewCharging && level >= 100,
                low = !previewCharging && battery.lowWarningEnabled && level <= battery.lowThresholdPercent,
                autoColor = battery.autoColor,
                fixedColor = battery.color,
                brightness = 1.0f,
                elapsedTimeMs = System.currentTimeMillis() - startMs,
                ledCount = 8
            )
            delay(33)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiffusedRingPreview(
                frames = frames,
                modifier = Modifier.size(96.dp).clip(CircleShape),
                size = 96.dp
            )
            Slider(
                value = previewLevel,
                onValueChange = { previewLevel = it },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.battery_preview_level_format, previewLevel.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.battery_preview_charging),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(checked = previewCharging, onCheckedChange = { previewCharging = it })
                }
            }
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
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "batteryPatternContainer"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = Modifier.width(108.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DiffusedRingPreview(
                frames = frames,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                size = 48.dp
            )
            Text(
                text = stringResource(pattern.titleRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(pattern.subtitleRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
