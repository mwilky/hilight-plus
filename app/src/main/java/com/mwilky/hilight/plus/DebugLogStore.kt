package com.mwilky.hilight.plus

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * App-process end of [DebugLog]: keeps recent lines in memory for the live view on the About
 * page and appends every line to a small rotating file, so a report still has the history after
 * the app was killed or the phone restarted. Two files of [MAX_FILE_BYTES] at most.
 */
class DebugLogStore private constructor(context: Context) : DebugLog.Sink {

    data class Line(val id: Long, val text: String)

    private val dir = File(context.filesDir, "logs")
    private val current = File(dir, "debug.log")
    private val previous = File(dir, "debug.log.1")
    private val exportFile = File(File(context.cacheDir, "logs"), "hilight-plus-debug.txt")

    private val nextId = AtomicLong()
    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private sealed interface Op {
        data class Append(val text: String) : Op
        data object Clear : Op
        class Export(val header: String, val result: CompletableDeferred<File>) : Op
    }

    // One consumer, so appends, clears and exports reach the file in the order they were made.
    private val ops = Channel<Op>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            runCatching { loadHistory() }.onFailure { Log.w(TAG, "Couldn't read log history", it) }
            for (op in ops) {
                runCatching { handle(op) }.onFailure {
                    Log.w(TAG, "Log file operation failed", it)
                    if (op is Op.Export) op.result.completeExceptionally(it)
                }
            }
        }
    }

    override fun write(line: String) {
        val entry = Line(nextId.incrementAndGet(), line)
        _lines.update { (it + entry).takeLast(MAX_LINES) }
        ops.trySend(Op.Append(line))
    }

    fun clear() {
        _lines.value = emptyList()
        ops.trySend(Op.Clear)
        DebugLog.i(TAG, "Log cleared")
    }

    /** Writes [header] followed by the whole log to a file in the cache, ready to share. */
    suspend fun export(header: String): File {
        val result = CompletableDeferred<File>()
        ops.send(Op.Export(header, result))
        return result.await()
    }

    private fun handle(op: Op) {
        when (op) {
            is Op.Append -> {
                // Drain whatever else is already queued so a burst costs one file write.
                val batch = StringBuilder(op.text).append('\n')
                var next = ops.tryReceive().getOrNull()
                while (next is Op.Append) {
                    batch.append(next.text).append('\n')
                    next = ops.tryReceive().getOrNull()
                }
                append(batch.toString())
                if (next != null) handle(next)
            }
            Op.Clear -> {
                current.delete()
                previous.delete()
            }
            is Op.Export -> {
                exportFile.parentFile?.mkdirs()
                exportFile.writeText(op.header)
                for (file in listOf(previous, current)) {
                    if (file.exists()) exportFile.appendText(file.readText())
                }
                op.result.complete(exportFile)
            }
        }
    }

    private fun append(text: String) {
        dir.mkdirs()
        current.appendText(text)
        if (current.length() > MAX_FILE_BYTES) {
            previous.delete()
            current.renameTo(previous)
        }
    }

    /** Shows the tail of what's on disk, so the live view isn't empty after a restart. */
    private fun loadHistory() {
        val history = mutableListOf<String>()
        for (file in listOf(previous, current)) {
            if (!file.exists()) continue
            file.forEachLine { line ->
                // Continuation lines (stack traces) belong to the entry above them.
                if (DebugLog.levelOf(line) == null && history.isNotEmpty()) {
                    history[history.lastIndex] = history.last() + "\n" + line
                } else {
                    history += line
                }
            }
        }
        if (history.isEmpty()) return
        val loaded = history.takeLast(MAX_LINES).map { Line(nextId.incrementAndGet(), it) }
        _lines.update { (loaded + it).takeLast(MAX_LINES) }
    }

    companion object {
        private const val TAG = "DebugLogStore"
        private const val MAX_LINES = 1_000
        private const val MAX_FILE_BYTES = 256 * 1024L

        @Volatile
        private var instance: DebugLogStore? = null

        /** Creates the store on first use and makes it the app process's [DebugLog] sink. */
        fun get(context: Context): DebugLogStore =
            instance ?: synchronized(this) {
                instance ?: DebugLogStore(context.applicationContext).also {
                    instance = it
                    DebugLog.attach(it)
                }
            }
    }
}
