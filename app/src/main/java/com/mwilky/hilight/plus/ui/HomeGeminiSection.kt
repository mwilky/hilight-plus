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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.GeminiSettings
import com.mwilky.hilight.plus.GeminiState
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.ConnectionStatusCard
import com.mwilky.hilight.plus.ui.diagnostics.GeminiStockCard

/**
 * Takes over the ring from Pixel's own Gemini feedback: a look for each of Gemini's listening,
 * thinking and replying states, and the stock setting to turn off so the two don't clash.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeGeminiPage(
    connectionState: DaemonBridge.State,
    connectionMethod: DaemonBridge.Method,
    connectionError: String?,
    onPauseConnection: () -> Unit,
    onResumeConnection: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
    onSetUpConnection: () -> Unit,
    gemini: GeminiSettings,
    stockState: StockHiLightState,
    onOpenStockSettings: () -> Unit,
    onGeminiChange: (GeminiSettings) -> Unit,
    onEditState: (GeminiState) -> Unit,
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
            title = stringResource(R.string.hero_gemini_title),
            statusText = if (gemini.enabled) {
                val lit = GeminiState.entries.count { gemini.look(it).isEnabled }
                stringResource(R.string.hero_status_on, pluralStringResource(R.plurals.hero_gemini_state_count, lit, lit))
            } else {
                stringResource(R.string.hero_status_off)
            },
            checked = gemini.enabled,
            onCheckedChange = { onGeminiChange(gemini.copy(enabled = it)) }
        )

        SectionBody(enabled = gemini.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (stockState.geminiKnown && stockState.geminiFeedbackActive) {
                    GeminiStockCard(stockState = stockState, takeoverEnabled = true, onOpenSettings = onOpenStockSettings)
                }

                RuleGroupHeader(stringResource(R.string.gemini_states_header))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    GeminiState.entries.forEachIndexed { index, state ->
                        val look = gemini.look(state)
                        RuleListItem(
                            index = index,
                            count = GeminiState.entries.size,
                            title = stringResource(state.titleRes),
                            pattern = look.pattern,
                            color = look.color,
                            faceDownMode = look.faceDownMode,
                            renderer = renderer,
                            isEnabled = look.isEnabled,
                            onToggle = { onGeminiChange(gemini.withLook(state, look.copy(isEnabled = it))) },
                            onEdit = { onEditState(state) },
                            dndMode = look.dndMode,
                            quietHoursMode = look.quietHoursMode
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
