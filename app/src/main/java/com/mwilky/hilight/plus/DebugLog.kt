package com.mwilky.hilight.plus

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logcat plus a user-shareable log. Used from both processes: the app attaches a [DebugLogStore]
 * that keeps lines on disk, and the daemon attaches a sink that forwards its lines to the app, so
 * one file holds both sides in order.
 *
 * Lines logged before a sink is attached (daemon start-up, before the app registers) are held
 * and flushed on attach. Keep contact names and notification text out of messages: the log is
 * meant to be shared.
 */
object DebugLog {

    fun interface Sink {
        fun write(line: String)
    }

    /** Written into every line so the shared log shows which process said what. */
    @Volatile
    var source: String = "app"

    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var sink: Sink? = null

    fun d(tag: String, message: String, t: Throwable? = null) = log(Log.DEBUG, tag, message, t)
    fun i(tag: String, message: String, t: Throwable? = null) = log(Log.INFO, tag, message, t)
    fun w(tag: String, message: String, t: Throwable? = null) = log(Log.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(Log.ERROR, tag, message, t)

    fun attach(sink: Sink) {
        synchronized(lock) {
            this.sink = sink
            while (pending.isNotEmpty() && this.sink === sink) {
                sink.write(pending.removeFirst())
            }
        }
    }

    fun detach(sink: Sink) {
        synchronized(lock) {
            if (this.sink === sink) this.sink = null
        }
    }

    /** A line already logged to logcat and formatted elsewhere, i.e. forwarded from the daemon. */
    fun forward(line: String) = dispatch(line)

    private fun log(priority: Int, tag: String, message: String, t: Throwable?) {
        if (t == null) Log.println(priority, tag, message) else Log.println(priority, tag, message + '\n' + Log.getStackTraceString(t))
        val text = if (t == null) message else "$message: ${t.javaClass.simpleName}: ${t.message}"
        dispatch(format(System.currentTimeMillis(), levelChar(priority), source, tag, text))
    }

    private fun dispatch(line: String) {
        synchronized(lock) {
            val target = sink
            if (target != null) {
                target.write(line)
            } else {
                if (pending.size >= MAX_PENDING) pending.removeFirst()
                pending.addLast(line)
            }
        }
    }

    private fun levelChar(priority: Int): Char = when (priority) {
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        else -> 'V'
    }

    /** One line per entry: continuation lines are indented so the file stays line-oriented. */
    internal fun format(timeMs: Long, level: Char, source: String, tag: String, message: String): String {
        val time = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(timeMs))
        return "$time $level ${source.padEnd(6)} $tag: ${message.replace("\n", "\n    ")}"
    }

    /**
     * A notification key made safe to log. Keys can embed a tag such as a chat's phone number, so
     * only the package survives; the hash still tells different notifications apart.
     */
    fun redactKey(key: String): String {
        val pkg = key.split('|').getOrNull(1) ?: "?"
        return "$pkg#${Integer.toHexString(key.hashCode())}"
    }

    data class Parsed(val time: String, val level: Char, val source: String, val tag: String, val message: String)

    /** Splits a line made by [format] back into its parts, or null if it isn't one. */
    fun parse(line: String): Parsed? {
        val level = levelOf(line) ?: return null
        val sourceStart = TIME_PATTERN.length + 3
        val sourceEnd = sourceStart + 6
        if (line.length <= sourceEnd) return null
        val rest = line.substring(sourceEnd + 1)
        val split = rest.indexOf(": ")
        if (split < 0) return null
        return Parsed(
            time = line.substring(0, TIME_PATTERN.length),
            level = level,
            source = line.substring(sourceStart, sourceEnd).trim(),
            tag = rest.substring(0, split),
            message = rest.substring(split + 2).replace("\n    ", "\n")
        )
    }

    /** The level char of a formatted line, or null for a continuation line. */
    fun levelOf(line: String): Char? =
        line.getOrNull(TIME_PATTERN.length + 1)?.takeIf {
            it in "DIWEV" && line.first().isDigit() && line[TIME_PATTERN.length] == ' '
        }

    private const val TIME_PATTERN = "MM-dd HH:mm:ss.SSS"
    private const val MAX_PENDING = 500
}
