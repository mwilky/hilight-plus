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
    ALWAYS("always"),
    SKIP("skip");

    fun blockedBy(globalEnabled: Boolean, dndActive: Boolean): Boolean = when (this) {
        ALWAYS -> false
        SKIP -> dndActive
        INHERIT -> globalEnabled && dndActive
    }

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: INHERIT
    }
}

enum class QuietHoursMode(val id: String) {
    INHERIT("inherit"),
    ALWAYS("always"),
    SKIP("skip");

    fun blockedBy(globalEnabled: Boolean, inQuietHours: Boolean): Boolean = when (this) {
        ALWAYS -> false
        SKIP -> inQuietHours
        INHERIT -> globalEnabled && inQuietHours
    }

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: INHERIT
    }
}

/**
 * Lighting rule assigned to a specific contact for Incoming Calls.
 */
data class ContactRule(
    val id: String,
    val name: String,
    val color: Long = DEFAULT_CONTACT_COLOR,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT,
    val quietHoursStartMinutes: Int? = null,
    val quietHoursEndMinutes: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        putSharedRuleFields(color, pattern, isEnabled, faceDownMode, dndMode, quietHoursMode, quietHoursStartMinutes, quietHoursEndMinutes)
    }

    companion object {
        fun fromJson(json: JSONObject): ContactRule {
            val shared = json.readSharedRuleFields(DEFAULT_CONTACT_COLOR)
            return ContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = shared.color,
                pattern = shared.pattern,
                isEnabled = shared.isEnabled,
                faceDownMode = shared.faceDownMode,
                dndMode = shared.dndMode,
                quietHoursMode = shared.quietHoursMode,
                quietHoursStartMinutes = shared.quietHoursStartMinutes,
                quietHoursEndMinutes = shared.quietHoursEndMinutes
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
    val color: Long = DEFAULT_MESSAGE_COLOR,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT,
    val quietHoursStartMinutes: Int? = null,
    val quietHoursEndMinutes: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        putSharedRuleFields(color, pattern, isEnabled, faceDownMode, dndMode, quietHoursMode, quietHoursStartMinutes, quietHoursEndMinutes)
    }

    companion object {
        fun fromJson(json: JSONObject): MessageContactRule {
            val shared = json.readSharedRuleFields(DEFAULT_MESSAGE_COLOR)
            return MessageContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = shared.color,
                pattern = shared.pattern,
                isEnabled = shared.isEnabled,
                faceDownMode = shared.faceDownMode,
                dndMode = shared.dndMode,
                quietHoursMode = shared.quietHoursMode,
                quietHoursStartMinutes = shared.quietHoursStartMinutes,
                quietHoursEndMinutes = shared.quietHoursEndMinutes
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
    val color: Long = DEFAULT_APP_COLOR,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true,
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT,
    val isAutoColor: Boolean = true,
    val dndMode: DndMode = DndMode.INHERIT,
    val quietHoursMode: QuietHoursMode = QuietHoursMode.INHERIT,
    val quietHoursStartMinutes: Int? = null,
    val quietHoursEndMinutes: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("isAutoColor", isAutoColor)
        putSharedRuleFields(color, pattern, isEnabled, faceDownMode, dndMode, quietHoursMode, quietHoursStartMinutes, quietHoursEndMinutes)
    }

    companion object {
        fun fromJson(json: JSONObject): AppNotificationRule {
            val shared = json.readSharedRuleFields(DEFAULT_APP_COLOR)
            return AppNotificationRule(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                color = shared.color,
                pattern = shared.pattern,
                isEnabled = shared.isEnabled,
                faceDownMode = shared.faceDownMode,
                isAutoColor = json.optBoolean("isAutoColor", true),
                dndMode = shared.dndMode,
                quietHoursMode = shared.quietHoursMode,
                quietHoursStartMinutes = shared.quietHoursStartMinutes,
                quietHoursEndMinutes = shared.quietHoursEndMinutes
            )
        }
    }
}

private const val DEFAULT_CONTACT_COLOR = 0xFF4285F4L
private const val DEFAULT_MESSAGE_COLOR = 0xFF00E5FFL
private const val DEFAULT_APP_COLOR = 0xFF34A853L

/**
 * The pattern/color/enable/condition fields every rule type shares, read and written
 * identically regardless of what the rule targets (a contact, a sender, or an app).
 */
private data class SharedRuleFields(
    val color: Long,
    val pattern: PatternMode,
    val isEnabled: Boolean,
    val faceDownMode: FaceDownMode,
    val dndMode: DndMode,
    val quietHoursMode: QuietHoursMode,
    val quietHoursStartMinutes: Int?,
    val quietHoursEndMinutes: Int?
)

private fun JSONObject.readSharedRuleFields(defaultColor: Long): SharedRuleFields = SharedRuleFields(
    color = optLong("color", defaultColor),
    pattern = runCatching { PatternMode.valueOf(optString("pattern", PatternMode.PULSE.name)) }.getOrDefault(PatternMode.PULSE),
    isEnabled = optBoolean("isEnabled", true),
    faceDownMode = runCatching { FaceDownMode.valueOf(optString("faceDownMode", FaceDownMode.INHERIT.name)) }.getOrDefault(FaceDownMode.INHERIT),
    dndMode = runCatching { DndMode.valueOf(optString("dndMode", DndMode.INHERIT.name)) }.getOrDefault(DndMode.INHERIT),
    quietHoursMode = runCatching { QuietHoursMode.valueOf(optString("quietHoursMode", QuietHoursMode.INHERIT.name)) }.getOrDefault(QuietHoursMode.INHERIT),
    quietHoursStartMinutes = optionalMinutes("quietHoursStartMinutes"),
    quietHoursEndMinutes = optionalMinutes("quietHoursEndMinutes")
)

private fun JSONObject.putSharedRuleFields(
    color: Long,
    pattern: PatternMode,
    isEnabled: Boolean,
    faceDownMode: FaceDownMode,
    dndMode: DndMode,
    quietHoursMode: QuietHoursMode,
    quietHoursStartMinutes: Int?,
    quietHoursEndMinutes: Int?
) {
    put("color", color)
    put("pattern", pattern.name)
    put("isEnabled", isEnabled)
    put("faceDownMode", faceDownMode.name)
    put("dndMode", dndMode.name)
    put("quietHoursMode", quietHoursMode.name)
    putOptionalMinutes("quietHoursStartMinutes", quietHoursStartMinutes)
    putOptionalMinutes("quietHoursEndMinutes", quietHoursEndMinutes)
}

private fun JSONObject.optionalMinutes(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.putOptionalMinutes(key: String, value: Int?) {
    if (value != null) put(key, value)
}
