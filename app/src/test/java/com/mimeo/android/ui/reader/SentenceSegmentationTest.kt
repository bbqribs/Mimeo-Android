package com.mimeo.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmentationTest {

    @Test
    fun splitsSimplePunctuationIntoStableRanges() {
        val text = "First sentence. Second sentence? Third sentence!"

        val ranges = segmentSentences(text)

        assertEquals(
            listOf(
                "First sentence. ",
                "Second sentence? ",
                "Third sentence!",
            ),
            ranges.map { text.substring(it.start, it.endExclusive) },
        )
    }

    @Test
    fun treatsNewlinesAsBoundaries() {
        val text = "Line one.\nLine two\nLine three"

        val ranges = segmentSentences(text)

        assertEquals(
            listOf(
                "Line one.\n",
                "Line two\n",
                "Line three",
            ),
            ranges.map { text.substring(it.start, it.endExclusive) },
        )
    }

    @Test
    fun preservesMultipleSpacesInsideBoundaries() {
        val text = "First sentence.  Second sentence."

        val ranges = segmentSentences(text)

        assertEquals(
            listOf(
                "First sentence.  ",
                "Second sentence.",
            ),
            ranges.map { text.substring(it.start, it.endExclusive) },
        )
    }

    @Test
    fun handlesEmptyAndShortStrings() {
        assertTrue(segmentSentences("").isEmpty())
        assertEquals(listOf("Hi"), segmentSentences("Hi").map { "Hi".substring(it.start, it.endExclusive) })
    }

    @Test
    fun keepsNoPunctuationTextAsSingleSentence() {
        val text = "No punctuation here at all"

        val ranges = segmentSentences(text)

        assertEquals(listOf(text), ranges.map { text.substring(it.start, it.endExclusive) })
    }

    @Test
    fun doesNotSplitCommonAbbreviations() {
        val text = "Dr. Smith arrived. He waved."

        val ranges = segmentSentences(text)

        assertEquals(
            listOf(
                "Dr. Smith arrived. ",
                "He waved.",
            ),
            ranges.map { text.substring(it.start, it.endExclusive) },
        )
    }

    @Test
    fun findsSentenceByOffset() {
        val text = "Alpha. Beta. Gamma."
        val ranges = segmentSentences(text)

        val active = findSentenceRangeForOffset(text, offsetInText = 9, sentenceRanges = ranges)

        requireNotNull(active)
        assertEquals("Beta. ", text.substring(active.start, active.endExclusive))
    }
}
