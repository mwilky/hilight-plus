package com.mwilky.hilight.plus.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDetectorTest {

    private val grace = PocketDetector.TABLE_GRACE_MS

    @Test
    fun coveredSensorUprightIsPocket() {
        assertTrue(PocketDetector.isPocketed(covered = true, faceDown = false, wasPocketed = false, coveredForMs = 1_000))
    }

    @Test
    fun faceDownAsItCoversIsATable() {
        assertFalse(PocketDetector.isPocketed(covered = true, faceDown = true, wasPocketed = false, coveredForMs = 1_000))
        assertFalse(PocketDetector.isPocketed(covered = true, faceDown = true, wasPocketed = false, coveredForMs = grace * 10))
    }

    @Test
    fun faceDownSoonAfterCoveringStillCountsAsATable() {
        assertFalse(PocketDetector.isPocketed(covered = true, faceDown = true, wasPocketed = true, coveredForMs = grace - 1))
    }

    @Test
    fun tippingFaceDownInAPocketStaysAPocket() {
        assertTrue(PocketDetector.isPocketed(covered = true, faceDown = true, wasPocketed = true, coveredForMs = grace))
    }

    @Test
    fun uncoveredSensorIsNeverPocket() {
        assertFalse(PocketDetector.isPocketed(covered = false, faceDown = false, wasPocketed = true, coveredForMs = grace))
        assertFalse(PocketDetector.isPocketed(covered = false, faceDown = true, wasPocketed = true, coveredForMs = grace))
    }

    @Test
    fun binaryProximityReadings() {
        assertTrue(PocketDetector.isCovered(distance = 0f, maximumRange = 5f))
        assertFalse(PocketDetector.isCovered(distance = 5f, maximumRange = 5f))
    }
}
