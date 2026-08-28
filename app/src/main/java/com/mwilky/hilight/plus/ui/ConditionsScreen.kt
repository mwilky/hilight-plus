package com.mwilky.hilight.plus.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwilky.hilight.plus.LightController
import kotlinx.coroutines.launch

/**
 * Conditions Screen:
 * Smart suppression and trigger rules for hardware lights.
 */
@Composable
fun ConditionsScreen(controller: LightController) {
    val scope = rememberCoroutineScope()
    val isOnlyWhenFaceDown by controller.store.isOnlyWhenFaceDown.collectAsStateWithLifecycle(initialValue = false)

    ConditionsContent(
        isOnlyWhenFaceDown = isOnlyWhenFaceDown,
        onToggleFaceDown = { enabled ->
            scope.launch { controller.store.setOnlyWhenFaceDown(enabled) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionsContent(
    isOnlyWhenFaceDown: Boolean,
    onToggleFaceDown: (Boolean) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Conditions") },
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
            // 1. Face-Down Master Condition (Active)
            ConditionCard(
                title = "Face-Down Only",
                subtitle = "Only illuminate rear lights when your Pixel is placed face down on a flat surface. Can be overridden per individual rule.",
                icon = Icons.Rounded.ScreenRotation,
                checked = isOnlyWhenFaceDown,
                onCheckedChange = onToggleFaceDown
            )
        }
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
            isOnlyWhenFaceDown = false,
            onToggleFaceDown = {}
        )
    }
}
