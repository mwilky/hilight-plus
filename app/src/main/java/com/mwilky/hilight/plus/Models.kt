package com.mwilky.hilight.plus

import org.json.JSONObject

enum class PatternMode(val id: String, val displayName: String) {
    OFF("off", "Off"),
    SOLID("solid", "Solid"),
    BREATHE("breathe", "Breathe"),
    PULSE("pulse", "Pulse"),
    WAVE("wave", "Wave"),
    COMET("comet", "Comet"),
    ORBIT("orbit", "Orbit"),
    BEACON("beacon", "Beacon"),
    RIPPLE("ripple", "Ripple"),
    SPARKLE("sparkle", "Sparkle"),
    RAINBOW("rainbow", "Rainbow")
}

/**
 * Behavior when device is unlocked.
 */
enum class UnlockBehavior(val id: String, val displayName: String) {
    NONE("none", "None"),
    PAUSE("pause", "Pause"),
    CLEAR("clear", "Clear")
}

/**
 * Orientation trigger preference for a rule.
 */
enum class FaceDownMode(val id: String, val displayName: String) {
    INHERIT("inherit", "Default (Follows Conditions)"),
    ALWAYS("always", "Always (Face Up or Down)"),
    ONLY_FACE_DOWN("face_down", "Face Down Only");

    fun requiresFaceDown(globalOnlyWhenFaceDown: Boolean): Boolean = when (this) {
        ALWAYS -> false
        ONLY_FACE_DOWN -> true
        INHERIT -> globalOnlyWhenFaceDown
    }
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
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ContactRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            return ContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = json.optLong("color", 0xFF4285F4),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown
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
    val faceDownMode: FaceDownMode = FaceDownMode.INHERIT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject): MessageContactRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            return MessageContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                color = json.optLong("color", 0xFF00E5FF),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown
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
    val isAutoColor: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
        put("faceDownMode", faceDownMode.name)
        put("isAutoColor", isAutoColor)
    }

    companion object {
        fun fromJson(json: JSONObject): AppNotificationRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            val faceDownName = json.optString("faceDownMode", FaceDownMode.INHERIT.name)
            val faceDown = runCatching { FaceDownMode.valueOf(faceDownName) }.getOrDefault(FaceDownMode.INHERIT)
            return AppNotificationRule(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                color = json.optLong("color", 0xFF34A853),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true),
                faceDownMode = faceDown,
                isAutoColor = json.optBoolean("isAutoColor", true)
            )
        }
    }
}
