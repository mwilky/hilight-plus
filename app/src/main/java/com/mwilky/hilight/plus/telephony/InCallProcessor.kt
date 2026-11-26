package com.mwilky.hilight.plus.telephony

import android.content.Context
import com.mwilky.hilight.plus.AppStore
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.LightController
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.core.DeviceOrientationDetector
import com.mwilky.hilight.plus.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shows the "On a call" look while a call is under way: from the moment its notification reads
 * as in progress (answered, or dialling out) until that notification goes, is re-posted as
 * something else, or the call is handed to call screening. Several calls at once (one on hold)
 * keep it lit until the last ends.
 *
 * The look is repeated to the daemon every [HEARTBEAT_MS], so if this process dies mid-call the
 * daemon lets it go dark by itself. Each beat also checks the shade, so a removal that was never
 * seen can't keep it lit for the rest of the day.
 */
internal object InCallProcessor {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var settingsWatchStarted = false

    // Calls under way, by notification key, and when each one's removal was seen (it may yet be
    // undone by a re-post, as the dialer does when the screen turns off).
    private val activeKeys = mutableSetOf<String>()
    private val removedAtMs = mutableMapOf<String, Long>()
    private var heartbeat: Job? = null
    private var lit = false

    /** The keys in the shade right now, or null when the listener can't say. Set by the listener. */
    @Volatile var shadeKeys: (() -> Set<String>?)? = null

    init {
        scope.launch {
            for (event in events) {
                try {
                    handle(event)
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "In-call event failed", t)
                }
            }
        }
    }

    /** The notification under [key] says its call is under way. */
    fun submitInProgress(appContext: Context, key: String) {
        ensureSettingsWatch(appContext)
        events.trySend(Event.InProgress(appContext, key))
    }

    /** The notification under [key] is no longer a call under way (ringing, screening, or not a call). */
    fun submitNotInProgress(appContext: Context, key: String) {
        events.trySend(Event.NotInProgress(appContext, key))
    }

    /** The notification under [key] was removed; it ends the call unless re-posted within the grace. */
    fun submitRemoved(appContext: Context, key: String) {
        events.trySend(Event.Removed(appContext, key))
    }

    /** Listener reconnected: a call whose notification is gone has ended. */
    fun submitShadeSync(appContext: Context, shadeKeys: Set<String>) {
        events.trySend(Event.ShadeSync(appContext, shadeKeys))
    }

    /** The notification listener is gone, so no removal will ever end a call. */
    fun submitListenerGone(appContext: Context) {
        events.trySend(Event.ListenerGone(appContext))
    }

    /** A (re)started daemon is dark, so a call still under way needs lighting again. */
    fun submitDaemonConnected(appContext: Context) {
        events.trySend(Event.Apply(appContext))
    }

    private fun ensureSettingsWatch(appContext: Context) {
        if (settingsWatchStarted) return
        settingsWatchStarted = true
        scope.launch {
            appContext.dataStore.data.collect { events.trySend(Event.Apply(appContext)) }
        }
    }

    private suspend fun handle(event: Event) {
        val context = event.appContext
        when (event) {
            is Event.InProgress -> {
                removedAtMs.remove(event.key)
                if (activeKeys.add(event.key)) DebugLog.i(TAG, "Call under way (${DebugLog.redactKey(event.key)})")
            }
            is Event.NotInProgress -> end(event.key, "no longer under way")
            is Event.Removed -> if (event.key in activeKeys) {
                removedAtMs[event.key] = System.currentTimeMillis()
                scope.launch {
                    delay(REMOVAL_GRACE_MS + 100L)
                    events.trySend(Event.Beat(context))
                }
                return
            }
            is Event.ShadeSync -> activeKeys.filter { it !in event.shadeKeys }.forEach { end(it, "gone after listener reconnect") }
            is Event.ListenerGone -> activeKeys.toList().forEach { end(it, "lost with the notification listener") }
            is Event.Beat -> {
                val now = System.currentTimeMillis()
                removedAtMs.filterValues { now - it >= REMOVAL_GRACE_MS }.keys.forEach { end(it, "removed with no re-post") }
                val shade = shadeKeys?.invoke()
                if (shade != null) activeKeys.filter { it !in shade && it !in removedAtMs }.forEach { end(it, "no longer in the shade") }
            }
            is Event.Apply -> Unit
        }
        apply(context)
    }

    private fun end(key: String, reason: String) {
        removedAtMs.remove(key)
        if (activeKeys.remove(key)) DebugLog.i(TAG, "Call $reason (${DebugLog.redactKey(key)})")
    }

    /** Lights, repeats or clears the look to match the calls under way and the settings. */
    private suspend fun apply(context: Context) {
        val controller = LightController.get(context)
        val snapshot = AppStore.get(context).snapshot()
        val show = activeKeys.isNotEmpty() && snapshot.isCallLightsEnabled && snapshot.isInCallEnabled &&
            snapshot.inCallPattern != PatternMode.OFF
        if (!show) {
            if (lit) {
                DebugLog.i(TAG, if (activeKeys.isEmpty()) "No call under way, stopping on-a-call lights" else "On-a-call lights are off")
                stop(controller)
            }
            if (activeKeys.isEmpty()) stopHeartbeat()
            else startHeartbeat(context)
            return
        }
        val requiresFaceDown = snapshot.inCallFaceDownMode.requiresFaceDown(snapshot.isOnlyWhenFaceDown)
        if (requiresFaceDown) {
            DeviceOrientationDetector.retainMonitoring(context, DeviceOrientationDetector.TOKEN_IN_CALL)
            controller.setDeviceFaceDown(DeviceOrientationDetector.isDeviceFaceDown(context))
        } else {
            DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_IN_CALL)
        }
        val skipQuiet = snapshot.inCallQuietHoursMode == QuietHoursMode.SKIP
        if (!lit) DebugLog.i(TAG, "Showing on-a-call lights: ${snapshot.inCallPattern}")
        controller.startInCallLights(
            pattern = snapshot.inCallPattern,
            color = snapshot.inCallColor,
            brightness = snapshot.inCallBrightness,
            speed = snapshot.inCallSpeed,
            requiresFaceDown = requiresFaceDown,
            dndMode = snapshot.inCallDndMode,
            quietHoursMode = snapshot.inCallQuietHoursMode,
            quietStartMinutes = if (skipQuiet) snapshot.inCallQuietHoursStartMinutes else null,
            quietEndMinutes = if (skipQuiet) snapshot.inCallQuietHoursEndMinutes else null
        )
        lit = true
        startHeartbeat(context)
    }

    private fun stop(controller: LightController) {
        DeviceOrientationDetector.releaseMonitoring(DeviceOrientationDetector.TOKEN_IN_CALL)
        controller.stopInCallLights()
        lit = false
    }

    // Runs while any call is under way, lit or not, so a missed end is still caught.
    private fun startHeartbeat(context: Context) {
        if (heartbeat?.isActive == true) return
        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                events.trySend(Event.Beat(context))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = null
    }

    private sealed interface Event {
        val appContext: Context

        data class InProgress(override val appContext: Context, val key: String) : Event
        data class NotInProgress(override val appContext: Context, val key: String) : Event
        data class Removed(override val appContext: Context, val key: String) : Event
        data class ShadeSync(override val appContext: Context, val shadeKeys: Set<String>) : Event
        data class ListenerGone(override val appContext: Context) : Event
        data class Beat(override val appContext: Context) : Event
        data class Apply(override val appContext: Context) : Event
    }

    private const val TAG = "InCallProcessor"
    // Well inside the daemon's 3-minute limit, so a missed beat or two doesn't blink the lights.
    private const val HEARTBEAT_MS = 60_000L
    private const val REMOVAL_GRACE_MS = 1_000L
}
