package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocusSearchMatchTest {

    @Test
    fun collectMatchesReturnsAllMatchesAcrossChunks() {
        val chunks = listOf(
            chunk(index = 0, text = "Alpha beta alpha"),
            chunk(index = 1, text = "No hit here"),
            chunk(index = 2, text = "alpha again"),
        )

        val matches = collectLocusSearchMatches(chunks, "alpha")

        assertEquals(3, matches.size)
        assertEquals(0, matches[0].chunkIndex)
        assertEquals(0 until 5, matches[0].rangeInChunk)
        assertEquals(0, matches[1].chunkIndex)
        assertEquals(11 until 16, matches[1].rangeInChunk)
        assertEquals(2, matches[2].chunkIndex)
        assertEquals(0 until 5, matches[2].rangeInChunk)
    }

    @Test
    fun collectMatchesIsCaseInsensitiveAndSupportsTrimmedQuery() {
        val chunks = listOf(chunk(index = 0, text = "Focus change on AUDIO"))

        val matches = collectLocusSearchMatches(chunks, "  audio  ")

        assertEquals(1, matches.size)
        assertEquals(16 until 21, matches.first().rangeInChunk)
    }

    @Test
    fun collectMatchesReturnsEmptyForBlankOrNoHitQuery() {
        val chunks = listOf(chunk(index = 0, text = "Playback item text"))

        val blankMatches = collectLocusSearchMatches(chunks, "   ")
        val missMatches = collectLocusSearchMatches(chunks, "missing")

        assertTrue(blankMatches.isEmpty())
        assertTrue(missMatches.isEmpty())
    }

    private fun chunk(index: Int, text: String): PlaybackChunk {
        return PlaybackChunk(
            index = index,
            startChar = 0,
            endChar = text.length,
            text = text,
        )
    }
}
