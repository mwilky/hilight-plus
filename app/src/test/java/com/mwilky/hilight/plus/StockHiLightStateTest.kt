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
}
