package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DndIndicatorSettingsJsonTest {

    @Test
    fun roundTripsEverySetting() {
        val settings = DndIndicatorSettings(
            enabled = true,
            indicatorEnabled = false,
            pattern = PatternMode.DND_DRIFT,
            color = 0xFFEA4335,
            brightness = 0.3f,
            speed = 1.5f,
            faceDownMode = FaceDownMode.ONLY_FACE_DOWN,
            quietHoursMode = QuietHoursMode.SKIP,
            quietHoursStartMinutes = 23 * 60,
            quietHoursEndMinutes = 6 * 60,
            showFor = DndIndicatorDuration.ONE_MINUTE,
            flashOnChange = false,
            overridesBattery = true
        )
        assertEquals(settings, DndIndicatorSettings.fromJson(settings.toJson().toString()))
    }

    @Test
    fun defaultsToOffWithTheDndSignAtFullBrightness() {
        val settings = DndIndicatorSettings.fromJson(null)
        assertFalse(settings.enabled)
        assertEquals(PatternMode.DND_MINUS, settings.pattern)
        // The same brightness as any new rule, so the slider behaves the same everywhere.
        assertEquals(DEFAULT_BRIGHTNESS, settings.brightness)
        assertEquals(DEFAULT_SPEED, settings.speed)
        assertEquals(DndIndicatorDuration.WHILE_ON, settings.showFor)
    }

    @Test
    fun conditionsFollowTheConditionsPageByDefault() {
        val settings = DndIndicatorSettings()
        assertEquals(FaceDownMode.INHERIT, settings.faceDownMode)
        assertEquals(QuietHoursMode.INHERIT, settings.quietHoursMode)
    }

    @Test
    fun missingKeysAndBadJsonFallBackToDefaults() {
        assertEquals(DndIndicatorSettings(), DndIndicatorSettings.fromJson("{}"))
        assertEquals(DndIndicatorSettings(), DndIndicatorSettings.fromJson(""))
        assertEquals(DndIndicatorSettings(), DndIndicatorSettings.fromJson("{not json"))
    }

    @Test
    fun anUnknownDurationFallsBackToWhileOn() {
        val parsed = DndIndicatorSettings.fromJson("""{"enabled":true,"showFor":"forever"}""")
        assertEquals(DndIndicatorDuration.WHILE_ON, parsed.showFor)
    }

    @Test
    fun pauseKnowsTheIndicator() {
        assertEquals(setOf(PauseFeature.DND, PauseFeature.CALLS), PauseFeature.parse("dnd,calls"))
        assertEquals(PauseFeature.ALL, PauseFeature.parse(PauseFeature.encode(PauseFeature.ALL)))
    }
}
