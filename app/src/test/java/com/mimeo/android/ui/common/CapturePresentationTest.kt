package com.mimeo.android.ui.common

import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePresentationTest {
    @Test
    fun `excerpt capture formats title and uses metadata source`() {
        val item = PlaybackQueueItem(
            itemId = 1,
            title = "The quick brown fox jumped over the lazy dog",
            url = "https://www.example.com/story",
            host = "example.com",
            sourceType = "web",
            sourceLabel = "www.example.com",
            sourceUrl = "https://www.example.com/story",
            captureKind = "shared_excerpt",
            sourceAppPackage = "com.android.chrome",
        )

        val presentation = queueCapturePresentation(item)

        assertTrue(presentation.title.startsWith("Excerpt: \""))
        assertEquals("example.com", presentation.sourceLabel)
        assertEquals("https://www.example.com/story", presentation.sourceUrl)
    }

    @Test
    fun `legacy item falls back to title and host`() {
        val item = PlaybackQueueItem(
            itemId = 2,
            title = "Legacy title",
            url = "https://legacy.example.com/a",
            host = "legacy.example.com",
            captureKind = null,
        )

        val presentation = queueCapturePresentation(item)

        assertEquals("Legacy title", presentation.title)
        assertEquals("legacy.example.com", presentation.sourceLabel)
        assertEquals("https://legacy.example.com/a", presentation.sourceUrl)
    }

    @Test
    fun `synthetic shared text url uses excerpt title and android selection fallback`() {
        val item = PlaybackQueueItem(
            itemId = 3,
            title = "Shared paragraph from app",
            url = "https://shared-text.mimeo.local/abc123",
            host = "shared-text.mimeo.local",
            captureKind = null,
        )

        val presentation = queueCapturePresentation(item)

        assertTrue(presentation.title.startsWith("Excerpt: \""))
        assertEquals("Android selection", presentation.sourceLabel)
        assertNull(presentation.sourceUrl)
    }

    @Test
    fun `excerpt capture ignores legacy raw host when trusted provenance is absent`() {
        val item = PlaybackQueueItem(
            itemId = 5,
            title = "Legacy excerpt",
            url = "https://legacy.example.com/source",
            host = "legacy.example.com",
            captureKind = "shared_excerpt",
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            sourceAppPackage = null,
        )

        val presentation = queueCapturePresentation(item)

        assertEquals("Android selection", presentation.sourceLabel)
        assertNull(presentation.sourceUrl)
    }

    @Test
    fun `locus presentation mirrors metadata fields`() {
        val payload = ItemTextResponse(
            itemId = 4,
            title = "Paragraph selected from article",
            url = "https://www.theguardian.com/example",
            host = "theguardian.com",
            sourceType = "web",
            sourceLabel = "theguardian.com",
            sourceUrl = "https://www.theguardian.com/example",
            captureKind = "shared_excerpt",
            sourceAppPackage = null,
            text = "Paragraph selected from article body.",
            chunks = emptyList(),
        )

        val presentation = locusCapturePresentation(payload)

        assertTrue(presentation.title.startsWith("Excerpt: \""))
        assertEquals("theguardian.com", presentation.sourceLabel)
        assertEquals("https://www.theguardian.com/example", presentation.sourceUrl)
    }
}
