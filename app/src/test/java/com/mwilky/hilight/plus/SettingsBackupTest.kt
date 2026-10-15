package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The settings file: every type comes back as it went in, and the licence never travels. */
class SettingsBackupTest {

    private val settings: Map<String, Any> = mapOf(
        "onboarding_completed" to true,
        "quiet_hours_start_min" to 1320,
        "other_contacts_color" to 0xFFFF0000L,
        "other_contacts_brightness" to 0.75f,
        "app_rules_json" to """[{"packageName":"com.whatsapp"}]""",
        "pause_last_duration" to "ONE_HOUR",
        "some_set" to setOf("a", "b")
    )

    @Test
    fun everyTypeRoundTrips() {
        val restored = SettingsBackup.decode(SettingsBackup.encode(settings, appVersionCode = 18)).getOrThrow()
        assertEquals(settings, restored)
    }

    @Test
    fun licenceAndAPauseInForceAreLeftOut() {
        val withLicence = settings + mapOf(
            "purchased" to true,
            "trial_start_ms" to 123L,
            "pause_until_ms" to 456L,
            "pause_features" to "notifications"
        )
        val restored = SettingsBackup.decode(SettingsBackup.encode(withLicence, appVersionCode = 18)).getOrThrow()
        assertEquals(settings, restored)
    }

    @Test
    fun aHandEditedFileCannotUnlockTheApp() {
        val raw = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{"purchased":{"type":"boolean","value":true}}}"""
        assertFalse("purchased" in SettingsBackup.decode(raw).getOrThrow())
    }

    @Test
    fun anythingElseIsRejectedWhole() {
        assertTrue(SettingsBackup.decode("not json").isFailure)
        assertTrue(SettingsBackup.decode("""{"format":"something-else","version":1,"settings":{}}""").isFailure)
        assertTrue(SettingsBackup.decode("""{"format":"${SettingsBackup.FORMAT}","version":99,"settings":{}}""").isFailure)
        val badType = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{"x":{"type":"bytes","value":"AA=="}}}"""
        assertTrue(SettingsBackup.decode(badType).isFailure)
    }
}
