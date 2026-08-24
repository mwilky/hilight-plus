package com.hilight.plus

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.hilight.plus.core.HiLightDaemonService
import com.hilight.plus.core.IHiLightService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Manages connection, permission requests, and typed IPC with [HiLightDaemonService].
 */
class ShizukuBridge private constructor(private val app: Application) {

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, CONNECTING, CONNECTED, FAILED }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _ledCount = MutableStateFlow(8)
    val ledCount: StateFlow<Int> = _ledCount.asStateFlow()

    private var service: IHiLightService? = null
    private var lastError: String? = null

    var onAvailabilityChanged: (() -> Unit)? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, HiLightDaemonService::class.java.name)
    )
        .daemon(true)
        .processNameSuffix("hilight")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                _state.value = State.FAILED
                lastError = "Service returned an invalid binder"
                return
            }
            service = IHiLightService.Stub.asInterface(binder)
            _state.value = State.CONNECTED
            val count = runCatching { service?.getLedCount() }.getOrNull() ?: 8
            if (count > 0) _ledCount.value = count
            Log.i(TAG, "HiLightDaemonService connected with $count LEDs")
            onAvailabilityChanged?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (_state.value == State.CONNECTED) _state.value = State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bind()
            } else {
                _state.value = State.NEEDS_PERMISSION
            }
        }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            _state.value = State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }
        refresh()
    }

    fun isInstalled(): Boolean = runCatching {
        app.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    }.getOrDefault(false)

    fun refresh() {
        if (_state.value == State.CONNECTED && service?.asBinder()?.pingBinder() == true) return
        if (!Shizuku.pingBinder()) {
            if (!isInstalled()) {
                _state.value = State.NOT_INSTALLED
                return
            }
            _state.value = State.NOT_RUNNING
            return
        }
        if (Shizuku.isPreV11()) {
            _state.value = State.FAILED
            lastError = "Shizuku is too old; v11 or newer is required"
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.NEEDS_PERMISSION
            return
        }
        bind()
    }

    fun requestPermission() {
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

    private fun bind() {
        if (_state.value == State.CONNECTING || _state.value == State.CONNECTED) return
        _state.value = State.CONNECTING
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                _state.value = State.FAILED
                lastError = it.message
                Log.w(TAG, "bindUserService failed: ${it.message}", it)
            }
    }

    fun unbind() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
        _state.value = State.NOT_RUNNING
    }

    fun errorText(): String? = lastError

    // --- Typed IPC Operations ---

    fun setAmbient(pattern: String, color: Long, brightness: Float, speedMs: Long) {
        val s = service ?: return
        runCatching { s.setAmbient(pattern, color, brightness, speedMs) }.onFailure {
            Log.w(TAG, "setAmbient failed", it)
            service = null
            _state.value = State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }
    }

    fun triggerAlert(pattern: String, color: Long, brightness: Float, speedMs: Long, durationMs: Long) {
        val s = service ?: return
        runCatching { s.triggerAlert(pattern, color, brightness, speedMs, durationMs) }.onFailure {
            Log.w(TAG, "triggerAlert failed", it)
            service = null
            _state.value = State.NOT_RUNNING
            onAvailabilityChanged?.invoke()
        }
    }

    fun clearAlert() {
        val s = service ?: return
        runCatching { s.clearAlert() }.onFailure {
            Log.w(TAG, "clearAlert failed", it)
        }
    }

    fun turnOff() {
        val s = service ?: return
        runCatching { s.turnOff() }.onFailure {
            Log.w(TAG, "turnOff failed", it)
        }
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
        const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST = 4242
        private const val TAG = "ShizukuBridge"

        @Volatile
        private var instance: ShizukuBridge? = null

        fun get(context: Context): ShizukuBridge {
            val app = if (context is Application) context else context.applicationContext as Application
            return instance ?: synchronized(this) {
                instance ?: ShizukuBridge(app).also { instance = it }
            }
        }
    }
}
