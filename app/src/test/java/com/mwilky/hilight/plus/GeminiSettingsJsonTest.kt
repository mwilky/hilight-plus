package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiSettingsJsonTest {

    @Test
    fun roundTripsEveryState() {
        val settings = GeminiSettings(
            enabled = true,
            listening = GeminiLook(
                isEnabled = true,
                pattern = PatternMode.COMET,
                color = 0xFFEA4335,
                faceDownMode = FaceDownMode.ALWAYS,
                dndMode = DndMode.SKIP,
                quietHoursMode = QuietHoursMode.SKIP,
                quietHoursStartMinutes = 23 * 60,
                quietHoursEndMinutes = 6 * 60
            ),
            thinking = GeminiLook(isEnabled = false, pattern = PatternMode.GEMINI_THINKING),
            replying = GeminiLook(isEnabled = true, pattern = PatternMode.BREATHE, color = 0xFF34A853)
        )
        assertEquals(settings, GeminiSettings.fromJson(settings.toJson().toString()))
    }

    @Test
    fun defaultsToOnWithTheStockLooks() {
        val settings = GeminiSettings.fromJson(null)
        assertEquals(true, settings.enabled)
        assertEquals(PatternMode.GEMINI_LISTENING, settings.listening.pattern)
        assertEquals(PatternMode.GEMINI_THINKING, settings.thinking.pattern)
        assertEquals(PatternMode.GEMINI_REPLYING, settings.replying.pattern)
    }

    @Test
    fun conditionsFollowTheConditionsPageByDefault() {
        GeminiState.entries.forEach { state ->
            val look = GeminiSettings().look(state)
            assertEquals(FaceDownMode.INHERIT, look.faceDownMode)
            assertEquals(DndMode.INHERIT, look.dndMode)
            assertEquals(QuietHoursMode.INHERIT, look.quietHoursMode)
        }
    }

    @Test
    fun badJsonFallsBackToDefaults() {
        assertEquals(GeminiSettings(), GeminiSettings.fromJson("{not json"))
    }

    @Test
    fun withLookChangesOnlyThatState() {
        val look = GeminiLook(isEnabled = false, pattern = PatternMode.PULSE)
        val changed = GeminiSettings().withLook(GeminiState.THINKING, look)
        assertEquals(look, changed.thinking)
        assertEquals(GeminiSettings().listening, changed.listening)
        assertEquals(GeminiSettings().replying, changed.replying)
    }
}
