package com.mimeo.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderVisibleBoundsTest {

    @Test
    fun appliesTopAndBottomOcclusionsToVisibleWindow() {
        val (top, bottom) = computeReaderVisibleBounds(
            viewportTopInRoot = 100f,
            viewportHeightPx = 600,
            topOcclusionPx = 120f,
            bottomOcclusionPx = 80f,
        )

        assertEquals(220f, top, 0.001f)
        assertEquals(620f, bottom, 0.001f)
    }

    @Test
    fun clampsCollapsedVisibleWindowToMinimumHeight() {
        val (top, bottom) = computeReaderVisibleBounds(
            viewportTopInRoot = 0f,
            viewportHeightPx = 100,
            topOcclusionPx = 90f,
            bottomOcclusionPx = 30f,
        )

        assertEquals(90f, top, 0.001f)
        assertEquals(91f, bottom, 0.001f)
    }

    @Test
    fun glyphOffsetPrefersNonWhitespaceNearStart() {
        val offset = findReaderVisibleGlyphOffset(
            text = "  hello",
            start = 0,
            end = 6,
            preferEnd = false,
        )

        assertEquals(2, offset)
    }

    @Test
    fun glyphOffsetPrefersNonWhitespaceNearEnd() {
        val offset = findReaderVisibleGlyphOffset(
            text = "word   ",
            start = 0,
            end = 6,
            preferEnd = true,
        )

        assertEquals(3, offset)
    }
}
