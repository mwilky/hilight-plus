package com.hilight.plus.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hilight.plus.ContactRule
import com.hilight.plus.LightController
import com.hilight.plus.PatternMode
import com.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(controller: LightController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { PatternRenderer() }

    val isCallLightsEnabled by controller.store.isCallLightsEnabled.collectAsStateWithLifecycle(initialValue = true)

    // Other Contacts Settings
    val isOtherContactsEnabled by controller.store.isOtherContactsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val otherContactsColor by controller.store.otherContactsColor.collectAsStateWithLifecycle(initialValue = 0xFF4285F4)
    val otherContactsPattern by controller.store.otherContactsPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)

    // Unknown Numbers Settings
    val isUnknownNumbersEnabled by controller.store.isUnknownNumbersEnabled.collectAsStateWithLifecycle(initialValue = true)
    val unknownNumbersColor by controller.store.unknownNumbersColor.collectAsStateWithLifecycle(initialValue = 0xFFFBBC05)
    val unknownNumbersPattern by controller.store.unknownNumbersPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)

    val contactRules by controller.store.contactRules.collectAsStateWithLifecycle(initialValue = emptyList())

    var ruleBeingEdited by remember { mutableStateOf<ContactRule?>(null) }
    var isConfiguringOtherContacts by remember { mutableStateOf(false) }
    var isConfiguringUnknownNumbers by remember { mutableStateOf(false) }

    // Telephony & Contact Specific Permission Checks
    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }

    fun checkMissingPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (!hasPhonePermission()) list.add(Manifest.permission.READ_PHONE_STATE)
        if (!hasContactsPermission()) list.add(Manifest.permission.READ_CONTACTS)
        return list.toTypedArray()
    }

    fun isPermanentlyDenied(): Boolean {
        val activity = context as? Activity ?: return false
        val missing = checkMissingPermissions()
        if (missing.isEmpty()) return false
        return missing.any { perm -> !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) }
    }

    var permanentlyDenied by remember { mutableStateOf(isPermanentlyDenied()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isContactsGranted = hasContactsPermission()
            permanentlyDenied = isPermanentlyDenied()
        }
    }

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
                    color = 0xFFEA4335,
                    pattern = PatternMode.PULSE
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isPhoneGranted = hasPhonePermission()
        isContactsGranted = hasContactsPermission()
        permanentlyDenied = isPermanentlyDenied()
        if (isPhoneGranted && isContactsGranted) {
            contactPickerLauncher.launch(null)
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun onAddContactClicked() {
        val missing = checkMissingPermissions()
        isPhoneGranted = hasPhonePermission()
        isContactsGranted = hasContactsPermission()
        permanentlyDenied = isPermanentlyDenied()

        if (missing.isEmpty()) {
            contactPickerLauncher.launch(null)
        } else if (permanentlyDenied) {
            openAppSettings()
        } else {
            permissionLauncher.launch(missing)
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
                    onClick = { onAddContactClicked() },
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
            // Permission Warning Card tailored to exact missing permissions
            val hasAllPermissions = isPhoneGranted && isContactsGranted
            if (!hasAllPermissions) {
                val missingName = when {
                    !isPhoneGranted && !isContactsGranted -> "Phone & Contacts permissions"
                    !isPhoneGranted -> "Phone State permission"
                    else -> "Contacts permission"
                }

                val descText = when {
                    !isPhoneGranted && !isContactsGranted ->
                        "HiLight Plus needs Phone State to detect incoming calls and Contacts to match your custom caller rules."
                    !isPhoneGranted ->
                        "HiLight Plus needs Phone State permission to detect when an incoming call is ringing and trigger the rear LEDs."
                    else ->
                        "HiLight Plus needs Contacts permission to look up and match your custom contact rules."
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (permanentlyDenied) "$missingName Blocked" else "$missingName Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = if (permanentlyDenied) {
                                    "$descText This is currently blocked in system settings. Tap 'Open Settings' -> 'Permissions' to allow it."
                                } else {
                                    descText
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (permanentlyDenied) {
                                    Button(
                                        onClick = { openAppSettings() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Open Settings")
                                    }
                                } else {
                                    Button(
                                        onClick = { permissionLauncher.launch(checkMissingPermissions()) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Grant Permission")
                                    }
                                    TextButton(onClick = { openAppSettings() }) {
                                        Text("Settings", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                // Default Setting: All Other Contacts Card
                item {
                    CallerCategoryCard(
                        title = "All Other Contacts",
                        subtitle = "Saved contacts with no custom rule",
                        pattern = otherContactsPattern,
                        color = otherContactsColor,
                        renderer = renderer,
                        isEnabled = isOtherContactsEnabled,
                        onToggle = { enabled ->
                            scope.launch { controller.store.setOtherContactsEnabled(enabled) }
                        },
                        onEdit = { isConfiguringOtherContacts = true }
                    )
                }

                // Default Setting: Unknown / Private Numbers Card
                item {
                    CallerCategoryCard(
                        title = "Unknown & Private Numbers",
                        subtitle = "Unsaved or hidden caller numbers",
                        pattern = unknownNumbersPattern,
                        color = unknownNumbersColor,
                        renderer = renderer,
                        isEnabled = isUnknownNumbersEnabled,
                        onToggle = { enabled ->
                            scope.launch { controller.store.setUnknownNumbersEnabled(enabled) }
                        },
                        onEdit = { isConfiguringUnknownNumbers = true }
                    )
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
                            renderer = renderer,
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
            renderer = renderer,
            onDismiss = { ruleBeingEdited = null },
            onSave = { updatedRule ->
                scope.launch {
                    controller.store.saveContactRule(updatedRule)
                    ruleBeingEdited = null
                }
            }
        )
    }

    // All Other Contacts Dialog
    if (isConfiguringOtherContacts) {
        PatternColorConfigDialog(
            title = "All Other Contacts",
            description = "Applied to incoming calls from saved contacts without a specific custom rule.",
            initialPattern = otherContactsPattern,
            initialColor = otherContactsColor,
            renderer = renderer,
            onDismiss = { isConfiguringOtherContacts = false },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.setOtherContactsPattern(pattern)
                    controller.store.setOtherContactsColor(color)
                    isConfiguringOtherContacts = false
                }
            }
        )
    }

    // Unknown Numbers Dialog
    if (isConfiguringUnknownNumbers) {
        PatternColorConfigDialog(
            title = "Unknown & Private Numbers",
            description = "Applied to incoming calls from unsaved or hidden caller numbers.",
            initialPattern = unknownNumbersPattern,
            initialColor = unknownNumbersColor,
            renderer = renderer,
            onDismiss = { isConfiguringUnknownNumbers = false },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.setUnknownNumbersPattern(pattern)
                    controller.store.setUnknownNumbersColor(color)
                    isConfiguringUnknownNumbers = false
                }
            }
        )
    }
}

@Composable
private fun CallerCategoryCard(
    title: String,
    subtitle: String,
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
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
                MiniRuleAnimationIcon(pattern = pattern, color = color, renderer = renderer, size = 36.dp)

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pattern:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pattern.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit rule")
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@Composable
private fun ContactRuleItem(
    rule: ContactRule,
    renderer: PatternRenderer,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                MiniRuleAnimationIcon(pattern = rule.pattern, color = rule.color, renderer = renderer, size = 36.dp)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = rule.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pattern:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rule.pattern.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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

/**
 * Animated mini icon showing the dynamic pattern animation directly in the contact card row.
 */
@Composable
private fun MiniRuleAnimationIcon(
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    size: androidx.compose.ui.unit.Dp
) {
    var miniFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }

    LaunchedEffect(pattern, color) {
        val startMs = System.currentTimeMillis()
        val speed = when (pattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 1000L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> 1000L
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            miniFrames = renderer.renderFrame(
                pattern = pattern.id,
                colorLong = color,
                brightness = 1.0f,
                speedMs = speed,
                elapsedTimeMs = elapsed,
                ledCount = 8
            )
            delay(33)
        }
    }

    DiffusedRingPreview(
        frames = miniFrames,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        size = size
    )
}

@Composable
private fun ContactRuleDialog(
    initialRule: ContactRule,
    renderer: PatternRenderer,
    onDismiss: () -> Unit,
    onSave: (ContactRule) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(initialRule.color) }
    var selectedPattern by remember { mutableStateOf(initialRule.pattern) }
    var dialogPreviewFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }

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

    LaunchedEffect(selectedPattern, selectedColor) {
        val startMs = System.currentTimeMillis()
        val speed = when (selectedPattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 1000L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> 1000L
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            dialogPreviewFrames = renderer.renderFrame(
                pattern = selectedPattern.id,
                colorLong = selectedColor,
                brightness = 1.0f,
                speedMs = speed,
                elapsedTimeMs = elapsed,
                ledCount = 8
            )
            delay(16)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${initialRule.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Phone: ${initialRule.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Ring inside Dialog
                DiffusedRingPreview(
                    frames = dialogPreviewFrames,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    size = 75.dp
                )

                Text(
                    text = "Select Ring Animation",
                    style = MaterialTheme.typography.labelLarge
                )

                val patterns = listOf(
                    PatternMode.PULSE,
                    PatternMode.BREATHE,
                    PatternMode.WAVE,
                    PatternMode.COMET,
                    PatternMode.RAINBOW,
                    PatternMode.SOLID
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patterns.forEach { p ->
                        FilterChip(
                            selected = selectedPattern == p,
                            onClick = { selectedPattern = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                // Crisp, snappy expansion/collapse animation for color palette
                AnimatedVisibility(
                    visible = selectedPattern != PatternMode.RAINBOW,
                    enter = expandVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(animationSpec = tween(120, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PatternColorConfigDialog(
    title: String,
    description: String,
    initialPattern: PatternMode,
    initialColor: Long,
    renderer: PatternRenderer,
    onDismiss: () -> Unit,
    onSave: (PatternMode, Long) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(initialColor) }
    var selectedPattern by remember { mutableStateOf(initialPattern) }
    var dialogPreviewFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }

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

    LaunchedEffect(selectedPattern, selectedColor) {
        val startMs = System.currentTimeMillis()
        val speed = when (selectedPattern) {
            PatternMode.BREATHE -> 2000L
            PatternMode.WAVE -> 1200L
            PatternMode.COMET -> 1000L
            PatternMode.RAINBOW -> 1200L
            PatternMode.PULSE -> 850L
            else -> 1000L
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            dialogPreviewFrames = renderer.renderFrame(
                pattern = selectedPattern.id,
                colorLong = selectedColor,
                brightness = 1.0f,
                speedMs = speed,
                elapsedTimeMs = elapsed,
                ledCount = 8
            )
            delay(16)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Ring inside Dialog
                DiffusedRingPreview(
                    frames = dialogPreviewFrames,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    size = 75.dp
                )

                Text(
                    text = "Select Pattern",
                    style = MaterialTheme.typography.labelLarge
                )

                val patterns = listOf(
                    PatternMode.PULSE,
                    PatternMode.BREATHE,
                    PatternMode.WAVE,
                    PatternMode.COMET,
                    PatternMode.RAINBOW,
                    PatternMode.SOLID
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patterns.forEach { p ->
                        FilterChip(
                            selected = selectedPattern == p,
                            onClick = { selectedPattern = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                // Crisp, snappy expansion/collapse animation for color palette
                AnimatedVisibility(
                    visible = selectedPattern != PatternMode.RAINBOW,
                    enter = expandVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(animationSpec = tween(120, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
