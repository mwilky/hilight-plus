package com.hilight.plus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hilight.plus.LightController

/**
 * Conditions Screen:
 * Smart suppression rules for hardware lights (Do Not Disturb, Low Battery, Flip to Shhh, Pocket mode).
 */
@Composable
fun ConditionsScreen(controller: LightController) {
    var dndEnabled by remember { mutableStateOf(true) }
    var lowBatteryEnabled by remember { mutableStateOf(true) }
    var flipToShhhEnabled by remember { mutableStateOf(false) }
    var pocketModeEnabled by remember { mutableStateOf(true) }

    ConditionsContent(
        dndEnabled = dndEnabled,
        onToggleDnd = { dndEnabled = it },
        lowBatteryEnabled = lowBatteryEnabled,
        onToggleLowBattery = { lowBatteryEnabled = it },
        flipToShhhEnabled = flipToShhhEnabled,
        onToggleFlipToShhh = { flipToShhhEnabled = it },
        pocketModeEnabled = pocketModeEnabled,
        onTogglePocketMode = { pocketModeEnabled = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionsContent(
    dndEnabled: Boolean,
    onToggleDnd: (Boolean) -> Unit,
    lowBatteryEnabled: Boolean,
    onToggleLowBattery: (Boolean) -> Unit,
    flipToShhhEnabled: Boolean,
    onToggleFlipToShhh: (Boolean) -> Unit,
    pocketModeEnabled: Boolean,
    onTogglePocketMode: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Conditions") }
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
                text = "Trigger Conditions & Behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // DND Rule
            ConditionCard(
                title = "Do Not Disturb (DND) Sync",
                subtitle = "Automatically suppress rear LED illumination when Do Not Disturb or Priority Only is active.",
                icon = Icons.Rounded.DoNotDisturbOn,
                checked = dndEnabled,
                onCheckedChange = onToggleDnd
            )

            // Low Battery Rule
            ConditionCard(
                title = "Low Battery Saver",
                subtitle = "Disable lighting effects when phone battery drops below 15% to conserve power.",
                icon = Icons.Rounded.BatterySaver,
                checked = lowBatteryEnabled,
                onCheckedChange = onToggleLowBattery
            )

            // Flip to Shhh / Face Down Mode
            ConditionCard(
                title = "Flip to Shhh Mode",
                subtitle = "Silence rear lights when the phone is face down on a flat surface.",
                icon = Icons.Rounded.ScreenRotation,
                checked = flipToShhhEnabled,
                onCheckedChange = onToggleFlipToShhh
            )

            // Pocket Detection
            ConditionCard(
                title = "Pocket & Bag Protection",
                subtitle = "Prevent LED illumination when proximity sensor detects the device is inside a pocket or bag.",
                icon = Icons.Rounded.Security,
                checked = pocketModeEnabled,
                onCheckedChange = onTogglePocketMode
            )
        }
    }
}

@Composable
private fun ConditionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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

@Preview(name = "Conditions Screen Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ConditionsScreenPreview() {
    HiLightPlusTheme {
        ConditionsContent(
            dndEnabled = true,
            onToggleDnd = {},
            lowBatteryEnabled = true,
            onToggleLowBattery = {},
            flipToShhhEnabled = false,
            onToggleFlipToShhh = {},
            pocketModeEnabled = true,
            onTogglePocketMode = {}
        )
    }
}
