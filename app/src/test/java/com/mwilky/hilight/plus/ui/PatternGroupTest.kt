package com.mwilky.hilight.plus.ui

import com.mwilky.hilight.plus.PatternMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternGroupTest {

    private val dndLooks = PatternMode.entries.filter { it.id.startsWith("dnd_") }

    @Test
    fun everyPatternIsInExactlyOneGroup() {
        val grouped = PatternGroup.entries.flatMap { it.patterns }
        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(PatternMode.entries.toSet() - PatternMode.OFF, grouped.toSet())
    }

    @Test
    fun dndLooksAreOnlyOfferedToTheDndIndicator() {
        val forRules = PatternGroup.RULES.flatMap { it.patterns }
        assertTrue(dndLooks.isNotEmpty())
        dndLooks.forEach { assertFalse("$it offered to rules", it in forRules) }
        assertEquals(PatternGroup.DND, PatternGroup.DND_INDICATOR.first())
    }

    @Test
    fun theDndIndicatorCanStillPickAnyRulePattern() {
        val forDnd = PatternGroup.DND_INDICATOR.flatMap { it.patterns }.toSet()
        assertTrue(forDnd.containsAll(PatternGroup.RULES.flatMap { it.patterns }))
    }
}
