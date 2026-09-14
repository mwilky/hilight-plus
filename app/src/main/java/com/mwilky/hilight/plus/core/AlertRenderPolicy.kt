package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.isInQuietHoursWindow

/**
 * Composed visibility for alerts. Unlock pause and an active call hide notifications;
 * face-down, DND, and quiet hours are per-alert and do not use the unlock pause flag.
 */
internal object AlertRenderPolicy {

    fun canShowAlert(
        requiresFaceDown: Boolean,
        deviceFaceDown: Boolean,
        suppressDuringDnd: Boolean = false,
        dndActive: Boolean = false,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null,
        nowMinutes: Int = 0
    ): Boolean {
        if (requiresFaceDown && !deviceFaceDown) return false
        if (suppressDuringDnd && dndActive) return false
        if (quietStartMinutes != null && quietEndMinutes != null &&
            isInQuietHoursWindow(nowMinutes, quietStartMinutes, quietEndMinutes)
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
        suppressDuringDnd: Boolean = false,
        dndActive: Boolean = false,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null,
        nowMinutes: Int = 0
    ): Boolean {
        if (unlockPaused || callActive) return false
        return canShowAlert(
            requiresFaceDown,
            deviceFaceDown,
            suppressDuringDnd,
            dndActive,
            quietStartMinutes,
            quietEndMinutes,
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
