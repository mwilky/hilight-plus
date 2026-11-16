package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressReaderTest {

    @Test
    fun `reads a running timer from Clock's sort key`() {
        val reading = ProgressReader.fromSortKey("1 0|↺3|RUNNING|▶12:16:12.338|Σ0:05:00|Δ0:00:06|⏳0:04:54")
        assertEquals(TimerReading(running = true, remainingMs = 294_000L, totalMs = 300_000L), reading)
    }

    @Test
    fun `reads hours`() {
        val reading = ProgressReader.fromSortKey("1 0|↺3|RUNNING|▶09:00:00.000|Σ1:30:00|Δ0:10:00|⏳1:20:00")
        assertEquals(90 * 60_000L, reading?.totalMs)
        assertEquals(80 * 60_000L, reading?.remainingMs)
    }

    @Test
    fun `a paused or expired timer is not running`() {
        assertEquals(false, ProgressReader.fromSortKey("1 0|↺3|PAUSED|▶12:16:12.338|Σ0:05:00|Δ0:01:00|⏳0:04:00")?.running)
        val expired = ProgressReader.fromSortKey("0 1|↺3|EXPIRED|▶12:16:49.477|Σ0:00:12|Δ0:00:12|⏳0:00:00")
        assertEquals(TimerReading(running = false, remainingMs = 0L, totalMs = 12_000L), expired)
    }

    @Test
    fun `time past zero reads as negative`() {
        assertEquals(-3_000L, ProgressReader.fromSortKey("0 1|↺3|EXPIRED|Σ0:00:12|Δ0:00:15|⏳-0:00:03")?.remainingMs)
    }

    @Test
    fun `other sort keys are not timers`() {
        assertNull(ProgressReader.fromSortKey(null))
        assertNull(ProgressReader.fromSortKey(""))
        assertNull(ProgressReader.fromSortKey("a|b|c"))
        assertNull(ProgressReader.fromSortKey("1 0|RUNNING|no times here"))
        assertNull(ProgressReader.fromSortKey("1 0|RUNNING|Σ0:05:00|⏳soon"))
    }

    @Test
    fun `a countdown chronometer gives the time left but not the length`() {
        assertEquals(
            TimerReading(running = true, remainingMs = 90_000L, totalMs = null),
            ProgressReader.fromChronometer(showsChronometer = true, countsDown = true, whenMs = 1_090_000L, postedAtMs = 1_000_000L)
        )
        assertNull(ProgressReader.fromChronometer(showsChronometer = true, countsDown = false, whenMs = 1_090_000L, postedAtMs = 1_000_000L))
        assertNull(ProgressReader.fromChronometer(showsChronometer = false, countsDown = true, whenMs = 1_090_000L, postedAtMs = 1_000_000L))
        assertNull(ProgressReader.fromChronometer(showsChronometer = true, countsDown = true, whenMs = 900_000L, postedAtMs = 1_000_000L))
    }

    @Test
    fun `a progress bar under way gives how far along it is`() {
        assertEquals(BarReading(0.25f), ProgressReader.fromProgressBar(progress = 25, max = 100, indeterminate = false))
        assertEquals(BarReading(0f), ProgressReader.fromProgressBar(progress = 0, max = 100, indeterminate = false))
        assertEquals(BarReading(0f), ProgressReader.fromProgressBar(progress = -5, max = 100, indeterminate = false))
    }

    @Test
    fun `a finished, endless or empty bar is not progress`() {
        assertNull(ProgressReader.fromProgressBar(progress = 100, max = 100, indeterminate = false))
        assertNull(ProgressReader.fromProgressBar(progress = 30, max = 100, indeterminate = true))
        assertNull(ProgressReader.fromProgressBar(progress = 0, max = 0, indeterminate = false))
    }
}
