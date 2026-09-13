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
        val contributors: Set<String>
    )

    data class Removal(
        val slotId: String?,
        val slotEmptied: Boolean,
        val wasLatest: Boolean
    )

    private val slots = linkedMapOf<String, MutableSlot>()
    private val sourceToSlot = mutableMapOf<String, String>()
    private var latestSourceKey: String? = null

    fun add(sourceKey: String, slotId: String, pattern: PatternMode, color: Long): Slot {
        val previousSlotId = sourceToSlot[sourceKey]
        if (previousSlotId != null && previousSlotId != slotId) {
            detach(sourceKey, previousSlotId)
        }

        val slot = slots.getOrPut(slotId) { MutableSlot(slotId, pattern, color) }
        slot.pattern = pattern
        slot.color = color
        slot.contributors.add(sourceKey)
        sourceToSlot[sourceKey] = slotId
        latestSourceKey = sourceKey
        return slot.snapshot()
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
        val contributors: MutableSet<String> = linkedSetOf()
    ) {
        fun snapshot(): Slot = Slot(id, pattern, color, contributors.toSet())
    }
}
