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
