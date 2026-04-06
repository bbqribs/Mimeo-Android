package com.mimeo.android.ui.reader

import com.mimeo.android.model.ItemTextContentBlock
import com.mimeo.android.model.ItemTextContentLink
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

    @Test
    fun extractReaderPreservedLinks_usesMetadataParagraphOffsets() {
        val text = "See the announcement for details."
        val blocks = listOf(
            ItemTextContentBlock(
                type = "paragraph",
                text = text,
                links = listOf(
                    ItemTextContentLink(
                        text = "announcement",
                        href = "https://example.com/announcement",
                        start = 8,
                        end = 20,
                    ),
                ),
            ),
        )

        val links = extractReaderPreservedLinks(text = text, contentBlocks = blocks)

        assertEquals(1, links.size)
        assertEquals(8, links.first().start)
        assertEquals(20, links.first().endExclusive)
        assertEquals("https://example.com/announcement", links.first().url)
    }

    @Test
    fun extractReaderPreservedLinks_dropsInvalidSchemeAndBounds() {
        val text = "Contact support now."
        val blocks = listOf(
            ItemTextContentBlock(
                type = "paragraph",
                text = text,
                links = listOf(
                    ItemTextContentLink(
                        text = "support",
                        href = "mailto:support@example.com",
                        start = 8,
                        end = 15,
                    ),
                    ItemTextContentLink(
                        text = "now",
                        href = "https://example.com/now",
                        start = 100,
                        end = 120,
                    ),
                ),
            ),
        )

        val links = extractReaderPreservedLinks(text = text, contentBlocks = blocks)

        assertEquals(1, links.size)
        assertEquals("https://example.com/now", links.first().url)
        assertEquals("now", text.substring(links.first().start, links.first().endExclusive))
    }

    @Test
    fun extractReaderPreservedLinks_fallsBackToLinkTextSearchWhenOffsetsMissing() {
        val text = "Track status on project board."
        val blocks = listOf(
            ItemTextContentBlock(
                type = "paragraph",
                text = text,
                links = listOf(
                    ItemTextContentLink(
                        text = "project board",
                        href = "https://example.com/board",
                    ),
                ),
            ),
        )

        val links = extractReaderPreservedLinks(text = text, contentBlocks = blocks)

        assertEquals(1, links.size)
        assertEquals("project board", text.substring(links.first().start, links.first().endExclusive))
        assertEquals("https://example.com/board", links.first().url)
    }

    @Test
    fun extractReaderPreservedLinks_fallsBackToHrefHostPathWhenLinkTextUnavailable() {
        val text = "New piece from me: newrepublic.com/article/2086..."
        val blocks = listOf(
            ItemTextContentBlock(
                type = "paragraph",
                text = text,
                links = listOf(
                    ItemTextContentLink(
                        text = "",
                        href = "https://newrepublic.com/article/2086/the-end-of-something",
                    ),
                ),
            ),
        )

        val links = extractReaderPreservedLinks(text = text, contentBlocks = blocks)

        assertEquals(1, links.size)
        val rendered = text.substring(links.first().start, links.first().endExclusive)
        assertTrue(rendered.startsWith("newrepublic.com/article/2086"))
        assertEquals("https://newrepublic.com/article/2086/the-end-of-something", links.first().url)
    }
}
