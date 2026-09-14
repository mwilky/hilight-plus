package com.mwilky.hilight.plus.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallSessionTest {

    @Test
    fun blankRingThenNumberUpgrades() {
        val session = IncomingCallSession()

        val first = session.onRinging("", nowMs = 1_000L)
        val second = session.onRinging("+15551212", nowMs = 1_100L)

        assertEquals(IncomingCallSession.Effect.Start(""), first)
        assertEquals(IncomingCallSession.Effect.Start("+15551212"), second)
    }

    @Test
    fun duplicateRingingIsIgnored() {
        val session = IncomingCallSession()
        session.onRinging("+15551212", nowMs = 1_000L)

        assertTrue(session.isRinging)
        assertEquals("+15551212", session.number)
        assertEquals(IncomingCallSession.Effect.Ignore, session.onRinging("+15551212", nowMs = 1_200L))
        assertEquals(IncomingCallSession.Effect.Ignore, session.onRinging("", nowMs = 1_300L))
        session.onEnded(nowMs = 1_400L)
        assertTrue(!session.isRinging)
        assertEquals("", session.number)
    }

    @Test
    fun ringingAfterEndIsRejectedAsStale() {
        val session = IncomingCallSession()
        session.onRinging("+15551212", nowMs = 1_000L)
        assertEquals(IncomingCallSession.Effect.Stop, session.onEnded(nowMs = 2_000L))

        assertEquals(IncomingCallSession.Effect.Ignore, session.onRinging("+15551212", nowMs = 2_100L))
        assertEquals(IncomingCallSession.Effect.Ignore, session.onRinging("", nowMs = 2_200L))
    }

    @Test
    fun aLaterDifferentCallerIsANewCall() {
        val session = IncomingCallSession()
        session.onRinging("+15550001", nowMs = 1_000L)
        session.onEnded(nowMs = 1_500L)

        val next = session.onRinging("+15550002", nowMs = 1_800L)
        assertEquals(IncomingCallSession.Effect.Start("+15550002"), next)
    }

    @Test
    fun ringingAfterTheStaleWindowStartsAgain() {
        val session = IncomingCallSession(staleWindowMs = 2_000L)
        session.onRinging("+15551212", nowMs = 1_000L)
        session.onEnded(nowMs = 2_000L)

        val next = session.onRinging("+15551212", nowMs = 4_100L)
        assertTrue(next is IncomingCallSession.Effect.Start)
    }

    @Test
    fun endAlwaysStopsEvenIfWeNeverSawRinging() {
        val session = IncomingCallSession()
        assertEquals(IncomingCallSession.Effect.Stop, session.onEnded(nowMs = 50L))
    }
}
