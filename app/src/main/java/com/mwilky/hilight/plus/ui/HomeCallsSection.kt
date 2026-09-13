package com.mwilky.hilight.plus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.core.PatternRenderer

@Composable
fun HomeCallsMasterCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
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
                    text = stringResource(R.string.calls_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.calls_section_subtitle),
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
fun HomeCallsSettingsCard(
    enabled: Boolean,
    alpha: Float,
    scale: Float,
    isOtherContactsEnabled: Boolean,
    otherContactsColor: Long,
    otherContactsPattern: PatternMode,
    otherContactsFaceDownMode: FaceDownMode,
    onToggleOtherContacts: (Boolean) -> Unit,
    onEditOtherContacts: () -> Unit,
    isUnknownNumbersEnabled: Boolean,
    unknownNumbersColor: Long,
    unknownNumbersPattern: PatternMode,
    unknownNumbersFaceDownMode: FaceDownMode,
    onToggleUnknownNumbers: (Boolean) -> Unit,
    onEditUnknownNumbers: () -> Unit,
    callContactRules: List<ContactRule>,
    onToggleCallContactRule: (ContactRule, Boolean) -> Unit,
    onEditCallContactRule: (ContactRule) -> Unit,
    onDeleteCallContactRule: (String) -> Unit,
    onAddCallContact: () -> Unit,
    renderer: PatternRenderer
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TonalRuleCard(
                title = stringResource(R.string.calls_other_contacts_title),
                pattern = otherContactsPattern,
                color = otherContactsColor,
                renderer = renderer,
                isEnabled = isOtherContactsEnabled && enabled,
                faceDownMode = otherContactsFaceDownMode,
                onToggle = { if (enabled) onToggleOtherContacts(it) },
                onEdit = { if (enabled) onEditOtherContacts() }
            )

            TonalRuleCard(
                title = stringResource(R.string.calls_unknown_numbers_title),
                pattern = unknownNumbersPattern,
                color = unknownNumbersColor,
                renderer = renderer,
                isEnabled = isUnknownNumbersEnabled && enabled,
                faceDownMode = unknownNumbersFaceDownMode,
                onToggle = { if (enabled) onToggleUnknownNumbers(it) },
                onEdit = { if (enabled) onEditUnknownNumbers() }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.calls_custom_rules_header, callContactRules.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (callContactRules.isEmpty()) {
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
                            Icons.Rounded.PhoneCallback,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.calls_no_rules),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                callContactRules.forEach { rule ->
                    TonalRuleCard(
                        title = rule.name,
                        pattern = rule.pattern,
                        color = rule.color,
                        renderer = renderer,
                        isEnabled = rule.isEnabled && enabled,
                        faceDownMode = rule.faceDownMode,
                        onToggle = { isEnabled -> if (enabled) onToggleCallContactRule(rule, isEnabled) },
                        onEdit = { if (enabled) onEditCallContactRule(rule) },
                        onDelete = { if (enabled) onDeleteCallContactRule(rule.id) }
                    )
                }
            }

            OutlinedButton(
                onClick = { if (enabled) onAddCallContact() },
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
        }
    }
}
