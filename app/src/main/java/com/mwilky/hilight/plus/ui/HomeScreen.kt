@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.BatterySettings
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.DEFAULT_SETTINGS_SNAPSHOT
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.NativeHiLightDetector
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.R
import com.mwilky.hilight.plus.SettingsSnapshot
import com.mwilky.hilight.plus.ShizukuBridge
import com.mwilky.hilight.plus.StockHiLightState
import com.mwilky.hilight.plus.core.PatternRenderer
import com.mwilky.hilight.plus.ui.diagnostics.PermissionState
import com.mwilky.hilight.plus.ui.diagnostics.rememberCallPermissionLauncher
import com.mwilky.hilight.plus.ui.diagnostics.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Home screen: Calls and Notifications pages behind a connected toggle group + pager.
 * Each page has a hero toggle, diagnostics shown only when something needs attention,
 * and a flat segmented list of rules.
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState()
    val requestCallPermissions = rememberCallPermissionLauncher(permissionState)

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
        permissionState = permissionState,
        onRequestPhonePerms = requestCallPermissions,
        onOpenAppSettings = { openAppSettings() },
        onOpenNotifSettings = { openNotifSettings() },
        state = state,
        onToggleCallLights = viewModel::setCallLightsEnabled,
        onToggleOtherContacts = viewModel::setOtherContactsEnabled,
        onEditOtherContacts = { isConfiguringOtherContacts = true },
        onToggleUnknownNumbers = viewModel::setUnknownNumbersEnabled,
        onEditUnknownNumbers = { isConfiguringUnknownNumbers = true },
        onToggleCallContactRule = { rule, isEnabled ->
            viewModel.saveContactRule(rule.copy(isEnabled = isEnabled))
        },
        onEditCallContactRule = { rule -> callRuleBeingEdited = rule },
        onDeleteCallContactRule = viewModel::deleteContactRule,
        onAddCallContact = { callContactPickerLauncher.launch(null) },
        onToggleNotifs = viewModel::setNotificationsEnabled,
        onChangeDuration = viewModel::setNotificationDurationSeconds,
        onToggleCycleNotifications = viewModel::setCycleNotifications,
        onToggleDefaultNotif = viewModel::setDefaultNotifEnabled,
        onEditDefaultNotif = { isConfiguringDefaultNotif = true },
        onToggleMessageRule = { rule, isEnabled ->
            viewModel.saveMessageContactRule(rule.copy(isEnabled = isEnabled))
        },
        onEditMessageRule = { rule -> msgRuleBeingEdited = rule },
        onDeleteMessageRule = viewModel::deleteMessageContactRule,
        onAddMessageContact = { msgContactPickerLauncher.launch(null) },
        onToggleAppRule = { rule, isEnabled ->
            viewModel.saveAppRule(rule.copy(isEnabled = isEnabled))
        },
        onEditAppRule = { rule -> appRuleBeingEdited = rule },
        onDeleteAppRule = viewModel::deleteAppRule,
        onAddApp = { isPickingApp = true },
        onBatteryChange = viewModel::setBattery,
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
            initialQuietStart = rule.quietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = rule.quietHoursEndMinutes ?: state.quietHoursEndMinutes,
            renderer = renderer,
            controller = controller,
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
            initialColor = state.otherContactsColor,
            initialPattern = state.otherContactsPattern,
            initialFaceDown = state.otherContactsFaceDownMode,
            initialDnd = state.otherContactsDndMode,
            initialQuietHours = state.otherContactsQuietHoursMode,
            initialQuietStart = state.otherContactsQuietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = state.otherContactsQuietHoursEndMinutes ?: state.quietHoursEndMinutes,
            renderer = renderer,
            controller = controller,
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
            initialColor = state.unknownNumbersColor,
            initialPattern = state.unknownNumbersPattern,
            initialFaceDown = state.unknownNumbersFaceDownMode,
            initialDnd = state.unknownNumbersDndMode,
            initialQuietHours = state.unknownNumbersQuietHoursMode,
            initialQuietStart = state.unknownNumbersQuietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = state.unknownNumbersQuietHoursEndMinutes ?: state.quietHoursEndMinutes,
            renderer = renderer,
            controller = controller,
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
            alreadyAdded = state.appRules.map { it.packageName }.toSet(),
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
            initialQuietStart = rule.quietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = rule.quietHoursEndMinutes ?: state.quietHoursEndMinutes,
            renderer = renderer,
            controller = controller,
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
            initialQuietStart = rule.quietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = rule.quietHoursEndMinutes ?: state.quietHoursEndMinutes,
            showAutoColorToggle = true,
            initialAutoColor = rule.isAutoColor,
            autoExtractedColor = autoColor,
            renderer = renderer,
            controller = controller,
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
            initialColor = state.defaultNotifColor,
            initialPattern = state.defaultNotifPattern,
            initialFaceDown = state.defaultNotifFaceDownMode,
            initialDnd = state.defaultNotifDndMode,
            initialQuietHours = state.defaultNotifQuietHoursMode,
            initialQuietStart = state.defaultNotifQuietHoursStartMinutes ?: state.quietHoursStartMinutes,
            initialQuietEnd = state.defaultNotifQuietHoursEndMinutes ?: state.quietHoursEndMinutes,
            showAutoColorToggle = true,
            initialAutoColor = state.isDefaultNotifAutoColor,
            autoExtractedColor = null,
            renderer = renderer,
            controller = controller,
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
    permissionState: PermissionState,
    onRequestPhonePerms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    state: SettingsSnapshot,
    // Calls
    onToggleCallLights: (Boolean) -> Unit,
    onToggleOtherContacts: (Boolean) -> Unit,
    onEditOtherContacts: () -> Unit,
    onToggleUnknownNumbers: (Boolean) -> Unit,
    onEditUnknownNumbers: () -> Unit,
    onToggleCallContactRule: (ContactRule, Boolean) -> Unit,
    onEditCallContactRule: (ContactRule) -> Unit,
    onDeleteCallContactRule: (String) -> Unit,
    onAddCallContact: () -> Unit,
    // Notifications
    onToggleNotifs: (Boolean) -> Unit,
    onChangeDuration: (Int) -> Unit,
    onToggleCycleNotifications: (Boolean) -> Unit = {},
    onToggleDefaultNotif: (Boolean) -> Unit,
    onEditDefaultNotif: () -> Unit,
    onToggleMessageRule: (MessageContactRule, Boolean) -> Unit,
    onEditMessageRule: (MessageContactRule) -> Unit,
    onDeleteMessageRule: (String) -> Unit,
    onAddMessageContact: () -> Unit,
    onToggleAppRule: (AppNotificationRule, Boolean) -> Unit,
    onEditAppRule: (AppNotificationRule) -> Unit,
    onDeleteAppRule: (String) -> Unit,
    onAddApp: () -> Unit,
    // Battery
    onBatteryChange: (BatterySettings) -> Unit,
    renderer: PatternRenderer
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HomePageToggle(
                selectedIndex = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> HomeCallsPage(
                        shizukuState = shizukuState,
                        shizukuError = shizukuError,
                        onDisconnectShizuku = onDisconnectShizuku,
                        onConnectShizuku = onConnectShizuku,
                        onRequestShizukuPermission = onRequestShizukuPermission,
                        onOpenShizukuApp = onOpenShizukuApp,
                        stockState = stockState,
                        onOpenStockSettings = onOpenStockSettings,
                        permissionState = permissionState,
                        onRequestPhonePerms = onRequestPhonePerms,
                        onOpenAppSettings = onOpenAppSettings,
                        state = state,
                        onToggleCallLights = onToggleCallLights,
                        onToggleOtherContacts = onToggleOtherContacts,
                        onEditOtherContacts = onEditOtherContacts,
                        onToggleUnknownNumbers = onToggleUnknownNumbers,
                        onEditUnknownNumbers = onEditUnknownNumbers,
                        onToggleCallContactRule = onToggleCallContactRule,
                        onEditCallContactRule = onEditCallContactRule,
                        onDeleteCallContactRule = onDeleteCallContactRule,
                        onAddCallContact = onAddCallContact,
                        renderer = renderer
                    )
                    1 -> HomeNotifsPage(
                        shizukuState = shizukuState,
                        shizukuError = shizukuError,
                        onDisconnectShizuku = onDisconnectShizuku,
                        onConnectShizuku = onConnectShizuku,
                        onRequestShizukuPermission = onRequestShizukuPermission,
                        onOpenShizukuApp = onOpenShizukuApp,
                        permissionState = permissionState,
                        onOpenNotifSettings = onOpenNotifSettings,
                        state = state,
                        onToggleNotifs = onToggleNotifs,
                        onToggleDefaultNotif = onToggleDefaultNotif,
                        onEditDefaultNotif = onEditDefaultNotif,
                        onToggleMessageRule = onToggleMessageRule,
                        onEditMessageRule = onEditMessageRule,
                        onDeleteMessageRule = onDeleteMessageRule,
                        onAddMessageContact = onAddMessageContact,
                        onToggleAppRule = onToggleAppRule,
                        onEditAppRule = onEditAppRule,
                        onDeleteAppRule = onDeleteAppRule,
                        onAddApp = onAddApp,
                        onChangeDuration = onChangeDuration,
                        onToggleCycleNotifications = onToggleCycleNotifications,
                        renderer = renderer
                    )
                    else -> HomeBatteryPage(
                        shizukuState = shizukuState,
                        shizukuError = shizukuError,
                        onDisconnectShizuku = onDisconnectShizuku,
                        onConnectShizuku = onConnectShizuku,
                        onRequestShizukuPermission = onRequestShizukuPermission,
                        onOpenShizukuApp = onOpenShizukuApp,
                        battery = state.battery,
                        onBatteryChange = onBatteryChange,
                        renderer = renderer
                    )
                }
            }
        }
    }
}

/**
 * Connected three-button toggle group that mirrors the pager position.
 */
@Composable
private fun HomePageToggle(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    // Only the selected tab carries its label; the others shrink to their icon. That leaves the
    // selected tab room for a word as long as "Notifications", which never fitted when all three
    // showed icon and label at a third of the screen each.
    val tabs = listOf(
        Icons.Rounded.Call to R.string.home_tab_calls,
        Icons.Rounded.Notifications to R.string.home_tab_notifications,
        Icons.Rounded.BatteryChargingFull to R.string.home_tab_battery
    )
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, (icon, labelRes) ->
            val selected = selectedIndex == index
            val label = stringResource(labelRes)
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(index) },
                // Circular while it's only an icon, rounded rectangle once it carries a label.
                shapes = ToggleButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(14.dp),
                    checkedShape = RoundedCornerShape(18.dp)
                ),
                // Surface tones rather than primary: the selected tab is a step darker than the
                // unselected ones instead of a colour change.
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    checkedContentColor = MaterialTheme.colorScheme.onSurface
                ),
                // 24dp icon plus 12dp either side makes an exact circle at this height. No width
                // is set, so a selected tab is only as wide as its own label needs.
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .height(TabHeight)
                    .semantics { role = Role.RadioButton }
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(effects) + expandHorizontally(spatial),
                    exit = fadeOut(effects) + shrinkHorizontally(spatial)
                ) {
                    Row {
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val TabHeight = 48.dp

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
                        LoadingIndicator()
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

@Preview(name = "Home Screen - Turned Off", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun HomeScreenPreviewOff() {
    HomeScreenPreviewContent(hasCallRules = true, callLightsEnabled = false)
}

@Composable
fun HomeScreenPreviewContent(
    hasCallRules: Boolean = false,
    hasMsgRules: Boolean = true,
    hasAppRules: Boolean = true,
    callLightsEnabled: Boolean = true
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
            permissionState = PermissionState(
                context = LocalContext.current,
                isPhoneGranted = true,
                isCallLogGranted = true,
                isContactsGranted = true,
                isNotifAccessGranted = true,
                isNotifListenerRunning = true
            ),
            onRequestPhonePerms = {},
            onOpenAppSettings = {},
            onOpenNotifSettings = {},
            state = DEFAULT_SETTINGS_SNAPSHOT.copy(
                isCallLightsEnabled = callLightsEnabled,
                contactRules = mockCallContacts,
                messageContactRules = mockMsgContacts,
                appRules = mockApps
            ),
            onToggleCallLights = {},
            onToggleOtherContacts = {},
            onEditOtherContacts = {},
            onToggleUnknownNumbers = {},
            onEditUnknownNumbers = {},
            onToggleCallContactRule = { _, _ -> },
            onEditCallContactRule = {},
            onDeleteCallContactRule = {},
            onAddCallContact = {},
            onToggleNotifs = {},
            onChangeDuration = {},
            onToggleDefaultNotif = {},
            onEditDefaultNotif = {},
            onToggleMessageRule = { _, _ -> },
            onEditMessageRule = {},
            onDeleteMessageRule = {},
            onAddMessageContact = {},
            onToggleAppRule = { _, _ -> },
            onEditAppRule = {},
            onDeleteAppRule = {},
            onAddApp = {},
            onBatteryChange = {},
            renderer = PatternRenderer()
        )
    }
}
