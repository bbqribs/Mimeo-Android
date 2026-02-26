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

    @Test
    fun percentStaysMonotonicAcrossVaryingChunkSizes() {
        val variableChunks = listOf(
            PlaybackChunk(index = 0, startChar = 0, endChar = 640, text = "a".repeat(640)),
            PlaybackChunk(index = 1, startChar = 640, endChar = 1710, text = "b".repeat(1070)),
            PlaybackChunk(index = 2, startChar = 1710, endChar = 2330, text = "c".repeat(620)),
            PlaybackChunk(index = 3, startChar = 2330, endChar = 3010, text = "d".repeat(680)),
        )
        val totalChars = 3010
        var lastPercent = -1

        for (chunk in variableChunks) {
            val chunkLength = chunk.endChar - chunk.startChar
            var offset = 0
            while (offset <= chunkLength) {
                val percent = calculateCanonicalPercent(
                    totalChars = totalChars,
                    chunks = variableChunks,
                    position = PlaybackPosition(chunkIndex = chunk.index, offsetInChunkChars = offset),
                )
                assert(percent in 0..100)
                assert(percent >= lastPercent)
                lastPercent = percent
                offset += 137
            }
        }
    }

    @Test
    fun absoluteOffsetMapsToExpectedChunkAndOffset() {
        val position = positionFromAbsoluteOffset(
            totalChars = 260,
            chunks = chunks,
            absoluteOffset = 145,
        )
        assertEquals(1, position.chunkIndex)
        assertEquals(45, position.offsetInChunkChars)
    }

    @Test
    fun absoluteOffsetClampsToBounds() {
        val beforeStart = positionFromAbsoluteOffset(
            totalChars = 260,
            chunks = chunks,
            absoluteOffset = -100,
        )
        val afterEnd = positionFromAbsoluteOffset(
            totalChars = 260,
            chunks = chunks,
            absoluteOffset = 9_999,
        )
        assertEquals(0, beforeStart.chunkIndex)
        assertEquals(0, beforeStart.offsetInChunkChars)
        assertEquals(2, afterEnd.chunkIndex)
        assertEquals(60, afterEnd.offsetInChunkChars)
    }
}
