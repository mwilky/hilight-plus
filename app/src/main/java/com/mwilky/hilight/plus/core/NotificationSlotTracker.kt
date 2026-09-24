package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode

/**
 * Tracks source notification keys separately from the display slots they share.
 * Standard mode uses [latestSourceKey] only; dismissing it does not restore an older source.
 */
internal class NotificationSlotTracker {

    data class Slot(
        val id: String,
        val pattern: PatternMode,
        val color: Long,
        val contributors: Set<String>,
        val requiresFaceDown: Boolean,
        val dndMode: DndMode,
        val quietHoursMode: QuietHoursMode,
        val quietStartMinutes: Int?,
        val quietEndMinutes: Int?
    )

    data class AddResult(
        val slot: Slot,
        val changed: Boolean,
        val emptiedSlotId: String? = null
    )

    data class Removal(
        val slotId: String?,
        val slotEmptied: Boolean,
        val wasLatest: Boolean
    )

    private val slots = linkedMapOf<String, MutableSlot>()
    private val sourceToSlot = mutableMapOf<String, String>()
    private var latestSourceKey: String? = null

    fun add(
        sourceKey: String,
        slotId: String,
        pattern: PatternMode,
        color: Long,
        requiresFaceDown: Boolean = false,
        dndMode: DndMode = DndMode.INHERIT,
        quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null,
        becomeLatest: Boolean = true
    ): AddResult {
        val previous = sourceToSlot[sourceKey]?.let { slots[it] }
        val unchanged = previous != null &&
            previous.id == slotId &&
            previous.pattern == pattern &&
            previous.color == color &&
            previous.requiresFaceDown == requiresFaceDown &&
            previous.dndMode == dndMode &&
            previous.quietHoursMode == quietHoursMode &&
            previous.quietStartMinutes == quietStartMinutes &&
            previous.quietEndMinutes == quietEndMinutes &&
            sourceKey in previous.contributors

        val previousSlotId = sourceToSlot[sourceKey]
        val emptiedSlotId = if (previousSlotId != null && previousSlotId != slotId && detach(sourceKey, previousSlotId)) {
            previousSlotId
        } else {
            null
        }

        val slot = slots.getOrPut(slotId) {
            MutableSlot(slotId, pattern, color, requiresFaceDown, dndMode, quietHoursMode, quietStartMinutes, quietEndMinutes)
        }
        slot.pattern = pattern
        slot.color = color
        slot.requiresFaceDown = requiresFaceDown
        slot.dndMode = dndMode
        slot.quietHoursMode = quietHoursMode
        slot.quietStartMinutes = quietStartMinutes
        slot.quietEndMinutes = quietEndMinutes
        slot.contributors.add(sourceKey)
        sourceToSlot[sourceKey] = slotId
        // Keep slots in the order the daemon's queue ends up in: every changed add is re-sent,
        // which moves that slot to the newest end there, so a replay rebuilds the same order.
        if (!unchanged) {
            slots.remove(slotId)
            slots[slotId] = slot
        }
        if (becomeLatest) {
            latestSourceKey = sourceKey
        }
        return AddResult(slot.snapshot(), changed = !unchanged, emptiedSlotId = emptiedSlotId)
    }

    fun remove(sourceKey: String): Removal {
        val slotId = sourceToSlot.remove(sourceKey)
        val wasLatest = latestSourceKey == sourceKey
        if (wasLatest) {
            latestSourceKey = null
        }
        if (slotId == null) {
            return Removal(slotId = null, slotEmptied = false, wasLatest = wasLatest)
        }
        return Removal(slotId = slotId, slotEmptied = detach(sourceKey, slotId), wasLatest = wasLatest)
    }

    fun pruneMissing(activeSourceKeys: Set<String>): List<Removal> {
        return sourceToSlot.keys.filter { it !in activeSourceKeys }.map { remove(it) }
    }

    fun clear() {
        slots.clear()
        sourceToSlot.clear()
        latestSourceKey = null
    }

    fun slotsInOrder(): List<Slot> = slots.values.map { it.snapshot() }

    fun latestSlot(): Slot? = latestSourceKey?.let { sourceToSlot[it] }?.let { slots[it]?.snapshot() }

    fun hasRestrictedSlot(): Boolean = slots.values.any { it.requiresFaceDown }

    fun contributorCount(slotId: String): Int = slots[slotId]?.contributors?.size ?: 0

    val sourceCount: Int get() = sourceToSlot.size

    fun sourceKeys(): Set<String> = sourceToSlot.keys.toSet()

    private fun detach(sourceKey: String, slotId: String): Boolean {
        val slot = slots[slotId] ?: return false
        slot.contributors.remove(sourceKey)
        if (slot.contributors.isEmpty()) {
            slots.remove(slotId)
            return true
        }
        return false
    }

    private class MutableSlot(
        val id: String,
        var pattern: PatternMode,
        var color: Long,
        var requiresFaceDown: Boolean,
        var dndMode: DndMode,
        var quietHoursMode: QuietHoursMode,
        var quietStartMinutes: Int?,
        var quietEndMinutes: Int?,
        val contributors: MutableSet<String> = linkedSetOf()
    ) {
        fun snapshot(): Slot = Slot(
            id,
            pattern,
            color,
            contributors.toSet(),
            requiresFaceDown,
            dndMode,
            quietHoursMode,
            quietStartMinutes,
            quietEndMinutes
        )
    }
}
