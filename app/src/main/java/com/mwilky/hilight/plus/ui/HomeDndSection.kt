package com.mwilky.hilight.plus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.DndIndicatorDuration
import com.mwilky.hilight.plus.DndIndicatorSettings
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.ConnectionStatusCard

/**
 * Lights the ring to show Do Not Disturb is on: a look while it's on, edited in the usual rule
 * editor, and a flash as it turns on and off. A single global config, so like the Gemini page it
 * has one rule row, with the rest in segmented option rows as on the Battery page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeDndPage(
    connectionState: DaemonBridge.State,
    connectionMethod: DaemonBridge.Method,
    connectionError: String?,
    onPauseConnection: () -> Unit,
    onResumeConnection: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
    onSetUpConnection: () -> Unit,
    dnd: DndIndicatorSettings,
    onDndChange: (DndIndicatorSettings) -> Unit,
    onEditIndicator: () -> Unit,
    renderer: PatternRenderer
) {
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

        SectionHero(
            title = stringResource(R.string.hero_dnd_title),
            statusText = if (dnd.enabled) {
                stringResource(
                    R.string.hero_status_on,
                    when {
                        dnd.indicatorEnabled -> stringResource(dnd.pattern.titleRes)
                        dnd.flashOnChange -> stringResource(R.string.hero_status_dnd_flash_only)
                        else -> stringResource(R.string.hero_status_dnd_nothing)
                    }
                )
            } else {
                stringResource(R.string.hero_status_off)
            },
            checked = dnd.enabled,
            onCheckedChange = { onDndChange(dnd.copy(enabled = it)) }
        )

        SectionBody(enabled = dnd.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RuleGroupHeader(stringResource(R.string.dnd_group_indicator))
                RuleListItem(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.dnd_indicator_title),
                    pattern = dnd.pattern,
                    speed = dnd.speed,
                    color = dnd.color,
                    faceDownMode = dnd.faceDownMode,
                    renderer = renderer,
                    isEnabled = dnd.indicatorEnabled,
                    onToggle = { onDndChange(dnd.copy(indicatorEnabled = it)) },
                    onEdit = onEditIndicator,
                    quietHoursMode = dnd.quietHoursMode
                )

                RuleGroupHeader(stringResource(R.string.dnd_group_options))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    // Only the look is timed, so this has nothing to do with it off: disabled
                    // rather than hidden, so the group keeps its shape, as on the Battery page.
                    OptionsRow(
                        index = 0,
                        count = 3,
                        title = stringResource(R.string.dnd_show_for_title),
                        description = stringResource(R.string.dnd_show_for_desc),
                        options = DndIndicatorDuration.entries.map { it to stringResource(it.titleRes) },
                        selected = dnd.showFor,
                        onSelect = { onDndChange(dnd.copy(showFor = it)) },
                        enabled = dnd.indicatorEnabled
                    )
                    SwitchRow(
                        index = 1,
                        count = 3,
                        title = stringResource(R.string.dnd_flash_title),
                        description = stringResource(R.string.dnd_flash_desc),
                        checked = dnd.flashOnChange,
                        onCheckedChange = { onDndChange(dnd.copy(flashOnChange = it)) }
                    )
                    SwitchRow(
                        index = 2,
                        count = 3,
                        title = stringResource(R.string.dnd_overrides_battery_title),
                        description = stringResource(R.string.dnd_overrides_battery_desc),
                        checked = dnd.overridesBattery,
                        onCheckedChange = { onDndChange(dnd.copy(overridesBattery = it)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
