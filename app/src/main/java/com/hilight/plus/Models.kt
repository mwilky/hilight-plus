package com.hilight.plus

import org.json.JSONObject

enum class PatternMode(val id: String, val displayName: String) {
    OFF("off", "Off"),
    SOLID("solid", "Solid"),
    BREATHE("breathe", "Breathe"),
    PULSE("pulse", "Pulse"),
    WAVE("wave", "Wave"),
    COMET("comet", "Comet"),
    RAINBOW("rainbow", "Rainbow"),

    // --- Effect Placeholders for Future Implementation ---
    GEMINI_LISTENING("gemini_listening", "Gemini Listening"),
    GEMINI_THINKING("gemini_thinking", "Gemini Thinking"),
    GEMINI_RESPONDING("gemini_responding", "Gemini Responding"),
    CONTACT_CALL_ALERT("contact_call_alert", "Favorite Call Alert")
}

data class LightStyle(
    val pattern: PatternMode = PatternMode.OFF,
    val color: Long = 0xFF000000,
    val speedMs: Long = 2000,
    val brightness: Float = 1.0f
)

/**
 * Lighting rule assigned to a specific contact or phone number.
 */
data class ContactRule(
    val id: String, // Unique identifier / Contact Lookup Key
    val name: String,
    val phoneNumber: String, // Normalized phone number
    val color: Long,
    val pattern: PatternMode = PatternMode.PULSE,
    val isEnabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phoneNumber", phoneNumber)
        put("color", color)
        put("pattern", pattern.name)
        put("isEnabled", isEnabled)
    }

    companion object {
        fun fromJson(json: JSONObject): ContactRule {
            val patternName = json.optString("pattern", PatternMode.PULSE.name)
            val pattern = runCatching { PatternMode.valueOf(patternName) }.getOrDefault(PatternMode.PULSE)
            return ContactRule(
                id = json.optString("id", ""),
                name = json.optString("name", "Unknown Contact"),
                phoneNumber = json.optString("phoneNumber", ""),
                color = json.optLong("color", 0xFF4285F4),
                pattern = pattern,
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}
