package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.GeminiState
import java.util.concurrent.TimeUnit

/**
 * Follows Gemini's state by reading the Gemini app's log, and reports each change to [onState]
 * (null when Gemini is idle). Runs only while enabled, as one `logcat` process limited to Gemini's
 * UID and printing only the message text. If that process ends, the state is reported idle, so
 * the ring can't be left showing a Gemini state nothing will end, and it is started again.
 */
class GeminiWatcher(private val onState: (GeminiState?) -> Unit) {

    private val lock = Any()
    private var thread: Thread? = null
    private var process: java.lang.Process? = null

    @Volatile
    private var enabled = false

    // Bumped on every start, so a thread left over from before a quick off-and-on stops and
    // never reports over the current one.
    @Volatile
    private var generation = 0

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            if (enabled == value) return
            enabled = value
            if (value) {
                val gen = ++generation
                thread = Thread({ run(gen) }, "HiLightPlus-Gemini").apply {
                    isDaemon = true
                    start()
                }
                DebugLog.i(TAG, "Watching Gemini")
            } else {
                thread?.interrupt()
                thread = null
                process?.destroy()
                process = null
                DebugLog.i(TAG, "Stopped watching Gemini")
            }
        }
    }

    private fun current(gen: Int) = enabled && gen == generation

    private fun run(gen: Int) {
        var failures = 0
        while (current(gen) && !Thread.currentThread().isInterrupted) {
            val uid = geminiUid()
            if (uid == null) {
                DebugLog.w(TAG, "Gemini ($GEMINI_PACKAGE) isn't installed; checking again later")
            } else {
                val lines = follow(uid, gen)
                if (lines > 0) failures = 0
            }
            if (!current(gen)) break
            onState(null)
            failures++
            try {
                Thread.sleep((RETRY_MS * failures).coerceAtMost(MAX_RETRY_MS))
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    /** Streams Gemini's log until it ends or watching stops. Returns how many lines it read. */
    private fun follow(uid: Int, gen: Int): Int {
        var state: GeminiState? = null
        var count = 0
        try {
            // -T 1: only what's logged from now on, not the backlog. -v raw: the message alone.
            val started = Runtime.getRuntime().exec(
                arrayOf("logcat", "-b", "main", "-v", "raw", "-T", "1", "--uid=$uid")
            )
            synchronized(lock) {
                if (!current(gen)) {
                    started.destroy()
                    return 0
                }
                process = started
            }
            runCatching { started.errorStream.close() }
            started.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!current(gen)) break
                    count++
                    val next = GeminiLogParser.next(state, line)
                    if (next != state) {
                        state = next
                        DebugLog.i(TAG, "Gemini -> ${next?.id ?: "idle"}")
                        onState(next)
                    }
                }
            }
        } catch (t: Throwable) {
            if (current(gen)) DebugLog.w(TAG, "Gemini log ended: ${t.message}")
        } finally {
            synchronized(lock) {
                if (current(gen)) {
                    process?.destroy()
                    process = null
                }
            }
        }
        return count
    }

    /** Gemini's app UID, from `cmd package list packages -U`, or null if it isn't installed. */
    private fun geminiUid(): Int? {
        var p: java.lang.Process? = null
        return try {
            p = Runtime.getRuntime().exec(arrayOf("cmd", "package", "list", "packages", "-U", GEMINI_PACKAGE))
            val output = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
            output.lineSequence()
                .firstOrNull { it.startsWith("package:$GEMINI_PACKAGE ") }
                ?.substringAfter("uid:")
                ?.trim()
                ?.toIntOrNull()
        } catch (t: Throwable) {
            DebugLog.w(TAG, "Couldn't look up Gemini's UID: ${t.message}")
            null
        } finally {
            p?.destroy()
        }
    }

    companion object {
        private const val TAG = "GeminiWatcher"
        const val GEMINI_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L
    }
}
