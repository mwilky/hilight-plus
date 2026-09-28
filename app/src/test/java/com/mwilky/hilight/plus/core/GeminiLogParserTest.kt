package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.GeminiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The lines here are Gemini's own, as logged on a Pixel 11 Pro with its ring feedback on and off,
 * so a Gemini update that rewords them shows up as a failure rather than a dark ring.
 */
class GeminiLogParserTest {

    private fun run(vararg lines: String): List<GeminiState?> {
        var state: GeminiState? = null
        return lines.map { line -> GeminiLogParser.next(state, line).also { state = it } }
    }

    @Test
    fun aVoiceQueryGoesListeningThinkingReplyingThenIdle() {
        val states = run(
            "(REDACTED) On new invocation request(%s).",
            "startListeningAnimation",
            "stopListeningAnimation",
            "got an SPEECH_END event",
            "#onStreamingStarted()",
            "TTS starting",
            "TTS finished",
            "Query state updates finished."
        )
        assertEquals(
            listOf(
                null,
                GeminiState.LISTENING,
                GeminiState.THINKING,
                GeminiState.THINKING,
                GeminiState.REPLYING,
                GeminiState.REPLYING,
                null,
                null
            ),
            states
        )
    }

    @Test
    fun listeningStartedTwiceStaysListening() {
        assertEquals(
            listOf(GeminiState.LISTENING, GeminiState.LISTENING),
            run("startListeningAnimation", "startListeningAnimation")
        )
    }

    @Test
    fun speechEndingAfterAReplyStartsDoesNotGoBackToThinking() {
        assertEquals(GeminiState.REPLYING, run("startListeningAnimation", "TTS starting", "got an SPEECH_END event").last())
    }

    @Test
    fun swipingGeminiAwayEndsItMidReply() {
        val line = "aytj: UI dismissed (FloatyExit(chatControllerId=ChatControllerId(value=4ed83154)"
        assertNull(run("startListeningAnimation", "TTS starting", line).last())
    }

    @Test
    fun aTypedQueryStillShowsTheReply() {
        assertEquals(GeminiState.REPLYING, run("#onStreamingStarted()").last())
    }

    @Test
    fun unrelatedLinesChangeNothing() {
        assertEquals(
            listOf(GeminiState.LISTENING, GeminiState.LISTENING, GeminiState.LISTENING),
            run(
                "startListeningAnimation",
                "DEBUG_SCRIM: recomputeMergedState viewStates.keys=[INVOCATION]",
                "Lights session closed"
            )
        )
    }

    @Test
    fun everyStateHasALimit() {
        GeminiState.entries.forEach { assert(GeminiLogParser.maxDurationMs(it) > 0) }
    }
}
