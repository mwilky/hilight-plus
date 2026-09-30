package com.mwilky.hilight.plus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip and migration coverage for the shared rule JSON logic in Models.kt.
 * Each rule type has its own default color/id shape but shares parsing/writing of
 * pattern, enable state, and the DND/quiet-hours condition fields.
 */
class RuleModelJsonTest {

    @Test
    fun contactRuleRoundTripsEveryField() {
        val rule = ContactRule(
            id = "abc-123",
            name = "Sarah Connor",
            color = 0xFFEA4335,
            pattern = PatternMode.COMET,
            isEnabled = false,
            faceDownMode = FaceDownMode.ONLY_FACE_DOWN,
            dndMode = DndMode.SKIP,
            quietHoursMode = QuietHoursMode.SKIP,
            quietHoursStartMinutes = 90,
            quietHoursEndMinutes = 300,
            brightness = 0.4f
        )
        assertEquals(rule, ContactRule.fromJson(rule.toJson()))
    }

    @Test
    fun messageContactRuleRoundTripsEveryField() {
        val rule = MessageContactRule(
            id = "msg-1",
            name = "Sarah Connor",
            color = 0xFF8A2BE2,
            pattern = PatternMode.SPARKLE,
            isEnabled = false,
            faceDownMode = FaceDownMode.ALWAYS,
            dndMode = DndMode.ALWAYS,
            quietHoursMode = QuietHoursMode.ALWAYS,
            quietHoursStartMinutes = null,
            quietHoursEndMinutes = null,
            brightness = 0.1f
        )
        assertEquals(rule, MessageContactRule.fromJson(rule.toJson()))
    }

    @Test
    fun appNotificationRuleRoundTripsEveryFieldIncludingAutoColor() {
        val rule = AppNotificationRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            color = 0xFF25D366,
            pattern = PatternMode.RAINBOW,
            isEnabled = true,
            faceDownMode = FaceDownMode.INHERIT,
            isAutoColor = false,
            dndMode = DndMode.INHERIT,
            quietHoursMode = QuietHoursMode.INHERIT,
            quietHoursStartMinutes = 60,
            quietHoursEndMinutes = 420,
            brightness = 0.7f
        )
        assertEquals(rule, AppNotificationRule.fromJson(rule.toJson()))
    }

    @Test
    fun contactRuleFallsBackToItsOwnDefaultsWhenFieldsAreMissing() {
        val parsed = ContactRule.fromJson(JSONObject())
        assertEquals(0xFF4285F4, parsed.color)
        assertEquals(PatternMode.PULSE, parsed.pattern)
        assertEquals(true, parsed.isEnabled)
        assertEquals(FaceDownMode.INHERIT, parsed.faceDownMode)
    }

    @Test
    fun messageContactRuleFallsBackToItsOwnDefaultColor() {
        val parsed = MessageContactRule.fromJson(JSONObject())
        assertEquals(0xFF00E5FF, parsed.color)
    }

    @Test
    fun appNotificationRuleFallsBackToItsOwnDefaultColorAndAutoColor() {
        val parsed = AppNotificationRule.fromJson(JSONObject())
        assertEquals(0xFF34A853, parsed.color)
        assertEquals(true, parsed.isAutoColor)
    }

    @Test
    fun unrecognisedEnumValuesFallBackInsteadOfThrowing() {
        val json = JSONObject().apply {
            put("id", "x")
            put("name", "Bad Data")
            put("pattern", "NOT_A_REAL_PATTERN")
            put("faceDownMode", "GARBAGE")
            put("dndMode", "")
        }
        val parsed = ContactRule.fromJson(json)
        assertEquals(PatternMode.PULSE, parsed.pattern)
        assertEquals(FaceDownMode.INHERIT, parsed.faceDownMode)
        assertEquals(DndMode.INHERIT, parsed.dndMode)
    }

    /**
     * A JSON blob shaped exactly like what earlier app versions persisted to DataStore,
     * pinned as a literal so a future refactor of the parsing code can't silently break
     * reading a real user's existing saved rules.
     */
    @Test
    fun migratesPreviouslyPersistedContactRuleJson() {
        val legacyJson = JSONObject(
            """
            {
              "id": "c1",
              "name": "Mom",
              "color": 4293467747,
              "pattern": "BREATHE",
              "isEnabled": true,
              "faceDownMode": "ALWAYS",
              "dndMode": "INHERIT",
              "quietHoursMode": "SKIP",
              "quietHoursStartMinutes": 1320,
              "quietHoursEndMinutes": 420
            }
            """.trimIndent()
        )
        val parsed = ContactRule.fromJson(legacyJson)
        assertEquals("c1", parsed.id)
        assertEquals("Mom", parsed.name)
        assertEquals(PatternMode.BREATHE, parsed.pattern)
        assertEquals(FaceDownMode.ALWAYS, parsed.faceDownMode)
        assertEquals(QuietHoursMode.SKIP, parsed.quietHoursMode)
        assertEquals(1320, parsed.quietHoursStartMinutes)
        assertEquals(420, parsed.quietHoursEndMinutes)
        // Saved before brightness existed, so it keeps lighting at full brightness.
        assertEquals(DEFAULT_BRIGHTNESS, parsed.brightness)
    }

    @Test
    fun migratesPreviouslyPersistedAppRuleJsonWithoutQuietHoursOverride() {
        val legacyJson = JSONObject(
            """
            {
              "packageName": "com.whatsapp",
              "appName": "WhatsApp",
              "color": 2447360,
              "pattern": "PULSE",
              "isEnabled": true,
              "faceDownMode": "INHERIT",
              "isAutoColor": true,
              "dndMode": "INHERIT",
              "quietHoursMode": "INHERIT"
            }
            """.trimIndent()
        )
        val parsed = AppNotificationRule.fromJson(legacyJson)
        assertEquals("com.whatsapp", parsed.packageName)
        assertEquals(true, parsed.isAutoColor)
        assertEquals(null, parsed.quietHoursStartMinutes)
        assertEquals(null, parsed.quietHoursEndMinutes)
        assertEquals(DEFAULT_BRIGHTNESS, parsed.brightness)
    }

    @Test
    fun outOfRangeBrightnessIsClampedToWhatTheSliderAllows() {
        assertEquals(MIN_BRIGHTNESS, ContactRule.fromJson(JSONObject().put("brightness", 0.0)).brightness)
        assertEquals(MIN_BRIGHTNESS, ContactRule.fromJson(JSONObject().put("brightness", -3.0)).brightness)
        assertEquals(1f, ContactRule.fromJson(JSONObject().put("brightness", 2.5)).brightness)
    }

    @Test
    fun nonNumericBrightnessFallsBackToFull() {
        assertEquals(DEFAULT_BRIGHTNESS, AppNotificationRule.fromJson(JSONObject().put("brightness", "bright")).brightness)
    }
}
