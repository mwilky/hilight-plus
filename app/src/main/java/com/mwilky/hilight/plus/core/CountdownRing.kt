package com.mwilky.hilight.plus.core

/**
 * A running timer drawn on the ring: every LED lit at the start, one going out for each equal
 * share of the time, so the lit arc is how much is left.
 *
 * The diffused ring only shows whole LEDs with hard edges, so the finer detail is carried by
 * colour and by time instead: green for the first half, amber for the second, and then the last
 * LED's share is drawn again as a full red ring draining at eight times the pace (the final lap).
 * A timer too short for that to be readable just turns its last LED red.
 */
object CountdownRing {

    /** Below this a final lap would step faster than it can be read. */
    const val LAP_MIN_TOTAL_MS = 120_000L

    private const val GREEN = 0xFF00FF00.toInt()
    private const val AMBER = 0xFFFFAA00.toInt()
    private const val RED = 0xFFFF0000.toInt()

    /** How long the final lap lasts, or 0 when [totalMs] is too short to have one. */
    fun lapMs(totalMs: Long, ledCount: Int): Long =
        if (totalMs >= LAP_MIN_TOTAL_MS && ledCount > 0) totalMs / ledCount else 0L

    /** The ring with [remainingMs] left of a timer [totalMs] long; dark once it has run out. */
    fun frame(totalMs: Long, remainingMs: Long, ledCount: Int): IntArray {
        val frame = IntArray(ledCount)
        if (totalMs <= 0L || remainingMs <= 0L || ledCount <= 0) return frame
        val remaining = minOf(remainingMs, totalMs)
        val lap = lapMs(totalMs, ledCount)
        val inLap = lap > 0L && remaining <= lap
        val span = if (inLap) lap else totalMs
        val lit = ((remaining * ledCount + span - 1) / span).toInt().coerceIn(1, ledCount)
        val color = when {
            inLap || remaining * ledCount <= totalMs -> RED
            remaining * 2 <= totalMs -> AMBER
            else -> GREEN
        }
        for (i in 0 until lit) frame[i] = color
        return frame
    }

    /**
     * The times left at which the ring changes, for a timer with [remainingMs] to go: every one
     * still ahead, soonest first. The ring going dark at the end isn't one of them.
     */
    fun changesAhead(totalMs: Long, remainingMs: Long, ledCount: Int): List<Long> {
        if (totalMs <= 0L || ledCount <= 0) return emptyList()
        val remaining = minOf(remainingMs, totalMs)
        val changes = sortedSetOf<Long>(reverseOrder())
        for (k in 1 until ledCount) changes += k * totalMs / ledCount
        changes += totalMs / 2
        val lap = lapMs(totalMs, ledCount)
        if (lap > 0L) for (j in 1 until ledCount) changes += j * lap / ledCount
        return changes.filter { it in 1 until remaining }
    }

    /**
     * Longest one LED is asked to hold a colour. Holds of a couple of minutes came out wrong on
     * the Pixel (an LED changed early, as if the delay had wrapped at 65.5 s), so a long hold is
     * sent as several of these in the same colour.
     */
    const val MAX_HOLD_MS = 60_000L

    private const val OPAQUE_BLACK = 0xFF000000.toInt()

    /**
     * As much of the rest of the countdown as fits in one effect, which the hardware plays through
     * by itself and then goes dark, so the ring keeps time while the phone sleeps and can't be
     * left lit. A long timer needs more keyframes than an LED may have, so the effect then stops
     * short of the end, on the colours showing at that point; its [RingEffect.durationMs] says
     * when, and the caller sends the next part from there.
     */
    fun effect(totalMs: Long, remainingMs: Long, periodMs: Long, ledCount: Int): RingEffect {
        val period = maxOf(1L, periodMs)
        val remaining = minOf(remainingMs, totalMs).coerceAtLeast(0L)
        fun align(t: Long) = (t + period / 2) / period * period
        val end = maxOf(period, align(remaining))
        val holdMax = maxOf(period, MAX_HOLD_MS / period * period)

        // When each look starts, from now, soonest first.
        val changes = changesAhead(totalMs, remaining, ledCount)
        val starts = listOf(0L) + changes.map { align(remaining - it) }
        val frames = listOf(frame(totalMs, remaining, ledCount)) + changes.map { frame(totalMs, it, ledCount) }

        // Each LED's own colour changes, then extra keyframes so no hold is longer than holdMax.
        val points = List(ledCount) { led ->
            val changed = mutableListOf<Pair<Long, Int>>()
            starts.forEachIndexed { i, at ->
                val color = frames[i][led] or OPAQUE_BLACK
                when {
                    changed.isEmpty() -> changed += 0L to color
                    color == changed.last().second -> Unit
                    at <= changed.last().first -> changed[changed.lastIndex] = changed.last().first to color
                    else -> changed += at to color
                }
            }
            changed += end to OPAQUE_BLACK
            val held = mutableListOf(changed[0])
            for (k in 1 until changed.size) {
                while (changed[k].first - held.last().first > holdMax) held += (held.last().first + holdMax) to held.last().second
                held += changed[k]
            }
            held
        }

        // Where the effect has to stop: the earliest point any LED runs out of keyframes.
        val stop = points.minOf { if (it.size > RingEffect.MAX_KEYFRAMES) it[RingEffect.MAX_KEYFRAMES - 1].first else end }
        val leds = points.map { all ->
            val kept = all.filter { it.first < stop }
            val last = if (stop == end) OPAQUE_BLACK else kept.last().second
            val times = kept.map { it.first } + stop
            val colors = kept.map { it.second } + last
            LedSequence(
                LongArray(times.size) { i -> if (i == 0) 0L else times[i] - times[i - 1] },
                colors.toIntArray()
            )
        }
        return RingEffect(leds, linear = false)
    }
}
