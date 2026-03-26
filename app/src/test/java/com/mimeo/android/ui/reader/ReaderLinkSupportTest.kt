package com.mimeo.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLinkSupportTest {

    @Test
    fun extractReaderHttpLinksFindsMultipleLinks() {
        val text = "Start https://example.com/a then https://foo.bar/b?q=1 end"

        val links = extractReaderHttpLinks(text)

        assertEquals(2, links.size)
        assertEquals("https://example.com/a", links[0].url)
        assertEquals("https://foo.bar/b?q=1", links[1].url)
    }

    @Test
    fun extractReaderHttpLinksTrimsTrailingPunctuation() {
        val text = "Read this: https://example.com/path)."

        val links = extractReaderHttpLinks(text)

        assertEquals(1, links.size)
        assertEquals("https://example.com/path", links[0].url)
        assertTrue(links[0].endExclusive <= text.length)
    }
}
