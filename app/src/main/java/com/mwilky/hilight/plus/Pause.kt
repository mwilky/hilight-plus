package com.mwilky.hilight.plus

import java.time.Instant
import java.time.ZoneId

/** A part of the ring the pause can silence, each with its own on/off switch elsewhere in the app. */
enum class PauseFeature(val id: String, val titleRes: Int) {
    CALLS("calls", R.string.home_tab_calls),
    NOTIFICATIONS("notifications", R.string.home_tab_notifications),
    BATTERY("battery", R.string.home_tab_battery),
    GEMINI("gemini", R.string.home_tab_gemini);

    companion object {
        val ALL: Set<PauseFeature> = entries.toSet()

        fun parse(csv: String?): Set<PauseFeature> =
            csv.orEmpty().split(',').mapNotNullTo(mutableSetOf()) { id -> entries.find { it.id == id } }

        fun encode(features: Set<PauseFeature>): String = features.joinToString(",") { it.id }
    }
}

/**
 * The ring kept dark for [features] until [untilMillis], a wall-clock time, or until the user
 * resumes when it is [UNTIL_RESUMED]. Alerts that arrive meanwhile still queue, and light once
 * the pause ends if they're still waiting.
 */
data class PauseState(val untilMillis: Long, val features: Set<PauseFeature>) {
    val untilResumed: Boolean get() = untilMillis == UNTIL_RESUMED
    val pausesEverything: Boolean get() = features.containsAll(PauseFeature.ALL)

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        features.isNotEmpty() && nowMillis < untilMillis

    fun pauses(feature: PauseFeature, nowMillis: Long = System.currentTimeMillis()): Boolean =
        feature in features && isActive(nowMillis)

    companion object {
        const val UNTIL_RESUMED = Long.MAX_VALUE
    }
}

/** How long the pause sheet's choices last. */
enum class PauseDuration(val id: String) {
    ONE_HOUR("one_hour"),
    TWO_HOURS("two_hours"),
    UNTIL_MORNING("until_morning"),
    UNTIL_RESUMED("until_resumed");

    /** When a pause started at [nowMillis] ends; [morningMinutes] is the time of day "morning" means. */
    fun endMillis(nowMillis: Long, morningMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        ONE_HOUR -> nowMillis + HOUR_MS
        TWO_HOURS -> nowMillis + 2 * HOUR_MS
        UNTIL_MORNING -> nextTimeOfDayMillis(nowMillis, morningMinutes, zone)
        UNTIL_RESUMED -> PauseState.UNTIL_RESUMED
    }

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: ONE_HOUR
    }
}

/** The pause sheet's last choice, offered again next time. */
data class PauseChoice(
    val features: Set<PauseFeature> = PauseFeature.ALL,
    val duration: PauseDuration = PauseDuration.ONE_HOUR
)

/** The features switched on, the only ones worth offering to pause on their own. */
internal fun SettingsSnapshot.enabledPauseFeatures(): List<PauseFeature> = PauseFeature.entries.filter {
    when (it) {
        PauseFeature.CALLS -> isCallLightsEnabled
        PauseFeature.NOTIFICATIONS -> isNotificationsEnabled
        PauseFeature.BATTERY -> battery.enabled
        PauseFeature.GEMINI -> gemini.enabled
    }
}

/**
 * A remembered or current choice fitted to the features on offer: what's left of it once
 * switched-off features are dropped, or everything when that's nothing or all of them.
 */
internal fun fitPauseSelection(features: Set<PauseFeature>, available: List<PauseFeature>): Set<PauseFeature> {
    if (features == PauseFeature.ALL) return PauseFeature.ALL
    val shown = features.intersect(available.toSet())
    return if (shown.isEmpty() || shown.containsAll(available)) PauseFeature.ALL else shown
}

/**
 * A tap in the sheet's "What to pause" group: null is Everything. A feature tapped while
 * Everything is chosen is chosen on its own; unchoosing the last falls back to Everything, and
 * choosing every feature on offer is the same as Everything.
 */
internal fun togglePauseSelection(
    selection: Set<PauseFeature>,
    feature: PauseFeature?,
    available: List<PauseFeature>
): Set<PauseFeature> {
    if (feature == null) return PauseFeature.ALL
    if (selection == PauseFeature.ALL) return fitPauseSelection(setOf(feature), available)
    val toggled = if (feature in selection) selection - feature else selection + feature
    return fitPauseSelection(toggled, available)
}

/**
 * "Morning" is when quiet hours end, so a pause until morning hands over to the same schedule,
 * or 07:00 for anyone without quiet hours.
 */
internal fun pauseMorningMinutes(quietHoursEnabled: Boolean, quietHoursEndMinutes: Int): Int =
    if (quietHoursEnabled) quietHoursEndMinutes else DEFAULT_MORNING_MINUTES

/** The next time the clock reads [minutesOfDay] after [nowMillis]: later today, or else tomorrow. */
internal fun nextTimeOfDayMillis(nowMillis: Long, minutesOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val minutes = minutesOfDay.mod(24 * 60)
    val today = now.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(zone)
    val next = if (today.isAfter(now)) today else today.plusDays(1)
    return next.toInstant().toEpochMilli()
}

private const val HOUR_MS = 60 * 60_000L
private const val DEFAULT_MORNING_MINUTES = 7 * 60
