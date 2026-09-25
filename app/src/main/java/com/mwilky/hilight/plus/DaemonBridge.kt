package com.mwilky.hilight.plus

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.database.ContentObserver
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.os.IBinder
import com.mwilky.hilight.plus.adb.ConnectionService
import com.mwilky.hilight.plus.adb.DevSettings
import com.mwilky.hilight.plus.adb.SetupNotifier
import com.mwilky.hilight.plus.adb.WirelessAdb
import com.mwilky.hilight.plus.core.HiLightDaemonService
import com.mwilky.hilight.plus.core.IHiLightService
import com.mwilky.hilight.plus.core.ILogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Connection to [HiLightDaemonService], the shell-UID process that drives the ring, plus typed IPC
 * with it. The daemon is started one of two ways ([Method]): by the app itself over the phone's own
 * Wireless debugging once the user has paired ([WirelessAdb]), or by Shizuku for users who run it.
 */
class DaemonBridge private constructor(private val app: Application) {

    enum class Method {
        BUILT_IN,
        SHIZUKU
    }

    enum class State {
        /** Built-in: not paired yet, or Wireless debugging is off and the app can't turn it on. */
        NEEDS_SETUP,
        /** Built-in: paired, but Developer options is off. Reconnects when it's back on. */
        DEV_OPTIONS_OFF,
        /** Built-in: paired, but Wireless debugging needs Wi-Fi to start the daemon. */
        WAITING_FOR_WIFI,
        /**
         * Built-in: this Wi-Fi network hasn't been allowed for Wireless debugging yet, so Android
         * switched it straight back off. Waits for the user to allow it.
         */
        NETWORK_NOT_ALLOWED,
        NOT_INSTALLED,
        NOT_RUNNING,
        NEEDS_PERMISSION,
        CONNECTING,
        /** Built-in start, switching Wireless debugging on and giving it a moment. */
        TURNING_ON_WIRELESS_DEBUGGING,
        /** Built-in start, Wireless debugging is on but not taking connections yet. */
        WAITING_FOR_WIRELESS_DEBUGGING,
        /** Built-in start, Android is asking whether to allow this Wi-Fi; waiting for the answer. */
        ASKING_TO_ALLOW_NETWORK,
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    private val _state = MutableStateFlow(State.CONNECTING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _method = MutableStateFlow(Method.BUILT_IN)
    val method: StateFlow<Method> = _method.asStateFlow()

    // One event per daemon connection. A fresh daemon starts dark, so whoever still has alerts
    // waiting (the notification listener, the call processor) sends them again.
    private val _connections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connections: SharedFlow<Unit> = _connections.asSharedFlow()

    val wireless = WirelessAdb.get(app)

    private var service: IHiLightService? = null
    private var lastError: String? = null
    private var manuallyDisconnected = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var builtInJob: Job? = null

    // A built-in daemon that's already running hands its binder to a new app process by itself,
    // so the first attempt in each process waits for that before starting another one. Not after
    // a reboot, when none can be running and starting straight away matters.
    private var awaitRunningDaemon = wireless.ranThisBoot()

    // The daemon keeps running once started, so Wireless debugging only needs to be on for the
    // start. Set when it was switched on for this start (by the app, or by the user for setup).
    private var turnOffWirelessDebugging = false

    // Consecutive failed built-in starts; retries back off and stop after a few until asked again.
    private var builtInFailures = 0

    // Set when the user has just been asked to allow this network, so the next start waits longer.
    private var askedToAllowNetwork = false

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, HiLightDaemonService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix(HiLightDaemonService.PROCESS_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .version(17)

    // Receives the daemon's log lines so they land in the same shareable log as the app's.
    private val logSink = object : ILogSink.Stub() {
        override fun onLog(line: String?) {
            if (line != null) DebugLog.forward(line)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            DebugLog.i("HiLightPlus", "onServiceConnected: binder=$binder")
            connectTimeoutJob?.cancel()
            if (manuallyDisconnected || _method.value != Method.SHIZUKU) {
                // Switched to the built-in connection while Shizuku was still binding.
                if (_method.value != Method.SHIZUKU) runCatching { Shizuku.unbindUserService(args, this, true) }
                return
            }
            attach(binder, Method.SHIZUKU)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            DebugLog.i("HiLightPlus", "onServiceDisconnected")
            if (_method.value != Method.SHIZUKU) return
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            DebugLog.i("HiLightPlus", "onBindingDied")
            if (_method.value != Method.SHIZUKU) return
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            DebugLog.e("HiLightPlus", "onNullBinding")
            if (_method.value != Method.SHIZUKU) return
            service = null
            _state.value = State.FAILED
            lastError = "UserService returned null binding"
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                DebugLog.i("HiLightPlus", "Shizuku permission granted by user")
                manuallyDisconnected = false
                refresh()
            } else {
                DebugLog.i("HiLightPlus", "Shizuku permission denied by user")
                if (_method.value == Method.SHIZUKU) _state.value = State.NEEDS_PERMISSION
            }
        }
    }

    // Wireless debugging only runs on Wi-Fi, so a built-in start waiting for it resumes here.
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch {
                // A different network may be one that's already allowed.
                if (_state.value == State.WAITING_FOR_WIFI || _state.value == State.NETWORK_NOT_ALLOWED) {
                    DebugLog.i("HiLightPlus", "Wi-Fi available -> starting the daemon")
                    refresh()
                }
            }
        }
    }

    // Developer options coming back on is all a paired user needs to do to get the lights back.
    private val devOptionsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!DevSettings.isDevOptionsOn(app) || _method.value != Method.BUILT_IN) return
            if (_state.value == State.DEV_OPTIONS_OFF || _state.value == State.FAILED) {
                DebugLog.i("HiLightPlus", "Developer options back on -> starting the daemon")
                builtInFailures = 0
                refresh()
            }
        }
    }

    // Wireless debugging coming on while waiting for the network to be allowed means the user has
    // just allowed it (it only stays on when the network is allowed).
    private val wirelessDebuggingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (_state.value != State.NETWORK_NOT_ALLOWED) return
            if (DevSettings.isWirelessDebuggingOn(app) != true) return
            DebugLog.i("HiLightPlus", "Wireless debugging allowed on this network -> starting the daemon")
            builtInFailures = 0
            refresh()
        }
    }

    var onAvailabilityChanged: (() -> Unit)? = null

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky {
            DebugLog.i("HiLightPlus", "Shizuku binder received (sticky)")
            if (!manuallyDisconnected) {
                refresh()
            }
        }
        Shizuku.addBinderDeadListener {
            DebugLog.i("HiLightPlus", "Shizuku binder died")
            if (_method.value != Method.SHIZUKU) return@addBinderDeadListener
            service = null
            _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback
            )
        }.onFailure { DebugLog.w("HiLightPlus", "Couldn't watch Wi-Fi: ${it.message}") }
        app.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, devOptionsObserver
        )
        app.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(DevSettings.ADB_WIFI_ENABLED), false, wirelessDebuggingObserver
        )
        refresh()
    }

    fun isShizukuInstalled(): Boolean = runCatching {
        app.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    }.getOrDefault(false)

    /**
     * Built-in once the user has paired; otherwise Shizuku if it's installed, so people already
     * using it keep working exactly as before until they choose to switch.
     */
    private fun chooseMethod(): Method = when {
        wireless.isPaired() -> Method.BUILT_IN
        isShizukuInstalled() -> Method.SHIZUKU
        else -> Method.BUILT_IN
    }

    fun refresh() {
        if (manuallyDisconnected) return
        if (_state.value == State.CONNECTED) {
            if (isBinderAlive()) return
            DebugLog.i("HiLightPlus", "CONNECTED with dead binder -> clearing for rebind")
            service = null
            _state.value = State.NOT_RUNNING
        }
        _method.value = chooseMethod()
        when (_method.value) {
            Method.BUILT_IN -> startBuiltIn()
            Method.SHIZUKU -> refreshShizuku()
        }
    }

    private fun refreshShizuku() {
        if (!Shizuku.pingBinder()) {
            if (!isShizukuInstalled()) {
                _state.value = State.NOT_INSTALLED
                DebugLog.i("HiLightPlus", "Shizuku not installed")
                return
            }
            _state.value = State.NOT_RUNNING
            DebugLog.i("HiLightPlus", "Shizuku daemon not running")
            return
        }
        if (Shizuku.isPreV11()) {
            _state.value = State.FAILED
            lastError = "Shizuku is too old; v11 or newer is required"
            DebugLog.i("HiLightPlus", "Shizuku version too old")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.NEEDS_PERMISSION
            DebugLog.i("HiLightPlus", "Shizuku needs permission")
            return
        }
        DebugLog.i("HiLightPlus", "Shizuku ping OK and permission GRANTED -> calling bind()")
        bind()
    }

    // --- Built-in (Wireless debugging) ---

    /**
     * Starts the daemon over Wireless debugging: turns it on first when the app is allowed to
     * (after the first successful start it is), and waits for Wi-Fi when there isn't any.
     */
    private fun startBuiltIn() {
        if (!wireless.isPaired()) {
            _state.value = State.NEEDS_SETUP
            return
        }
        if (builtInJob?.isActive == true) return
        _state.value = State.CONNECTING
        builtInJob = scope.launch {
            if (awaitRunningDaemon) {
                awaitRunningDaemon = false
                val handedOver = withTimeoutOrNull(RUNNING_DAEMON_GRACE_MS) { state.first { it == State.CONNECTED } }
                if (handedOver != null) return@launch
            }
            // Stay awake until the daemon has handed its binder over, even from the background.
            ConnectionService.hold(app, KEEP_ALIVE_START)
            try {
                startBuiltInNow()
            } finally {
                ConnectionService.release(KEEP_ALIVE_START)
            }
        }
    }

    private suspend fun startBuiltInNow() {
        if (!DevSettings.isDevOptionsOn(app)) {
            DebugLog.w("HiLightPlus", "Developer options is off; can't start the daemon")
            lastError = null
            _state.value = State.DEV_OPTIONS_OFF
            return
        }
        if (!DevSettings.isWifiConnected(app)) {
            DebugLog.i("HiLightPlus", "No Wi-Fi; waiting for it to start the daemon")
            _state.value = State.WAITING_FOR_WIFI
            return
        }
        if (DevSettings.isWirelessDebuggingOn(app) == false) {
            _state.value = State.TURNING_ON_WIRELESS_DEBUGGING
            if (!DevSettings.enableWirelessDebugging(app)) {
                DebugLog.w("HiLightPlus", "Wireless debugging is off and the app can't turn it on")
                _state.value = State.NEEDS_SETUP
                return
            }
            turnOffWirelessDebugging = true
            DebugLog.i("HiLightPlus", "Turned Wireless debugging on")
            // Give it time to come up, or the connection can reach a stale port and close.
            delay(WIRELESS_DEBUGGING_SETTLE_MS)
            if (switchedBackOff()) return
        }
        // Wireless debugging can read as on (after a boot, or just switched on) a little before it
        // takes connections; keep trying quietly for a while rather than reporting each failure.
        // Longer after asking the user to allow this network, to give them time to answer.
        var asking = askedToAllowNetwork
        val window = if (asking) ALLOW_NETWORK_WINDOW_MS else QUICK_RETRY_WINDOW_MS
        askedToAllowNetwork = false
        _state.value = if (asking) State.ASKING_TO_ALLOW_NETWORK else State.CONNECTING
        var result = withContext(Dispatchers.IO) { wireless.startDaemon() }
        var unreachable = if (result is WirelessAdb.StartResult.NotReachable) 1 else 0
        val deadline = SystemClock.elapsedRealtime() + window
        while (result.isRetryable() && SystemClock.elapsedRealtime() < deadline) {
            delay(QUICK_RETRY_DELAY_MS)
            if (_state.value == State.CONNECTED || manuallyDisconnected) return
            // Android's "Allow on this network?" may have been on screen until now, then declined.
            if (switchedBackOff()) return
            // Switched on but never running: declining that question can leave it like this, and
            // only switching it off and on again makes Android ask again.
            if (unreachable == UNREACHABLE_BEFORE_ASKING && !asking && askAgainToAllowNetwork()) {
                asking = true
                _state.value = State.ASKING_TO_ALLOW_NETWORK
                delay(WIRELESS_DEBUGGING_SETTLE_MS)
            }
            // Say what the wait is for, rather than a bare "connecting".
            if (!asking) {
                _state.value = if (unreachable > 0) State.WAITING_FOR_WIRELESS_DEBUGGING else State.CONNECTING
            }
            result = withContext(Dispatchers.IO) { wireless.startDaemon() }
            unreachable = if (result is WirelessAdb.StartResult.NotReachable) unreachable + 1 else 0
        }
        if (result is WirelessAdb.StartResult.Started) _state.value = State.CONNECTING
        when (result) {
            WirelessAdb.StartResult.Started -> {
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { state.first { it == State.CONNECTED } }
                if (connected == null) {
                    lastError = "The lights service started but didn't answer"
                    DebugLog.e("HiLightPlus", lastError!!)
                    _state.value = State.FAILED
                }
            }
            WirelessAdb.StartResult.PairingRequired -> {
                DebugLog.w("HiLightPlus", "Pairing was removed; setup needed again")
                _state.value = State.NEEDS_SETUP
                // Falls back to Shizuku here if the user has it.
                if (isShizukuInstalled()) refresh()
            }
            is WirelessAdb.StartResult.Failed -> {
                lastError = result.message
                DebugLog.e("HiLightPlus", "Built-in start failed: ${result.message}")
                _state.value = State.FAILED
                retryBuiltInLater()
            }
            WirelessAdb.StartResult.NotReachable -> {
                // On Wi-Fi with Developer options on, so almost always this network not being
                // allowed. Retrying won't change that; wait for the user instead.
                DebugLog.w("HiLightPlus", "Wireless debugging isn't reachable; waiting for this Wi-Fi to be allowed")
                _state.value = State.NETWORK_NOT_ALLOWED
                SetupNotifier.showNetworkApproval(app)
            }
        }
    }

    private fun WirelessAdb.StartResult.isRetryable(): Boolean =
        this is WirelessAdb.StartResult.Failed || this is WirelessAdb.StartResult.NotReachable

    /**
     * Makes Android ask "Allow wireless debugging on this network?" again by switching Wireless
     * debugging off and back on. False when the app isn't allowed to.
     */
    private suspend fun askAgainToAllowNetwork(): Boolean {
        if (!DevSettings.disableWirelessDebugging(app)) return false
        DebugLog.i("HiLightPlus", "Switching Wireless debugging off and on so Android asks about this network again")
        turnOffWirelessDebugging = true
        delay(WIRELESS_DEBUGGING_TOGGLE_MS)
        return DevSettings.enableWirelessDebugging(app)
    }

    /** From the card's "Ask again" or the notification: ask again and give the user time to answer. */
    private fun askToAllowNetwork() {
        SetupNotifier.cancelNetworkApproval(app)
        builtInFailures = 0
        builtInJob?.cancel()
        _state.value = State.ASKING_TO_ALLOW_NETWORK
        builtInJob = scope.launch {
            ConnectionService.hold(app, KEEP_ALIVE_START)
            try {
                askAgainToAllowNetwork()
                delay(WIRELESS_DEBUGGING_SETTLE_MS)
                askedToAllowNetwork = true
                startBuiltInNow()
            } finally {
                ConnectionService.release(KEEP_ALIVE_START)
            }
        }
    }

    /**
     * Called (on a binder thread) by the provider when a built-in daemon hands over its binder:
     * right after it starts, and again whenever this app process was restarted.
     */
    fun onBuiltInBinder(binder: IBinder?, version: Int) {
        scope.launch {
            if (manuallyDisconnected) {
                // Paused: this daemon was supposed to be gone.
                binder?.let { runCatching { IHiLightService.Stub.asInterface(it).destroy() } }
                return@launch
            }
            if (_method.value == Method.SHIZUKU) {
                runCatching { Shizuku.unbindUserService(args, connection, false) }
            }
            _method.value = Method.BUILT_IN
            if (!attach(binder, Method.BUILT_IN)) return@launch
            wireless.markRanThisBoot()
            binder?.linkToDeath({ scope.launch { onBuiltInDied(binder) } }, 0)
            if (version != BuildConfig.VERSION_CODE) {
                // From before an app update: keep using it until a fresh one replaces it.
                DebugLog.i("HiLightPlus", "Daemon is from version $version; starting a current one")
                builtInJob?.cancel()
                awaitRunningDaemon = false
                replaceStaleDaemon()
            }
        }
    }

    /**
     * Android switches Wireless debugging back off on a network nobody has allowed yet: straight
     * away, or once its "Allow on this network?" question is declined or dismissed. Only counts when
     * the app switched it on for this start. Then there's no point retrying, so wait for the user.
     */
    private fun switchedBackOff(): Boolean {
        if (!turnOffWirelessDebugging || DevSettings.isWirelessDebuggingOn(app) != false) return false
        DebugLog.w("HiLightPlus", "Wireless debugging was switched back off: this Wi-Fi isn't allowed yet")
        _state.value = State.NETWORK_NOT_ALLOWED
        SetupNotifier.showNetworkApproval(app)
        return true
    }

    /** Retries a failed start after 2, 4, 8, 16 then 30 seconds; after that, only when asked. */
    private fun retryBuiltInLater() {
        builtInFailures++
        if (builtInFailures > BUILT_IN_RETRIES) {
            DebugLog.w("HiLightPlus", "Built-in start failed $builtInFailures times; waiting to be asked again")
            return
        }
        val wait = (RECONNECT_DELAY_MS shl (builtInFailures - 1)).coerceAtMost(MAX_RETRY_DELAY_MS)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait)
            if (!manuallyDisconnected && _state.value == State.FAILED) refresh()
        }
    }

    private fun replaceStaleDaemon() {
        scope.launch {
            if (!DevSettings.isWifiConnected(app) || !DevSettings.isDevOptionsOn(app)) return@launch
            if (DevSettings.isWirelessDebuggingOn(app) == false) {
                if (!DevSettings.enableWirelessDebugging(app)) return@launch
                turnOffWirelessDebugging = true
            }
            withContext(Dispatchers.IO) { wireless.startDaemon() }
        }
    }

    private fun onBuiltInDied(binder: IBinder) {
        if (service?.asBinder() !== binder) return
        DebugLog.w("HiLightPlus", "Built-in daemon died")
        service = null
        _state.value = if (manuallyDisconnected) State.DISCONNECTED else State.CONNECTING
        onAvailabilityChanged?.invoke()
        if (!manuallyDisconnected) scheduleReconnect()
    }

    /**
     * After pairing: drops Shizuku if it was in use and starts the built-in daemon. Returns whether
     * it connected.
     */
    suspend fun switchToBuiltIn(): Boolean {
        DebugLog.i("HiLightPlus", "Switching to the built-in connection")
        manuallyDisconnected = false
        builtInFailures = 0
        awaitRunningDaemon = false
        // It was switched on just for setup.
        turnOffWirelessDebugging = true
        if (_method.value == Method.SHIZUKU) {
            // Removing the UserService stops the Shizuku-started daemon, which clears the ring.
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            if (service != null) {
                service = null
                onAvailabilityChanged?.invoke()
            }
        }
        _method.value = Method.BUILT_IN
        if (_state.value != State.CONNECTED || !isBinderAlive()) {
            builtInJob?.cancel()
            _state.value = State.CONNECTING
            startBuiltIn()
        }
        return withTimeoutOrNull(SWITCH_TIMEOUT_MS) { state.first { it == State.CONNECTED } } != null &&
            _method.value == Method.BUILT_IN
    }

    // --- Shared ---

    /** Validates a daemon binder and makes it the live connection. */
    private fun attach(binder: IBinder?, via: Method): Boolean {
        if (binder == null || !binder.pingBinder()) {
            service = null
            _state.value = State.FAILED
            lastError = "Received null/dead binder from the lights service"
            DebugLog.e("HiLightPlus", "Binder ping failed")
            return false
        }
        val bound: IHiLightService = IHiLightService.Stub.asInterface(binder)
        val count = runCatching { bound.getLedCount() }.getOrNull() ?: 0
        if (count <= 0) {
            service = null
            _state.value = State.FAILED
            lastError = "Lights backend unavailable"
            DebugLog.e("HiLightPlus", "Binder connected but no LEDs")
            return false
        }
        service = bound
        builtInFailures = 0
        builtInJob?.cancel()
        reconnectJob?.cancel()
        _state.value = State.CONNECTED
        SetupNotifier.cancelNetworkApproval(app)
        lastError = null
        runCatching { bound.setLogSink(logSink) }
        DebugLog.i("HiLightPlus", "Connected to HiLightDaemonService via $via ($count LEDs)")
        // Fresh daemon process, or a reconnect after one died: replay whatever battery
        // config/state was last set so it can't be lost to a connection that wasn't ready yet.
        lastBatteryConfig?.let { sendBatteryConfig(it) }
        lastBatteryState?.let { sendBatteryState(it) }
        lastEntitled?.let { sendEntitled(it) }
        onAvailabilityChanged?.invoke()
        _connections.tryEmit(Unit)
        afterConnect(bound, via)
        return true
    }

    /**
     * Makes sure the app may write secure settings, granted through whichever daemon is connected
     * (so Shizuku users have it before they ever switch). That lets it switch Wireless debugging
     * on for the next start, and off again now if it was only on for this one.
     */
    private fun afterConnect(bound: IHiLightService, via: Method) {
        val turnOff = via == Method.BUILT_IN && turnOffWirelessDebugging
        turnOffWirelessDebugging = false
        scope.launch {
            val canWrite = DevSettings.canWriteSecureSettings(app) || withContext(Dispatchers.IO) {
                runCatching { bound.grantWriteSecureSettings() }
                    .onFailure { DebugLog.w("HiLightPlus", "grantWriteSecureSettings failed: ${it.message}") }
                    .getOrDefault(false)
            }
            if (!turnOff || !canWrite) return@launch
            if (!DevSettings.isUsbDebuggingOn(app)) {
                // Nothing else would keep the debugging service running, and the daemon with it.
                DebugLog.i("HiLightPlus", "Leaving Wireless debugging on: USB debugging is off, so turning it off would stop the daemon")
                return@launch
            }
            if (DevSettings.disableWirelessDebugging(app)) {
                DebugLog.i("HiLightPlus", "Turned Wireless debugging off again; USB debugging keeps the daemon running")
            }
        }
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
        DebugLog.i("HiLightPlus", "=== connectManually() called ===")
        manuallyDisconnected = false
        builtInFailures = 0
        if (_state.value == State.NETWORK_NOT_ALLOWED && _method.value == Method.BUILT_IN) {
            askToAllowNetwork()
            return
        }
        refresh()
    }

    private fun bind() {
        // Its own bind in flight, not the shared state: that also reads CONNECTING at start-up.
        if (connectTimeoutJob?.isActive == true) {
            DebugLog.i("HiLightPlus", "bind() skipped, a Shizuku bind is already in progress")
            return
        }
        if (_state.value == State.CONNECTED && isBinderAlive()) {
            DebugLog.i("HiLightPlus", "bind() skipped, already connected")
            return
        }
        _state.value = State.CONNECTING
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value == State.CONNECTING && _method.value == Method.SHIZUKU) {
                service = null
                _state.value = State.FAILED
                lastError = "Connection timed out"
                DebugLog.e("HiLightPlus", "bind() timed out")
            }
        }
        DebugLog.i("HiLightPlus", "Calling Shizuku.bindUserService()...")
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                connectTimeoutJob?.cancel()
                _state.value = State.FAILED
                lastError = it.message
                DebugLog.e("HiLightPlus", "bindUserService failed: ${it.message}", it)
            }
    }

    /** Pauses the lights: stops the daemon (which clears the ring) until [connectManually]. */
    fun unbind() {
        DebugLog.i("HiLightPlus", "=== unbind() called by user ===")
        connectTimeoutJob?.cancel()
        builtInJob?.cancel()
        manuallyDisconnected = true
        if (_method.value == Method.SHIZUKU) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        } else {
            val s = service
            if (s != null) scope.launch(Dispatchers.IO) { runCatching { s.destroy() } }
        }
        service = null
        _state.value = State.DISCONNECTED
        onAvailabilityChanged?.invoke()
    }

    fun isConnected(): Boolean = _state.value == State.CONNECTED && isBinderAlive()

    private fun isBinderAlive(): Boolean = service?.asBinder()?.pingBinder() == true

    private fun markDead(op: String, error: Throwable) {
        DebugLog.e("HiLightPlus", "$op failed", error)
        if (manuallyDisconnected) return
        if (error is DeadObjectException || error.cause is DeadObjectException || !isBinderAlive()) {
            service = null
            _state.value = if (_method.value == Method.BUILT_IN) State.CONNECTING else State.NOT_RUNNING
            lastError = error.message
            onAvailabilityChanged?.invoke()
            scheduleReconnect()
        }
    }

    /**
     * Reconnects after the daemon goes away. Without this nothing reconnects on its own, and since
     * the daemon keeps rendering whatever it last knew, a dropped binder could strand the LEDs
     * lit with every command from the app silently going nowhere.
     */
    private fun scheduleReconnect() {
        if (manuallyDisconnected || reconnectJob?.isActive == true) return
        if (_method.value == Method.BUILT_IN) {
            // One start; if that fails, retryBuiltInLater takes over with its own back-off.
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (!manuallyDisconnected && _state.value != State.CONNECTED) refresh()
            }
            return
        }
        reconnectJob = scope.launch {
            repeat(RECONNECT_ATTEMPTS) {
                delay(RECONNECT_DELAY_MS)
                if (manuallyDisconnected || _state.value == State.CONNECTED) return@launch
                // Waiting on Wi-Fi or on the user: retrying now can't help.
                if (_state.value == State.NEEDS_PERMISSION || _state.value == State.NOT_INSTALLED) return@launch
                DebugLog.i("HiLightPlus", "Attempting to reconnect to the daemon")
                if (_state.value == State.FAILED) _state.value = State.NOT_RUNNING
                refresh()
            }
        }
    }

    private fun runRemote(op: String, block: (IHiLightService) -> Unit) {
        val s = service
        if (s == null) {
            // Worth seeing in a report: a dropped removeAlert/stopIncomingCall is one way the
            // ring could be left lit.
            DebugLog.w("HiLightPlus", "$op dropped: daemon not connected (${_state.value})")
            return
        }
        runCatching { block(s) }.onFailure { markDead(op, it) }
    }

    fun errorText(): String? = lastError

    /**
     * Restarts the daemon. Its process exiting makes the system drop every light session it held,
     * which clears a ring frozen on a session nothing can update any more. Reconnects on its own.
     */
    suspend fun resetDaemon() {
        val s = service ?: return
        DebugLog.i("HiLightPlus", "Resetting the lights service")
        // The daemon exits mid-call, so this normally ends in a DeadObjectException.
        withContext(Dispatchers.IO) { runCatching { s.restart() } }
        if (_method.value == Method.SHIZUKU) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
        awaitRunningDaemon = false
        _state.value = if (_method.value == Method.BUILT_IN) State.CONNECTING else State.NOT_RUNNING
        onAvailabilityChanged?.invoke()
        scheduleReconnect()
    }

    /** Whether the LEDs show something other than what the daemon sent. False when unknown. */
    fun isRingStuck(): Boolean {
        val s = service ?: return false
        return runCatching { s.isRingStuck() }
            .onFailure { markDead("isRingStuck", it) }
            .getOrDefault(false)
    }

    /** What the daemon is holding right now, for debug reports. Null when it isn't reachable. */
    fun dumpDaemonState(): String? {
        val s = service ?: return null
        return runCatching { s.dumpState() }
            .onFailure { markDead("dumpState", it) }
            .getOrNull()
    }

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
        DebugLog.i("HiLightPlus", "triggerAlert: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
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
        DebugLog.i("HiLightPlus", "postAlert [key=$key]: pattern=$pattern, color=$color, durationMs=$durationMs, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
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
        DebugLog.i("HiLightPlus", "startIncomingCall: pattern=$pattern, color=$color, requiresFaceDown=$requiresFaceDown, dndMode=$dndMode, quietHoursMode=$quietHoursMode")
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

    fun setSplitAnimation(animation: SplitAnimation) {
        runRemote("setSplitAnimation") { it.setSplitAnimation(animation.id) }
    }

    fun stopIncomingCall() {
        DebugLog.i("HiLightPlus", "stopIncomingCall called")
        runRemote("stopIncomingCall") { it.stopIncomingCall() }
    }

    fun testAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        DebugLog.i("HiLightPlus", "testAlert: pattern=$pattern, color=$color, durationMs=$durationMs")
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
        DebugLog.i("HiLightPlus", "removeAlert [key=$key]")
        runRemote("removeAlert") { it.removeAlert(key) }
    }

    fun clearAlert() {
        DebugLog.i("HiLightPlus", "clearAlert called")
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
        DebugLog.i("HiLightPlus", "turnOff called")
        runRemote("turnOff") { it.turnOff() }
    }

    /**
     * Shizuku only pushes its binder into app processes it sees start or come to the foreground,
     * so a process that was already running when Shizuku was started never receives one and
     * [refresh] can't help. The only reliable way to get a binder is a fresh process.
     */
    fun restartApp(context: Context) {
        refresh()
        if (_state.value != State.NOT_RUNNING) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
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

    /** Shizuku's app info page, where the user can uninstall it once they've switched. */
    fun openShizukuAppInfo(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", SHIZUKU_PKG, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 4001
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val RECONNECT_ATTEMPTS = 5
        private const val RUNNING_DAEMON_GRACE_MS = 3_000L
        private const val SWITCH_TIMEOUT_MS = 30_000L
        private const val KEEP_ALIVE_START = "start"
        private const val BUILT_IN_RETRIES = 5
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val WIRELESS_DEBUGGING_SETTLE_MS = 3_000L
        private const val QUICK_RETRY_WINDOW_MS = 30_000L
        private const val QUICK_RETRY_DELAY_MS = 2_000L
        private const val ALLOW_NETWORK_WINDOW_MS = 120_000L
        private const val WIRELESS_DEBUGGING_TOGGLE_MS = 1_000L
        private const val UNREACHABLE_BEFORE_ASKING = 5

        @Volatile
        private var instance: DaemonBridge? = null

        fun get(app: Application): DaemonBridge =
            instance ?: synchronized(this) {
                instance ?: DaemonBridge(app).also { instance = it }
            }
    }
}
