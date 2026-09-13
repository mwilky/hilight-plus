package com.mwilky.hilight.plus.core

/**
 * Composed visibility for alerts. Unlock pause and an active call hide notifications;
 * face-down restriction is per-alert and does not use the unlock pause flag.
 */
internal object AlertRenderPolicy {

    fun canShowAlert(requiresFaceDown: Boolean, deviceFaceDown: Boolean): Boolean {
        return !requiresFaceDown || deviceFaceDown
    }

    fun canShowNotification(
        unlockPaused: Boolean,
        callActive: Boolean,
        requiresFaceDown: Boolean,
        deviceFaceDown: Boolean
    ): Boolean {
        if (unlockPaused || callActive) return false
        return canShowAlert(requiresFaceDown, deviceFaceDown)
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
