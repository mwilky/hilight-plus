package com.hilight.plus

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
