package com.mwilky.hilight.plus

import org.json.JSONObject

enum class PatternMode(val id: String, val titleRes: Int) {
    OFF("off", R.string.pattern_off),
    SOLID("solid", R.string.pattern_solid),
    BREATHE("breathe", R.string.pattern_breathe),
    PULSE("pulse", R.string.pattern_pulse),
    WAVE("wave", R.string.pattern_wave),
    COMET("comet", R.string.pattern_comet),
    ORBIT("orbit", R.string.pattern_orbit),
    BEACON("beacon", R.string.pattern_beacon),
    RIPPLE("ripple", R.string.pattern_ripple),
    SPARKLE("sparkle", R.string.pattern_sparkle),
    RAINBOW("rainbow", R.string.pattern_rainbow);

    fun speedMs(fallback: Long = 1000L): Long = when (this) {
        BREATHE -> 2000L
        WAVE -> 1200L
        COMET -> 800L
        ORBIT -> 1000L
        BEACON -> 750L
        RIPPLE -> 900L
        SPARKLE -> 1400L
        RAINBOW -> 1200L
        PULSE -> 850L
        else -> fallback
    }
}

/**
 * Behavior when device is unlocked.
 */
enum class UnlockBehavior(val id: String) {
    NONE("none"),
    PAUSE("pause"),
    CLEAR("clear")
}

/**
 * Orientation trigger preference for a rule.
 */
enum class FaceDownMode(val id: String) {
    INHERIT("inherit"),
    ALWAYS("always"),
    ONLY_FACE_DOWN("face_down");

    fun requiresFaceDown(globalOnlyWhenFaceDown: Boolean): Boolean = when (this) {
        ALWAYS -> false
        ONLY_FACE_DOWN -> true
        INHERIT -> globalOnlyWhenFaceDown
    }
}

enum class DndMode(val id: String) {
    INHERIT("inherit"),
    ALWAYS("always");

    fun blockedBy(globalEnabled: Boolean, dndActive: Boolean): Boolean =
        this == INHERIT && globalEnabled && dndActive
}

enum class QuietHoursMode(val id: String) {
    INHERIT("inherit"),
    ALWAYS("always");

    fun blockedBy(globalEnabled: Boolean, inQuietHours: Boolean): Boolean =
        this == INHERIT && globalEnabled && inQuietHours
}

data class LightStyle(
    val pattern: PatternMode = PatternMode.OFF,
    val color: Long = 0xFF000000,
    val speedMs: Long = 2000,
    val brightness: Float = 1.0f
)

/**
 * Lighting rule assigned to a specific contact for Incoming Calls.
 */
data class ContactRule(
    val id: String,
    val name: String,
    val color: Long = 0xFF4285F4,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
        put("dndMode", dndMode.name)
        put("quietHoursMode", quietHoursMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ContactRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            val dndName = json.optString("dndMode", DndMode.INHERIT.name)
            val dnd = runCatching { DndMode.valueOf(dndName) }.getOrDefault(DndMode.INHERIT)
            val quietName = json.optString("quietHoursMode", QuietHoursMode.INHERIT.name)
            val quiet = runCatching { QuietHoursMode.valueOf(quietName) }.getOrDefault(QuietHoursMode.INHERIT)
            return ContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = json.optLong("color", 0xFF4285F4),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown,
                dndMode = dnd,
                quietHoursMode = quiet
            )
        }
    }
}

/**
 * Lighting rule assigned to a specific contact for Messages & Chats (Notifications).
 */
data class MessageContactRule(
    val id: String,
    val name: String,
    val color: Long = 0xFF00E5FF,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
        put("dndMode", dndMode.name)
        put("quietHoursMode", quietHoursMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject): MessageContactRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            val dndName = json.optString("dndMode", DndMode.INHERIT.name)
            val dnd = runCatching { DndMode.valueOf(dndName) }.getOrDefault(DndMode.INHERIT)
            val quietName = json.optString("quietHoursMode", QuietHoursMode.INHERIT.name)
            val quiet = runCatching { QuietHoursMode.valueOf(quietName) }.getOrDefault(QuietHoursMode.INHERIT)
            return MessageContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = json.optLong("color", 0xFF00E5FF),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown,
                dndMode = dnd,
                quietHoursMode = quiet
            )
        }
    }
}

/**
 * Lighting rule assigned to an installed Android application (e.g. WhatsApp, Slack).
 */
data class AppNotificationRule(
    val packageName: String,
    val appName: String,
    val color: Long = 0xFF34A853,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val isAutoColor: Boolean = true,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
        put("isAutoColor", isAutoColor)
        put("dndMode", dndMode.name)
        put("quietHoursMode", quietHoursMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject): AppNotificationRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            val dndName = json.optString("dndMode", DndMode.INHERIT.name)
            val dnd = runCatching { DndMode.valueOf(dndName) }.getOrDefault(DndMode.INHERIT)
            val quietName = json.optString("quietHoursMode", QuietHoursMode.INHERIT.name)
            val quiet = runCatching { QuietHoursMode.valueOf(quietName) }.getOrDefault(QuietHoursMode.INHERIT)
            return AppNotificationRule(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                color = json.optLong("color", 0xFF34A853),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown,
                isAutoColor = json.optBoolean("isAutoColor", true),
                dndMode = dnd,
                quietHoursMode = quiet
            )
        }
    }
}
