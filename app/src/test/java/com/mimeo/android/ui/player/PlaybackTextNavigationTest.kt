package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTextNavigationTest {

    @Test
    fun nextSentenceMovesWithinChunk() {
        val chunks = chunksOf(
            "First sentence. Second sentence. Third sentence.",
        )

        val next = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 2),
            direction = 1,
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 16), next)
    }

    @Test
    fun previousSentenceMovesAcrossChunkBoundary() {
        val chunks = chunksOf(
            "One. Two.",
            "Three. Four.",
        )

        val prev = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 0),
            direction = -1,
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 5), prev)
    }

    @Test
    fun nextSentenceMovesAcrossChunkBoundary() {
        val chunks = chunksOf(
            "One. Two.",
            "Three. Four.",
        )

        val next = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 6),
            direction = 1,
        )

        assertEquals(PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 0), next)
    }

    @Test
    fun paragraphJumpMovesToNextAndPreviousChunkStart() {
        val chunks = chunksOf(
            "Paragraph one.",
            "Paragraph two.",
            "Paragraph three.",
        )

        val nextParagraph = resolveParagraphJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 5),
            direction = 1,
        )
        val previousParagraph = resolveParagraphJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 3),
            direction = -1,
        )

        assertEquals(PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 0), nextParagraph)
        assertEquals(PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 0), previousParagraph)
    }

    @Test
    fun boundariesReturnNullWhenNoFurtherTarget() {
        val chunks = chunksOf(
            "Only one sentence.",
        )

        val previous = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            direction = -1,
        )
        val nextParagraph = resolveParagraphJumpPosition(
            chunks = chunks,
            currentPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 5),
            direction = 1,
        )

        assertNull(previous)
        assertNull(nextParagraph)
    }

    private fun chunksOf(vararg texts: String): List<PlaybackChunk> {
        var cursor = 0
        return texts.mapIndexed { index, text ->
            val start = cursor
            val end = start + text.length
            cursor = end
            PlaybackChunk(
                index = index,
                startChar = start,
                endChar = end,
                text = text,
            )
        }
    }
}

