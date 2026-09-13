package com.mwilky.hilight.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresenceTest {

    @Test
    fun screensaverOrFaceDownIsNotUsingThePhone() {
        assertFalse(
            DevicePresence.isActivelyUsing(
                dreaming = true,
                faceDown = false,
                interactive = true,
                keyguardLocked = false
            )
        )
        assertFalse(
            DevicePresence.isActivelyUsing(
                dreaming = false,
                faceDown = true,
                interactive = true,
                keyguardLocked = false
            )
        )
    }

    @Test
    fun unlockedInteractiveAndFaceUpIsUsingThePhone() {
        assertTrue(
            DevicePresence.isActivelyUsing(
                dreaming = false,
                faceDown = false,
                interactive = true,
                keyguardLocked = false
            )
        )
    }

    @Test
    fun lockscreenIsNotUsingThePhone() {
        assertFalse(
            DevicePresence.isActivelyUsing(
                dreaming = false,
                faceDown = false,
                interactive = true,
                keyguardLocked = true
            )
        )
    }
}
