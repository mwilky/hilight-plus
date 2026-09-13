package com.mwilky.hilight.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatchTest {

    @Test
    fun exactNameMatchesIgnoringCaseAndSpace() {
        assertTrue(contactNamesMatch("Ann", "ann"))
        assertTrue(contactNamesMatch("  Joanne  ", "Joanne"))
    }

    @Test
    fun substringDoesNotMatch() {
        assertFalse(contactNamesMatch("Ann", "Joanne"))
        assertFalse(contactNamesMatch("Joanne", "Ann"))
    }

    @Test
    fun blankNeverMatches() {
        assertFalse(contactNamesMatch("", "Ann"))
        assertFalse(contactNamesMatch("Ann", "   "))
        assertFalse(contactNamesMatch("  ", "  "))
    }

    @Test
    fun firstEnabledNameMatchSkipsDisabledAndBlank() {
        val ann = MessageContactRule("1", "Ann", isEnabled = true)
        val joanne = MessageContactRule("2", "Joanne", isEnabled = true)
        val disabled = MessageContactRule("3", "Sam", isEnabled = false)
        val rules = listOf(ann, joanne, disabled)

        assertSame(ann, firstEnabledNameMatch("Ann", rules, MessageContactRule::name, MessageContactRule::isEnabled))
        assertNull(firstEnabledNameMatch("Joanne Smith", rules, MessageContactRule::name, MessageContactRule::isEnabled))
        assertNull(firstEnabledNameMatch("Sam", rules, MessageContactRule::name, MessageContactRule::isEnabled))
        assertNull(firstEnabledNameMatch("  ", rules, MessageContactRule::name, MessageContactRule::isEnabled))
    }
}
