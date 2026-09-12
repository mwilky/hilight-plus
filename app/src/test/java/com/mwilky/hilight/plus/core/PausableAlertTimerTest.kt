package com.mwilky.hilight.plus.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PausableAlertTimerTest {

    @Test
    fun playbackConsumesDuration() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 1_000L)

        assertEquals(20_000L, timer.remainingMs(11_000L))
        assertEquals(10_000L, timer.elapsedMs(11_000L))
    }

    @Test
    fun pausePreservesRemainingDurationAndAnimationTime() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 1_000L)
        timer.pause(nowMs = 11_000L)

        assertEquals(20_000L, timer.remainingMs(100_000L))
        assertEquals(10_000L, timer.elapsedMs(100_000L))

        timer.resume(nowMs = 100_000L)

        assertEquals(15_000L, timer.remainingMs(105_000L))
        assertEquals(15_000L, timer.elapsedMs(105_000L))
    }

    @Test
    fun startingWhileSuppressedDoesNotConsumeDuration() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 1_000L, paused = true)

        assertEquals(30_000L, timer.remainingMs(100_000L))

        timer.resume(nowMs = 100_000L)

        assertEquals(25_000L, timer.remainingMs(105_000L))
    }

    @Test
    fun repeatedPauseAndResumeCallsDoNotResetTheTimer() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 0L)
        timer.pause(nowMs = 10_000L)
        timer.pause(nowMs = 50_000L)
        timer.resume(nowMs = 100_000L)
        timer.resume(nowMs = 105_000L)

        assertEquals(10_000L, timer.remainingMs(110_000L))
    }

    @Test
    fun expiredTimerCannotBeResumed() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 5_000L, nowMs = 0L)
        timer.pause(nowMs = 10_000L)
        timer.resume(nowMs = 20_000L)

        assertEquals(0L, timer.remainingMs(21_000L))
        assertEquals(5_000L, timer.elapsedMs(21_000L))
    }

    @Test
    fun replacingAnAlertStartsAFreshDuration() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 0L)
        timer.pause(nowMs = 10_000L)
        timer.start(durationMs = 15_000L, nowMs = 20_000L, paused = true)

        assertEquals(15_000L, timer.remainingMs(100_000L))
        assertEquals(0L, timer.elapsedMs(100_000L))
    }

    @Test
    fun clearDiscardsTheRemainingDuration() {
        val timer = PausableAlertTimer()
        timer.start(durationMs = 30_000L, nowMs = 0L)
        timer.clear()
        timer.resume(nowMs = 10_000L)

        assertEquals(0L, timer.remainingMs(20_000L))
        assertEquals(0L, timer.elapsedMs(20_000L))
    }
}
