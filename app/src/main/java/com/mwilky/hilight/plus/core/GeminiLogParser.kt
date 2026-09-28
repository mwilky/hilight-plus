package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.GeminiState

/**
 * Follows Gemini's listening, thinking and replying from its own log lines. Gemini broadcasts none
 * of this, but it logs each change, and the daemon (shell UID) can read its log.
 *
 * Gemini's code is obfuscated and its log tags change between versions, so only the message text
 * is matched. These are the lines it logs with its own ring feedback on and off alike.
 */
object GeminiLogParser {

    private val LISTENING = listOf("startListeningAnimation")
    private val THINKING = listOf("stopListeningAnimation", "got an SPEECH_END event")
    private val REPLYING = listOf("#onStreamingStarted()", "TTS starting")
    private val IDLE = listOf("TTS finished", "Query state updates finished.", "UI dismissed")

    /**
     * The state after [line], given the [current] one (null is idle), or [current] unchanged when
     * the line means nothing here.
     */
    fun next(current: GeminiState?, line: String): GeminiState? = when {
        IDLE.any { it in line } -> null
        LISTENING.any { it in line } -> GeminiState.LISTENING
        // Speech ending only means thinking straight after listening; the same line also turns
        // up once a reply has started.
        THINKING.any { it in line } -> if (current == GeminiState.LISTENING) GeminiState.THINKING else current
        REPLYING.any { it in line } -> GeminiState.REPLYING
        else -> current
    }

    /**
     * Longest a state can last before it's taken to be over. A line Gemini didn't log, or a
     * conversation ended some way these lines don't cover, must not leave the ring lit.
     */
    fun maxDurationMs(state: GeminiState): Long = when (state) {
        GeminiState.LISTENING -> 30_000L
        GeminiState.THINKING -> 60_000L
        GeminiState.REPLYING -> 5 * 60_000L
    }
}
