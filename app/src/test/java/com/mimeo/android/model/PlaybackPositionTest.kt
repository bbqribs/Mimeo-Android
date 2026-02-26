package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {
    private val chunks = listOf(
        PlaybackChunk(index = 0, startChar = 0, endChar = 100, text = "a".repeat(100)),
        PlaybackChunk(index = 1, startChar = 100, endChar = 200, text = "b".repeat(100)),
        PlaybackChunk(index = 2, startChar = 200, endChar = 260, text = "c".repeat(60)),
    )

    @Test
    fun percentUsesChunkStartPlusOffset() {
        val percent = calculateCanonicalPercent(
            totalChars = 260,
            chunks = chunks,
            position = PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 20),
        )
        assertEquals(46, percent)
    }

    @Test
    fun percentClampsAtBounds() {
        val beforeStart = calculateCanonicalPercent(
            totalChars = 260,
            chunks = chunks,
            position = PlaybackPosition(chunkIndex = -9, offsetInChunkChars = -10),
        )
        val afterEnd = calculateCanonicalPercent(
            totalChars = 260,
            chunks = chunks,
            position = PlaybackPosition(chunkIndex = 99, offsetInChunkChars = 9_999),
        )
        assertEquals(0, beforeStart)
        assertEquals(100, afterEnd)
    }

    @Test
    fun absoluteOffsetMatchesChunkMath() {
        val absolute = absoluteCharOffset(
            totalChars = 260,
            chunks = chunks,
            position = PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 15),
        )
        assertEquals(215, absolute)
    }
}
