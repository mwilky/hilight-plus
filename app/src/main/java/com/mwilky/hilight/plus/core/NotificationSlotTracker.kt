package com.mwilky.hilight.plus.core

import com.mwilky.hilight.plus.PatternMode

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
        val suppressDuringDnd: Boolean,
        val quietStartMinutes: Int?,
        val quietEndMinutes: Int?
    )

    data class AddResult(
        val slot: Slot,
        val changed: Boolean
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
        suppressDuringDnd: Boolean = false,
        quietStartMinutes: Int? = null,
        quietEndMinutes: Int? = null
    ): AddResult {
        val previous = sourceToSlot[sourceKey]?.let { slots[it] }
        val unchanged = previous != null &&
            previous.id == slotId &&
            previous.pattern == pattern &&
            previous.color == color &&
            previous.requiresFaceDown == requiresFaceDown &&
            previous.suppressDuringDnd == suppressDuringDnd &&
            previous.quietStartMinutes == quietStartMinutes &&
            previous.quietEndMinutes == quietEndMinutes &&
            sourceKey in previous.contributors

        val previousSlotId = sourceToSlot[sourceKey]
        if (previousSlotId != null && previousSlotId != slotId) {
            detach(sourceKey, previousSlotId)
        }

        val slot = slots.getOrPut(slotId) {
            MutableSlot(slotId, pattern, color, requiresFaceDown, suppressDuringDnd, quietStartMinutes, quietEndMinutes)
        }
        slot.pattern = pattern
        slot.color = color
        slot.requiresFaceDown = requiresFaceDown
        slot.suppressDuringDnd = suppressDuringDnd
        slot.quietStartMinutes = quietStartMinutes
        slot.quietEndMinutes = quietEndMinutes
        slot.contributors.add(sourceKey)
        sourceToSlot[sourceKey] = slotId
        latestSourceKey = sourceKey
        return AddResult(slot.snapshot(), changed = !unchanged)
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
        var suppressDuringDnd: Boolean,
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
            suppressDuringDnd,
            quietStartMinutes,
            quietEndMinutes
        )
    }
}
