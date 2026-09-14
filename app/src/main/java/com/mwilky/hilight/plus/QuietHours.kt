package com.mwilky.hilight.plus

import android.app.NotificationManager
import java.util.Calendar

/** Minutes from midnight. If [startMinutes] > [endMinutes], the window crosses midnight. */
internal fun isInQuietHoursWindow(
    nowMinutes: Int,
    startMinutes: Int,
    endMinutes: Int
): Boolean {
    val now = nowMinutes.mod(24 * 60)
    val start = startMinutes.mod(24 * 60)
    val end = endMinutes.mod(24 * 60)
    return if (start == end) {
        false
    } else if (start < end) {
        now in start until end
    } else {
        now >= start || now < end
    }
}

internal fun isInQuietHours(
    nowMinutes: Int,
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int
): Boolean = enabled && isInQuietHoursWindow(nowMinutes, startMinutes, endMinutes)

internal fun currentMinutesOfDay(nowMillis: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

internal fun isSystemDndActive(interruptionFilter: Int): Boolean =
    interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

internal fun SettingsSnapshot.suppressesDuringDnd(mode: DndMode): Boolean =
    mode.blockedBy(suppressDuringDnd, dndActive = true)

internal fun SettingsSnapshot.quietWindowFor(
    mode: QuietHoursMode,
    startOverride: Int? = null,
    endOverride: Int? = null
): Pair<Int, Int>? {
    if (!mode.blockedBy(quietHoursEnabled, inQuietHours = true)) return null
    val start = if (mode == QuietHoursMode.SKIP) {
        startOverride ?: quietHoursStartMinutes
    } else {
        quietHoursStartMinutes
    }
    val end = if (mode == QuietHoursMode.SKIP) {
        endOverride ?: quietHoursEndMinutes
    } else {
        quietHoursEndMinutes
    }
    return start to end
}

internal fun SettingsSnapshot.blocksArrival(
    dndMode: DndMode,
    quietHoursMode: QuietHoursMode,
    dndActive: Boolean,
    nowMinutes: Int = currentMinutesOfDay(),
    quietStartMinutes: Int? = null,
    quietEndMinutes: Int? = null
): Boolean {
    if (dndMode.blockedBy(suppressDuringDnd, dndActive)) return true
    val start = if (quietHoursMode == QuietHoursMode.SKIP) {
        quietStartMinutes ?: quietHoursStartMinutes
    } else {
        quietHoursStartMinutes
    }
    val end = if (quietHoursMode == QuietHoursMode.SKIP) {
        quietEndMinutes ?: quietHoursEndMinutes
    } else {
        quietHoursEndMinutes
    }
    val inWindow = isInQuietHoursWindow(nowMinutes, start, end)
    return quietHoursMode.blockedBy(quietHoursEnabled, inWindow)
}
