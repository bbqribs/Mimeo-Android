package com.mimeo.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderHighlightingTest {

    @Test
    fun prefersActiveRangeWhenPresent() {
        val resolved = resolveReaderHighlightRange(
            textLength = 30,
            activeRange = 7 until 11,
            sentenceRange = SentenceRange(0, 20),
        )

        assertEquals(7 until 11, resolved)
    }

    @Test
    fun fallsBackToSentenceRangeWhenActiveRangeMissing() {
        val resolved = resolveReaderHighlightRange(
            textLength = 30,
            activeRange = null,
            sentenceRange = SentenceRange(5, 14),
        )

        assertEquals(5 until 14, resolved)
    }

    @Test
    fun clampsActiveRangeToTextLength() {
        val resolved = resolveReaderHighlightRange(
            textLength = 10,
            activeRange = 8..20,
            sentenceRange = SentenceRange(0, 5),
        )

        assertEquals(8 until 10, resolved)
    }

    @Test
    fun returnsNullWhenNothingValidExists() {
        assertNull(
            resolveReaderHighlightRange(
                textLength = 0,
                activeRange = 1 until 2,
                sentenceRange = SentenceRange(0, 1),
            ),
        )
        assertNull(
            resolveReaderHighlightRange(
                textLength = 5,
                activeRange = 4..3,
                sentenceRange = SentenceRange(5, 5),
            ),
        )
    }
}
