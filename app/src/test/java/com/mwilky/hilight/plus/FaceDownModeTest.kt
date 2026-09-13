package com.mwilky.hilight.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownModeTest {

    @Test
    fun alwaysNeverRequiresFaceDown() {
        assertFalse(FaceDownMode.ALWAYS.requiresFaceDown(globalOnlyWhenFaceDown = true))
        assertFalse(FaceDownMode.ALWAYS.requiresFaceDown(globalOnlyWhenFaceDown = false))
    }

    @Test
    fun onlyFaceDownAlwaysRequiresFaceDown() {
        assertTrue(FaceDownMode.ONLY_FACE_DOWN.requiresFaceDown(globalOnlyWhenFaceDown = true))
        assertTrue(FaceDownMode.ONLY_FACE_DOWN.requiresFaceDown(globalOnlyWhenFaceDown = false))
    }

    @Test
    fun inheritFollowsTheGlobalCondition() {
        assertTrue(FaceDownMode.INHERIT.requiresFaceDown(globalOnlyWhenFaceDown = true))
        assertFalse(FaceDownMode.INHERIT.requiresFaceDown(globalOnlyWhenFaceDown = false))
    }
}
