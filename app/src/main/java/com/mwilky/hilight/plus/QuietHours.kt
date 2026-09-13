package com.mwilky.hilight.plus

import android.app.NotificationManager
import java.util.Calendar

/** Minutes from midnight. If [startMinutes] > [endMinutes], the window crosses midnight. */
internal fun isInQuietHours(
    nowMinutes: Int,
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int
): Boolean {
    if (!enabled) return false
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

internal fun currentMinutesOfDay(nowMillis: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

internal fun isSystemDndActive(interruptionFilter: Int): Boolean =
    interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

internal fun SettingsSnapshot.blocksArrival(
    dndMode: DndMode,
    quietHoursMode: QuietHoursMode,
    dndActive: Boolean,
    nowMinutes: Int = currentMinutesOfDay()
): Boolean {
    if (dndMode.blockedBy(suppressDuringDnd, dndActive)) return true
    val inQuiet = isInQuietHours(
        nowMinutes,
        quietHoursEnabled,
        quietHoursStartMinutes,
        quietHoursEndMinutes
    )
    return quietHoursMode.blockedBy(quietHoursEnabled, inQuiet)
}
