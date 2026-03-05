package com.mimeo.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsRangeAdapterTest {

    @Test
    fun normalizesRangeIntoChunkCoordinates() {
        val range = normalizeActiveChunkRange(
            textLength = 40,
            baseOffset = 10,
            start = 2,
            endExclusive = 6,
        )

        assertEquals(12 until 16, range)
    }

    @Test
    fun clampsOutOfBoundsRange() {
        val range = normalizeActiveChunkRange(
            textLength = 20,
            baseOffset = 5,
            start = -3,
            endExclusive = 99,
        )

        assertEquals(2 until 20, range)
    }

    @Test
    fun rejectsEmptyOrBackwardsRange() {
        assertNull(
            normalizeActiveChunkRange(
                textLength = 20,
                baseOffset = 0,
                start = 5,
                endExclusive = 5,
            ),
        )
        assertNull(
            normalizeActiveChunkRange(
                textLength = 20,
                baseOffset = 0,
                start = 8,
                endExclusive = 4,
            ),
        )
    }
}
