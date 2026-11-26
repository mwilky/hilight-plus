package com.mwilky.hilight.plus.core

/**
 * What can light the ring, in the default priority order: the first channel with something to
 * show takes the ring, and one with nothing to show (or hidden by its conditions) lets the next
 * through. The user can reorder them. [id] is stored and sent to the daemon, so never change one.
 */
enum class LightChannel(val id: String) {
    CALLS("calls"),
    IN_CALL("in_call"),
    GEMINI("gemini"),
    NOTIFICATIONS("notifications"),
    PROGRESS("progress"),
    BATTERY("battery"),
    DND("dnd");

    companion object {
        val DEFAULT_ORDER: List<LightChannel> = entries.toList()

        fun encode(order: List<LightChannel>): String = order.joinToString(",") { it.id }

        /** A stored or sent order: unknown ids dropped, anything missing put back by [normalize]. */
        fun decode(raw: String?): List<LightChannel> {
            if (raw.isNullOrBlank()) return DEFAULT_ORDER
            val ids = raw.split(',').map { it.trim() }
            return normalize(ids.mapNotNull { id -> entries.firstOrNull { it.id == id } })
        }

        /**
         * [order] without repeats and with every channel in it. A missing one (new in a later
         * version) goes straight after whichever channel comes before it in [DEFAULT_ORDER].
         */
        fun normalize(order: List<LightChannel>): List<LightChannel> {
            val result = order.distinct().toMutableList()
            DEFAULT_ORDER.forEachIndexed { index, channel ->
                if (channel !in result) {
                    val before = DEFAULT_ORDER.take(index).lastOrNull { it in result }
                    result.add(before?.let { result.indexOf(it) + 1 } ?: 0, channel)
                }
            }
            return result
        }

        /**
         * The order that comes closest to the two toggles this list replaced, so an update leaves
         * the ring as it was: battery's "Prioritise over notifications" and the DND indicator's
         * "Prioritise over battery". Progress used to go over the battery even when the battery
         * went over notifications, so it moves up with it.
         */
        fun fromLegacy(batteryOverNotifications: Boolean, dndOverBattery: Boolean): List<LightChannel> {
            val lower = when {
                batteryOverNotifications && dndOverBattery -> listOf(PROGRESS, DND, BATTERY, NOTIFICATIONS)
                batteryOverNotifications -> listOf(PROGRESS, BATTERY, NOTIFICATIONS, DND)
                dndOverBattery -> listOf(NOTIFICATIONS, PROGRESS, DND, BATTERY)
                else -> listOf(NOTIFICATIONS, PROGRESS, BATTERY, DND)
            }
            return listOf(CALLS, IN_CALL, GEMINI) + lower
        }
    }
}
