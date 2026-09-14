package com.mwilky.hilight.plus

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
