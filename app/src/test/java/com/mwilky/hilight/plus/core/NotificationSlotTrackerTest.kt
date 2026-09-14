package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.PatternMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSlotTrackerTest {

    @Test
    fun tenMatchingNotificationsShareOneSlot() {
        val tracker = NotificationSlotTracker()
        repeat(10) { index ->
            tracker.add("key_$index", "app_whatsapp", PatternMode.PULSE, 0xFF25D366)
        }

        assertEquals(1, tracker.slotsInOrder().size)
        assertEquals(10, tracker.contributorCount("app_whatsapp"))
        assertEquals(10, tracker.sourceCount)
    }

    @Test
    fun removingNineKeepsTheSlotRemovingTheLastEmptiesIt() {
        val tracker = NotificationSlotTracker()
        repeat(10) { index ->
            tracker.add("key_$index", "app_whatsapp", PatternMode.PULSE, 0xFF25D366)
        }

        repeat(9) { index ->
            val removal = tracker.remove("key_$index")
            assertEquals("app_whatsapp", removal.slotId)
            assertFalse(removal.slotEmptied)
        }
        assertEquals(1, tracker.slotsInOrder().size)

        val last = tracker.remove("key_9")
        assertTrue(last.slotEmptied)
        assertTrue(last.wasLatest)
        assertTrue(tracker.slotsInOrder().isEmpty())
        assertNull(tracker.latestSlot())
    }

    @Test
    fun standardDismissalDoesNotRestoreAnOlderSource() {
        val tracker = NotificationSlotTracker()
        tracker.add("old", "fallback_mail", PatternMode.BREATHE, 0xFFFFFFFF)
        tracker.add("latest", "fallback_chat", PatternMode.PULSE, 0xFF00E5FF)

        val removal = tracker.remove("latest")
        assertTrue(removal.wasLatest)
        assertNull(tracker.latestSlot())
        assertEquals(1, tracker.slotsInOrder().size)
        assertEquals("fallback_mail", tracker.slotsInOrder().single().id)
    }

    @Test
    fun fallbackSlotsStayPerApplication() {
        val tracker = NotificationSlotTracker()
        tracker.add("wa_1", "fallback_whatsapp", PatternMode.PULSE, 0xFF25D366)
        tracker.add("wa_2", "fallback_whatsapp", PatternMode.PULSE, 0xFF25D366)
        tracker.add("slack_1", "fallback_slack", PatternMode.PULSE, 0xFF611F69)

        assertEquals(listOf("fallback_whatsapp", "fallback_slack"), tracker.slotsInOrder().map { it.id })
        assertEquals(2, tracker.contributorCount("fallback_whatsapp"))
    }

    @Test
    fun pruneMissingOnlyRemovesAbsentSources() {
        val tracker = NotificationSlotTracker()
        tracker.add("keep", "app_a", PatternMode.PULSE, 1)
        tracker.add("gone", "app_a", PatternMode.PULSE, 1)

        val removals = tracker.pruneMissing(setOf("keep"))
        assertEquals(1, removals.size)
        assertFalse(removals.single().slotEmptied)
        assertEquals(1, tracker.contributorCount("app_a"))
    }

    @Test
    fun movingASourceToANewSlotEmptiesTheOldSlot() {
        val tracker = NotificationSlotTracker()
        tracker.add("key", "contact_ann", PatternMode.PULSE, 1)
        val moved = tracker.add("key", "app_sms", PatternMode.WAVE, 2)

        assertEquals("contact_ann", moved.emptiedSlotId)
        assertEquals(listOf("app_sms"), tracker.slotsInOrder().map { it.id })
        assertEquals("app_sms", tracker.latestSlot()?.id)
    }

    @Test
    fun movingOneContributorDoesNotEmptyASharedSlot() {
        val tracker = NotificationSlotTracker()
        tracker.add("keep", "contact_ann", PatternMode.PULSE, 1)
        tracker.add("move", "contact_ann", PatternMode.PULSE, 1)

        val moved = tracker.add(
            "move",
            "fallback_sms",
            PatternMode.WAVE,
            2,
            becomeLatest = false
        )

        assertEquals(null, moved.emptiedSlotId)
        assertEquals(1, tracker.contributorCount("contact_ann"))
        assertEquals("fallback_sms", tracker.latestSlot()?.id)
    }

    @Test
    fun updatingTheSameSourceDoesNotCreateANewSlot() {
        val tracker = NotificationSlotTracker()
        val first = tracker.add("key", "app_wa", PatternMode.PULSE, 1)
        val update = tracker.add("key", "app_wa", PatternMode.PULSE, 1)

        assertTrue(first.changed)
        assertFalse(update.changed)
        assertEquals(1, tracker.slotsInOrder().size)
        assertEquals(1, tracker.contributorCount("app_wa"))
    }

    @Test
    fun hasRestrictedSlotTracksFaceDownOnlyRules() {
        val tracker = NotificationSlotTracker()
        tracker.add("always", "app_a", PatternMode.PULSE, 1, requiresFaceDown = false)
        assertFalse(tracker.hasRestrictedSlot())

        tracker.add("restricted", "app_b", PatternMode.PULSE, 2, requiresFaceDown = true)
        assertTrue(tracker.hasRestrictedSlot())

        tracker.remove("restricted")
        assertFalse(tracker.hasRestrictedSlot())
    }

    @Test
    fun updatingDndModeMarksTheSlotChanged() {
        val tracker = NotificationSlotTracker()
        val first = tracker.add("key", "app_a", PatternMode.PULSE, 1, dndMode = DndMode.INHERIT)
        val second = tracker.add("key", "app_a", PatternMode.PULSE, 1, dndMode = DndMode.SKIP)

        assertTrue(first.changed)
        assertTrue(second.changed)
        assertEquals(DndMode.SKIP, tracker.latestSlot()?.dndMode)
    }

    @Test
    fun updatingWithoutBecomingLatestKeepsTheCurrentLatest() {
        val tracker = NotificationSlotTracker()
        tracker.add("old", "app_a", PatternMode.PULSE, 1)
        tracker.add("latest", "app_b", PatternMode.PULSE, 2)

        val update = tracker.add(
            "old",
            "app_a",
            PatternMode.PULSE,
            1,
            dndMode = DndMode.SKIP,
            becomeLatest = false
        )

        assertTrue(update.changed)
        assertEquals("app_b", tracker.latestSlot()?.id)
        assertEquals(DndMode.SKIP, tracker.slotsInOrder().single { it.id == "app_a" }.dndMode)
    }

    @Test
    fun clearDropsAllSourcesAndSlots() {
        val tracker = NotificationSlotTracker()
        tracker.add("a", "app_a", PatternMode.PULSE, 1)
        tracker.add("b", "app_b", PatternMode.PULSE, 2)
        tracker.clear()

        assertTrue(tracker.slotsInOrder().isEmpty())
        assertNull(tracker.latestSlot())
        assertEquals(0, tracker.sourceCount)
    }
}
