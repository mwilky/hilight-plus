package com.mwilky.hilight.plus.core

import kotlin.math.abs
import kotlin.math.max

/**
 * One LED's keyframes for a hardware light effect, in the form `ColorSequence` takes them: the
 * first delay is 0 and each later one is the time since the previous keyframe.
 */
class LedSequence(val delaysMs: LongArray, val colors: IntArray) {
    val durationMs: Long get() = delaysMs.sum()
}

/**
 * A looping animation for the whole ring that the lights hardware plays by itself, so nothing
 * has to push frames while it runs. Every LED's sequence lasts the same time, so they stay in step.
 */
class RingEffect(val leds: List<LedSequence>, val linear: Boolean) {

    val durationMs: Long get() = leds.maxOfOrNull { it.durationMs } ?: 0L

    /** Every LED holds one colour throughout. */
    val isStatic: Boolean get() = leds.all { led -> led.colors.all { it == led.colors[0] } }

    companion object {
        // The Pixel lights HAL packs the whole effect into a fixed-size buffer and aborts, taking
        // the lights service down, when it doesn't fit: 12 keyframes per LED crashed it, 11 did
        // not. 9 per LED, 72 across the ring, is what stock Gemini sends.
        const val MAX_KEYFRAMES = 9

        // Largest per-channel error the keyframe reduction may introduce, out of 255, before it
        // relaxes to fit MAX_KEYFRAMES. Below what the diffused ring shows.
        private const val TOLERANCE = 4

        // Fine enough that the reduction, not the sampling, decides where the keyframes go.
        private const val SAMPLE_MS = 20L

        private const val REFINE_PASSES = 3
        private const val ROTATION_PROBES = 16
        private const val ROTATION_MATCH = 3

        /**
         * Samples [frameAt] across one loop of [loopMs] and reduces it to the fewest keyframes that
         * reproduce it. Times are kept on multiples of [periodMs], the hardware's frame length, as
         * the lights service truncates every delay to a whole number of frames. [frameAt] is called
         * with times from 0 to [loopMs] inclusive; the loop end should match the start.
         *
         * A look made of hard steps (a strobe, the split ring stepping round) goes out as steps,
         * which draws it exactly in few keyframes; anything else as fades. Either way no LED gets
         * more than [MAX_KEYFRAMES].
         *
         * A look that turns round the ring, every LED showing the same thing in turn (a comet, a
         * wave, the rainbow), is built from one LED and copied to the rest, each shifted by its
         * place on the ring. Its loop is stretched to the nearest length that makes every shift a
         * whole number of hardware frames, so it moves evenly and every LED looks the same.
         */
        fun sample(
            loopMs: Long,
            periodMs: Long,
            ledCount: Int,
            frameAt: (Long) -> IntArray
        ): RingEffect {
            val period = max(1L, periodMs)
            val step = alignUp(SAMPLE_MS, period)
            val direction = rotationDirection(loopMs, ledCount, frameAt)
            if (direction != 0) return rotating(loopMs, step, ledCount, direction, frameAt)
            val end = alignUp(max(loopMs, step), period)
            val times = ArrayList<Long>()
            var t = 0L
            while (t < end) {
                times += t
                t += step
            }
            times += end
            val frames = times.map { time -> frameAt(minOf(time, loopMs)) }
            return build(times.toLongArray(), frames, ledCount)
        }

        private fun build(times: LongArray, frames: List<IntArray>, ledCount: Int): RingEffect {
            val colors = List(ledCount) { led -> IntArray(times.size) { i -> opaque(frames[i][led % frames[i].size]) } }
            val steps = colors.map { reduceSteps(it) }
            // Steps reproduce the samples exactly, so when they fit they win; a smooth look has a
            // change at nearly every sample and never fits.
            val useSteps = steps.all { it.size <= MAX_KEYFRAMES }
            val leds = List(ledCount) { led ->
                val keep = if (useSteps) steps[led] else reduceLinear(times, colors[led])
                toSequence(LongArray(keep.size) { times[keep[it]] }, IntArray(keep.size) { colors[led][keep[it]] })
            }
            return RingEffect(leds, linear = !useSteps)
        }

        /**
         * +1 when LED i shows at time t what LED 0 showed a 1/[ledCount] turn earlier times i (the
         * look turns towards higher LEDs), -1 for the other way, 0 when the look doesn't turn.
         */
        private fun rotationDirection(loopMs: Long, ledCount: Int, frameAt: (Long) -> IntArray): Int {
            if (ledCount < 2 || loopMs <= 0) return 0
            // Probe off the exact turn points, where a hard edge would compare two sides of a jump.
            val probes = (0 until ROTATION_PROBES).map { ((it + 0.37) * loopMs / ROTATION_PROBES).toLong() }
            val frames = probes.map { frameAt(it) }
            if (frames.all { f -> f.all { it == f[0] } } && frames.all { it[0] == frames[0][0] }) return 0
            for (direction in intArrayOf(1, -1)) {
                val turns = probes.indices.all { p ->
                    (1 until ledCount).all { led ->
                        val t = Math.floorMod(probes[p] - direction * led * loopMs / ledCount, loopMs)
                        channelDistance(frames[p][led], frameAt(t)[0]) <= ROTATION_MATCH
                    }
                }
                if (turns) return direction
            }
            return 0
        }

        /** See [sample]: one LED's keyframes, copied round the ring with whole-frame shifts. */
        private fun rotating(loopMs: Long, step: Long, ledCount: Int, direction: Int, frameAt: (Long) -> IntArray): RingEffect {
            val unit = step * ledCount
            val end = max(unit, (loopMs + unit / 2) / unit * unit)
            val n = (end / step).toInt()
            // LED 0 across one loop, with the pattern's time scaled to the adjusted loop length.
            val base = IntArray(n) { j -> opaque(frameAt(j * step * loopMs / end)[0]) }

            val changes = (0 until n).filter { base[it] != base[Math.floorMod(it - 1, n)] }
            val stepped = changes.isNotEmpty() && changes.size + 2 <= MAX_KEYFRAMES
            val vertices: List<Int> = if (stepped || changes.isEmpty()) {
                changes.ifEmpty { listOf(0) }
            } else {
                // Start the reduction at the sharpest change, so a comet's head is a keyframe.
                val start = (0 until n).maxBy { channelDistance(base[it], base[Math.floorMod(it - 1, n)]) }
                val times = LongArray(n + 1) { it * step }
                val colors = IntArray(n + 1) { base[(start + it) % n] }
                // Each LED's loop starts partway along this one, which costs it one more keyframe
                // than the reduction kept (the start is repeated at the end); leave room for it.
                reduceLinear(times, colors, MAX_KEYFRAMES - 1).dropLast(1).map { (it + start) % n }
            }

            val leds = List(ledCount) { led ->
                val shift = Math.floorMod(direction * led * (n / ledCount), n)
                val points = vertices.map { v -> (v + shift) % n to base[v] }.sortedBy { it.first }
                val atStart = points.firstOrNull { it.first == 0 }?.second ?: run {
                    val last = points.last()
                    val first = points.first()
                    if (stepped) {
                        last.second
                    } else {
                        val span = (n - last.first + first.first).toDouble()
                        lerp(last.second, first.second, (n - last.first) / span)
                    }
                }
                val times = ArrayList<Long>()
                val colors = ArrayList<Int>()
                times += 0L
                colors += atStart
                points.filter { it.first != 0 }.forEach { (at, color) -> times += at * step; colors += color }
                times += end
                colors += atStart
                toSequence(times.toLongArray(), colors.toIntArray())
            }
            return RingEffect(leds, linear = !stepped)
        }

        /** Hard steps: a keyframe only where the colour changes, plus the loop's first and last. */
        private fun reduceSteps(colors: IntArray): List<Int> {
            val keep = mutableListOf(0)
            for (i in 1 until colors.size - 1) {
                if (colors[i] != colors[keep.last()]) keep += i
            }
            if (colors.size > 1) keep += colors.size - 1
            return keep
        }

        /**
         * Fades: drops every sample a straight fade between its neighbours reproduces closely
         * enough (Ramer-Douglas-Peucker on the colour channels), relaxing the tolerance until the
         * result fits in [MAX_KEYFRAMES].
         */
        private fun reduceLinear(times: LongArray, colors: IntArray, maxKeyframes: Int = MAX_KEYFRAMES): List<Int> {
            var tolerance = TOLERANCE
            while (true) {
                val keep = BooleanArray(colors.size)
                keep[0] = true
                keep[colors.size - 1] = true
                simplify(times, colors, 0, colors.size - 1, tolerance, keep)
                val kept = keep.indices.filter { keep[it] }
                if (kept.size <= maxKeyframes || tolerance >= 255) return refine(times, colors, kept)
                tolerance = minOf(255, tolerance * 3 / 2 + 1)
            }
        }

        /**
         * The reduction picks keyframes greedily, so one can land just beside a corner rather than
         * on it. Moves each to whichever sample between its neighbours fits the fades either side
         * best, which snaps them onto the corners.
         */
        private fun refine(times: LongArray, colors: IntArray, kept: List<Int>): List<Int> {
            val keep = kept.toMutableList()
            repeat(REFINE_PASSES) {
                for (k in 1 until keep.size - 1) {
                    var best = keep[k]
                    var bestError = spanError(times, colors, keep[k - 1], best, keep[k + 1])
                    for (candidate in keep[k - 1] + 1 until keep[k + 1]) {
                        val error = spanError(times, colors, keep[k - 1], candidate, keep[k + 1])
                        if (error < bestError) {
                            bestError = error
                            best = candidate
                        }
                    }
                    keep[k] = best
                }
            }
            return keep
        }

        /** Worst error of the two fades from→via and via→to against the samples they skip. */
        private fun spanError(times: LongArray, colors: IntArray, from: Int, via: Int, to: Int): Int =
            max(segmentError(times, colors, from, via), segmentError(times, colors, via, to))

        private fun segmentError(times: LongArray, colors: IntArray, from: Int, to: Int): Int {
            var worst = 0
            for (i in from + 1 until to) {
                val f = (times[i] - times[from]).toDouble() / (times[to] - times[from])
                worst = max(worst, channelError(colors[i], colors[from], colors[to], f))
            }
            return worst
        }

        private fun simplify(times: LongArray, colors: IntArray, from: Int, to: Int, tolerance: Int, keep: BooleanArray) {
            if (to - from < 2) return
            var worst = -1
            var worstError = tolerance
            for (i in from + 1 until to) {
                val f = (times[i] - times[from]).toDouble() / (times[to] - times[from])
                val error = channelError(colors[i], colors[from], colors[to], f)
                if (error > worstError) {
                    worstError = error
                    worst = i
                }
            }
            if (worst < 0) return
            keep[worst] = true
            simplify(times, colors, from, worst, tolerance, keep)
            simplify(times, colors, worst, to, tolerance, keep)
        }

        private fun channelError(actual: Int, from: Int, to: Int, fraction: Double): Int {
            var worst = 0
            for (shift in intArrayOf(16, 8, 0)) {
                val a = (from ushr shift) and 0xFF
                val b = (to ushr shift) and 0xFF
                val expected = a + (b - a) * fraction
                worst = max(worst, abs(((actual ushr shift) and 0xFF) - expected).toInt())
            }
            return worst
        }

        private fun toSequence(times: LongArray, colors: IntArray): LedSequence {
            val delays = LongArray(times.size) { i -> if (i == 0) 0L else times[i] - times[i - 1] }
            return LedSequence(delays, colors)
        }

        private fun channelDistance(a: Int, b: Int): Int =
            intArrayOf(16, 8, 0).maxOf { abs(((a ushr it) and 0xFF) - ((b ushr it) and 0xFF)) }

        private fun lerp(from: Int, to: Int, fraction: Double): Int {
            fun ch(shift: Int): Int {
                val a = (from ushr shift) and 0xFF
                val b = (to ushr shift) and 0xFF
                return (a + (b - a) * fraction).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private fun alignUp(value: Long, period: Long): Long = ((value + period - 1) / period) * period

        // The ring ignores alpha, but "off" frames carry 0 there; keep every keyframe opaque.
        private fun opaque(color: Int): Int = color or 0xFF000000.toInt()
    }
}
