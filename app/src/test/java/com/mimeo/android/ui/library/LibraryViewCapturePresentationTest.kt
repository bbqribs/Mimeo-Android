package com.mimeo.android.ui.library

import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.ui.common.queueCapturePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies spec §10 acceptance criteria for library-view source rendering once
 * ArticleSummary source metadata fields are forwarded through toPlaybackQueueItem().
 *
 * Each test represents the PlaybackQueueItem state that results from the conversion
 * of an ArticleSummary with various combinations of source metadata.
 */
class LibraryViewCapturePresentationTest {

    // --- Article-like items (§3.1 precedence) ---

    @Test
    fun `article-like with source_label uses label and strips www prefix`() {
        val item = PlaybackQueueItem(
            itemId = 1,
            title = "Article title",
            url = "https://www.theverge.com/article",
            host = "The Verge",
            sourceType = "web",
            sourceLabel = "www.theverge.com",
            sourceUrl = "https://www.theverge.com/article",
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertEquals("Article title", p.title)
        assertEquals("theverge.com", p.sourceLabel)
        assertEquals("https://www.theverge.com/article", p.sourceUrl)
    }

    @Test
    fun `article-like with no source_label falls back to host (siteName)`() {
        val item = PlaybackQueueItem(
            itemId = 2,
            title = "Article title",
            url = "https://news.example.com/story",
            host = "news.example.com",
            sourceType = "web",
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertEquals("Article title", p.title)
        assertEquals("news.example.com", p.sourceLabel)
        assertEquals("https://news.example.com/story", p.sourceUrl)
    }

    @Test
    fun `article-like app capture with no label shows App share`() {
        val item = PlaybackQueueItem(
            itemId = 3,
            title = "App article",
            url = "https://app.example.com/note",
            host = null,
            sourceType = "app",
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertEquals("App share", p.sourceLabel)
    }

    // --- Excerpt-like items (§3.2 precedence) ---

    @Test
    fun `excerpt with source_url uses provenance host as label`() {
        val item = PlaybackQueueItem(
            itemId = 4,
            title = "Interesting paragraph about AI",
            url = "https://www.wired.com/story",
            host = "wired.com",
            sourceType = "web",
            sourceLabel = "www.wired.com",
            sourceUrl = "https://www.wired.com/story",
            captureKind = "shared_excerpt",
            sourceAppPackage = "com.android.chrome",
        )

        val p = queueCapturePresentation(item)

        assertTrue(p.title.startsWith("Excerpt: \""))
        assertEquals("wired.com", p.sourceLabel)
        assertEquals("https://www.wired.com/story", p.sourceUrl)
    }

    @Test
    fun `excerpt without source_url falls back to origin label`() {
        val item = PlaybackQueueItem(
            itemId = 5,
            title = "Shared text",
            url = "https://shared-text.mimeo.local/xyz",
            host = "shared-text.mimeo.local",
            sourceType = "app",
            sourceLabel = "Pocket",
            sourceUrl = null,
            captureKind = "manual_text",
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertTrue(p.title.startsWith("Excerpt: \""))
        assertEquals("Pocket", p.sourceLabel)
        assertNull(p.sourceUrl)
    }

    @Test
    fun `excerpt with no source metadata falls back to Android selection`() {
        val item = PlaybackQueueItem(
            itemId = 6,
            title = "Some text",
            url = "https://shared-text.mimeo.local/abc",
            host = "shared-text.mimeo.local",
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertTrue(p.title.startsWith("Excerpt: \""))
        assertEquals("Android selection", p.sourceLabel)
        assertNull(p.sourceUrl)
    }

    // --- Legacy items (§7 fallback) ---

    @Test
    fun `legacy item with all null source metadata renders via host fallback`() {
        val item = PlaybackQueueItem(
            itemId = 7,
            title = "Old article",
            url = "https://legacy.example.com/old",
            host = "legacy.example.com",
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertEquals("Old article", p.title)
        assertEquals("legacy.example.com", p.sourceLabel)
        assertEquals("https://legacy.example.com/old", p.sourceUrl)
    }

    @Test
    fun `legacy item with null title uses url as title`() {
        val item = PlaybackQueueItem(
            itemId = 8,
            title = null,
            url = "https://legacy.example.com/old",
            host = "legacy.example.com",
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
        )

        val p = queueCapturePresentation(item)

        assertEquals("https://legacy.example.com/old", p.title)
        assertEquals("legacy.example.com", p.sourceLabel)
    }

    // --- Excerpt title formatting (§4.2) ---

    @Test
    fun `excerpt title strips existing Excerpt prefix and re-wraps`() {
        val item = PlaybackQueueItem(
            itemId = 9,
            title = "Excerpt: \"Already formatted\"",
            url = "https://shared-text.mimeo.local/abc",
            host = "shared-text.mimeo.local",
            captureKind = "shared_excerpt",
        )

        val p = queueCapturePresentation(item)

        assertEquals("Excerpt: \"Already formatted\"", p.title)
    }

    @Test
    fun `excerpt title is truncated at 96 chars with ellipsis`() {
        val long = "a".repeat(100)
        val item = PlaybackQueueItem(
            itemId = 10,
            title = long,
            url = "https://shared-text.mimeo.local/abc",
            host = "shared-text.mimeo.local",
            captureKind = "manual_text",
        )

        val p = queueCapturePresentation(item)

        val inner = p.title.removePrefix("Excerpt: \"").removeSuffix("\"")
        assertTrue(inner.endsWith("…"))
        assertTrue(inner.length <= 97)
    }
}
