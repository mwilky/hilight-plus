package com.mwilky.hilight.plus.ui

import com.mwilky.hilight.plus.PatternMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PatternGroupTest {

    @Test
    fun everyPatternIsInExactlyOneGroup() {
        val grouped = PatternGroup.entries.flatMap { it.patterns }
        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(PatternMode.entries.toSet() - PatternMode.OFF, grouped.toSet())
    }
}
