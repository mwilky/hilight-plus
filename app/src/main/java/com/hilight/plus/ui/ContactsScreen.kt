package com.hilight.plus.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hilight.plus.ContactRule
import com.hilight.plus.LightController
import com.hilight.plus.PatternMode
import com.hilight.plus.R
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(controller: LightController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isCallLightsEnabled by controller.store.isCallLightsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val defaultCallColor by controller.store.defaultCallColor.collectAsStateWithLifecycle(initialValue = 0xFF4285F4)
    val defaultCallPattern by controller.store.defaultCallPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)
    val contactRules by controller.store.contactRules.collectAsStateWithLifecycle(initialValue = emptyList())

    var ruleBeingEdited by remember { mutableStateOf<ContactRule?>(null) }
    var isConfiguringDefault by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val contactInfo = resolveContactDetails(context, contactUri)
            if (contactInfo != null) {
                ruleBeingEdited = ContactRule(
                    id = UUID.randomUUID().toString(),
                    name = contactInfo.first,
                    phoneNumber = contactInfo.second,
                    color = 0xFFEA4335, // Google Red as starter
                    pattern = PatternMode.PULSE
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Calls") }
            )
        },
        floatingActionButton = {
            if (isCallLightsEnabled) {
                ExtendedFloatingActionButton(
                    onClick = { contactPickerLauncher.launch(null) },
                    icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    text = { Text("Add Contact") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp, top = 10.dp)
        ) {
            // Master Switch for Incoming Call Lights
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Incoming Call Illumination",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Light the rear array when calls are ringing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isCallLightsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { controller.store.setCallLightsEnabled(enabled) }
                            }
                        )
                    }
                }
            }

            if (isCallLightsEnabled) {
                // Default / Fallback Call Illumination Rule Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "All Other / Unknown Callers",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Pattern: ${defaultCallPattern.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(defaultCallColor))
                                    )

                                    IconButton(onClick = { isConfiguringDefault = true }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit default")
                                    }
                                }
                            }
                        }
                    }
                }

                // Header for Custom Rules
                item {
                    Text(
                        text = "Custom Contact Rules (${contactRules.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (contactRules.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No custom contact rules yet.\nTap 'Add Contact' to pick any contact from your address book.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(contactRules, key = { it.id }) { rule ->
                        ContactRuleItem(
                            rule = rule,
                            onToggle = { isEnabled ->
                                scope.launch {
                                    controller.store.saveContactRule(rule.copy(isEnabled = isEnabled))
                                }
                            },
                            onEdit = { ruleBeingEdited = rule },
                            onDelete = {
                                scope.launch {
                                    controller.store.deleteContactRule(rule.id)
                                }
                            },
                            onPreview = {
                                controller.previewEffect(
                                    pattern = rule.pattern,
                                    color = rule.color,
                                    durationMs = 3000L
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Contact Rule Customizer Dialog
    if (ruleBeingEdited != null) {
        val rule = ruleBeingEdited!!
        ContactRuleDialog(
            initialRule = rule,
            onDismiss = { ruleBeingEdited = null },
            onPreview = { pattern, color ->
                controller.previewEffect(pattern = pattern, color = color, durationMs = 2500L)
            },
            onSave = { updatedRule ->
                scope.launch {
                    controller.store.saveContactRule(updatedRule)
                    ruleBeingEdited = null
                }
            }
        )
    }

    // Default Call Style Customizer Dialog
    if (isConfiguringDefault) {
        DefaultCallRuleDialog(
            initialPattern = defaultCallPattern,
            initialColor = defaultCallColor,
            onDismiss = { isConfiguringDefault = false },
            onPreview = { pattern, color ->
                controller.previewEffect(pattern = pattern, color = color, durationMs = 2500L)
            },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.setDefaultCallPattern(pattern)
                    controller.store.setDefaultCallColor(color)
                    isConfiguringDefault = false
                }
            }
        )
    }
}

@Composable
private fun ContactRuleItem(
    rule: ContactRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(rule.color))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )

                Column {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${rule.phoneNumber} · ${rule.pattern.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreview) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview alert")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit rule")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error)
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@Composable
private fun ContactRuleDialog(
    initialRule: ContactRule,
    onDismiss: () -> Unit,
    onPreview: (PatternMode, Long) -> Unit,
    onSave: (ContactRule) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(initialRule.color) }
    var selectedPattern by remember { mutableStateOf(initialRule.pattern) }

    val palette = listOf(
        0xFF4285F4, // Google Blue
        0xFFEA4335, // Google Red
        0xFFFBBC05, // Google Yellow
        0xFF34A853, // Google Green
        0xFFFF007F, // Neon Pink
        0xFF8A2BE2, // Purple
        0xFF00E5FF, // Cyan
        0xFFFFFFFF  // Pure White
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${initialRule.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Phone: ${initialRule.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Select Alert Color",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    palette.forEach { c ->
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Text(
                    text = "Select Ring Animation",
                    style = MaterialTheme.typography.labelLarge
                )

                val patterns = listOf(PatternMode.PULSE, PatternMode.BREATHE, PatternMode.WAVE, PatternMode.COMET, PatternMode.SOLID)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    patterns.forEach { p ->
                        FilterChip(
                            selected = selectedPattern == p,
                            onClick = { selectedPattern = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initialRule.copy(color = selectedColor, pattern = selectedPattern))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPreview(selectedPattern, selectedColor) }) {
                    Text("Preview")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun DefaultCallRuleDialog(
    initialPattern: PatternMode,
    initialColor: Long,
    onDismiss: () -> Unit,
    onPreview: (PatternMode, Long) -> Unit,
    onSave: (PatternMode, Long) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(initialColor) }
    var selectedPattern by remember { mutableStateOf(initialPattern) }

    val palette = listOf(
        0xFF4285F4, 0xFFEA4335, 0xFFFBBC05, 0xFF34A853,
        0xFFFF007F, 0xFF8A2BE2, 0xFF00E5FF, 0xFFFFFFFF
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Caller Illumination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Applied to incoming calls from non-customized numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    palette.forEach { c ->
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Text(
                    text = "Select Pattern",
                    style = MaterialTheme.typography.labelLarge
                )

                val patterns = listOf(PatternMode.PULSE, PatternMode.BREATHE, PatternMode.WAVE, PatternMode.COMET, PatternMode.SOLID)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    patterns.forEach { p ->
                        FilterChip(
                            selected = selectedPattern == p,
                            onClick = { selectedPattern = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedPattern, selectedColor) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPreview(selectedPattern, selectedColor) }) {
                    Text("Preview")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun resolveContactDetails(context: Context, contactUri: Uri): Pair<String, String>? {
    var name = "Unknown Contact"
    var phoneNumber = ""

    val contentResolver = context.contentResolver
    contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

            if (nameIndex != -1) {
                name = cursor.getString(nameIndex) ?: name
            }

            if (idIndex != -1) {
                val contactId = cursor.getString(idIndex)
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numberIndex != -1) {
                            phoneNumber = phoneCursor.getString(numberIndex) ?: ""
                        }
                    }
                }
            }
        }
    }

    val normalized = phoneNumber.replace(Regex("[^0-9+]"), "")
    return if (name.isNotBlank()) Pair(name, normalized) else null
}
