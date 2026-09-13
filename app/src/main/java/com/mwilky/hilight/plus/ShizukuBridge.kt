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

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, HiLightDaemonService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_daemon")
        .debuggable(BuildConfig.DEBUG)
        .version(3)

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
            onAvailabilityChanged?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e("HiLightPlus", "onServiceDisconnected")
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e("HiLightPlus", "onBindingDied")
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
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
        }
    }

    private fun runRemote(op: String, block: (IHiLightService) -> Unit) {
        val s = service ?: return
        runCatching { block(s) }.onFailure { markDead(op, it) }
    }

    fun errorText(): String? = lastError

    // --- Typed Control Methods ---

    fun setAmbient(pattern: String, color: Long, brightness: Float, speedMs: Long) {
        runRemote("setAmbient") { it.setAmbient(pattern, color, brightness, speedMs) }
    }

    fun triggerAlert(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false
    ) {
        Log.e("HiLightPlus", "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown")
        runRemote("triggerAlert") {
            it.triggerAlert(pattern, color, brightness, speedMs, durationMs, requiresFaceDown)
        }
    }

    fun postAlert(
        key: String,
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        durationMs: Long,
        requiresFaceDown: Boolean = false
    ) {
        Log.e("HiLightPlus", "postAlert [key=$key]: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown")
        runRemote("postAlert") {
            it.postAlert(key, pattern, color, brightness, speedMs, durationMs, requiresFaceDown)
        }
    }

    fun startIncomingCall(
        pattern: String,
        color: Long,
        brightness: Float,
        speedMs: Long,
        requiresFaceDown: Boolean = false
    ) {
        runRemote("startIncomingCall") {
            it.startIncomingCall(pattern, color, brightness, speedMs, requiresFaceDown)
        }
    }

    fun setDeviceFaceDown(faceDown: Boolean) {
        runRemote("setDeviceFaceDown") { it.setDeviceFaceDown(faceDown) }
    }

    fun stopIncomingCall() {
        runRemote("stopIncomingCall") { it.stopIncomingCall() }
    }

    fun removeAlert(key: String) {
        Log.e("HiLightPlus", "removeAlert [key=$key]")
        runRemote("removeAlert") { it.removeAlert(key) }
    }

    fun clearAlert() {
        Log.e("HiLightPlus", "clearAlert called")
        runRemote("clearAlert") { it.clearAlert() }
    }

    fun pauseAlerts() {
        Log.e("HiLightPlus", "pauseAlerts called")
        runRemote("pauseAlerts") { it.pauseAlerts() }
    }

    fun resumeAlerts() {
        Log.e("HiLightPlus", "resumeAlerts called")
        runRemote("resumeAlerts") { it.resumeAlerts() }
    }

    fun getSecureInt(key: String, defaultValue: Int = -1): Int {
        val s = service ?: return defaultValue
        return runCatching { s.getSecureInt(key, defaultValue) }
            .onFailure { markDead("getSecureInt", it) }
            .getOrDefault(defaultValue)
    }

    fun getSecureString(key: String): String? {
        val s = service ?: return null
        return runCatching { s.getSecureString(key) }
            .onFailure { markDead("getSecureString", it) }
            .getOrNull()
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

        @Volatile
        private var instance: ShizukuBridge? = null

        fun get(app: Application): ShizukuBridge =
            instance ?: synchronized(this) {
                instance ?: ShizukuBridge(app).also { instance = it }
            }
    }
}
