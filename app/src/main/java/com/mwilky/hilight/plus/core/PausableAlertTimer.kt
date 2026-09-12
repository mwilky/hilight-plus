package com.mwilky.hilight.plus.core

/**
 * Duration accounting using caller-supplied monotonic timestamps.
 * Paused time does not contribute to playback duration.
 */
internal class PausableAlertTimer {
    private var durationMs = 0L
    private var remainingAtPauseMs = 0L
    private var resumedAtMs: Long? = null

    fun start(durationMs: Long, nowMs: Long, paused: Boolean = false) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        remainingAtPauseMs = this.durationMs
        resumedAtMs = if (paused || this.durationMs == 0L) null else nowMs
    }

    fun remainingMs(nowMs: Long): Long {
        val startedAt = resumedAtMs ?: return remainingAtPauseMs
        val elapsed = (nowMs - startedAt).coerceAtLeast(0L)
        return (remainingAtPauseMs - elapsed).coerceAtLeast(0L)
    }

    fun elapsedMs(nowMs: Long): Long = durationMs - remainingMs(nowMs)

    fun pause(nowMs: Long) {
        remainingAtPauseMs = remainingMs(nowMs)
        resumedAtMs = null
    }

    fun resume(nowMs: Long) {
        if (resumedAtMs == null && remainingAtPauseMs > 0L) {
            resumedAtMs = nowMs
        }
    }

    fun clear() {
        durationMs = 0L
        remainingAtPauseMs = 0L
        resumedAtMs = null
    }
}
