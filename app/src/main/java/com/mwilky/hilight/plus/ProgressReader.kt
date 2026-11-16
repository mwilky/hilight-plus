package com.mwilky.hilight.plus

/** Something a notification says is under way, for the ring to drain or fill while it runs. */
sealed interface ProgressReading

/**
 * A timer as of when its notification was posted: whether it is counting down, how long is left,
 * and how long it was set for when the notification says.
 */
data class TimerReading(val running: Boolean, val remainingMs: Long, val totalMs: Long? = null) : ProgressReading

/** A progress bar part way along: [fraction] done, from 0 to just under 1. */
data class BarReading(val fraction: Float) : ProgressReading

/**
 * Reads what a notification says is under way. The Android types (a timer metric) are read by
 * the notification listener; these are the parts that only need plain values.
 */
object ProgressReader {
    const val CLOCK_PACKAGE = "com.google.android.deskclock"

    private const val STATE_RUNNING = "RUNNING"
    private val STATES = setOf(STATE_RUNNING, "PAUSED", "EXPIRED", "MISSED", "RESET")
    private const val TOTAL_MARK = 'Σ'
    private const val REMAINING_MARK = '⏳'

    /**
     * Older Google Clock draws its timer in a custom view, so the usual title and text are empty,
     * but it sorts its notifications by a key that spells the timer out, as of the moment it posted:
     * `1 0|↺3|RUNNING|▶12:16:12.338|Σ0:05:00|Δ0:00:06|⏳0:04:54` (state, then Σ the length it was
     * set for, Δ the time gone and ⏳ the time left). Null when [sortKey] isn't one of those.
     */
    fun fromSortKey(sortKey: String?): TimerReading? {
        val parts = sortKey?.split('|') ?: return null
        val state = parts.firstOrNull { it in STATES } ?: return null
        val remaining = parts.firstOrNull { it.startsWith(REMAINING_MARK) }?.let(::parseDuration) ?: return null
        val total = parts.firstOrNull { it.startsWith(TOTAL_MARK) }?.let(::parseDuration)?.takeIf { it > 0L }
        return TimerReading(running = state == STATE_RUNNING, remainingMs = remaining, totalMs = total)
    }

    /**
     * A notification showing the system's own countdown clock: [whenMs] is when it reaches zero.
     * It doesn't say how long the timer was set for. Null when it isn't counting down.
     */
    fun fromChronometer(showsChronometer: Boolean, countsDown: Boolean, whenMs: Long, postedAtMs: Long): TimerReading? {
        if (!showsChronometer || !countsDown || whenMs <= postedAtMs) return null
        return TimerReading(running = true, remainingMs = whenMs - postedAtMs)
    }

    /**
     * A progress bar that is under way. A full bar is finished rather than in progress, and a bar
     * with no length says nothing about how far along it is, so both are null.
     */
    fun fromProgressBar(progress: Int, max: Int, indeterminate: Boolean): BarReading? {
        if (indeterminate || max <= 0 || progress >= max) return null
        return BarReading(progress.coerceAtLeast(0).toFloat() / max)
    }

    /** `Σ0:05:00` or `⏳-0:00:03` as milliseconds; null if it isn't hours, minutes and seconds. */
    private fun parseDuration(part: String): Long? {
        val text = part.drop(1).trim()
        val fields = text.removePrefix("-").split(':').map { it.toLongOrNull() ?: return null }
        if (fields.size != 3) return null
        val ms = ((fields[0] * 60 + fields[1]) * 60 + fields[2]) * 1000L
        return if (text.startsWith("-")) -ms else ms
    }
}

/**
 * What the ring's progress slot should show for one notification: a timer ([totalMs] long,
 * ending at [endsAtElapsedMs] on the elapsed-realtime clock) or a bar ([fraction] done, with
 * [totalMs] 0), with the look and conditions of the app rule it came through.
 */
data class ProgressShow(
    val key: String,
    val totalMs: Long,
    val endsAtElapsedMs: Long,
    val fraction: Float,
    val color: Long,
    val brightness: Float,
    val requiresFaceDown: Boolean,
    val dndMode: DndMode,
    val quietHoursMode: QuietHoursMode,
    val quietStartMinutes: Int?,
    val quietEndMinutes: Int?
) {
    val isTimer: Boolean get() = totalMs > 0L
}
