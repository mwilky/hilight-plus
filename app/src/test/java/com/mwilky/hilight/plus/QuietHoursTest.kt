package com.mwilky.hilight.plus

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun disabledNeverMatches() {
        assertFalse(isInQuietHours(23 * 60, enabled = false, startMinutes = 22 * 60, endMinutes = 7 * 60))
    }

    @Test
    fun sameStartAndEndNeverMatches() {
        assertFalse(isInQuietHours(22 * 60, enabled = true, startMinutes = 22 * 60, endMinutes = 22 * 60))
    }

    @Test
    fun sameDayWindowIncludesStartExcludesEnd() {
        assertTrue(isInQuietHours(13 * 60, enabled = true, startMinutes = 12 * 60, endMinutes = 14 * 60))
        assertTrue(isInQuietHours(12 * 60, enabled = true, startMinutes = 12 * 60, endMinutes = 14 * 60))
        assertFalse(isInQuietHours(14 * 60, enabled = true, startMinutes = 12 * 60, endMinutes = 14 * 60))
        assertFalse(isInQuietHours(11 * 60, enabled = true, startMinutes = 12 * 60, endMinutes = 14 * 60))
    }

    @Test
    fun overnightWindowWrapsMidnight() {
        assertTrue(isInQuietHours(23 * 60, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertTrue(isInQuietHours(0, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertTrue(isInQuietHours(6 * 60 + 59, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertFalse(isInQuietHours(7 * 60, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertFalse(isInQuietHours(12 * 60, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertFalse(isInQuietHours(21 * 60 + 59, enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60))
    }
}

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

class ArrivalConditionTest {

    @Test
    fun inheritIsBlockedByDndAndQuietHours() {
        val snapshot = snapshot(suppressDnd = true, quietEnabled = true, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertTrue(snapshot.blocksArrival(DndMode.INHERIT, QuietHoursMode.INHERIT, dndActive = true, nowMinutes = 12 * 60))
        assertTrue(snapshot.blocksArrival(DndMode.INHERIT, QuietHoursMode.INHERIT, dndActive = false, nowMinutes = 23 * 60))
        assertFalse(snapshot.blocksArrival(DndMode.INHERIT, QuietHoursMode.INHERIT, dndActive = false, nowMinutes = 12 * 60))
    }

    @Test
    fun alwaysBypassesBothConditions() {
        val snapshot = snapshot(suppressDnd = true, quietEnabled = true, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertFalse(snapshot.blocksArrival(DndMode.ALWAYS, QuietHoursMode.ALWAYS, dndActive = true, nowMinutes = 23 * 60))
    }

    @Test
    fun skipAppliesEvenWhenTheGlobalSwitchIsOff() {
        val snapshot = snapshot(suppressDnd = false, quietEnabled = false, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertTrue(snapshot.blocksArrival(DndMode.SKIP, QuietHoursMode.INHERIT, dndActive = true, nowMinutes = 12 * 60))
        assertTrue(snapshot.blocksArrival(DndMode.INHERIT, QuietHoursMode.SKIP, dndActive = false, nowMinutes = 23 * 60))
        assertFalse(snapshot.blocksArrival(DndMode.SKIP, QuietHoursMode.SKIP, dndActive = false, nowMinutes = 12 * 60))
    }

    @Test
    fun inheritStoresALiveDndSuppressFlagWhenTheSwitchIsOn() {
        val snapshot = snapshot(suppressDnd = true, quietEnabled = false, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertTrue(snapshot.suppressesDuringDnd(DndMode.INHERIT))
        assertFalse(snapshot.suppressesDuringDnd(DndMode.ALWAYS))
        assertNull(snapshot.quietWindowFor(QuietHoursMode.INHERIT))
    }

    @Test
    fun skipStoresAQuietWindowEvenWhenTheSwitchIsOff() {
        val snapshot = snapshot(suppressDnd = false, quietEnabled = false, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertEquals(12 * 60 to 14 * 60, snapshot.quietWindowFor(QuietHoursMode.SKIP, 12 * 60, 14 * 60))
        assertEquals(22 * 60 to 7 * 60, snapshot.quietWindowFor(QuietHoursMode.SKIP))
        assertTrue(snapshot.suppressesDuringDnd(DndMode.SKIP))
        assertFalse(snapshot.suppressesDuringDnd(DndMode.INHERIT))
    }

    @Test
    fun skipUsesTheRuleWindowInsteadOfTheConditionsPage() {
        val snapshot = snapshot(suppressDnd = false, quietEnabled = false, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertTrue(
            snapshot.blocksArrival(
                DndMode.INHERIT,
                QuietHoursMode.SKIP,
                dndActive = false,
                nowMinutes = 13 * 60,
                quietStartMinutes = 12 * 60,
                quietEndMinutes = 14 * 60
            )
        )
        assertFalse(
            snapshot.blocksArrival(
                DndMode.INHERIT,
                QuietHoursMode.SKIP,
                dndActive = false,
                nowMinutes = 23 * 60,
                quietStartMinutes = 12 * 60,
                quietEndMinutes = 14 * 60
            )
        )
    }

    @Test
    fun systemDndTreatsUnknownAndAllAsInactive() {
        assertFalse(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertFalse(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertTrue(isSystemDndActive(NotificationManager.INTERRUPTION_FILTER_ALARMS))
    }

    private fun snapshot(
        suppressDnd: Boolean,
        quietEnabled: Boolean,
        quietStart: Int,
        quietEnd: Int
    ) = SettingsSnapshot(
        isEnabled = true,
        isOnlyWhenFaceDown = false,
        suppressDuringDnd = suppressDnd,
        quietHoursEnabled = quietEnabled,
        quietHoursStartMinutes = quietStart,
        quietHoursEndMinutes = quietEnd,
        isCallLightsEnabled = true,
        contactRules = emptyList(),
        isOtherContactsEnabled = true,
        otherContactsColor = 0L,
        otherContactsPattern = PatternMode.PULSE,
        otherContactsFaceDownMode = FaceDownMode.INHERIT,
        otherContactsDndMode = DndMode.INHERIT,
        otherContactsQuietHoursMode = QuietHoursMode.INHERIT,
        otherContactsQuietHoursStartMinutes = null,
        otherContactsQuietHoursEndMinutes = null,
        isUnknownNumbersEnabled = true,
        unknownNumbersColor = 0L,
        unknownNumbersPattern = PatternMode.PULSE,
        unknownNumbersFaceDownMode = FaceDownMode.INHERIT,
        unknownNumbersDndMode = DndMode.INHERIT,
        unknownNumbersQuietHoursMode = QuietHoursMode.INHERIT,
        unknownNumbersQuietHoursStartMinutes = null,
        unknownNumbersQuietHoursEndMinutes = null,
        isNotificationsEnabled = true,
        notificationDurationSeconds = 30,
        isCycleNotifications = false,
        isDefaultNotifEnabled = true,
        defaultNotifColor = 0L,
        defaultNotifPattern = PatternMode.PULSE,
        defaultNotifFaceDownMode = FaceDownMode.INHERIT,
        isDefaultNotifAutoColor = true,
        defaultNotifDndMode = DndMode.INHERIT,
        defaultNotifQuietHoursMode = QuietHoursMode.INHERIT,
        defaultNotifQuietHoursStartMinutes = null,
        defaultNotifQuietHoursEndMinutes = null,
        messageContactRules = emptyList(),
        appRules = emptyList()
    )
}
