package com.mimeo.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSaveUtilsTest {
    @Test
    fun `extractFirstHttpUrl finds first URL inside shared text`() {
        val sharedText = "Interesting read: https://example.com/path?ref=abc and another https://mimeo.app"

        val result = extractFirstHttpUrl(sharedText)

        assertEquals("https://example.com/path?ref=abc", result)
    }

    @Test
    fun `extractFirstHttpUrl trims trailing punctuation`() {
        val sharedText = "Read this one (https://example.com/story?x=1)."

        val result = extractFirstHttpUrl(sharedText)

        assertEquals("https://example.com/story?x=1", result)
    }

    @Test
    fun `extractFirstHttpUrl returns null when no URL is present`() {
        assertNull(extractFirstHttpUrl("Just a note without any link"))
    }

    @Test
    fun `buildShareIdempotencyKey stays stable for normalized duplicates`() {
        val first = buildShareIdempotencyKey("https://Example.com:443/story?id=42#fragment")
        val second = buildShareIdempotencyKey("https://example.com/story?id=42")

        assertEquals(first, second)
        assertEquals("android-share-", first.take("android-share-".length))
    }

    @Test
    fun `extractPlainTextShareBody trims and rejects blank`() {
        assertNull(extractPlainTextShareBody("   \n  "))
        assertEquals("Some copied paragraph", extractPlainTextShareBody("  Some copied paragraph  "))
    }

    @Test
    fun `derivePlainTextShareTitle prefers subject then first line`() {
        val withSubject = derivePlainTextShareTitle(
            sharedTitle = "Quoted passage",
            plainTextBody = "First line\nSecond line",
        )
        val withoutSubject = derivePlainTextShareTitle(
            sharedTitle = null,
            plainTextBody = "First line\nSecond line",
        )

        assertEquals("Quoted passage", withSubject)
        assertEquals("First line", withoutSubject)
    }

    @Test
    fun `buildPlainTextShareSyntheticUrl is stable and https`() {
        val first = buildPlainTextShareSyntheticUrl(
            title = "Shared text",
            plainTextBody = "Body paragraph one.\nBody paragraph two.",
        )
        val second = buildPlainTextShareSyntheticUrl(
            title = "Shared text",
            plainTextBody = "Body paragraph one.\nBody paragraph two.",
        )

        assertEquals(first, second)
        assertTrue(first.startsWith("https://shared-text.mimeo.local/"))
    }

    @Test
    fun `url-only share text uses url capture path`() {
        val shared = "https://example.com/story"
        val extracted = extractFirstHttpUrl(shared)

        val useUrlPath = shouldTreatShareAsUrlCapture(sharedText = shared, extractedUrl = extracted)

        assertTrue(useUrlPath)
    }

    @Test
    fun `mixed text plus url uses plain-text capture path`() {
        val shared = "Including link: https://example.com/story and this selected note matters."
        val extracted = extractFirstHttpUrl(shared)

        val useUrlPath = shouldTreatShareAsUrlCapture(sharedText = shared, extractedUrl = extracted)

        assertEquals(false, useUrlPath)
    }

    @Test
    fun `normalizeSharedSourceUrl strips text fragment`() {
        val url = "https://www.theguardian.com/x/y#:~:text=quoted%20fragment"

        val normalized = normalizeSharedSourceUrl(url)

        assertEquals("https://www.theguardian.com/x/y", normalized)
    }

    @Test
    fun `removeSharedUrlFromText removes pasted url`() {
        val shared = "\"Quote text\" https://example.com/story"
        val stripped = removeSharedUrlFromText(sharedText = shared, url = "https://example.com/story")

        assertEquals("\"Quote text\"", stripped)
    }

    @Test
    fun `derivePlainTextSourceUrl returns normalized url when mixed text present`() {
        val shared = "\"Quote text\" https://example.com/story#:~:text=foo"
        val extracted = extractFirstHttpUrl(shared)

        val source = derivePlainTextSourceUrl(sharedText = shared, extractedUrl = extracted)

        assertEquals("https://example.com/story", source)
    }

    @Test
    fun `appendOriginalArticleFooter appends source link line`() {
        val body = "Quoted paragraph."
        val source = "https://example.com/story"

        val withFooter = appendOriginalArticleFooter(body = body, sourceUrl = source)

        assertTrue(withFooter.contains("Quoted paragraph."))
        assertTrue(withFooter.contains("To see the original article, open: https://example.com/story"))
    }

    @Test
    fun `buildManualTextSourcePayload uses web source when url present`() {
        val payload = buildManualTextSourcePayload(
            urlInput = "https://www.theguardian.com/business/story",
            captureKind = "shared_excerpt",
            sourceAppPackage = "com.android.chrome",
        )

        assertEquals("web", payload.sourceType)
        assertEquals("theguardian.com", payload.sourceLabel)
        assertEquals("https://www.theguardian.com/business/story", payload.sourceUrl)
        assertEquals("shared_excerpt", payload.captureKind)
        assertEquals("com.android.chrome", payload.sourceAppPackage)
    }

    @Test
    fun `buildManualTextSourcePayload falls back to app metadata when no web url`() {
        val payload = buildManualTextSourcePayload(
            urlInput = "https://shared-text.mimeo.local/abc123",
            captureKind = "manual_text",
            explicitSourceUrl = null,
            sourceAppPackage = "com.google.android.gm",
        )

        assertEquals("app", payload.sourceType)
        assertEquals("com.google.android.gm", payload.sourceLabel)
        assertNull(payload.sourceUrl)
        assertEquals("manual_text", payload.captureKind)
        assertEquals("com.google.android.gm", payload.sourceAppPackage)
    }
}
