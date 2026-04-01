package com.mimeo.android.ui.reader

import com.mimeo.android.model.PlaybackChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderFullTextRangeMappingTest {

    @Test
    fun buildsJoinedChunkStartOffsetsWithParagraphSeparators() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "Alpha", startChar = 0, endChar = 5),
            PlaybackChunk(index = 1, text = "Beta", startChar = 5, endChar = 9),
            PlaybackChunk(index = 2, text = "Gamma", startChar = 9, endChar = 14),
        )

        val starts = buildChunkStartOffsetsForJoinedText(chunks)

        assertEquals(listOf(0, 7, 13), starts)
    }

    @Test
    fun mapsChunkRangesToJoinedFullTextPrecisely() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "hello", startChar = 0, endChar = 5),
            PlaybackChunk(index = 1, text = "world", startChar = 5, endChar = 10),
        )
        val fullText = chunks.joinToString(separator = "\n\n") { it.text }
        val starts = buildChunkStartOffsetsForJoinedText(chunks)

        val mapped = mapChunkRangeToFullText(
            chunkIndex = 1,
            chunks = chunks,
            chunkStartOffsets = starts,
            range = 1..3, // "orl"
            fullTextLength = fullText.length,
        )

        assertEquals((8..10), mapped)
    }

    @Test
    fun returnsNullWhenChunkIndexIsInvalid() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "only", startChar = 0, endChar = 4),
        )
        val starts = buildChunkStartOffsetsForJoinedText(chunks)

        val mapped = mapChunkRangeToFullText(
            chunkIndex = 2,
            chunks = chunks,
            chunkStartOffsets = starts,
            range = 0..1,
            fullTextLength = 4,
        )

        assertNull(mapped)
    }
}
