package com.mwilky.hilight.plus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    @Test
    fun titleContainsIgnoresCase() {
        val match = TextMatch(TextMatchField.TITLE, TextMatchType.CONTAINS, "delivery")
        assertTrue(match.matches("any.app", "Your DELIVERY is here", emptyList()))
        assertFalse(match.matches("any.app", "Your parcel is here", listOf("delivery today")))
    }

    @Test
    fun titleEqualsNeedsTheWholeTitle() {
        val match = TextMatch(TextMatchField.TITLE, TextMatchType.EQUALS, " Mum ")
        assertTrue(match.matches("any.app", "mum", emptyList()))
        assertTrue(match.matches("any.app", "  Mum  ", emptyList()))
        assertFalse(match.matches("any.app", "Mum and Dad", emptyList()))
    }

    @Test
    fun messageMatchesAnyOfItsParts() {
        val match = TextMatch(TextMatchField.MESSAGE, TextMatchType.CONTAINS, "verification code")
        assertTrue(match.matches("any.app", "Bank", listOf("New message", "Your verification code is 1234")))
        assertFalse(match.matches("any.app", "verification code", listOf("Hello")))
    }

    @Test
    fun messageEqualsComparesEachPart() {
        val match = TextMatch(TextMatchField.MESSAGE, TextMatchType.EQUALS, "ok")
        assertTrue(match.matches("any.app", null, listOf("Thanks!", "OK")))
        assertFalse(match.matches("any.app", null, listOf("ok then")))
    }

    @Test
    fun appLimitOnlyMatchesThatApp() {
        val match = TextMatch(TextMatchField.TITLE, TextMatchType.CONTAINS, "alert", "com.example.app", "Example")
        assertTrue(match.matches("com.example.app", "Alert", emptyList()))
        assertFalse(match.matches("com.other.app", "Alert", emptyList()))
    }

    @Test
    fun blankTextNeverMatches() {
        val match = TextMatch(TextMatchField.TITLE, TextMatchType.CONTAINS, "   ")
        assertFalse(match.matches("any.app", "Anything", listOf("Anything")))
    }

    @Test
    fun firstEnabledMatchingRuleWins() {
        val disabled = TextNotificationRule("a", TextMatch(text = "sale"), isEnabled = false)
        val first = TextNotificationRule("b", TextMatch(text = "sale"))
        val second = TextNotificationRule("c", TextMatch(text = "flash sale"))
        val snapshot = DEFAULT_SETTINGS_SNAPSHOT.copy(textRules = listOf(disabled, first, second))
        assertEquals("b", snapshot.findTextRule("any.app", "Flash sale now", emptyList())?.id)
        assertNull(snapshot.findTextRule("any.app", "Nothing here", emptyList()))
    }

    @Test
    fun textRuleRoundTripsEveryField() {
        val rule = TextNotificationRule(
            id = "text-1",
            match = TextMatch(TextMatchField.MESSAGE, TextMatchType.EQUALS, "Door open", "com.example.app", "Example"),
            color = 0xFF34A853,
            pattern = PatternMode.BEACON,
            isEnabled = false,
            faceDownMode = FaceDownMode.ONLY_FACE_DOWN,
            dndMode = DndMode.ALWAYS,
            quietHoursMode = QuietHoursMode.SKIP,
            quietHoursStartMinutes = 60,
            quietHoursEndMinutes = 420,
            brightness = 0.5f,
            speed = 1.5f
        )
        assertEquals(rule, TextNotificationRule.fromJson(rule.toJson()))
    }

    @Test
    fun textRuleWithoutAppReadsBackAsAnyApp() {
        val rule = TextNotificationRule("text-2", TextMatch(text = "Hi"))
        val parsed = TextNotificationRule.fromJson(JSONObject(rule.toJson().toString()))
        assertNull(parsed.match.packageName)
        assertNull(parsed.match.appName)
        assertEquals(TextMatchField.TITLE, parsed.match.field)
        assertEquals(TextMatchType.CONTAINS, parsed.match.type)
    }
}
