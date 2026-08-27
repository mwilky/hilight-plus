package com.mwilky.hilight.plus.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Modern Unified Home Screen:
 * - Persistent Shizuku Privileged Control status card.
 * - Dynamic warning cards (ONLY shown when permissions are missing or stock conflicts occur).
 * - Calls & Notifications sections with nested Material 3 elevation hierarchy.
 * - Master toggles outside containers with smooth alpha dimming & disabled interaction when switched off.
 * - Custom low-profile compact slider with smaller thumb.
 * - Android 11+ launcher intent query app picker with app icons.
 */
@Composable
fun HomeScreen(controller: LightController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { PatternRenderer() }

    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

    // Calls state
    val isCallLightsEnabled by controller.store.isCallLightsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val isOtherContactsEnabled by controller.store.isOtherContactsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val otherContactsColor by controller.store.otherContactsColor.collectAsStateWithLifecycle(initialValue = 0xFF4285F4)
    val otherContactsPattern by controller.store.otherContactsPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)
    val isUnknownNumbersEnabled by controller.store.isUnknownNumbersEnabled.collectAsStateWithLifecycle(initialValue = true)
    val unknownNumbersColor by controller.store.unknownNumbersColor.collectAsStateWithLifecycle(initialValue = 0xFFFBBC05)
    val unknownNumbersPattern by controller.store.unknownNumbersPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)
    val callContactRules by controller.store.contactRules.collectAsStateWithLifecycle(initialValue = emptyList())

    // Notifications state
    val isNotifsEnabled by controller.store.isNotificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val notifDurationSec by controller.store.notificationDurationSeconds.collectAsStateWithLifecycle(initialValue = 30)
    val isDefaultNotifEnabled by controller.store.isDefaultNotifEnabled.collectAsStateWithLifecycle(initialValue = true)
    val defaultNotifColor by controller.store.defaultNotifColor.collectAsStateWithLifecycle(initialValue = 0xFFFFFFFF)
    val defaultNotifPattern by controller.store.defaultNotifPattern.collectAsStateWithLifecycle(initialValue = PatternMode.PULSE)
    val messageContactRules by controller.store.messageContactRules.collectAsStateWithLifecycle(initialValue = emptyList())
    val appRules by controller.store.appRules.collectAsStateWithLifecycle(initialValue = emptyList())

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var isPhoneGranted by remember { mutableStateOf(hasPhonePermission()) }
    var isCallLogGranted by remember { mutableStateOf(hasCallLogPermission()) }
    var isContactsGranted by remember { mutableStateOf(hasContactsPermission()) }
    var isNotifAccessGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isCallLogGranted = hasCallLogPermission()
            isContactsGranted = hasContactsPermission()
            isNotifAccessGranted = isNotificationListenerEnabled(context)
            NativeHiLightDetector.check(context)
            controller.refreshStatus()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isPhoneGranted = hasPhonePermission()
        isCallLogGranted = hasCallLogPermission()
        isContactsGranted = hasContactsPermission()
    }

    var callRuleBeingEdited by remember { mutableStateOf<ContactRule?>(null) }
    var isConfiguringOtherContacts by remember { mutableStateOf(false) }
    var isConfiguringUnknownNumbers by remember { mutableStateOf(false) }

    var msgRuleBeingEdited by remember { mutableStateOf<MessageContactRule?>(null) }
    var appRuleBeingEdited by remember { mutableStateOf<AppNotificationRule?>(null) }
    var isPickingApp by remember { mutableStateOf(false) }
    var isConfiguringDefaultNotif by remember { mutableStateOf(false) }

    // Pick Contact for Call
    val callContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val contactName = resolveContactName(context, contactUri)
            if (contactName != null) {
                callRuleBeingEdited = ContactRule(
                    id = UUID.randomUUID().toString(),
                    name = contactName,
                    color = 0xFFEA4335,
                    pattern = PatternMode.PULSE
                )
            }
        }
    }

    // Pick Contact for Message
    val msgContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val contactName = resolveContactName(context, contactUri)
            if (contactName != null) {
                msgRuleBeingEdited = MessageContactRule(
                    id = UUID.randomUUID().toString(),
                    name = contactName,
                    color = 0xFF00E5FF,
                    pattern = PatternMode.PULSE
                )
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openNotifSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    HomeContent(
        shizukuState = shizukuState,
        shizukuError = controller.shizuku.errorText(),
        onDisconnectShizuku = { controller.shizuku.unbind() },
        onConnectShizuku = { controller.shizuku.connectManually() },
        onRequestShizukuPermission = { controller.shizuku.requestPermission() },
        onOpenShizukuApp = { controller.shizuku.openShizukuApp(context) },
        stockState = stockState,
        onOpenStockSettings = { NativeHiLightDetector.openHiLightSettings(context) },
        isPhoneGranted = isPhoneGranted,
        isCallLogGranted = isCallLogGranted,
        isContactsGranted = isContactsGranted,
        onRequestPhonePerms = {
            val missing = mutableListOf<String>().apply {
                if (!isPhoneGranted) add(Manifest.permission.READ_PHONE_STATE)
                if (!isCallLogGranted) add(Manifest.permission.READ_CALL_LOG)
                if (!isContactsGranted) add(Manifest.permission.READ_CONTACTS)
            }.toTypedArray()
            permissionLauncher.launch(missing)
        },
        onOpenAppSettings = { openAppSettings() },
        isNotifAccessGranted = isNotifAccessGranted,
        onOpenNotifSettings = { openNotifSettings() },
        // Calls Section
        isCallLightsEnabled = isCallLightsEnabled,
        onToggleCallLights = { enabled -> scope.launch { controller.store.setCallLightsEnabled(enabled) } },
        isOtherContactsEnabled = isOtherContactsEnabled,
        otherContactsColor = otherContactsColor,
        otherContactsPattern = otherContactsPattern,
        onToggleOtherContacts = { enabled -> scope.launch { controller.store.setOtherContactsEnabled(enabled) } },
        onEditOtherContacts = { isConfiguringOtherContacts = true },
        isUnknownNumbersEnabled = isUnknownNumbersEnabled,
        unknownNumbersColor = unknownNumbersColor,
        unknownNumbersPattern = unknownNumbersPattern,
        onToggleUnknownNumbers = { enabled -> scope.launch { controller.store.setUnknownNumbersEnabled(enabled) } },
        onEditUnknownNumbers = { isConfiguringUnknownNumbers = true },
        callContactRules = callContactRules,
        onToggleCallContactRule = { rule, isEnabled ->
            scope.launch { controller.store.saveContactRule(rule.copy(isEnabled = isEnabled)) }
        },
        onEditCallContactRule = { rule -> callRuleBeingEdited = rule },
        onDeleteCallContactRule = { ruleId -> scope.launch { controller.store.deleteContactRule(ruleId) } },
        onAddCallContact = { callContactPickerLauncher.launch(null) },
        // Notifications Section
        isNotifsEnabled = isNotifsEnabled,
        onToggleNotifs = { enabled -> scope.launch { controller.store.setNotificationsEnabled(enabled) } },
        notifDurationSec = notifDurationSec,
        onChangeDuration = { sec -> scope.launch { controller.store.setNotificationDurationSeconds(sec) } },
        isDefaultNotifEnabled = isDefaultNotifEnabled,
        defaultNotifColor = defaultNotifColor,
        defaultNotifPattern = defaultNotifPattern,
        onToggleDefaultNotif = { enabled -> scope.launch { controller.store.setDefaultNotifEnabled(enabled) } },
        onEditDefaultNotif = { isConfiguringDefaultNotif = true },
        messageContactRules = messageContactRules,
        onToggleMessageRule = { rule, isEnabled ->
            scope.launch { controller.store.saveMessageContactRule(rule.copy(isEnabled = isEnabled)) }
        },
        onEditMessageRule = { rule -> msgRuleBeingEdited = rule },
        onDeleteMessageRule = { ruleId -> scope.launch { controller.store.deleteMessageContactRule(ruleId) } },
        onAddMessageContact = { msgContactPickerLauncher.launch(null) },
        appRules = appRules,
        onToggleAppRule = { rule, isEnabled ->
            scope.launch { controller.store.saveAppRule(rule.copy(isEnabled = isEnabled)) }
        },
        onEditAppRule = { rule -> appRuleBeingEdited = rule },
        onDeleteAppRule = { pkg -> scope.launch { controller.store.deleteAppRule(pkg) } },
        onAddApp = { isPickingApp = true },
        renderer = renderer
    )

    // Call Contact Dialog
    if (callRuleBeingEdited != null) {
        val rule = callRuleBeingEdited!!
        CustomRuleDialog(
            title = "Configure ${rule.name}",
            initialColor = rule.color,
            initialPattern = rule.pattern,
            renderer = renderer,
            onDismiss = { callRuleBeingEdited = null },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.saveContactRule(rule.copy(pattern = pattern, color = color))
                    callRuleBeingEdited = null
                }
            }
        )
    }

    // All Other Contacts Dialog
    if (isConfiguringOtherContacts) {
        CustomRuleDialog(
            title = "All Other Contacts",
            description = "Applied to incoming calls from saved contacts without a specific custom rule.",
            initialColor = otherContactsColor,
            initialPattern = otherContactsPattern,
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
        CustomRuleDialog(
            title = "Unknown & Private Numbers",
            description = "Applied to incoming calls from unsaved or hidden caller numbers.",
            initialColor = unknownNumbersColor,
            initialPattern = unknownNumbersPattern,
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

    // App Picker Dialog (Android 11+ compliant launcher query + icons)
    if (isPickingApp) {
        AppPickerDialog(
            context = context,
            alreadyAdded = appRules.map { it.packageName }.toSet(),
            onDismiss = { isPickingApp = false },
            onAppSelected = { pkg, name ->
                isPickingApp = false
                appRuleBeingEdited = AppNotificationRule(
                    packageName = pkg,
                    appName = name,
                    color = 0xFF34A853,
                    pattern = PatternMode.PULSE
                )
            }
        )
    }

    // Message Contact Dialog
    if (msgRuleBeingEdited != null) {
        val rule = msgRuleBeingEdited!!
        CustomRuleDialog(
            title = "Configure ${rule.name}",
            initialColor = rule.color,
            initialPattern = rule.pattern,
            renderer = renderer,
            onDismiss = { msgRuleBeingEdited = null },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.saveMessageContactRule(rule.copy(pattern = pattern, color = color))
                    msgRuleBeingEdited = null
                }
            }
        )
    }

    // App Customizer Dialog
    if (appRuleBeingEdited != null) {
        val rule = appRuleBeingEdited!!
        CustomRuleDialog(
            title = "Configure ${rule.appName}",
            initialColor = rule.color,
            initialPattern = rule.pattern,
            renderer = renderer,
            onDismiss = { appRuleBeingEdited = null },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.saveAppRule(rule.copy(pattern = pattern, color = color))
                    appRuleBeingEdited = null
                }
            }
        )
    }

    // Default Fallback Notif Dialog
    if (isConfiguringDefaultNotif) {
        CustomRuleDialog(
            title = "All Other Notifications",
            description = "Applied to incoming notifications from apps and senders without a specific custom rule.",
            initialColor = defaultNotifColor,
            initialPattern = defaultNotifPattern,
            renderer = renderer,
            onDismiss = { isConfiguringDefaultNotif = false },
            onSave = { pattern, color ->
                scope.launch {
                    controller.store.setDefaultNotifPattern(pattern)
                    controller.store.setDefaultNotifColor(color)
                    isConfiguringDefaultNotif = false
                }
            }
        )
    }
}

/**
 * Pure stateless Composable rendering the unified Home UI.
 * Directly consumed by both the live runtime screen and the Compose Preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    shizukuState: ShizukuBridge.State,
    shizukuError: String?,
    onDisconnectShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    stockState: StockHiLightState,
    onOpenStockSettings: () -> Unit,
    isPhoneGranted: Boolean,
    isCallLogGranted: Boolean,
    isContactsGranted: Boolean,
    onRequestPhonePerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    isNotifAccessGranted: Boolean,
    onOpenNotifSettings: () -> Unit,
    // Calls
    isCallLightsEnabled: Boolean,
    onToggleCallLights: (Boolean) -> Unit,
    isOtherContactsEnabled: Boolean,
    otherContactsColor: Long,
    otherContactsPattern: PatternMode,
    onToggleOtherContacts: (Boolean) -> Unit,
    onEditOtherContacts: () -> Unit,
    isUnknownNumbersEnabled: Boolean,
    unknownNumbersColor: Long,
    unknownNumbersPattern: PatternMode,
    onToggleUnknownNumbers: (Boolean) -> Unit,
    onEditUnknownNumbers: () -> Unit,
    callContactRules: List<ContactRule>,
    onToggleCallContactRule: (ContactRule, Boolean) -> Unit,
    onEditCallContactRule: (ContactRule) -> Unit,
    onDeleteCallContactRule: (String) -> Unit,
    onAddCallContact: () -> Unit,
    // Notifications
    isNotifsEnabled: Boolean,
    onToggleNotifs: (Boolean) -> Unit,
    notifDurationSec: Int,
    onChangeDuration: (Int) -> Unit,
    isDefaultNotifEnabled: Boolean,
    defaultNotifColor: Long,
    defaultNotifPattern: PatternMode,
    onToggleDefaultNotif: (Boolean) -> Unit,
    onEditDefaultNotif: () -> Unit,
    messageContactRules: List<MessageContactRule>,
    onToggleMessageRule: (MessageContactRule, Boolean) -> Unit,
    onEditMessageRule: (MessageContactRule) -> Unit,
    onDeleteMessageRule: (String) -> Unit,
    onAddMessageContact: () -> Unit,
    appRules: List<AppNotificationRule>,
    onToggleAppRule: (AppNotificationRule, Boolean) -> Unit,
    onEditAppRule: (AppNotificationRule) -> Unit,
    onDeleteAppRule: (String) -> Unit,
    onAddApp: () -> Unit,
    renderer: PatternRenderer
) {
    val callAlpha by animateFloatAsState(
        targetValue = if (isCallLightsEnabled) 1.0f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "callAlpha"
    )

    val notifAlpha by animateFloatAsState(
        targetValue = if (isNotifsEnabled) 1.0f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "notifAlpha"
    )

    val callScale by animateFloatAsState(
        targetValue = if (isCallLightsEnabled) 1.0f else 0.985f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "callScale"
    )

    val notifScale by animateFloatAsState(
        targetValue = if (isNotifsEnabled) 1.0f else 0.985f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "notifScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
        ) {
            // 1. Shizuku Privileged Access Card (Persistent)
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            val isExplicitlyDisconnected = shizukuState == ShizukuBridge.State.DISCONNECTED
            item {
                ExpressiveStatusCard(
                    title = "Shizuku Privileged Access",
                    subtitle = when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> "Active session holding privileged control over your Pixel's rear light array."
                        ShizukuBridge.State.DISCONNECTED -> "Session is paused/disconnected. Tap 'Connect' to re-engage hardware lights."
                        ShizukuBridge.State.NEEDS_PERMISSION -> "Shizuku is running. Tap 'Authorize' below to grant privileged LED access."
                        ShizukuBridge.State.NOT_RUNNING -> "Shizuku daemon is stopped. Start via Wireless Debugging or ADB."
                        ShizukuBridge.State.NOT_INSTALLED -> "Shizuku Manager is not installed on this device."
                        ShizukuBridge.State.CONNECTING -> "Connecting to local Shizuku binder daemon..."
                        else -> shizukuError ?: "Could not establish binder connection to Shizuku."
                    },
                    icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                    statusText = when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> "Connected"
                        ShizukuBridge.State.DISCONNECTED -> "Disconnected (Paused)"
                        ShizukuBridge.State.CONNECTING -> "Connecting"
                        ShizukuBridge.State.NEEDS_PERMISSION -> "Needs Permission"
                        ShizukuBridge.State.NOT_RUNNING -> "Not Running"
                        ShizukuBridge.State.NOT_INSTALLED -> "Not Installed"
                        else -> "Disconnected"
                    },
                    accentColor = if (isShizukuConnected) MaterialTheme.colorScheme.primary else if (isExplicitlyDisconnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    containerColor = if (isShizukuConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isShizukuConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    bottomAction = {
                        when (shizukuState) {
                            ShizukuBridge.State.CONNECTED -> {
                                OutlinedButton(
                                    onClick = onDisconnectShizuku,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Disconnect Shizuku Session")
                                }
                            }
                            ShizukuBridge.State.DISCONNECTED -> {
                                Button(
                                    onClick = onConnectShizuku,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connect Shizuku Session")
                                }
                            }
                            ShizukuBridge.State.NEEDS_PERMISSION -> {
                                Button(
                                    onClick = onRequestShizukuPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Authorize Shizuku Access")
                                }
                            }
                            ShizukuBridge.State.NOT_INSTALLED -> {
                                Button(
                                    onClick = onOpenShizukuApp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Install Shizuku Manager")
                                }
                            }
                            ShizukuBridge.State.NOT_RUNNING -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onOpenShizukuApp,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open Shizuku")
                                    }
                                    OutlinedButton(
                                        onClick = onConnectShizuku,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Check Again")
                                    }
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onConnectShizuku,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry Connection")
                                }
                            }
                        }
                    }
                )
            }

            // 2. Conditional Warning Banners (ONLY visible if issues exist)
            val isNativeConflict = stockState.favoriteCallsActive
            if (isNativeConflict) {
                item {
                    ExpressiveStatusCard(
                        title = "Stock Favorite Calls Active",
                        subtitle = "Stock Favorite Calls is active in System Settings and will conflict with custom caller lighting.",
                        icon = Icons.Rounded.Warning,
                        statusText = "Conflict Active",
                        accentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        isWarning = true,
                        bottomAction = {
                            Button(
                                onClick = onOpenStockSettings,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Disable in System Settings")
                            }
                        }
                    )
                }
            }

            val hasAllPhonePerms = isPhoneGranted && isCallLogGranted && isContactsGranted
            if (!hasAllPhonePerms) {
                val missingName = when {
                    !isPhoneGranted || !isCallLogGranted -> "Phone & Call Log permissions"
                    else -> "Contacts permission"
                }
                item {
                    ExpressiveStatusCard(
                        title = "$missingName Required",
                        subtitle = "Phone & Call Log permissions are needed to detect incoming callers and match lighting rules.",
                        icon = Icons.Rounded.PermPhoneMsg,
                        statusText = "Missing Permissions",
                        accentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        isWarning = true,
                        bottomAction = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onRequestPhonePerms,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Grant Permission")
                                }
                                OutlinedButton(
                                    onClick = onOpenAppSettings,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("App Info")
                                }
                            }
                        }
                    )
                }
            }

            if (!isNotifAccessGranted) {
                item {
                    ExpressiveStatusCard(
                        title = "Notification Access Required",
                        subtitle = "Notification Listener access is required to detect app notifications and contact messages.",
                        icon = Icons.Rounded.NotificationsActive,
                        statusText = "Access Needed",
                        accentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        isWarning = true,
                        bottomAction = {
                            Button(
                                onClick = onOpenNotifSettings,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.NotificationAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Grant Notification Access")
                            }
                        }
                    )
                }
            }

            // =========================================================================
            // SECTION: INCOMING CALLS (Material 3 Tonal Elevation)
            // =========================================================================

            // Master Switch for Incoming Calls (OUTSIDE container)
            item {
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
                                text = "Incoming calls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Light the rear array when calls are ringing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Switch(
                            checked = isCallLightsEnabled,
                            onCheckedChange = onToggleCallLights
                        )
                    }
                }
            }

            // Dedicated Surface container for Call Settings
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(callScale)
                        .alpha(callAlpha),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TonalRuleCard(
                            title = "All Other Contacts",
                            pattern = otherContactsPattern,
                            color = otherContactsColor,
                            renderer = renderer,
                            isEnabled = isOtherContactsEnabled && isCallLightsEnabled,
                            onToggle = { if (isCallLightsEnabled) onToggleOtherContacts(it) },
                            onEdit = { if (isCallLightsEnabled) onEditOtherContacts() }
                        )

                        TonalRuleCard(
                            title = "Unknown & Private Numbers",
                            pattern = unknownNumbersPattern,
                            color = unknownNumbersColor,
                            renderer = renderer,
                            isEnabled = isUnknownNumbersEnabled && isCallLightsEnabled,
                            onToggle = { if (isCallLightsEnabled) onToggleUnknownNumbers(it) },
                            onEdit = { if (isCallLightsEnabled) onEditUnknownNumbers() }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Custom caller rules (${callContactRules.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (callContactRules.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No custom caller rules.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
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
                                    isEnabled = rule.isEnabled && isCallLightsEnabled,
                                    onToggle = { isEnabled -> if (isCallLightsEnabled) onToggleCallContactRule(rule, isEnabled) },
                                    onEdit = { if (isCallLightsEnabled) onEditCallContactRule(rule) },
                                    onDelete = { if (isCallLightsEnabled) onDeleteCallContactRule(rule.id) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { if (isCallLightsEnabled) onAddCallContact() },
                            enabled = isCallLightsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add contact")
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION: NOTIFICATIONS & MESSAGES (Material 3 Tonal Elevation)
            // =========================================================================

            // Master Switch for Notifications (OUTSIDE container)
            item {
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
                                text = "Notification Illumination",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Light the rear array when notifications arrive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Switch(
                            checked = isNotifsEnabled,
                            onCheckedChange = onToggleNotifs
                        )
                    }
                }
            }

            // Dedicated Surface container for Notifications & Messages
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(notifScale)
                        .alpha(notifAlpha),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Light Duration Slider Card with Compact Sleek Track & Handle
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Light Duration",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "How long the rear array stays illuminated",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        enabled = isNotifsEnabled,
                                        label = {
                                            Text(
                                                text = if (notifDurationSec < 60) "${notifDurationSec}s" else "${notifDurationSec / 60}m ${if (notifDurationSec % 60 != 0) "${notifDurationSec % 60}s" else ""}".trim(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ),
                                        border = null
                                    )
                                }

                                // Compact Low-Profile Slider with Small Drag Handle
                                Slider(
                                    value = notifDurationSec.toFloat(),
                                    enabled = isNotifsEnabled,
                                    onValueChange = { value ->
                                        val rounded = (value / 30f).roundToInt() * 30
                                        onChangeDuration(rounded.coerceIn(30, 300))
                                    },
                                    valueRange = 30f..300f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                )
                            }
                        }

                        // Default Fallback Card
                        TonalRuleCard(
                            title = "All Other Notifications",
                            subtitle = "Notifications with no specific contact or app rule",
                            pattern = defaultNotifPattern,
                            color = defaultNotifColor,
                            renderer = renderer,
                            isEnabled = isDefaultNotifEnabled && isNotifsEnabled,
                            onToggle = { if (isNotifsEnabled) onToggleDefaultNotif(it) },
                            onEdit = { if (isNotifsEnabled) onEditDefaultNotif() }
                        )

                        // Contact Message Rules Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Custom contact rules (${messageContactRules.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (messageContactRules.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No contact message rules.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            messageContactRules.forEach { rule ->
                                TonalRuleCard(
                                    title = rule.name,
                                    pattern = rule.pattern,
                                    color = rule.color,
                                    renderer = renderer,
                                    isEnabled = rule.isEnabled && isNotifsEnabled,
                                    onToggle = { isEnabled -> if (isNotifsEnabled) onToggleMessageRule(rule, isEnabled) },
                                    onEdit = { if (isNotifsEnabled) onEditMessageRule(rule) },
                                    onDelete = { if (isNotifsEnabled) onDeleteMessageRule(rule.id) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { if (isNotifsEnabled) onAddMessageContact() },
                            enabled = isNotifsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add contact")
                        }

                        // App Rules Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Custom app rules (${appRules.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (appRules.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No custom app rules.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            appRules.forEach { rule ->
                                TonalRuleCard(
                                    title = rule.appName,
                                    pattern = rule.pattern,
                                    color = rule.color,
                                    renderer = renderer,
                                    isEnabled = rule.isEnabled && isNotifsEnabled,
                                    onToggle = { isEnabled -> if (isNotifsEnabled) onToggleAppRule(rule, isEnabled) },
                                    onEdit = { if (isNotifsEnabled) onEditAppRule(rule) },
                                    onDelete = { if (isNotifsEnabled) onDeleteAppRule(rule.packageName) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { if (isNotifsEnabled) onAddApp() },
                            enabled = isNotifsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add app")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Universal Tonal Rule Card:
 * Replaces redundant ContactRuleItem, MessageContactRuleItem, AppRuleItem, and CategoryCards.
 */
@Composable
fun TonalRuleCard(
    title: String,
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    subtitle: String? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                AnimatedRingBadge(pattern = pattern, color = color, renderer = renderer, size = 36.dp)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error)
                    }
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
fun AnimatedRingBadge(
    pattern: PatternMode,
    color: Long,
    renderer: PatternRenderer,
    size: Dp
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

/**
 * Universal Dialog for Call Contact, Message Contact, App Rule, or Fallback configuration.
 */
@Composable
fun CustomRuleDialog(
    title: String,
    initialColor: Long,
    initialPattern: PatternMode,
    renderer: PatternRenderer,
    onDismiss: () -> Unit,
    onSave: (PatternMode, Long) -> Unit,
    description: String? = null
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

    val isColorEnabled = selectedPattern != PatternMode.RAINBOW
    val colorAlpha by animateFloatAsState(
        targetValue = if (isColorEnabled) 1.0f else 0.35f,
        animationSpec = tween(durationMillis = 200),
        label = "dialogColorAlpha"
    )

    val patterns = listOf(
        PatternMode.PULSE,
        PatternMode.BREATHE,
        PatternMode.WAVE,
        PatternMode.COMET,
        PatternMode.RAINBOW,
        PatternMode.SOLID
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Hero Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DiffusedRingPreview(
                            frames = dialogPreviewFrames,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            size = 72.dp
                        )
                    }
                }

                Text(
                    text = "Select Animation Pattern",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(colorAlpha),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Color",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isColorEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        palette.forEach { c ->
                            val isSelected = selectedColor == c && isColorEnabled
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
                                    .clickable(enabled = isColorEnabled) { selectedColor = c }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedPattern, selectedColor)
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

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

/**
 * Android 11+ compliant AppPickerDialog using Launcher Intent Querying & Async Icon loading.
 */
@Composable
fun AppPickerDialog(
    context: Context,
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, appName: String) -> Unit
) {
    val pm = context.packageManager
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val apps = resolveInfos
                .map { ri ->
                    val pkg = ri.activityInfo.packageName
                    val label = ri.loadLabel(pm).toString()
                    val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                    InstalledAppItem(pkg, label, icon)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }

            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = installedApps.filter {
        !alreadyAdded.contains(it.packageName) &&
            (it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select App") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                ),
                                leadingContent = {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon.toBitmap(width = 96, height = 96).asImageBitmap(),
                                            contentDescription = app.appName,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Android,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                headlineContent = { Text(app.appName, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAppSelected(app.packageName, app.appName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(context.packageName)
}

fun resolveContactName(context: Context, contactUri: Uri): String? {
    var name: String? = null
    val contentResolver = context.contentResolver
    contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    return if (!name.isNullOrBlank()) name else null
}

@Preview(name = "Home Screen - With Rules", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun HomeScreenPreviewWithRules() {
    HomeScreenPreviewContent(hasCallRules = true, hasMsgRules = true, hasAppRules = true)
}

@Preview(name = "Home Screen - Empty State", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun HomeScreenPreviewEmptyState() {
    HomeScreenPreviewContent(hasCallRules = false, hasMsgRules = false, hasAppRules = false)
}

@Composable
fun HomeScreenPreviewContent(
    hasCallRules: Boolean = false,
    hasMsgRules: Boolean = true,
    hasAppRules: Boolean = true
) {
    val mockCallContacts = if (hasCallRules) {
        listOf(
            ContactRule("1", "Sarah Connor", 0xFFEA4335, PatternMode.PULSE, true),
            ContactRule("2", "Mom", 0xFFFF007F, PatternMode.BREATHE, true)
        )
    } else emptyList()

    val mockMsgContacts = if (hasMsgRules) {
        listOf(
            MessageContactRule("1", "Sarah Connor", 0xFF00E5FF, PatternMode.PULSE, true)
        )
    } else emptyList()

    val mockApps = if (hasAppRules) {
        listOf(
            AppNotificationRule("com.whatsapp", "WhatsApp", 0xFF25D366, PatternMode.PULSE, true)
        )
    } else emptyList()

    HiLightPlusTheme {
        HomeContent(
            shizukuState = ShizukuBridge.State.CONNECTED,
            shizukuError = null,
            onDisconnectShizuku = {},
            onConnectShizuku = {},
            onRequestShizukuPermission = {},
            onOpenShizukuApp = {},
            stockState = StockHiLightState(favoriteCallsActive = false),
            onOpenStockSettings = {},
            isPhoneGranted = true,
            isCallLogGranted = true,
            isContactsGranted = true,
            onRequestPhonePerms = {},
            onOpenAppSettings = {},
            isNotifAccessGranted = true,
            onOpenNotifSettings = {},
            isCallLightsEnabled = true,
            onToggleCallLights = {},
            isOtherContactsEnabled = true,
            otherContactsColor = 0xFF4285F4,
            otherContactsPattern = PatternMode.PULSE,
            onToggleOtherContacts = {},
            onEditOtherContacts = {},
            isUnknownNumbersEnabled = true,
            unknownNumbersColor = 0xFFFBBC05,
            unknownNumbersPattern = PatternMode.PULSE,
            onToggleUnknownNumbers = {},
            onEditUnknownNumbers = {},
            callContactRules = mockCallContacts,
            onToggleCallContactRule = { _, _ -> },
            onEditCallContactRule = {},
            onDeleteCallContactRule = {},
            onAddCallContact = {},
            isNotifsEnabled = true,
            onToggleNotifs = {},
            notifDurationSec = 30,
            onChangeDuration = {},
            isDefaultNotifEnabled = true,
            defaultNotifColor = 0xFFFFFFFF,
            defaultNotifPattern = PatternMode.PULSE,
            onToggleDefaultNotif = {},
            onEditDefaultNotif = {},
            messageContactRules = mockMsgContacts,
            onToggleMessageRule = { _, _ -> },
            onEditMessageRule = {},
            onDeleteMessageRule = {},
            onAddMessageContact = {},
            appRules = mockApps,
            onToggleAppRule = { _, _ -> },
            onEditAppRule = {},
            onDeleteAppRule = {},
            onAddApp = {},
            renderer = PatternRenderer()
        )
    }
}
