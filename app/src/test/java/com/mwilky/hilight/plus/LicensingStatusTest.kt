package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trial arithmetic on [Licensing.Status]: days-left rounding, the expiry boundary, and that a
 * purchase or a not-yet-started trial always counts as entitled.
 */
class LicensingStatusTest {

    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_700_000_000_000L

    private fun status(now: Long, purchased: Boolean = false, trialStart: Long? = start) =
        Licensing.Status(purchased = purchased, trialStartMillis = trialStart, now = now, priceText = null)

    @Test
    fun freshTrialHasFullDaysLeftAndIsEntitled() {
        val s = status(now = start)
        assertEquals(Licensing.TRIAL_DAYS, s.trialDaysLeft)
        assertFalse(s.trialExpired)
        assertTrue(s.entitled)
    }

    @Test
    fun partialLastDayStillReadsAsOneDay() {
        val s = status(now = start + Licensing.TRIAL_DAYS * day - 1)
        assertEquals(1, s.trialDaysLeft)
        assertTrue(s.entitled)
    }

    @Test
    fun trialExpiresExactlyAtTheEnd() {
        val s = status(now = start + Licensing.TRIAL_DAYS * day)
        assertEquals(0, s.trialDaysLeft)
        assertTrue(s.trialExpired)
        assertFalse(s.entitled)
    }

    @Test
    fun purchaseOverridesExpiredTrial() {
        val s = status(now = start + 30 * day, purchased = true)
        assertTrue(s.trialExpired)
        assertTrue(s.entitled)
    }

    @Test
    fun noTrialStartMeansNothingToGate() {
        val s = status(now = start, trialStart = null)
        assertNull(s.trialDaysLeft)
        assertFalse(s.trialExpired)
        assertTrue(s.entitled)
    }
}
