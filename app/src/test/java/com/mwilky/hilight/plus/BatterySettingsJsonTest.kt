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
            enabled = true,
            chargingPattern = BatteryPattern.GRADIENT_RING,
            lowPattern = LowBatteryPattern.SOLID,
            autoColor = false,
            color = 0xFF8A2BE2,
            showCharging = false,
            lowWarningEnabled = false,
            lowThresholdPercent = 35,
            fullTimeout = BatteryFullTimeout.THIRTY_MIN,
            overridesNotifications = true,
            faceDownMode = FaceDownMode.ONLY_FACE_DOWN,
            dndMode = DndMode.SKIP,
            quietHoursMode = QuietHoursMode.SKIP,
            quietHoursStartMinutes = 23 * 60,
            quietHoursEndMinutes = 6 * 60 + 30
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
            put("chargingPattern", "NOT_REAL")
            put("lowPattern", "NOT_REAL_EITHER")
            put("fullTimeout", "ALSO_NOT_REAL")
            put("faceDownMode", "NOPE")
            put("dndMode", "NOPE")
            put("quietHoursMode", "GARBAGE")
        }
        val parsed = BatterySettings.fromJson(json.toString())
        assertEquals(BatteryPattern.GAUGE, parsed.chargingPattern)
        assertEquals(LowBatteryPattern.HEARTBEAT, parsed.lowPattern)
        assertEquals(BatteryFullTimeout.FIVE_MIN, parsed.fullTimeout)
        assertEquals(FaceDownMode.INHERIT, parsed.faceDownMode)
        assertEquals(DndMode.INHERIT, parsed.dndMode)
        assertEquals(QuietHoursMode.INHERIT, parsed.quietHoursMode)
    }

    @Test
    fun stayOnTimeoutHasNullMinutes() {
        assertEquals(null, BatteryFullTimeout.STAY_ON.minutes)
        assertEquals(5, BatteryFullTimeout.FIVE_MIN.minutes)
    }
}
