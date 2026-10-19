package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.quietHoursBlocked

/**
 * Composed visibility for alerts. An active call hides notifications;
 * face-down, DND, and quiet hours are per-alert.
 */
internal object AlertRenderPolicy {

    fun canShowAlert(
        requiresFaceDown: Boolean,
        deviceFaceDown: Boolean,
        dndMode: DndMode = DndMode.ALWAYS,
        dndSuppressEnabled: Boolean = false,
        dndActive: Boolean = false,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietHoursEnabled: Boolean = false,
        quietHoursStartMinutes: Int = 0,
        quietHoursEndMinutes: Int = 0,
        quietStartOverride: Int? = null,
        quietEndOverride: Int? = null,
        nowMinutes: Int = 0
    ): Boolean {
        if (requiresFaceDown && !deviceFaceDown) return false
        if (dndMode.blockedBy(dndSuppressEnabled, dndActive)) return false
        if (quietHoursBlocked(
                quietHoursMode,
                quietHoursEnabled,
                quietHoursStartMinutes,
                quietHoursEndMinutes,
                quietStartOverride,
                quietEndOverride,
                nowMinutes
            )
        ) {
            return false
        }
        return true
    }

    /** What the Do Not Disturb indicator layer shows: nothing, the on/off flash, or its look. */
    enum class DndLayer { NONE, FLASH, LOOK }

    /**
     * [visible] is whether its conditions (pause, face-down, quiet hours) allow it at all. The app
     * repeats the DND state as a heartbeat; once [stateUpdatedAtMs] is older than
     * [DND_STATE_STALE_MS] the layer stays dark, since an app that died can't report DND ending.
     * A flash in progress plays out even with the indicator's look turned off or timed out.
     */
    fun dndLayer(
        visible: Boolean,
        stateUpdatedAtMs: Long,
        dndActive: Boolean,
        dndActiveSinceMs: Long,
        flashStartedAtMs: Long?,
        showIndicator: Boolean,
        showForMs: Long?,
        nowMs: Long
    ): DndLayer {
        if (!visible) return DndLayer.NONE
        if (stateUpdatedAtMs == 0L || nowMs - stateUpdatedAtMs > DND_STATE_STALE_MS) return DndLayer.NONE
        if (flashStartedAtMs != null && nowMs - flashStartedAtMs in 0 until PatternRenderer.DND_FLASH_MS) return DndLayer.FLASH
        if (!dndActive || !showIndicator) return DndLayer.NONE
        if (showForMs != null && nowMs - dndActiveSinceMs >= showForMs) return DndLayer.NONE
        return DndLayer.LOOK
    }

    // Comfortably longer than the app's heartbeat, as for the battery layer.
    const val DND_STATE_STALE_MS = 3 * 60_000L

    fun canShowNotification(
        callActive: Boolean,
        requiresFaceDown: Boolean,
        deviceFaceDown: Boolean,
        dndMode: DndMode = DndMode.ALWAYS,
        dndSuppressEnabled: Boolean = false,
        dndActive: Boolean = false,
        quietHoursMode: QuietHoursMode = QuietHoursMode.ALWAYS,
        quietHoursEnabled: Boolean = false,
        quietHoursStartMinutes: Int = 0,
        quietHoursEndMinutes: Int = 0,
        quietStartOverride: Int? = null,
        quietEndOverride: Int? = null,
        nowMinutes: Int = 0
    ): Boolean {
        if (callActive) return false
        return canShowAlert(
            requiresFaceDown,
            deviceFaceDown,
            dndMode,
            dndSuppressEnabled,
            dndActive,
            quietHoursMode,
            quietHoursEnabled,
            quietHoursStartMinutes,
            quietHoursEndMinutes,
            quietStartOverride,
            quietEndOverride,
            nowMinutes
        )
    }
}
