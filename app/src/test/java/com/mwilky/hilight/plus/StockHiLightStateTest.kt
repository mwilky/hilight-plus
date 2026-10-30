package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockHiLightStateTest {

    @Test
    fun missingReadIsUnknownNotDisabled() {
        val state = parseFavoriteCallsSetting(null)
        assertFalse(state.known)
        assertFalse(state.favoriteCallsActive)
    }

    @Test
    fun zeroIsKnownAndOff() {
        val state = parseFavoriteCallsSetting("0")
        assertTrue(state.known)
        assertFalse(state.favoriteCallsActive)
    }

    @Test
    fun oneIsKnownAndOn() {
        val state = parseFavoriteCallsSetting("1")
        assertTrue(state.known)
        assertTrue(state.favoriteCallsActive)
    }

    @Test
    fun geminiFeedbackIsReadSeparatelyFromFavourites() {
        val state = parseFavoriteCallsSetting("0").withGeminiFeedback("1")
        assertFalse(state.favoriteCallsActive)
        assertTrue(state.geminiKnown)
        assertTrue(state.geminiFeedbackActive)
        assertFalse(parseFavoriteCallsSetting("0").withGeminiFeedback(null).geminiKnown)
    }

    @Test
    fun favouriteMessagesOnlyCountOnBuildsWithTheToggle() {
        val base = parseFavoriteCallsSetting("0")
        // Older builds: an unwritten or unread key isn't the stock default, it's no setting at all.
        assertFalse(base.withFavoriteMessages("", buildHasToggle = false).favoriteMessagesAvailable)
        assertFalse(base.withFavoriteMessages(null, buildHasToggle = false).favoriteMessagesAvailable)

        val unwritten = base.withFavoriteMessages("", buildHasToggle = true)
        assertTrue(unwritten.favoriteMessagesAvailable)
        assertTrue(unwritten.favoriteMessagesKnown)
        assertTrue(unwritten.favoriteMessagesActive)

        val off = base.withFavoriteMessages("0", buildHasToggle = true)
        assertTrue(off.favoriteMessagesKnown)
        assertFalse(off.favoriteMessagesActive)

        val unread = base.withFavoriteMessages(null, buildHasToggle = true)
        assertTrue(unread.favoriteMessagesAvailable)
        assertFalse(unread.favoriteMessagesKnown)
    }

    @Test
    fun unwrittenKeysUseStockDefaultOn() {
        val state = parseFavoriteCallsSetting("").withGeminiFeedback("")
        assertTrue(state.known)
        assertTrue(state.favoriteCallsActive)
        assertTrue(state.geminiKnown)
        assertTrue(state.geminiFeedbackActive)
    }
}
