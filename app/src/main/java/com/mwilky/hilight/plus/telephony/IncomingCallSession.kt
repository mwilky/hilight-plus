package com.mwilky.hilight.plus.telephony

/**
 * Serial call-state machine. A late RINGING after IDLE/OFFHOOK is ignored so
 * contact lookup finishing after hangup cannot restart lights.
 */
internal class IncomingCallSession(
    private val staleWindowMs: Long = 2_000L
) {
    sealed class Effect {
        data object Ignore : Effect()
        data object Stop : Effect()
        data class Start(val number: String) : Effect()
    }

    private var ringing = false
    private var currentNumber = ""
    private var endedAtMs = 0L
    private var endedNumber = ""

    val isRinging: Boolean get() = ringing
    val number: String get() = currentNumber

    fun onRinging(number: String, nowMs: Long): Effect {
        val normalized = number.trim()
        if (!ringing) {
            if (isStale(normalized, nowMs)) return Effect.Ignore
            ringing = true
            currentNumber = normalized
            return Effect.Start(normalized)
        }
        if (normalized.isNotBlank() && normalized != currentNumber) {
            currentNumber = normalized
            return Effect.Start(normalized)
        }
        return Effect.Ignore
    }

    fun onEnded(nowMs: Long): Effect {
        endedNumber = currentNumber
        endedAtMs = nowMs
        ringing = false
        currentNumber = ""
        return Effect.Stop
    }

    private fun isStale(number: String, nowMs: Long): Boolean {
        if (endedAtMs == 0L) return false
        if (nowMs - endedAtMs > staleWindowMs) return false
        return number.isBlank() || endedNumber.isBlank() || number == endedNumber
    }
}
