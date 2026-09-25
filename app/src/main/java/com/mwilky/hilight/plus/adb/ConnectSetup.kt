package com.mwilky.hilight.plus.adb

import android.app.Application
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.mwilky.hilight.plus.DaemonBridge
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.PatternMode
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

/**
 * Drives the guided setup of the built-in connection. Every step ticks itself off by watching the
 * system (Developer options, Wi-Fi, Wireless debugging, the pairing dialog), and the guide
 * notification follows along so the user never has to come back to the app mid-setup.
 *
 * A session runs from [start] until [stop]; nothing is watched outside one.
 */
class ConnectSetup private constructor(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bridge: DaemonBridge get() = LightController.get(app).daemon
    private val wireless get() = bridge.wireless
    private val notifier = SetupNotifier(app)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    private var active = false
    private var bridgeJob: Job? = null
    // Flags rather than Job checks: launches on Main.immediate re-enter update() before returning.
    private var pairing = false
    private var starting = false
    private var discovery: AdbMdns? = null
    private val pairingPort = MutableStateFlow(-1)
    private var wasConnected = false

    // Once per session: on a network that isn't allowed yet, each attempt makes Android ask again.
    private var autoEnabledWirelessDebugging = false

    // Off while the screen is asking for permissions: scanning first makes Android show its own
    // device picker instead of letting the app look for the pairing dialog.
    private var discoveryAllowed = true

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = update()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post(::update)
        }

        override fun onLost(network: Network) {
            handler.post(::update)
        }
    }

    fun start() {
        if (active) {
            update()
            return
        }
        active = true
        DebugLog.i(TAG, "Setup session started")
        // Stay awake while the user is in Settings, or each step only ticks off once they're back.
        ConnectionService.hold(app, ConnectionService.HOLD_SETUP)
        val resolver = app.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, settingsObserver
        )
        resolver.registerContentObserver(Settings.Global.getUriFor(DevSettings.ADB_WIFI_ENABLED), false, settingsObserver)
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                networkCallback
            )
        }
        bridgeJob = scope.launch {
            combine(bridge.state, bridge.method) { _, _ -> }.collect { update() }
        }
        wasConnected = isBuiltInConnected()
        autoEnabledWirelessDebugging = false
        _state.value = _state.value.copy(pairing = PairingPhase.SEARCHING, error = null)
        update()
    }

    fun stop() {
        if (!active) return
        active = false
        DebugLog.i(TAG, "Setup session stopped")
        app.contentResolver.unregisterContentObserver(settingsObserver)
        runCatching { app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        bridgeJob?.cancel()
        stopDiscovery()
        ConnectionService.release(ConnectionService.HOLD_SETUP)
        notifier.cancel()
    }

    /** Re-reads everything, e.g. when the app comes back to the foreground. */
    fun update() {
        val current = _state.value
        val next = current.copy(
            devOptionsOn = DevSettings.isDevOptionsOn(app),
            wifiConnected = DevSettings.isWifiConnected(app),
            // Unreadable: assume on and let the pairing step carry the instructions.
            wirelessDebuggingOn = DevSettings.isWirelessDebuggingOn(app) ?: true,
            codeEntryAvailable = pairingPort.value > 0,
            connected = isBuiltInConnected()
        )
        _state.value = next
        if (!active) return

        if (next.connected && !wasConnected) celebrate()
        wasConnected = next.connected
        // Nothing left to watch for.
        if (next.connected) ConnectionService.release(ConnectionService.HOLD_SETUP)

        when (next.step) {
            SetupStep.WIRELESS_DEBUGGING -> {
                // Shizuku users already granted the app this, so it can flip the switch for them.
                if (!autoEnabledWirelessDebugging && DevSettings.enableWirelessDebugging(app)) {
                    autoEnabledWirelessDebugging = true
                    DebugLog.i(TAG, "Turned Wireless debugging on")
                }
                stopDiscovery()
            }
            SetupStep.PAIR -> {
                if (wireless.isPaired()) {
                    startBuiltInOnce()
                } else if (discoveryAllowed) {
                    startDiscovery()
                }
            }
            else -> stopDiscovery()
        }
        notifier.show(next)
    }

    /** A code typed into the notification or the app. */
    fun submitCode(input: String) {
        // A reply can arrive in a freshly restarted process; pick the session back up.
        if (!active) start()
        val code = normalisePairingCode(input)
        if (code == null) {
            _state.value = _state.value.copy(pairing = PairingPhase.WRONG_CODE, error = null)
            notifier.show(_state.value, force = true)
            return
        }
        if (pairing || starting) return
        pairing = true
        // A notification reply wakes the app only briefly; stay awake until connected.
        ConnectionService.hold(app, KEEP_ALIVE_PAIR)
        _state.value = _state.value.copy(pairing = PairingPhase.PAIRING, error = null)
        notifier.show(_state.value)
        scope.launch {
            try {
                pairWith(code)
            } finally {
                pairing = false
                ConnectionService.release(KEEP_ALIVE_PAIR)
            }
        }
    }

    private suspend fun pairWith(code: String) {
        // The process may have been restarted by the notification reply, so find the dialog again.
        if (pairingPort.value <= 0) startDiscovery()
        val port = withTimeoutOrNull(PORT_WAIT_MS) { pairingPort.first { it > 0 } }
        if (port == null) {
            DebugLog.w(TAG, "Pairing dialog not found")
            setPairing(PairingPhase.SEARCHING)
            return
        }
        val paired = withContext(Dispatchers.IO) {
            runCatching { wireless.pair(port, code) }
                .onFailure { DebugLog.w(TAG, "Pairing failed: ${it.message}") }
                .isSuccess
        }
        if (!paired) {
            _state.value = _state.value.copy(pairing = PairingPhase.WRONG_CODE, error = null)
            notifier.show(_state.value, force = true)
            update()
            return
        }
        stopDiscovery()
        switchToBuiltIn()
    }

    private fun startBuiltInOnce() {
        if (isBuiltInConnected() || starting || pairing) return
        if (_state.value.pairing == PairingPhase.FAILED) return
        starting = true
        scope.launch {
            try {
                switchToBuiltIn()
            } finally {
                starting = false
            }
        }
    }

    private suspend fun switchToBuiltIn() {
        setPairing(PairingPhase.STARTING)
        val connected = bridge.switchToBuiltIn()
        if (connected) {
            setPairing(PairingPhase.SEARCHING)
        } else if (!wireless.isPaired()) {
            // The phone forgot us between pairing and connecting; pair again.
            setPairing(PairingPhase.SEARCHING)
        } else {
            _state.value = _state.value.copy(pairing = PairingPhase.FAILED, error = bridge.errorText())
            notifier.show(_state.value, force = true)
        }
    }

    fun allowDiscovery(allowed: Boolean) {
        if (discoveryAllowed == allowed) return
        discoveryAllowed = allowed
        if (!allowed) stopDiscovery()
        update()
    }

    /** Clears a failure so the user can try again. */
    fun retry() {
        _state.value = _state.value.copy(pairing = PairingPhase.SEARCHING, error = null)
        update()
    }

    private fun setPairing(phase: PairingPhase) {
        _state.value = _state.value.copy(pairing = phase, error = null)
        update()
    }

    private fun isBuiltInConnected(): Boolean =
        bridge.state.value == DaemonBridge.State.CONNECTED && bridge.method.value == DaemonBridge.Method.BUILT_IN

    private fun startDiscovery() {
        if (discovery != null) return
        discovery = AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
            handler.post {
                DebugLog.d(TAG, "Pairing service ${if (port > 0) "found" else "gone"}")
                pairingPort.value = port
                val phase = _state.value.pairing
                if (port > 0 && (phase == PairingPhase.SEARCHING || phase == PairingPhase.FAILED)) {
                    setPairing(PairingPhase.CODE_NEEDED)
                } else if (port <= 0 && phase == PairingPhase.CODE_NEEDED) {
                    setPairing(PairingPhase.SEARCHING)
                } else {
                    update()
                }
            }
        }.also { runCatching { it.start() }.onFailure { e -> DebugLog.w(TAG, "mDNS discovery failed: ${e.message}") } }
    }

    private fun stopDiscovery() {
        discovery?.let { runCatching { it.stop() } }
        discovery = null
        pairingPort.value = -1
    }

    /** A sweep round the real ring, the clearest proof that setup worked. */
    private fun celebrate() {
        DebugLog.i(TAG, "Built-in connection is up")
        LightController.get(app).testPattern(PatternMode.COMET, CELEBRATE_COLOR, CELEBRATE_MS)
    }

    companion object {
        private const val TAG = "ConnectSetup"
        private const val PORT_WAIT_MS = 8_000L
        private const val KEEP_ALIVE_PAIR = "pair"
        private const val CELEBRATE_COLOR = 0xFF8AB4F8
        private const val CELEBRATE_MS = 2_500L

        @Volatile
        private var instance: ConnectSetup? = null

        fun get(app: Application): ConnectSetup =
            instance ?: synchronized(this) {
                instance ?: ConnectSetup(app).also { instance = it }
            }
    }
}
