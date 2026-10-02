package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PauseTest {

    private val london = ZoneId.of("Europe/London")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()

    @Test
    fun morningLaterTodayWhenBeforeIt() {
        assertEquals(at("2026-10-02T07:00"), nextTimeOfDayMillis(at("2026-10-02T05:30"), 7 * 60, london))
    }

    @Test
    fun morningTomorrowOnceItHasPassed() {
        assertEquals(at("2026-10-03T07:00"), nextTimeOfDayMillis(at("2026-10-02T22:15"), 7 * 60, london))
        // Exactly at the time counts as passed, so a pause is never zero length.
        assertEquals(at("2026-10-03T07:00"), nextTimeOfDayMillis(at("2026-10-02T07:00"), 7 * 60, london))
    }

    @Test
    fun morningKeepsItsClockTimeAcrossTheClocksGoingBack() {
        // British Summer Time ends at 02:00 on 25 October 2026, making that night 25 hours long.
        assertEquals(at("2026-10-25T07:00"), nextTimeOfDayMillis(at("2026-10-24T23:00"), 7 * 60, london))
    }

    @Test
    fun morningFollowsQuietHoursWhenTheyAreOn() {
        assertEquals(6 * 60 + 30, pauseMorningMinutes(quietHoursEnabled = true, quietHoursEndMinutes = 6 * 60 + 30))
        assertEquals(7 * 60, pauseMorningMinutes(quietHoursEnabled = false, quietHoursEndMinutes = 6 * 60 + 30))
    }

    @Test
    fun durationsEndWhereExpected() {
        val now = at("2026-10-02T14:30")
        assertEquals(at("2026-10-02T15:30"), PauseDuration.ONE_HOUR.endMillis(now, 7 * 60, london))
        assertEquals(at("2026-10-02T16:30"), PauseDuration.TWO_HOURS.endMillis(now, 7 * 60, london))
        assertEquals(at("2026-10-03T07:00"), PauseDuration.UNTIL_MORNING.endMillis(now, 7 * 60, london))
        assertEquals(PauseState.UNTIL_RESUMED, PauseDuration.UNTIL_RESUMED.endMillis(now, 7 * 60, london))
    }

    @Test
    fun pauseCoversOnlyItsFeaturesAndOnlyUntilItEnds() {
        val pause = PauseState(untilMillis = 1_000L, features = setOf(PauseFeature.NOTIFICATIONS))
        assertTrue(pause.pauses(PauseFeature.NOTIFICATIONS, nowMillis = 999L))
        assertFalse(pause.pauses(PauseFeature.CALLS, nowMillis = 999L))
        assertFalse(pause.pauses(PauseFeature.NOTIFICATIONS, nowMillis = 1_000L))
        assertTrue(PauseState(PauseState.UNTIL_RESUMED, PauseFeature.ALL).isActive(nowMillis = Long.MAX_VALUE - 1))
    }

    @Test
    fun featuresRoundTripAndIgnoreUnknownIds() {
        val features = setOf(PauseFeature.CALLS, PauseFeature.GEMINI)
        assertEquals(features, PauseFeature.parse(PauseFeature.encode(features)))
        assertEquals(setOf(PauseFeature.BATTERY), PauseFeature.parse("battery,torch"))
        assertEquals(emptySet<PauseFeature>(), PauseFeature.parse(null))
    }

    @Test
    fun tappingAFeatureUnderEverythingChoosesItAlone() {
        val available = PauseFeature.entries
        assertEquals(setOf(PauseFeature.CALLS), togglePauseSelection(PauseFeature.ALL, PauseFeature.CALLS, available))
    }

    @Test
    fun unchoosingTheLastFeatureFallsBackToEverything() {
        val available = PauseFeature.entries
        assertEquals(PauseFeature.ALL, togglePauseSelection(setOf(PauseFeature.CALLS), PauseFeature.CALLS, available))
    }

    @Test
    fun choosingEveryFeatureOnOfferIsEverything() {
        val available = listOf(PauseFeature.CALLS, PauseFeature.NOTIFICATIONS)
        assertEquals(PauseFeature.ALL, togglePauseSelection(setOf(PauseFeature.CALLS), PauseFeature.NOTIFICATIONS, available))
    }

    @Test
    fun everythingTapAlwaysChoosesEverything() {
        val available = PauseFeature.entries
        assertEquals(PauseFeature.ALL, togglePauseSelection(setOf(PauseFeature.BATTERY), null, available))
    }

    @Test
    fun rememberedChoiceDropsFeaturesSwitchedOffSince() {
        val available = listOf(PauseFeature.CALLS, PauseFeature.NOTIFICATIONS, PauseFeature.BATTERY)
        assertEquals(
            setOf(PauseFeature.NOTIFICATIONS),
            fitPauseSelection(setOf(PauseFeature.NOTIFICATIONS, PauseFeature.GEMINI), available)
        )
        assertEquals(PauseFeature.ALL, fitPauseSelection(setOf(PauseFeature.GEMINI), available))
    }
}
