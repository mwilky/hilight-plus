package com.mwilky.hilight.plus

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class DndModeTest {

    @Test
    fun inheritBlocksOnlyWhenGlobalAndActive() {
        assertTrue(DndMode.INHERIT.blockedBy(globalEnabled = true, dndActive = true))
        assertFalse(DndMode.INHERIT.blockedBy(globalEnabled = true, dndActive = false))
        assertFalse(DndMode.INHERIT.blockedBy(globalEnabled = false, dndActive = true))
    }

    @Test
    fun alwaysNeverBlocks() {
        assertFalse(DndMode.ALWAYS.blockedBy(globalEnabled = true, dndActive = true))
        assertFalse(DndMode.ALWAYS.blockedBy(globalEnabled = false, dndActive = false))
    }

    @Test
    fun skipBlocksWheneverDndIsActive() {
        assertTrue(DndMode.SKIP.blockedBy(globalEnabled = false, dndActive = true))
        assertFalse(DndMode.SKIP.blockedBy(globalEnabled = true, dndActive = false))
    }
}

class QuietHoursModeTest {

    @Test
    fun inheritBlocksOnlyWhenGlobalAndInWindow() {
        assertTrue(QuietHoursMode.INHERIT.blockedBy(globalEnabled = true, inQuietHours = true))
        assertFalse(QuietHoursMode.INHERIT.blockedBy(globalEnabled = true, inQuietHours = false))
        assertFalse(QuietHoursMode.INHERIT.blockedBy(globalEnabled = false, inQuietHours = true))
    }

    @Test
    fun alwaysNeverBlocks() {
        assertFalse(QuietHoursMode.ALWAYS.blockedBy(globalEnabled = true, inQuietHours = true))
    }

    @Test
    fun skipBlocksWheneverTheWindowIsActive() {
        assertTrue(QuietHoursMode.SKIP.blockedBy(globalEnabled = false, inQuietHours = true))
        assertFalse(QuietHoursMode.SKIP.blockedBy(globalEnabled = true, inQuietHours = false))
    }
}

class WeeklyQuietHoursTest {

    private fun at(day: DayOfWeek, hour: Int, minute: Int = 0) = day.ordinal * 24 * 60 + hour * 60 + minute

    private val main = QuietWindow(22 * 60, 7 * 60)
    private val weekend = QuietWindow(23 * 60 + 30, 9 * 60)
    private val satSun = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    @Test
    fun everyDayMatchesTheSingleWindow() {
        val week = QuietHoursSchedule().week(main, satSun)
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.MONDAY, 23), week))
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.TUESDAY, 6, 59), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.TUESDAY, 7), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.WEDNESDAY, 21, 59), week))
    }

    @Test
    fun anOvernightWindowBelongsToTheDayItStarts() {
        val week = QuietHoursSchedule(QuietHoursRepeat.WEEKDAYS_WEEKENDS, weekend).week(main, satSun)
        // Friday is a weekday: quiet from 22:00, until 07:00 on Saturday.
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.FRIDAY, 22), week))
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.SATURDAY, 6, 30), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.SATURDAY, 8), week))
        // Saturday night starts later and runs to 09:00 on Sunday.
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.SATURDAY, 23), week))
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.SUNDAY, 8, 59), week))
        // Sunday's runs into Monday morning, across the end of the week.
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.MONDAY, 8), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.MONDAY, 9), week))
    }

    @Test
    fun aDaySwitchedOffIsNeverQuietButYesterdaysStillEnds() {
        val schedule = QuietHoursSchedule(
            QuietHoursRepeat.EACH_DAY,
            days = mapOf(DayOfWeek.WEDNESDAY to QuietWindow(22 * 60, 7 * 60, enabled = false))
        )
        val week = schedule.week(main, satSun)
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.WEDNESDAY, 6), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.WEDNESDAY, 23), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.THURSDAY, 6), week))
    }

    @Test
    fun daytimeWindowsStayWithinTheirDay() {
        val week = QuietHoursSchedule(QuietHoursRepeat.EACH_DAY, days = mapOf(DayOfWeek.MONDAY to QuietWindow(9 * 60, 17 * 60)))
            .week(QuietWindow(0, 0), satSun)
        assertTrue(isInWeeklyQuietHours(at(DayOfWeek.MONDAY, 12), week))
        assertFalse(isInWeeklyQuietHours(at(DayOfWeek.TUESDAY, 12), week))
    }

    @Test
    fun eachDayStartsFromWhatWeekdaysAndWeekendsGave() {
        val schedule = QuietHoursSchedule(QuietHoursRepeat.EACH_DAY, weekend)
        assertEquals(main, schedule.windowFor(DayOfWeek.FRIDAY, main, satSun))
        assertEquals(weekend, schedule.windowFor(DayOfWeek.SUNDAY, main, satSun))
        // A locale whose weekend is Friday and Saturday.
        assertEquals(weekend, schedule.windowFor(DayOfWeek.FRIDAY, main, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)))
    }

    @Test
    fun otherModesKeepTheirTimesWhileUnused() {
        val custom = mapOf(DayOfWeek.MONDAY to QuietWindow(21 * 60, 6 * 60))
        val schedule = QuietHoursSchedule(QuietHoursRepeat.EVERY_DAY, weekend, custom)
        assertEquals(List(7) { main }, schedule.week(main, satSun))
        assertEquals(custom.getValue(DayOfWeek.MONDAY), schedule.copy(repeat = QuietHoursRepeat.EACH_DAY).week(main, satSun)[0])
    }

    @Test
    fun scheduleRoundTripsThroughJson() {
        val schedule = QuietHoursSchedule(
            QuietHoursRepeat.EACH_DAY,
            weekend,
            mapOf(DayOfWeek.MONDAY to QuietWindow(21 * 60, 6 * 60), DayOfWeek.SUNDAY to QuietWindow(0, 0, enabled = false))
        )
        assertEquals(schedule, QuietHoursSchedule.fromJson(schedule.toJson().toString()))
        assertEquals(QuietHoursSchedule(), QuietHoursSchedule.fromJson(null))
        assertEquals(QuietHoursSchedule(), QuietHoursSchedule.fromJson("not json"))
    }

    @Test
    fun aRuleWithItsOwnWindowIgnoresTheWeek() {
        val week = List(7) { QuietWindow(0, 0, enabled = false) }
        assertTrue(quietHoursBlocked(QuietHoursMode.SKIP, false, week, 22 * 60, 7 * 60, at(DayOfWeek.SATURDAY, 23)))
        assertFalse(quietHoursBlocked(QuietHoursMode.INHERIT, true, week, null, null, at(DayOfWeek.SATURDAY, 23)))
        assertTrue(quietHoursBlocked(QuietHoursMode.SKIP, false, List(7) { main }, null, null, at(DayOfWeek.SATURDAY, 23)))
    }
}

class SystemDndActiveTest {

    @Test
    fun systemDndTreatsUnknownAndAllAsInactive() {
        assertFalse(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertFalse(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_ALARMS))
    }
}
