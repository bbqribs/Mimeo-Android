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
    fun chunkSeparatorIsSingleZeroWidthCharacter() {
        // A single zero-width char keeps chunk offsets stable; the visible gap
        // comes from a ParagraphStyle, not from the separator string itself.
        assertEquals(1, READER_CHUNK_SEPARATOR.length)
        assertEquals('\u200B', READER_CHUNK_SEPARATOR[0])
    }

    @Test
    fun paragraphGapLineHeightScalesWithMultiplierAndBodyHeight() {
        val body = 25.6f
        val small = readerParagraphGapLineHeight(spacingMultiplier = 0.35f, bodyLineHeightSp = body)
        val full = readerParagraphGapLineHeight(spacingMultiplier = 1.0f, bodyLineHeightSp = body)
        val double = readerParagraphGapLineHeight(spacingMultiplier = 2.0f, bodyLineHeightSp = body)

        // The gap is the multiplier times the body line height.
        assertEquals(body * 0.35f, small, 0.001f)
        assertEquals(body, full, 0.001f)
        assertEquals(body * 2.0f, double, 0.001f)
        // A zero body height yields no gap regardless of the multiplier.
        assertEquals(0f, readerParagraphGapLineHeight(spacingMultiplier = 2.0f, bodyLineHeightSp = 0f), 0.001f)
        // Out-of-range multipliers are clamped to the valid preset range.
        assertEquals(body * 4.0f, readerParagraphGapLineHeight(spacingMultiplier = 99f, bodyLineHeightSp = body), 0.001f)
    }

    @Test
    fun chunkSeparatorParagraphRangesTargetSeparatorCharacters() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "Alpha", startChar = 0, endChar = 5),
            PlaybackChunk(index = 1, text = "Beta", startChar = 5, endChar = 9),
            PlaybackChunk(index = 2, text = "Gamma", startChar = 9, endChar = 14),
        )
        val starts = buildChunkStartOffsetsForJoinedText(chunks, READER_CHUNK_SEPARATOR.length)
        val joined = chunks.joinToString(separator = READER_CHUNK_SEPARATOR) { it.text }

        val ranges = buildChunkSeparatorParagraphRanges(chunks, starts, READER_CHUNK_SEPARATOR.length)

        // One single-character range per inter-chunk separator, each landing on
        // the zero-width separator so it forms its own gap paragraph.
        assertEquals(2, ranges.size)
        ranges.forEach { range ->
            assertEquals(range.first, range.last)
            assertEquals('\u200B', joined[range.first])
        }
    }

    @Test
    fun chunkSeparatorParagraphRangesEmptyForSingleChunk() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "Alpha", startChar = 0, endChar = 5),
        )
        val starts = buildChunkStartOffsetsForJoinedText(chunks, READER_CHUNK_SEPARATOR.length)

        assertEquals(
            emptyList<IntRange>(),
            buildChunkSeparatorParagraphRanges(chunks, starts, READER_CHUNK_SEPARATOR.length),
        )
    }

    @Test
    fun joinedChunkStartOffsetsHonorSeparatorLength() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "Alpha", startChar = 0, endChar = 5),
            PlaybackChunk(index = 1, text = "Beta", startChar = 5, endChar = 9),
            PlaybackChunk(index = 2, text = "Gamma", startChar = 9, endChar = 14),
        )

        assertEquals(listOf(0, 6, 11), buildChunkStartOffsetsForJoinedText(chunks, separatorLength = 1))
        assertEquals(listOf(0, 8, 15), buildChunkStartOffsetsForJoinedText(chunks, separatorLength = 3))
    }

    @Test
    fun joinedOffsetsIndexChunksWithinSeparatedText() {
        val chunks = listOf(
            PlaybackChunk(index = 0, text = "first", startChar = 0, endChar = 5),
            PlaybackChunk(index = 1, text = "second", startChar = 5, endChar = 11),
        )
        val joined = chunks.joinToString(separator = READER_CHUNK_SEPARATOR) { it.text }
        val starts = buildChunkStartOffsetsForJoinedText(chunks, READER_CHUNK_SEPARATOR.length)
        // The computed start of the second chunk must index its real position in the joined text.
        assertEquals(chunks[1].text, joined.substring(starts[1], starts[1] + chunks[1].text.length))
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

    @Test
    fun searchFocusTargetInFullTextAlignsToDesiredTopOffset() {
        val target = computeReaderSearchFocusScrollTarget(
            useFullTextLayout = true,
            scrollValue = 500,
            scrollMaxValue = 5_000,
            startBoxTop = 1_240f,
            startTopInRoot = 0f,
            desiredAnchorInRoot = 0f,
            topComfortPx = 0f,
            searchFocusExtraTopPx = 24f,
        )

        assertEquals(1216, target)
    }

    @Test
    fun searchFocusTargetClampsAtBoundsNearTopAndBottom() {
        val nearTop = computeReaderSearchFocusScrollTarget(
            useFullTextLayout = true,
            scrollValue = 100,
            scrollMaxValue = 2_000,
            startBoxTop = 12f,
            startTopInRoot = 0f,
            desiredAnchorInRoot = 0f,
            topComfortPx = 0f,
            searchFocusExtraTopPx = 24f,
        )
        val nearBottom = computeReaderSearchFocusScrollTarget(
            useFullTextLayout = true,
            scrollValue = 100,
            scrollMaxValue = 2_000,
            startBoxTop = 5_000f,
            startTopInRoot = 0f,
            desiredAnchorInRoot = 0f,
            topComfortPx = 0f,
            searchFocusExtraTopPx = 24f,
        )

        assertEquals(0, nearTop)
        assertEquals(2_000, nearBottom)
    }
}
