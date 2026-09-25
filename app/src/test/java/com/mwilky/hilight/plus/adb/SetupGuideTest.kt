package com.mwilky.hilight.plus.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-in connection's guided setup: which step the user is on, what the guide notification
 * says, and when it offers the code field. All derived from system state, so it survives the app
 * being killed mid-setup.
 */
class SetupGuideTest {

    private val ready = SetupState(devOptionsOn = true, wifiConnected = true, wirelessDebuggingOn = true)

    @Test
    fun stepsFollowTheOrderTheSystemRequires() {
        assertEquals(SetupStep.DEV_OPTIONS, SetupState().step)
        assertEquals(SetupStep.WIFI, SetupState(devOptionsOn = true).step)
        assertEquals(SetupStep.WIRELESS_DEBUGGING, SetupState(devOptionsOn = true, wifiConnected = true).step)
        assertEquals(SetupStep.PAIR, ready.step)
    }

    @Test
    fun developerOptionsComesFirstEvenWhenLaterSwitchesAreOn() {
        // Turning Developer options off also hides Wireless debugging, so it has to be fixed first.
        assertEquals(SetupStep.DEV_OPTIONS, ready.copy(devOptionsOn = false).step)
    }

    @Test
    fun connectedIsDoneWhateverElseSays() {
        assertEquals(SetupStep.DONE, SetupState(connected = true).step)
        assertEquals(GuideNotice.DONE, SetupState(connected = true).guideNotice())
    }

    @Test
    fun noticeTracksEachStep() {
        assertEquals(GuideNotice.TAP_BUILD_NUMBER, SetupState().guideNotice())
        assertEquals(GuideNotice.CONNECT_WIFI, SetupState(devOptionsOn = true).guideNotice())
        assertEquals(GuideNotice.TURN_ON_WIRELESS_DEBUGGING, SetupState(devOptionsOn = true, wifiConnected = true).guideNotice())
        assertEquals(GuideNotice.TAP_PAIR, ready.guideNotice())
    }

    @Test
    fun noticeTracksEachPairingPhase() {
        assertEquals(GuideNotice.ENTER_CODE, ready.copy(pairing = PairingPhase.CODE_NEEDED).guideNotice())
        assertEquals(GuideNotice.CONNECTING, ready.copy(pairing = PairingPhase.PAIRING).guideNotice())
        assertEquals(GuideNotice.CONNECTING, ready.copy(pairing = PairingPhase.STARTING).guideNotice())
        assertEquals(GuideNotice.WRONG_CODE, ready.copy(pairing = PairingPhase.WRONG_CODE).guideNotice())
        assertEquals(GuideNotice.FAILED, ready.copy(pairing = PairingPhase.FAILED).guideNotice())
    }

    @Test
    fun codeFieldOnlyWhileThePairingDialogIsOpen() {
        assertFalse(ready.copy(pairing = PairingPhase.CODE_NEEDED).acceptsCode())
        assertTrue(ready.copy(pairing = PairingPhase.CODE_NEEDED, codeEntryAvailable = true).acceptsCode())
        assertTrue(ready.copy(pairing = PairingPhase.WRONG_CODE, codeEntryAvailable = true).acceptsCode())
        assertFalse(ready.copy(pairing = PairingPhase.PAIRING, codeEntryAvailable = true).acceptsCode())
        assertFalse(ready.copy(pairing = PairingPhase.SEARCHING, codeEntryAvailable = true).acceptsCode())
    }

    @Test
    fun codeFieldNeverOnAnEarlierStep() {
        val stale = SetupState(devOptionsOn = true, pairing = PairingPhase.CODE_NEEDED, codeEntryAvailable = true)
        assertFalse(stale.acceptsCode())
    }

    @Test
    fun pairingCodeAcceptsWhatPeopleActuallyType() {
        assertEquals("123456", normalisePairingCode("123456"))
        assertEquals("123456", normalisePairingCode(" 123 456 "))
        assertEquals("123456", normalisePairingCode("123-456"))
    }

    @Test
    fun pairingCodeRejectsAnythingElse() {
        assertNull(normalisePairingCode(""))
        assertNull(normalisePairingCode("12345"))
        assertNull(normalisePairingCode("1234567"))
        assertNull(normalisePairingCode("12a456"))
    }
}
