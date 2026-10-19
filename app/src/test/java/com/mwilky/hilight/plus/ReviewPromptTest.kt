package com.mwilky.hilight.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the in-app review sheet is due: every usage condition must hold, the cooldown must have
 * run out, and the boundaries sit exactly at the thresholds.
 */
class ReviewPromptTest {

    private val day = 24 * 60 * 60 * 1000L
    private val installed = 1_700_000_000_000L
    private val due = installed + ReviewPrompt.MIN_INSTALL_AGE_MS

    private fun isDue(
        now: Long = due,
        litAlerts: Int = ReviewPrompt.MIN_LIT_ALERTS,
        hasCustomRule: Boolean = true,
        entitled: Boolean = true,
        connected: Boolean = true,
        lastRequestMs: Long? = null
    ) = ReviewPrompt.isDue(now, installed, litAlerts, hasCustomRule, entitled, connected, lastRequestMs)

    @Test
    fun dueOnceEveryConditionHolds() {
        assertTrue(isDue())
    }

    @Test
    fun notDueBeforeTheInstallIsOldEnough() {
        assertFalse(isDue(now = due - 1))
    }

    @Test
    fun notDueBeforeEnoughAlertsHaveLit() {
        assertFalse(isDue(litAlerts = ReviewPrompt.MIN_LIT_ALERTS - 1))
    }

    @Test
    fun notDueWithoutACustomRule() {
        assertFalse(isDue(hasCustomRule = false))
    }

    @Test
    fun notDueAfterTheTrialHasEnded() {
        assertFalse(isDue(entitled = false))
    }

    @Test
    fun notDueWhileTheRingIsDisconnected() {
        assertFalse(isDue(connected = false))
    }

    @Test
    fun cooldownHoldsBackARepeatUntilItRunsOut() {
        val asked = due
        assertFalse(isDue(now = asked + ReviewPrompt.COOLDOWN_MS - 1, lastRequestMs = asked))
        assertTrue(isDue(now = asked + ReviewPrompt.COOLDOWN_MS, lastRequestMs = asked))
    }

    @Test
    fun cooldownIsOverWhenNeverAsked() {
        assertTrue(ReviewPrompt.cooldownOver(now = installed, lastRequestMs = null))
        assertFalse(ReviewPrompt.cooldownOver(now = installed + 30 * day, lastRequestMs = installed))
    }
}
