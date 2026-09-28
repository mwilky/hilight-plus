package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuleSortTest {

    private val names = listOf("zoe Adams", "Émile Zola", "adam smith", "Mom", "Bob Brown")

    @Test
    fun addedKeepsSavedOrder() {
        assertSame(names, RuleSort.ADDED.sorted(names) { it })
    }

    @Test
    fun nameSortsIgnoreCaseAndAccents() {
        assertEquals(
            listOf("adam smith", "Bob Brown", "Émile Zola", "Mom", "zoe Adams"),
            RuleSort.NAME_ASC.sorted(names) { it }
        )
        assertEquals(
            listOf("zoe Adams", "Mom", "Émile Zola", "Bob Brown", "adam smith"),
            RuleSort.NAME_DESC.sorted(names) { it }
        )
    }

    @Test
    fun lastNameUsesLastWordAndOneWordNamesSortByThemselves() {
        assertEquals(
            listOf("zoe Adams", "Bob Brown", "Mom", "adam smith", "Émile Zola"),
            RuleSort.LAST_NAME_ASC.sorted(names) { it }
        )
        assertEquals(
            listOf("Émile Zola", "adam smith", "Mom", "Bob Brown", "zoe Adams"),
            RuleSort.LAST_NAME_DESC.sorted(names) { it }
        )
    }

    @Test
    fun sameLastNameFallsBackToFullName() {
        val family = listOf("Tom Smith", "Anna Smith")
        assertEquals(listOf("Anna Smith", "Tom Smith"), RuleSort.LAST_NAME_ASC.sorted(family) { it })
    }
}
