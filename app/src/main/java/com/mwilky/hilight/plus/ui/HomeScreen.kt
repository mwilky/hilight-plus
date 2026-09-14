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
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.QuietHoursMode
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.NotificationTrigger
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.core.PatternRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID

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
fun HomeScreen(
    controller: LightController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val renderer = remember { PatternRenderer() }

    val stockState by NativeHiLightDetector.state.collectAsStateWithLifecycle()
    val shizukuState by controller.shizuku.state.collectAsStateWithLifecycle()

    val isCallLightsEnabled by viewModel.isCallLightsEnabled.collectAsStateWithLifecycle()
    val isOtherContactsEnabled by viewModel.isOtherContactsEnabled.collectAsStateWithLifecycle()
    val otherContactsColor by viewModel.otherContactsColor.collectAsStateWithLifecycle()
    val otherContactsPattern by viewModel.otherContactsPattern.collectAsStateWithLifecycle()
    val otherContactsFaceDown by viewModel.otherContactsFaceDownMode.collectAsStateWithLifecycle()
    val otherContactsDnd by viewModel.otherContactsDndMode.collectAsStateWithLifecycle()
    val otherContactsQuiet by viewModel.otherContactsQuietHoursMode.collectAsStateWithLifecycle()
    val otherContactsQuietStart by viewModel.otherContactsQuietHoursStartMinutes.collectAsStateWithLifecycle()
    val otherContactsQuietEnd by viewModel.otherContactsQuietHoursEndMinutes.collectAsStateWithLifecycle()
    val conditionsQuietStart by viewModel.quietHoursStartMinutes.collectAsStateWithLifecycle()
    val conditionsQuietEnd by viewModel.quietHoursEndMinutes.collectAsStateWithLifecycle()
    val isUnknownNumbersEnabled by viewModel.isUnknownNumbersEnabled.collectAsStateWithLifecycle()
    val unknownNumbersColor by viewModel.unknownNumbersColor.collectAsStateWithLifecycle()
    val unknownNumbersPattern by viewModel.unknownNumbersPattern.collectAsStateWithLifecycle()
    val unknownNumbersFaceDown by viewModel.unknownNumbersFaceDownMode.collectAsStateWithLifecycle()
    val unknownNumbersDnd by viewModel.unknownNumbersDndMode.collectAsStateWithLifecycle()
    val unknownNumbersQuiet by viewModel.unknownNumbersQuietHoursMode.collectAsStateWithLifecycle()
    val unknownNumbersQuietStart by viewModel.unknownNumbersQuietHoursStartMinutes.collectAsStateWithLifecycle()
    val unknownNumbersQuietEnd by viewModel.unknownNumbersQuietHoursEndMinutes.collectAsStateWithLifecycle()
    val callContactRules by viewModel.callContactRules.collectAsStateWithLifecycle()

    val isNotifsEnabled by viewModel.isNotifsEnabled.collectAsStateWithLifecycle()
    val notifDurationSec by viewModel.notifDurationSec.collectAsStateWithLifecycle()
    val isCycleNotifications by viewModel.isCycleNotifications.collectAsStateWithLifecycle()
    val isDefaultNotifEnabled by viewModel.isDefaultNotifEnabled.collectAsStateWithLifecycle()
    val defaultNotifColor by viewModel.defaultNotifColor.collectAsStateWithLifecycle()
    val defaultNotifPattern by viewModel.defaultNotifPattern.collectAsStateWithLifecycle()
    val defaultNotifFaceDown by viewModel.defaultNotifFaceDownMode.collectAsStateWithLifecycle()
    val isDefaultNotifAutoColor by viewModel.isDefaultNotifAutoColor.collectAsStateWithLifecycle()
    val defaultNotifDnd by viewModel.defaultNotifDndMode.collectAsStateWithLifecycle()
    val defaultNotifQuiet by viewModel.defaultNotifQuietHoursMode.collectAsStateWithLifecycle()
    val defaultNotifQuietStart by viewModel.defaultNotifQuietHoursStartMinutes.collectAsStateWithLifecycle()
    val defaultNotifQuietEnd by viewModel.defaultNotifQuietHoursEndMinutes.collectAsStateWithLifecycle()
    val messageContactRules by viewModel.messageContactRules.collectAsStateWithLifecycle()
    val appRules by viewModel.appRules.collectAsStateWithLifecycle()

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
    var isNotifListenerRunning by remember { mutableStateOf(isNotificationListenerRunning()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isPhoneGranted = hasPhonePermission()
            isCallLogGranted = hasCallLogPermission()
            isContactsGranted = hasContactsPermission()
            isNotifAccessGranted = isNotificationListenerEnabled(context)
            isNotifListenerRunning = isNotificationListenerRunning()
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
        isNotifListenerRunning = isNotifListenerRunning,
        onOpenNotifSettings = { openNotifSettings() },
        // Calls Section
        isCallLightsEnabled = isCallLightsEnabled,
        onToggleCallLights = viewModel::setCallLightsEnabled,
        isOtherContactsEnabled = isOtherContactsEnabled,
        otherContactsColor = otherContactsColor,
        otherContactsPattern = otherContactsPattern,
        otherContactsFaceDownMode = otherContactsFaceDown,
        onToggleOtherContacts = viewModel::setOtherContactsEnabled,
        onEditOtherContacts = { isConfiguringOtherContacts = true },
        isUnknownNumbersEnabled = isUnknownNumbersEnabled,
        unknownNumbersColor = unknownNumbersColor,
        unknownNumbersPattern = unknownNumbersPattern,
        unknownNumbersFaceDownMode = unknownNumbersFaceDown,
        onToggleUnknownNumbers = viewModel::setUnknownNumbersEnabled,
        onEditUnknownNumbers = { isConfiguringUnknownNumbers = true },
        callContactRules = callContactRules,
        onToggleCallContactRule = { rule, isEnabled ->
            viewModel.saveContactRule(rule.copy(isEnabled = isEnabled))
        },
        onEditCallContactRule = { rule -> callRuleBeingEdited = rule },
        onDeleteCallContactRule = viewModel::deleteContactRule,
        onAddCallContact = { callContactPickerLauncher.launch(null) },
        // Notifications Section
        isNotifsEnabled = isNotifsEnabled,
        onToggleNotifs = viewModel::setNotificationsEnabled,
        notifDurationSec = notifDurationSec,
        onChangeDuration = viewModel::setNotificationDurationSeconds,
        isCycleNotifications = isCycleNotifications,
        onToggleCycleNotifications = viewModel::setCycleNotifications,
        isDefaultNotifEnabled = isDefaultNotifEnabled,
        defaultNotifColor = defaultNotifColor,
        defaultNotifPattern = defaultNotifPattern,
        defaultNotifFaceDownMode = defaultNotifFaceDown,
        onToggleDefaultNotif = viewModel::setDefaultNotifEnabled,
        onEditDefaultNotif = { isConfiguringDefaultNotif = true },
        messageContactRules = messageContactRules,
        onToggleMessageRule = { rule, isEnabled ->
            viewModel.saveMessageContactRule(rule.copy(isEnabled = isEnabled))
        },
        onEditMessageRule = { rule -> msgRuleBeingEdited = rule },
        onDeleteMessageRule = viewModel::deleteMessageContactRule,
        onAddMessageContact = { msgContactPickerLauncher.launch(null) },
        appRules = appRules,
        onToggleAppRule = { rule, isEnabled ->
            viewModel.saveAppRule(rule.copy(isEnabled = isEnabled))
        },
        onEditAppRule = { rule -> appRuleBeingEdited = rule },
        onDeleteAppRule = viewModel::deleteAppRule,
        onAddApp = { isPickingApp = true },
        renderer = renderer
    )

    // Call Contact Dialog
    if (callRuleBeingEdited != null) {
        val rule = callRuleBeingEdited!!
        CustomRuleDialog(
            title = stringResource(R.string.dialog_configure_title, rule.name),
            initialColor = rule.color,
            initialPattern = rule.pattern,
            initialFaceDown = rule.faceDownMode,
            initialDnd = rule.dndMode,
            initialQuietHours = rule.quietHoursMode,
            initialQuietStart = rule.quietHoursStartMinutes ?: conditionsQuietStart,
            initialQuietEnd = rule.quietHoursEndMinutes ?: conditionsQuietEnd,
            renderer = renderer,
            onDismiss = { callRuleBeingEdited = null },
            onSave = { result ->
                viewModel.saveContactRule(
                    rule.copy(
                        pattern = result.pattern,
                        color = result.color,
                        faceDownMode = result.faceDown,
                        dndMode = result.dndMode,
                        quietHoursMode = result.quietHoursMode,
                        quietHoursStartMinutes = result.quietHoursStartMinutes,
                        quietHoursEndMinutes = result.quietHoursEndMinutes
                    )
                )
                callRuleBeingEdited = null
            }
        )
    }

    // All Other Contacts Dialog
    if (isConfiguringOtherContacts) {
        CustomRuleDialog(
            title = stringResource(R.string.calls_other_contacts_title),
            initialColor = otherContactsColor,
            initialPattern = otherContactsPattern,
            initialFaceDown = otherContactsFaceDown,
            initialDnd = otherContactsDnd,
            initialQuietHours = otherContactsQuiet,
            initialQuietStart = otherContactsQuietStart ?: conditionsQuietStart,
            initialQuietEnd = otherContactsQuietEnd ?: conditionsQuietEnd,
            renderer = renderer,
            onDismiss = { isConfiguringOtherContacts = false },
            onSave = { result ->
                viewModel.setOtherContactsStyle(
                    result.pattern,
                    result.color,
                    result.faceDown,
                    result.dndMode,
                    result.quietHoursMode,
                    result.quietHoursStartMinutes,
                    result.quietHoursEndMinutes
                )
                isConfiguringOtherContacts = false
            }
        )
    }

    // Unknown Numbers Dialog
    if (isConfiguringUnknownNumbers) {
        CustomRuleDialog(
            title = stringResource(R.string.calls_unknown_numbers_title),
            initialColor = unknownNumbersColor,
            initialPattern = unknownNumbersPattern,
            initialFaceDown = unknownNumbersFaceDown,
            initialDnd = unknownNumbersDnd,
            initialQuietHours = unknownNumbersQuiet,
            initialQuietStart = unknownNumbersQuietStart ?: conditionsQuietStart,
            initialQuietEnd = unknownNumbersQuietEnd ?: conditionsQuietEnd,
            renderer = renderer,
            onDismiss = { isConfiguringUnknownNumbers = false },
            onSave = { result ->
                viewModel.setUnknownNumbersStyle(
                    result.pattern,
                    result.color,
                    result.faceDown,
                    result.dndMode,
                    result.quietHoursMode,
                    result.quietHoursStartMinutes,
                    result.quietHoursEndMinutes
                )
                isConfiguringUnknownNumbers = false
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
                val autoColor = com.mwilky.hilight.plus.core.AppIconColorExtractor.extractColorForPackage(context, pkg)
                appRuleBeingEdited = AppNotificationRule(
                    packageName = pkg,
                    appName = name,
                    color = autoColor,
                    pattern = PatternMode.PULSE,
                    isAutoColor = true
                )
            }
        )
    }

    // Message Contact Dialog
    if (msgRuleBeingEdited != null) {
        val rule = msgRuleBeingEdited!!
        CustomRuleDialog(
            title = stringResource(R.string.dialog_configure_title, rule.name),
            initialColor = rule.color,
            initialPattern = rule.pattern,
            initialFaceDown = rule.faceDownMode,
            initialDnd = rule.dndMode,
            initialQuietHours = rule.quietHoursMode,
            initialQuietStart = rule.quietHoursStartMinutes ?: conditionsQuietStart,
            initialQuietEnd = rule.quietHoursEndMinutes ?: conditionsQuietEnd,
            renderer = renderer,
            onDismiss = { msgRuleBeingEdited = null },
            onSave = { result ->
                viewModel.saveMessageContactRule(
                    rule.copy(
                        pattern = result.pattern,
                        color = result.color,
                        faceDownMode = result.faceDown,
                        dndMode = result.dndMode,
                        quietHoursMode = result.quietHoursMode,
                        quietHoursStartMinutes = result.quietHoursStartMinutes,
                        quietHoursEndMinutes = result.quietHoursEndMinutes
                    )
                )
                msgRuleBeingEdited = null
            }
        )
    }

    // App Customizer Dialog
    if (appRuleBeingEdited != null) {
        val rule = appRuleBeingEdited!!
        val autoColor = remember(rule.packageName) {
            com.mwilky.hilight.plus.core.AppIconColorExtractor.extractColorForPackage(context, rule.packageName)
        }
        CustomRuleDialog(
            title = stringResource(R.string.dialog_configure_title, rule.appName),
            initialColor = rule.color,
            initialPattern = rule.pattern,
            initialFaceDown = rule.faceDownMode,
            initialDnd = rule.dndMode,
            initialQuietHours = rule.quietHoursMode,
            initialQuietStart = rule.quietHoursStartMinutes ?: conditionsQuietStart,
            initialQuietEnd = rule.quietHoursEndMinutes ?: conditionsQuietEnd,
            showAutoColorToggle = true,
            initialAutoColor = rule.isAutoColor,
            autoExtractedColor = autoColor,
            renderer = renderer,
            onDismiss = { appRuleBeingEdited = null },
            onSave = { result ->
                viewModel.saveAppRule(
                    rule.copy(
                        pattern = result.pattern,
                        color = result.color,
                        faceDownMode = result.faceDown,
                        isAutoColor = result.autoColor,
                        dndMode = result.dndMode,
                        quietHoursMode = result.quietHoursMode,
                        quietHoursStartMinutes = result.quietHoursStartMinutes,
                        quietHoursEndMinutes = result.quietHoursEndMinutes
                    )
                )
                appRuleBeingEdited = null
            }
        )
    }

    // Default Fallback Notif Dialog
    if (isConfiguringDefaultNotif) {
        CustomRuleDialog(
            title = stringResource(R.string.notifs_default_title),
            initialColor = defaultNotifColor,
            initialPattern = defaultNotifPattern,
            initialFaceDown = defaultNotifFaceDown,
            initialDnd = defaultNotifDnd,
            initialQuietHours = defaultNotifQuiet,
            initialQuietStart = defaultNotifQuietStart ?: conditionsQuietStart,
            initialQuietEnd = defaultNotifQuietEnd ?: conditionsQuietEnd,
            showAutoColorToggle = true,
            initialAutoColor = isDefaultNotifAutoColor,
            autoExtractedColor = null,
            renderer = renderer,
            onDismiss = { isConfiguringDefaultNotif = false },
            onSave = { result ->
                viewModel.setDefaultNotifStyle(
                    result.pattern,
                    result.color,
                    result.faceDown,
                    result.autoColor,
                    result.dndMode,
                    result.quietHoursMode,
                    result.quietHoursStartMinutes,
                    result.quietHoursEndMinutes
                )
                isConfiguringDefaultNotif = false
            }
        )
    }
}

/**
 * Pure stateless Composable rendering the unified Home UI.
 * Directly consumed by both the live runtime screen and the Compose Preview.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    isNotifListenerRunning: Boolean,
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
    isCycleNotifications: Boolean = false,
    onToggleCycleNotifications: (Boolean) -> Unit = {},
    isDefaultNotifEnabled: Boolean,
    defaultNotifColor: Long,
    defaultNotifPattern: PatternMode,
    defaultNotifFaceDownMode: com.mwilky.hilight.plus.FaceDownMode = com.mwilky.hilight.plus.FaceDownMode.INHERIT,
    otherContactsFaceDownMode: com.mwilky.hilight.plus.FaceDownMode = com.mwilky.hilight.plus.FaceDownMode.INHERIT,
    unknownNumbersFaceDownMode: com.mwilky.hilight.plus.FaceDownMode = com.mwilky.hilight.plus.FaceDownMode.INHERIT,
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
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val callAlpha by animateFloatAsState(
        targetValue = if (isCallLightsEnabled) 1.0f else 0.40f,
        animationSpec = effectsSpec,
        label = "callAlpha"
    )

    val notifAlpha by animateFloatAsState(
        targetValue = if (isNotifsEnabled) 1.0f else 0.40f,
        animationSpec = effectsSpec,
        label = "notifAlpha"
    )

    val callScale by animateFloatAsState(
        targetValue = if (isCallLightsEnabled) 1.0f else 0.985f,
        animationSpec = spatialSpec,
        label = "callScale"
    )

    val notifScale by animateFloatAsState(
        targetValue = if (isNotifsEnabled) 1.0f else 0.985f,
        animationSpec = spatialSpec,
        label = "notifScale"
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val headerColors = TopAppBarDefaults.topAppBarColors()
    val headerColor = headerColors.containerColor

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            Column(modifier = Modifier.background(headerColor)) {
                CenterAlignedTopAppBar(
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
                    },
                    colors = headerColors
                )
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = headerColor,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.home_tab_calls)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.home_tab_notifications)) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
        ) {
            // 1. Shizuku Privileged Access Card (Persistent)
            val isShizukuConnected = shizukuState == ShizukuBridge.State.CONNECTED
            val isExplicitlyDisconnected = shizukuState == ShizukuBridge.State.DISCONNECTED
            item {
                ExpressiveStatusCard(
                    title = stringResource(R.string.shizuku_card_title),
                    subtitle = when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> stringResource(R.string.shizuku_desc_connected)
                        ShizukuBridge.State.DISCONNECTED -> stringResource(R.string.shizuku_desc_disconnected)
                        ShizukuBridge.State.NEEDS_PERMISSION -> stringResource(R.string.shizuku_desc_needs_permission)
                        ShizukuBridge.State.NOT_RUNNING -> stringResource(R.string.shizuku_desc_not_running)
                        ShizukuBridge.State.NOT_INSTALLED -> stringResource(R.string.shizuku_desc_not_installed)
                        ShizukuBridge.State.CONNECTING -> stringResource(R.string.shizuku_desc_connecting)
                        else -> shizukuError ?: stringResource(R.string.shizuku_status_disconnected)
                    },
                    icon = if (isShizukuConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                    statusText = when (shizukuState) {
                        ShizukuBridge.State.CONNECTED -> stringResource(R.string.shizuku_status_connected)
                        ShizukuBridge.State.DISCONNECTED -> stringResource(R.string.shizuku_status_disconnected_paused)
                        ShizukuBridge.State.CONNECTING -> stringResource(R.string.shizuku_status_connecting)
                        ShizukuBridge.State.NEEDS_PERMISSION -> stringResource(R.string.shizuku_status_needs_permission)
                        ShizukuBridge.State.NOT_RUNNING -> stringResource(R.string.shizuku_status_not_running)
                        ShizukuBridge.State.NOT_INSTALLED -> stringResource(R.string.shizuku_status_not_installed)
                        else -> stringResource(R.string.shizuku_status_disconnected)
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
                                    Text(stringResource(R.string.shizuku_btn_disconnect))
                                }
                            }
                            ShizukuBridge.State.DISCONNECTED -> {
                                Button(
                                    onClick = onConnectShizuku,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shizuku_btn_connect))
                                }
                            }
                            ShizukuBridge.State.NEEDS_PERMISSION -> {
                                Button(
                                    onClick = onRequestShizukuPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shizuku_btn_authorize))
                                }
                            }
                            ShizukuBridge.State.NOT_INSTALLED -> {
                                Button(
                                    onClick = onOpenShizukuApp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shizuku_btn_install))
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
                                        Text(stringResource(R.string.shizuku_btn_open))
                                    }
                                    OutlinedButton(
                                        onClick = onConnectShizuku,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.shizuku_btn_check_again))
                                    }
                                }
                            }
                            ShizukuBridge.State.CONNECTING -> null
                            else -> {
                                Button(
                                    onClick = onConnectShizuku,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shizuku_btn_retry))
                                }
                            }
                        }
                    }
                )
            }

            // 2. Conditional Warning Banners (tab-specific)
            if (selectedTab == 0) {
            val isNativeConflict = stockState.known && stockState.favoriteCallsActive
            if (isNativeConflict) {
                item {
                    StandardDiagnosticCard(
                        title = stringResource(R.string.onboarding_stock_card_title),
                        subtitle = stringResource(R.string.onboarding_stock_conflict_active_desc),
                        icon = Icons.Rounded.Warning,
                        statusText = stringResource(R.string.onboarding_stock_status_conflict),
                        isOk = false,
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
                                Text(stringResource(R.string.onboarding_stock_btn_open))
                            }
                        }
                    )
                }
            }

            val hasAllPhonePerms = isPhoneGranted && isCallLogGranted && isContactsGranted
            if (!hasAllPhonePerms) {
                item {
                    StandardDiagnosticCard(
                        title = stringResource(R.string.onboarding_perms_calls_title),
                        subtitle = stringResource(R.string.onboarding_perms_calls_needed_desc),
                        icon = Icons.Rounded.PermPhoneMsg,
                        statusText = stringResource(R.string.onboarding_perms_calls_status_needed),
                        isOk = false,
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
                                    Text(stringResource(R.string.onboarding_perms_calls_btn_grant))
                                }
                                OutlinedButton(
                                    onClick = onOpenAppSettings,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.onboarding_perms_calls_btn_app_info))
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }

            item {
                HomeCallsMasterCard(
                    enabled = isCallLightsEnabled,
                    onToggle = onToggleCallLights
                )
            }

            item {
                HomeCallsSettingsCard(
                    enabled = isCallLightsEnabled,
                    alpha = callAlpha,
                    scale = callScale,
                    isOtherContactsEnabled = isOtherContactsEnabled,
                    otherContactsColor = otherContactsColor,
                    otherContactsPattern = otherContactsPattern,
                    otherContactsFaceDownMode = otherContactsFaceDownMode,
                    onToggleOtherContacts = onToggleOtherContacts,
                    onEditOtherContacts = onEditOtherContacts,
                    isUnknownNumbersEnabled = isUnknownNumbersEnabled,
                    unknownNumbersColor = unknownNumbersColor,
                    unknownNumbersPattern = unknownNumbersPattern,
                    unknownNumbersFaceDownMode = unknownNumbersFaceDownMode,
                    onToggleUnknownNumbers = onToggleUnknownNumbers,
                    onEditUnknownNumbers = onEditUnknownNumbers,
                    callContactRules = callContactRules,
                    onToggleCallContactRule = onToggleCallContactRule,
                    onEditCallContactRule = onEditCallContactRule,
                    onDeleteCallContactRule = onDeleteCallContactRule,
                    onAddCallContact = onAddCallContact,
                    renderer = renderer
                )
            }
            }

            if (selectedTab == 1) {
            if (!isNotifAccessGranted || !isNotifListenerRunning) {
                item {
                    StandardDiagnosticCard(
                        title = stringResource(R.string.onboarding_perms_notif_title),
                        subtitle = if (!isNotifAccessGranted) {
                            stringResource(R.string.onboarding_perms_notif_needed_desc)
                        } else {
                            stringResource(R.string.onboarding_perms_notif_not_running_desc)
                        },
                        icon = Icons.Rounded.NotificationsActive,
                        statusText = if (!isNotifAccessGranted) {
                            stringResource(R.string.onboarding_perms_notif_status_needed)
                        } else {
                            stringResource(R.string.onboarding_perms_notif_status_not_running)
                        },
                        isOk = false,
                        bottomAction = {
                            Button(
                                onClick = onOpenNotifSettings,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.onboarding_perms_notif_btn_enable))
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }

            item {
                HomeNotifsMasterCard(
                    enabled = isNotifsEnabled,
                    onToggle = onToggleNotifs
                )
            }

            item {
                HomeNotifsSettingsCard(
                    enabled = isNotifsEnabled,
                    alpha = notifAlpha,
                    scale = notifScale,
                    isDefaultNotifEnabled = isDefaultNotifEnabled,
                    defaultNotifColor = defaultNotifColor,
                    defaultNotifPattern = defaultNotifPattern,
                    defaultNotifFaceDownMode = defaultNotifFaceDownMode,
                    onToggleDefaultNotif = onToggleDefaultNotif,
                    onEditDefaultNotif = onEditDefaultNotif,
                    messageContactRules = messageContactRules,
                    onToggleMessageRule = onToggleMessageRule,
                    onEditMessageRule = onEditMessageRule,
                    onDeleteMessageRule = onDeleteMessageRule,
                    onAddMessageContact = onAddMessageContact,
                    appRules = appRules,
                    onToggleAppRule = onToggleAppRule,
                    onEditAppRule = onEditAppRule,
                    onDeleteAppRule = onDeleteAppRule,
                    onAddApp = onAddApp,
                    notifDurationSec = notifDurationSec,
                    onChangeDuration = onChangeDuration,
                    isCycleNotifications = isCycleNotifications,
                    onToggleCycleNotifications = onToggleCycleNotifications,
                    renderer = renderer
                )
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
    faceDownMode: com.mwilky.hilight.plus.FaceDownMode = com.mwilky.hilight.plus.FaceDownMode.INHERIT,
    onDelete: (() -> Unit)? = null,
    controlsEnabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HiLightTheme.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HiLightTheme.RuleRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedRingBadge(
                    pattern = pattern,
                    color = color,
                    renderer = renderer,
                    size = 36.dp,
                    animate = controlsEnabled
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.rule_pattern_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(pattern.titleRes),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (faceDownMode != com.mwilky.hilight.plus.FaceDownMode.INHERIT) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.rule_trigger_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (faceDownMode == com.mwilky.hilight.plus.FaceDownMode.ONLY_FACE_DOWN) stringResource(R.string.rule_trigger_face_down) else stringResource(R.string.rule_trigger_always),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, enabled = controlsEnabled) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rule_edit_cd))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, enabled = controlsEnabled) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.rule_delete_cd), tint = MaterialTheme.colorScheme.error)
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    enabled = controlsEnabled
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
    size: Dp,
    animate: Boolean = true
) {
    var miniFrames by remember { mutableStateOf(IntArray(8) { 0x00000000 }) }
    val shouldAnimate = animate && pattern != PatternMode.OFF && pattern != PatternMode.SOLID

    LaunchedEffect(pattern, color, shouldAnimate) {
        val speed = pattern.speedMs()
        fun frame(elapsed: Long) = renderer.renderFrame(
            pattern = pattern.id,
            colorLong = color,
            brightness = 1.0f,
            speedMs = speed,
            elapsedTimeMs = elapsed,
            ledCount = 8
        )
        if (!shouldAnimate) {
            miniFrames = frame(0L)
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (isActive) {
            miniFrames = frame(System.currentTimeMillis() - startMs)
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

data class RuleEditorResult(
    val pattern: PatternMode,
    val color: Long,
    val faceDown: FaceDownMode,
    val autoColor: Boolean,
    val dndMode: DndMode,
    val quietHoursMode: QuietHoursMode,
    val quietHoursStartMinutes: Int,
    val quietHoursEndMinutes: Int
)

/**
 * Full-screen Look / When editor for a call or notification rule.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomRuleDialog(
    title: String,
    initialColor: Long,
    initialPattern: PatternMode,
    renderer: PatternRenderer,
    onDismiss: () -> Unit,
    onSave: (RuleEditorResult) -> Unit,
    initialFaceDown: FaceDownMode = FaceDownMode.INHERIT,
    initialDnd: DndMode = DndMode.INHERIT,
    initialQuietHours: QuietHoursMode = QuietHoursMode.INHERIT,
    initialQuietStart: Int = 22 * 60,
    initialQuietEnd: Int = 7 * 60,
    showAutoColorToggle: Boolean = false,
    initialAutoColor: Boolean = true,
    autoExtractedColor: Long? = null
) {
    var isAutoColor by remember(initialAutoColor) { mutableStateOf(initialAutoColor) }
    var selectedColor by remember(initialColor) {
        mutableLongStateOf(
            if (showAutoColorToggle && initialAutoColor && autoExtractedColor != null) autoExtractedColor else initialColor
        )
    }
    var selectedPattern by remember(initialPattern) { mutableStateOf(initialPattern) }
    var selectedFaceDown by remember(initialFaceDown) { mutableStateOf(initialFaceDown) }
    var selectedDnd by remember(initialDnd) { mutableStateOf(initialDnd) }
    var selectedQuietHours by remember(initialQuietHours) { mutableStateOf(initialQuietHours) }
    var quietStartMinutes by remember(initialQuietStart) { mutableIntStateOf(initialQuietStart) }
    var quietEndMinutes by remember(initialQuietEnd) { mutableIntStateOf(initialQuietEnd) }
    var editingQuietStart by remember { mutableStateOf(false) }
    var editingQuietEnd by remember { mutableStateOf(false) }
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
        val speed = selectedPattern.speedMs()
        fun frame(elapsed: Long) = renderer.renderFrame(
            pattern = selectedPattern.id,
            colorLong = selectedColor,
            brightness = 1.0f,
            speedMs = speed,
            elapsedTimeMs = elapsed,
            ledCount = 8
        )
        if (selectedPattern == PatternMode.OFF || selectedPattern == PatternMode.SOLID) {
            dialogPreviewFrames = frame(0L)
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (isActive) {
            dialogPreviewFrames = frame(System.currentTimeMillis() - startMs)
            delay(33)
        }
    }

    val isColorEnabled = selectedPattern != PatternMode.RAINBOW

    val patterns = PatternMode.entries.filter { it != PatternMode.OFF }

    fun save() {
        onSave(
            RuleEditorResult(
                pattern = selectedPattern,
                color = selectedColor,
                faceDown = selectedFaceDown,
                autoColor = isAutoColor,
                dndMode = selectedDnd,
                quietHoursMode = selectedQuietHours,
                quietHoursStartMinutes = quietStartMinutes,
                quietHoursEndMinutes = quietEndMinutes
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dialog_btn_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = { save() }) {
                            Text(stringResource(R.string.dialog_btn_save))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(148.dp),
                        shape = HiLightTheme.DialogCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            DiffusedRingPreview(
                                frames = dialogPreviewFrames,
                                size = 112.dp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_pattern_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        patterns.forEach { p ->
                            FilterChip(
                                selected = selectedPattern == p,
                                onClick = { selectedPattern = p },
                                label = { Text(stringResource(p.titleRes)) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (showAutoColorToggle) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = HiLightTheme.DialogCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dialog_auto_color_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.dialog_auto_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoColor,
                                onCheckedChange = { auto ->
                                    isAutoColor = auto
                                    if (auto && autoExtractedColor != null) {
                                        selectedColor = autoExtractedColor
                                    }
                                }
                            )
                        }
                    }
                }

                val canPickManualColor = isColorEnabled && (!showAutoColorToggle || !isAutoColor)
                val manualColorAlpha by animateFloatAsState(
                    targetValue = if (canPickManualColor) 1.0f else 0.35f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "manualColorAlpha"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(manualColorAlpha),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (showAutoColorToggle && isAutoColor) stringResource(R.string.dialog_color_auto_label) else stringResource(R.string.dialog_color_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                       )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        palette.forEach { c ->
                            val isSelected = selectedColor == c && canPickManualColor
                            Box(
                                modifier = Modifier
                                    .size(HiLightTheme.PaletteSwatch)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = canPickManualColor) { selectedColor = c }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_orientation_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            when (selectedFaceDown) {
                                FaceDownMode.INHERIT -> R.string.dialog_orientation_desc_default
                                FaceDownMode.ALWAYS -> R.string.dialog_orientation_desc_always
                                FaceDownMode.ONLY_FACE_DOWN -> R.string.dialog_orientation_desc_face_down
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = FaceDownMode.entries
                        modes.forEachIndexed { index, mode ->
                            val isSelected = selectedFaceDown == mode
                            SegmentedButton(
                                selected = isSelected,
                                onClick = { selectedFaceDown = mode },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                label = {
                                    Text(
                                        text = when (mode) {
                                            FaceDownMode.INHERIT -> stringResource(R.string.dialog_orientation_default)
                                            FaceDownMode.ALWAYS -> stringResource(R.string.dialog_orientation_always)
                                            FaceDownMode.ONLY_FACE_DOWN -> stringResource(R.string.dialog_orientation_face_down)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }

                ConditionModeRow(
                    title = stringResource(R.string.dialog_dnd_title),
                    description = when (selectedDnd) {
                        DndMode.INHERIT -> stringResource(R.string.dialog_dnd_desc_default)
                        DndMode.ALWAYS -> stringResource(R.string.dialog_dnd_desc_always)
                        DndMode.SKIP -> ""
                    },
                    options = DndMode.entries.map { mode ->
                        mode to when (mode) {
                            DndMode.INHERIT -> stringResource(R.string.dialog_mode_default)
                            DndMode.ALWAYS -> stringResource(R.string.dialog_mode_always)
                            DndMode.SKIP -> stringResource(R.string.dialog_mode_skip)
                        }
                    },
                    selected = selectedDnd,
                    onSelect = { selectedDnd = it }
                )
                ConditionModeRow(
                    title = stringResource(R.string.dialog_quiet_hours_title),
                    description = when (selectedQuietHours) {
                        QuietHoursMode.INHERIT -> stringResource(R.string.dialog_quiet_hours_desc_default)
                        QuietHoursMode.ALWAYS -> stringResource(R.string.dialog_quiet_hours_desc_always)
                        QuietHoursMode.SKIP -> ""
                    },
                    options = QuietHoursMode.entries.map { mode ->
                        mode to when (mode) {
                            QuietHoursMode.INHERIT -> stringResource(R.string.dialog_mode_default)
                            QuietHoursMode.ALWAYS -> stringResource(R.string.dialog_mode_always)
                            QuietHoursMode.SKIP -> stringResource(R.string.dialog_mode_skip)
                        }
                    },
                    selected = selectedQuietHours,
                    onSelect = { selectedQuietHours = it }
                )
                if (selectedQuietHours == QuietHoursMode.SKIP) {
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingQuietStart = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${stringResource(R.string.conditions_quiet_hours_start)} ${formatClockMinutes(context, quietStartMinutes)}")
                        }
                        OutlinedButton(
                            onClick = { editingQuietEnd = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${stringResource(R.string.conditions_quiet_hours_end)} ${formatClockMinutes(context, quietEndMinutes)}")
                        }
                    }
                }
            }
        }
    }
    if (editingQuietStart) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_start),
            initialMinutes = quietStartMinutes,
            onDismiss = { editingQuietStart = false },
            onConfirm = { minutes ->
                quietStartMinutes = minutes
                editingQuietStart = false
            }
        )
    }
    if (editingQuietEnd) {
        QuietHoursTimePickerDialog(
            title = stringResource(R.string.conditions_quiet_hours_end),
            initialMinutes = quietEndMinutes,
            onDismiss = { editingQuietEnd = false },
            onConfirm = { minutes ->
                quietEndMinutes = minutes
                editingQuietEnd = false
            }
        )
    }
}

@Composable
private fun <T> ConditionModeRow(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                val isSelected = selected == mode
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    }
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
        title = { Text(stringResource(R.string.dialog_app_picker_title)) },
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
                    placeholder = { Text(stringResource(R.string.dialog_app_picker_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    shape = HiLightTheme.DialogCardShape
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
                            text = stringResource(R.string.dialog_app_picker_no_apps),
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
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(context.packageName)
}

fun isNotificationListenerRunning(): Boolean = NotificationTrigger.isListenerConnected

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
            isNotifListenerRunning = true,
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
