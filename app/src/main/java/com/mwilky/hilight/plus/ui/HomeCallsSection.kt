@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.automirrored.rounded.PhoneCallback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.SettingsSnapshot
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.CallPermissionsCard
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.ShizukuStatusCard
import com.mwilky.hilight.plus.ui.diagnostics.StockConflictCard

@Composable
fun HomeCallsPage(
    shizukuState: ShizukuBridge.State,
    shizukuError: String?,
    onDisconnectShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRestartApp: () -> Unit,
    stockState: StockHiLightState,
    onOpenStockSettings: () -> Unit,
    permissionState: PermissionState,
    onRequestPhonePerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    state: SettingsSnapshot,
    onToggleCallLights: (Boolean) -> Unit,
    onToggleFavouriteCalls: (Boolean) -> Unit,
    onEditFavouriteCalls: () -> Unit,
    onToggleOtherContacts: (Boolean) -> Unit,
    onEditOtherContacts: () -> Unit,
    onToggleUnknownNumbers: (Boolean) -> Unit,
    onEditUnknownNumbers: () -> Unit,
    onToggleMissedCalls: (Boolean) -> Unit,
    onEditMissedCalls: () -> Unit,
    onToggleCallContactRule: (ContactRule, Boolean) -> Unit,
    onEditCallContactRule: (ContactRule) -> Unit,
    onDeleteCallContactRule: (String) -> Unit,
    onAddCallContact: () -> Unit,
    renderer: PatternRenderer
) {
    val rules = state.contactRules
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
                onOpenShizukuApp = onOpenShizukuApp,
                onRestartApp = onRestartApp
            )
        }
        if (stockState.known && stockState.favoriteCallsActive) {
            StockConflictCard(stockState = stockState, onOpenSettings = onOpenStockSettings)
        }
        if (!permissionState.hasContactsPermission) {
            CallPermissionsCard(
                state = permissionState,
                onRequestPermissions = onRequestPhonePerms,
                onOpenAppSettings = onOpenAppSettings
            )
        }

        SectionHero(
            title = stringResource(R.string.hero_calls_title),
            statusText = heroStatusText(enabled = state.isCallLightsEnabled, ruleCount = rules.size),
            checked = state.isCallLightsEnabled,
            onCheckedChange = onToggleCallLights
        )

        SectionBody(enabled = state.isCallLightsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    RuleListItem(
                        index = 0,
                        count = 4,
                        title = stringResource(R.string.favourite_contacts_title),
                        pattern = state.favouriteCallsPattern,
                        color = state.favouriteCallsColor,
                        faceDownMode = state.favouriteCallsFaceDownMode,
                        dndMode = state.favouriteCallsDndMode,
                        quietHoursMode = state.favouriteCallsQuietHoursMode,
                        renderer = renderer,
                        isEnabled = state.isFavouriteCallsEnabled,
                        onToggle = onToggleFavouriteCalls,
                        onEdit = onEditFavouriteCalls
                    )
                    RuleListItem(
                        index = 1,
                        count = 4,
                        title = stringResource(R.string.calls_other_contacts_title),
                        pattern = state.otherContactsPattern,
                        color = state.otherContactsColor,
                        faceDownMode = state.otherContactsFaceDownMode,
                        dndMode = state.otherContactsDndMode,
                        quietHoursMode = state.otherContactsQuietHoursMode,
                        renderer = renderer,
                        isEnabled = state.isOtherContactsEnabled,
                        onToggle = onToggleOtherContacts,
                        onEdit = onEditOtherContacts
                    )
                    RuleListItem(
                        index = 2,
                        count = 4,
                        title = stringResource(R.string.calls_unknown_numbers_title),
                        pattern = state.unknownNumbersPattern,
                        color = state.unknownNumbersColor,
                        faceDownMode = state.unknownNumbersFaceDownMode,
                        dndMode = state.unknownNumbersDndMode,
                        quietHoursMode = state.unknownNumbersQuietHoursMode,
                        renderer = renderer,
                        isEnabled = state.isUnknownNumbersEnabled,
                        onToggle = onToggleUnknownNumbers,
                        onEdit = onEditUnknownNumbers
                    )
                    RuleListItem(
                        index = 3,
                        count = 4,
                        title = stringResource(R.string.calls_missed_calls_title),
                        pattern = state.missedCallsPattern,
                        color = state.missedCallsColor,
                        faceDownMode = state.missedCallsFaceDownMode,
                        dndMode = state.missedCallsDndMode,
                        quietHoursMode = state.missedCallsQuietHoursMode,
                        renderer = renderer,
                        isEnabled = state.isMissedCallsEnabled,
                        onToggle = onToggleMissedCalls,
                        onEdit = onEditMissedCalls
                    )
                }

                RuleGroupHeader(stringResource(R.string.calls_custom_rules_header, rules.size))
                if (rules.isEmpty()) {
                    EmptyRuleHint(stringResource(R.string.calls_no_rules), Icons.AutoMirrored.Rounded.PhoneCallback)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        rules.forEachIndexed { index, rule ->
                            RuleListItem(
                                index = index,
                                count = rules.size,
                                title = rule.name,
                                pattern = rule.pattern,
                                color = rule.color,
                                faceDownMode = rule.faceDownMode,
                                dndMode = rule.dndMode,
                                quietHoursMode = rule.quietHoursMode,
                                renderer = renderer,
                                isEnabled = rule.isEnabled,
                                onToggle = { isEnabled -> onToggleCallContactRule(rule, isEnabled) },
                                onEdit = { onEditCallContactRule(rule) },
                                onDelete = { onDeleteCallContactRule(rule.id) }
                            )
                        }
                    }
                }
                AddRuleButton(stringResource(R.string.calls_add_contact_btn), Icons.Rounded.PersonAdd, onAddCallContact)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun heroStatusText(enabled: Boolean, ruleCount: Int): String {
    return if (enabled) {
        stringResource(R.string.hero_status_on, pluralStringResource(R.plurals.hero_rule_count, ruleCount, ruleCount))
    } else {
        stringResource(R.string.hero_status_off)
    }
}

/**
 * Collapses a section's rules into a short "turned off" hint when the feature is disabled.
 */
@Composable
internal fun SectionBody(enabled: Boolean, content: @Composable () -> Unit) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedContent(
        targetState = enabled,
        transitionSpec = {
            (fadeIn(effects) + expandVertically(spatial))
                .togetherWith(fadeOut(effects) + shrinkVertically(spatial))
                .using(SizeTransform(clip = false) { _, _ -> spatial })
        },
        label = "sectionBody"
    ) { on ->
        if (on) content() else SectionOffHint(stringResource(R.string.section_off_hint))
    }
}
