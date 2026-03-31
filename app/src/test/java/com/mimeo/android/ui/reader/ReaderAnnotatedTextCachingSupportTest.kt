package com.mimeo.android.ui.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAnnotatedTextCachingSupportTest {

    @Test
    fun baseAnnotatedTextPreservesLinksAndPassiveSearchRanges() {
        val text = "Visit https://example.com now"
        val links = listOf(ReaderLinkRange(start = 6, endExclusive = 25, url = "https://example.com"))
        val passiveSearchBg = Color(0x66112233)
        val passiveRanges = listOf(0..4)

        val result = buildReaderBaseAnnotatedText(
            text = text,
            links = links,
            passiveSearchRanges = passiveRanges,
            passiveSearchHighlightBg = passiveSearchBg,
        )

        assertEquals(text, result.text)
        val urlAnnotations = result.getStringAnnotations(
            tag = "reader-url",
            start = 10,
            end = 10,
        )
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://example.com", urlAnnotations.first().item)
        assertTrue(
            result.spanStyles.any { span ->
                span.start == 0 &&
                    span.end == 5 &&
                    span.item.background == passiveSearchBg
            },
        )
    }

    @Test
    fun overlayAddsHighlightWithoutLosingExistingAnnotations() {
        val base = buildReaderBaseAnnotatedText(
            text = "abcdef",
            links = emptyList(),
            passiveSearchRanges = emptyList(),
            passiveSearchHighlightBg = Color.Transparent,
        )
        val highlightBg = Color(0x5500FF00)
        val focusBg = Color(0x55FF0000)

        val result = withReaderHighlightOverlays(
            base = base,
            textLength = base.text.length,
            highlightRange = 1..2,
            focusedSearchRange = 3..4,
            highlightBg = highlightBg,
            focusedSearchHighlightBg = focusBg,
        )

        assertEquals("abcdef", result.text)
        assertTrue(
            result.spanStyles.any { span ->
                span.start == 1 && span.end == 3 && span.item.background == highlightBg
            },
        )
        assertTrue(
            result.spanStyles.any { span ->
                span.start == 3 && span.end == 5 && span.item.background == focusBg
            },
        )
    }
}
