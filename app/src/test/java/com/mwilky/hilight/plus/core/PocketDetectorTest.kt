package com.mwilky.hilight.plus.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDetectorTest {

    @Test
    fun coveredSensorUprightIsPocket() {
        assertTrue(PocketDetector.isPocketed(covered = true, faceDown = false))
    }

    @Test
    fun faceDownNeverCountsAsPocket() {
        assertFalse(PocketDetector.isPocketed(covered = true, faceDown = true))
    }

    @Test
    fun uncoveredSensorIsNeverPocket() {
        assertFalse(PocketDetector.isPocketed(covered = false, faceDown = false))
        assertFalse(PocketDetector.isPocketed(covered = false, faceDown = true))
    }

    @Test
    fun binaryProximityReadings() {
        assertTrue(PocketDetector.isCovered(distance = 0f, maximumRange = 5f))
        assertFalse(PocketDetector.isCovered(distance = 5f, maximumRange = 5f))
    }
}
