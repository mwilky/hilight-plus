package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.quietHoursBlocked

/**
 * Composed visibility for alerts. Unlock pause and an active call hide notifications;
 * face-down, DND, and quiet hours are per-alert and do not use the unlock pause flag.
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
        unlockPaused: Boolean,
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
        if (unlockPaused || callActive) return false
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

    fun firstEligibleIndex(
        requiresFaceDown: List<Boolean>,
        deviceFaceDown: Boolean,
        startIndex: Int
    ): Int? {
        if (requiresFaceDown.isEmpty()) return null
        val start = startIndex.coerceAtLeast(0) % requiresFaceDown.size
        for (offset in requiresFaceDown.indices) {
            val i = (start + offset) % requiresFaceDown.size
            if (canShowAlert(requiresFaceDown[i], deviceFaceDown)) return i
        }
        return null
    }
}
