package com.mwilky.hilight.plus

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import com.mwilky.hilight.plus.core.HiLightDaemonService
import com.mwilky.hilight.plus.core.IHiLightService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * High-level bridge to Shizuku on Android 11+.
 * Manages connection, permission requests, and typed IPC with [HiLightDaemonService].
 */
class ShizukuBridge private constructor(private val app: Application) {

    enum class State {
        NOT_INSTALLED,
        NOT_RUNNING,
        NEEDS_PERMISSION,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    private var service: IHiLightService? = null
    private var lastError: String? = null
    private var manuallyDisconnected = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectTimeoutJob: Job? = null
    private var reconnectJob: Job? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, HiLightDaemonService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_daemon")
        .debuggable(BuildConfig.DEBUG)
        .version(12)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.e("HiLightPlus", "onServiceConnected: binder=$binder")
            connectTimeoutJob?.cancel()
            if (manuallyDisconnected) {
                service = null
                return
            }
            if (binder == null || !binder.pingBinder()) {
                service = null
                _state.value = State.FAILED
                lastError = "Received null/dead binder from Shizuku"
                Log.e("HiLightPlus", "Binder ping failed")
                return
            }
            val bound: IHiLightService = IHiLightService.Stub.asInterface(binder)
            val count = runCatching { bound.getLedCount() }.getOrNull() ?: 0
            if (count <= 0) {
                service = null
                _state.value = State.FAILED
                lastError = "Lights backend unavailable"
                Log.e("HiLightPlus", "Binder connected but no LEDs")
                return
            }
            service = bound
            _state.value = State.CONNECTED
            lastError = null
            Log.i("HiLightPlus", "Connected to HiLightDaemonService! ($count LEDs)")
            // Fresh daemon process, or a reconnect after one died: replay whatever battery
            // config/state was last set so it can't be lost to a connection that wasn't ready yet.
            lastBatteryConfig?.let { sendBatteryConfig(it) }
            lastBatteryState?.let { sendBatteryState(it) }
            lastEntitled?.let { sendEntitled(it) }
            onAvailabilityChanged?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e("HiLightPlus", "onServiceDisconnected")
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e("HiLightPlus", "onBindingDied")
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e("HiLightPlus", "onNullBinding")
            service = null
            _state.value = State.FAILED
            lastError = "UserService returned null binding"
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.e("HiLightPlus", "Shizuku permission granted by user")
                manuallyDisconnected = false
                refresh()
            } else {
                Log.e("HiLightPlus", "Shizuku permission denied by user")
                _state.value = State.NEEDS_PERMISSION
            }
        }
    }

    var onAvailabilityChanged: (() -> Unit)? = null

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky {
            Log.e("HiLightPlus", "Shizuku binder received (sticky)")
            if (!manuallyDisconnected) {
                refresh()
            }
        }
        Shizuku.addBinderDeadListener {
            Log.e("HiLightPlus", "Shizuku binder died")
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }
        refresh()
    }

    fun isInstalled(): Boolean = runCatching {
        app.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    }.getOrDefault(false)

    fun refresh() {
        if (manuallyDisconnected) return
        if (_state.value == State.CONNECTED) {
            if (isBinderAlive()) return
            Log.e("HiLightPlus", "CONNECTED with dead binder -> clearing for rebind")
            service = null
            _state.value = State.NOT_RUNNING
        }
        if (!Shizuku.pingBinder()) {
            if (!isInstalled()) {
                _state.value = State.NOT_INSTALLED
                Log.e("HiLightPlus", "Shizuku not installed")
                return
            }
            _state.value = State.NOT_RUNNING
            Log.e("HiLightPlus", "Shizuku daemon not running")
            return
        }
        if (Shizuku.isPreV11()) {
            _state.value = State.FAILED
            lastError = "Shizuku is too old; v11 or newer is required"
            Log.e("HiLightPlus", "Shizuku version too old")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.NEEDS_PERMISSION
            Log.e("HiLightPlus", "Shizuku needs permission")
            return
        }
        Log.e("HiLightPlus", "Shizuku ping OK and permission GRANTED -> calling bind()")
        bind()
    }

    fun requestPermission() {
        manuallyDisconnected = false
        if (!Shizuku.pingBinder()) {
            refresh()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bind()
        } else {
            Shizuku.requestPermission(PERMISSION_REQUEST)
        }
    }

    fun connectManually() {
        Log.e("HiLightPlus", "=== connectManually() called ===")
        manuallyDisconnected = false
        refresh()
    }

    private fun bind() {
        if (_state.value == State.CONNECTING) {
            Log.e("HiLightPlus", "bind() skipped, current state is CONNECTING")
            return
        }
        if (_state.value == State.CONNECTED && isBinderAlive()) {
            Log.e("HiLightPlus", "bind() skipped, already connected")
            return
        }
        _state.value = State.CONNECTING
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value == State.CONNECTING) {
                service = null
                _state.value = State.FAILED
                lastError = "Connection timed out"
                Log.e("HiLightPlus", "bind() timed out")
            }
        }
        Log.e("HiLightPlus", "Calling Shizuku.bindUserService()...")
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                connectTimeoutJob?.cancel()
                _state.value = State.FAILED
                lastError = it.message
                Log.e("HiLightPlus", "bindUserService failed: ${it.message}", it)
            }
    }

    fun unbind() {
        Log.e("HiLightPlus", "=== unbind() called by user ===")
        connectTimeoutJob?.cancel()
        manuallyDisconnected = true
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
        _state.value = State.DISCONNECTED
        onAvailabilityChanged?.invoke()
    }

    fun isConnected(): Boolean = _state.value == State.CONNECTED && isBinderAlive()

    private fun isBinderAlive(): Boolean = service?.asBinder()?.pingBinder() == true

    private fun markDead(op: String, error: Throwable) {
        Log.e("HiLightPlus", "$op failed", error)
        if (manuallyDisconnected) return
        if (error is DeadObjectException || error.cause is DeadObjectException || !isBinderAlive()) {
            service = null
            _state.value = State.NOT_RUNNING
            lastError = error.message
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }
    }

    /**
     * Rebinds after the daemon goes away. Without this nothing reconnects on its own, and since
     * the daemon keeps rendering whatever it last knew, a dropped binder could strand the LEDs
     * lit with every command from the app silently going nowhere.
     */
    private fun scheduleReconnect() {
        if (manuallyDisconnected || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            repeat(RECONNECT_ATTEMPTS) {
                delay(RECONNECT_DELAY_MS)
                if (manuallyDisconnected || _state.value == State.CONNECTED) return@launch
                Log.i("HiLightPlus", "Attempting to reconnect to the daemon")
                refresh()
            }
        }
    }

    private fun runRemote(op: String, block: (IHiLightService) -> Unit) {
        val s = service ?: return
        runCatching { block(s) }.onFailure { markDead(op, it) }
    }

    fun errorText(): String? = lastError

    // --- Typed Control Methods ---

    fun triggerAlert(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int = -1,
        quietEndMinutes: Int = -1
    ) {
        Log.e("HiLightPlus", "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
        runRemote("triggerAlert") {
            it.triggerAlert(
                pattern,
                color,
                brightness,
                speedMs,
                durationMs,
                requiresFaceDown,
                dndMode.id,
                quietHoursMode.id,
                quietStartMinutes,
                quietEndMinutes
            )
        }
    }

    fun postAlert(
        key: String,
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int = -1,
        quietEndMinutes: Int = -1
    ) {
        Log.e("HiLightPlus", "postAlert [key=$key]: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
        runRemote("postAlert") {
            it.postAlert(
                key,
                pattern,
                color,
                brightness,
                speedMs,
                durationMs,
                requiresFaceDown,
                dndMode.id,
                quietHoursMode.id,
                quietStartMinutes,
                quietEndMinutes
            )
        }
    }

    fun startIncomingCall(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.ALWAYS,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietStartMinutes: Int = -1,
        quietEndMinutes: Int = -1
    ) {
        runRemote("startIncomingCall") {
            it.startIncomingCall(
                pattern,
                color,
                brightness,
                speedMs,
                requiresFaceDown,
                dndMode.id,
                quietHoursMode.id,
                quietStartMinutes,
                quietEndMinutes
            )
        }
    }

    fun setDeviceFaceDown(faceDown: Boolean) {
        runRemote("setDeviceFaceDown") { it.setDeviceFaceDown(faceDown) }
    }

    fun setDndActive(dndActive: Boolean) {
        runRemote("setDndActive") { it.setDndActive(dndActive) }
    }

    fun setDndSuppressEnabled(enabled: Boolean) {
        runRemote("setDndSuppressEnabled") { it.setDndSuppressEnabled(enabled) }
    }

    fun setQuietHours(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        runRemote("setQuietHours") { it.setQuietHours(enabled, startMinutes, endMinutes) }
    }

    fun setSplitRing(enabled: Boolean) {
        runRemote("setSplitRing") { it.setSplitRing(enabled) }
    }

    fun stopIncomingCall() {
        runRemote("stopIncomingCall") { it.stopIncomingCall() }
    }

    fun testAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        Log.e("HiLightPlus", "testAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
        runRemote("testAlert") { it.testAlert(pattern, color, brightness, speedMs, durationMs) }
    }

    fun cancelTestAlert() {
        runRemote("cancelTestAlert") { it.cancelTestAlert() }
    }

    /**
     * Battery config/state setters can be called before the daemon has finished (re)connecting
     * — there's no notification-style event to naturally retry on. So the latest values are
     * cached here and replayed automatically as soon as [onServiceConnected] fires, instead of
     * being silently dropped by [runRemote] until something else happens to resend them.
     */
    private var lastBatteryConfig: CachedBatteryConfig? = null
    private var lastBatteryState: CachedBatteryState? = null

    private data class CachedBatteryConfig(
        val enabled: Boolean,
        val chargingPattern: BatteryPattern,
        val lowPattern: LowBatteryPattern,
        val autoColor: Boolean,
        val color: Long,
        val showCharging: Boolean,
        val lowWarningEnabled: Boolean,
        val lowThresholdPercent: Int,
        val fullTimeoutMinutes: Int?,
        val overridesNotifications: Boolean,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartMinutes: Int?,
        val quietEndMinutes: Int?
    )

    private data class CachedBatteryState(val levelPercent: Int, val charging: Boolean, val full: Boolean)

    fun setBatteryConfig(
        enabled: Boolean,
        chargingPattern: BatteryPattern,
        lowPattern: LowBatteryPattern,
        autoColor: Boolean,
        color: Long,
        showCharging: Boolean,
        lowWarningEnabled: Boolean,
        lowThresholdPercent: Int,
        fullTimeoutMinutes: Int?,
        overridesNotifications: Boolean,
        requiresFaceDown: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietStartMinutes: Int?,
        quietEndMinutes: Int?
    ) {
        val config = CachedBatteryConfig(
            enabled, chargingPattern, lowPattern, autoColor, color, showCharging,
            lowWarningEnabled, lowThresholdPercent, fullTimeoutMinutes,
            overridesNotifications, requiresFaceDown, dndMode, quietHoursMode,
            quietStartMinutes, quietEndMinutes
        )
        lastBatteryConfig = config
        sendBatteryConfig(config)
    }

    private fun sendBatteryConfig(config: CachedBatteryConfig) {
        runRemote("setBatteryConfig") {
            it.setBatteryConfig(
                config.enabled,
                config.chargingPattern.id,
                config.lowPattern.id,
                config.autoColor,
                config.color,
                config.showCharging,
                config.lowWarningEnabled,
                config.lowThresholdPercent,
                config.fullTimeoutMinutes ?: -1,
                config.overridesNotifications,
                config.requiresFaceDown,
                config.dndMode.id,
                // .id (not .name): QuietHoursMode.fromId on the daemon side matches against the
                // lowercase wire id, same as every other mode sent over this AIDL surface.
                config.quietHoursMode.id,
                config.quietStartMinutes ?: -1,
                config.quietEndMinutes ?: -1
            )
        }
    }

    fun setBatteryState(levelPercent: Int, charging: Boolean, full: Boolean) {
        val state = CachedBatteryState(levelPercent, charging, full)
        lastBatteryState = state
        sendBatteryState(state)
    }

    private fun sendBatteryState(state: CachedBatteryState) {
        runRemote("setBatteryState") { it.setBatteryState(state.levelPercent, state.charging, state.full) }
    }

    fun removeAlert(key: String) {
        Log.e("HiLightPlus", "removeAlert [key=$key]")
        runRemote("removeAlert") { it.removeAlert(key) }
    }

    fun clearAlert() {
        Log.e("HiLightPlus", "clearAlert called")
        runRemote("clearAlert") { it.clearAlert() }
    }

    fun getSecureString(key: String): String? {
        val s = service ?: return null
        return runCatching { s.getSecureString(key) }
            .onFailure { markDead("getSecureString", it) }
            .getOrNull()
    }

    fun getGlobalString(key: String): String? {
        val s = service ?: return null
        return runCatching { s.getGlobalString(key) }
            .onFailure { markDead("getGlobalString", it) }
            .getOrNull()
    }

    fun putGlobalString(key: String, value: String): Boolean {
        val s = service ?: return false
        return runCatching { s.putGlobalString(key, value) }
            .onFailure { markDead("putGlobalString", it) }
            .getOrDefault(false)
    }

    /** Cached and replayed on (re)connect, like the battery config: the daemon boots entitled. */
    private var lastEntitled: Boolean? = null

    fun setEntitled(entitled: Boolean) {
        lastEntitled = entitled
        sendEntitled(entitled)
    }

    private fun sendEntitled(entitled: Boolean) {
        runRemote("setEntitled") { it.setEntitled(entitled) }
    }

    fun turnOff() {
        Log.e("HiLightPlus", "turnOff called")
        runRemote("turnOff") { it.turnOff() }
    }

    fun openShizukuApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        if (launch != null) {
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launch)
        } else {
            val uri = Uri.parse("https://shizuku.rikka.app/")
            val browser = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(browser) }
        }
    }

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 4001
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val RECONNECT_ATTEMPTS = 5

        @Volatile
        private var instance: ShizukuBridge? = null

        fun get(app: Application): ShizukuBridge =
            instance ?: synchronized(this) {
                instance ?: ShizukuBridge(app).also { instance = it }
            }
    }
}
