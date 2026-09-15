package com.mwilky.hilight.plus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip and fallback coverage for [BatterySettings], the single global config for the
 * battery indicator layer (not a per-target rule, so it has its own JSON shape).
 */
class BatterySettingsJsonTest {

    @Test
    fun roundTripsEveryFieldThroughJsonString() {
        val settings = BatterySettings(
            visibility = BatteryVisibility.FACE_DOWN_ONLY,
            chargingPattern = BatteryPattern.GRADIENT_RING,
            autoColor = false,
            color = 0xFF8A2BE2,
            lowWarningEnabled = false,
            lowThresholdPercent = 35,
            fullTimeout = BatteryFullTimeout.THIRTY_MIN,
            overridesNotifications = true,
            quietHoursMode = QuietHoursMode.SKIP
        )
        assertEquals(settings, BatterySettings.fromJson(settings.toJson().toString()))
    }

    @Test
    fun missingRawStringFallsBackToDefaults() {
        assertEquals(BatterySettings(), BatterySettings.fromJson(null as String?))
        assertEquals(BatterySettings(), BatterySettings.fromJson(""))
    }

    @Test
    fun malformedJsonFallsBackToDefaultsInsteadOfThrowing() {
        assertEquals(BatterySettings(), BatterySettings.fromJson("{not valid json"))
    }

    @Test
    fun missingFieldsFallBackToDefaultsIndividually() {
        val parsed = BatterySettings.fromJson(JSONObject().toString())
        assertEquals(BatterySettings(), parsed)
    }

    @Test
    fun unrecognisedEnumValuesFallBackInsteadOfThrowing() {
        val json = JSONObject().apply {
            put("visibility", "NOT_REAL")
            put("chargingPattern", "ALSO_NOT_REAL")
            put("fullTimeout", "NOPE")
            put("quietHoursMode", "GARBAGE")
        }
        val parsed = BatterySettings.fromJson(json.toString())
        assertEquals(BatteryVisibility.OFF, parsed.visibility)
        assertEquals(BatteryPattern.CHARGE_FILL, parsed.chargingPattern)
        assertEquals(BatteryFullTimeout.FIVE_MIN, parsed.fullTimeout)
        assertEquals(QuietHoursMode.INHERIT, parsed.quietHoursMode)
    }

    @Test
    fun stayOnTimeoutHasNullMinutes() {
        assertEquals(null, BatteryFullTimeout.STAY_ON.minutes)
        assertEquals(5, BatteryFullTimeout.FIVE_MIN.minutes)
    }
}
