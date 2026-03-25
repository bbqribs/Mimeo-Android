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
    fun `extractHttpUrls returns all http urls in order`() {
        val sharedText = "One https://a.example/x and two https://b.example/y."

        val urls = extractHttpUrls(sharedText)

        assertEquals(listOf("https://a.example/x", "https://b.example/y"), urls)
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

        assertEquals("Excerpt: \"Quoted passage\"", withSubject)
        assertEquals("Excerpt: \"First line\"", withoutSubject)
    }

    @Test
    fun `derivePlainTextShareTitle ignores boilerplate subject with link`() {
        val title = derivePlainTextShareTitle(
            sharedTitle = "Including link: https://superuser.com/questions/123",
            plainTextBody = "I was forwarded to a different website with SSL enabled.",
        )

        assertEquals("Excerpt: \"I was forwarded to a different website with SSL enabled.\"", title)
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
    fun `selected url plus trailing browser provenance stays url capture`() {
        val shared = "\"https://www.bbc.co.uk/news/entertainment-arts-64510095\" https://news.ycombinator.com/item?id=41668905#:~:text=https://www.bbc.co.uk/news/entertainment-arts-64510095"
        val extracted = extractFirstHttpUrl(shared)

        val useUrlPath = shouldTreatShareAsUrlCapture(sharedText = shared, extractedUrl = extracted)

        assertTrue(useUrlPath)
    }

    @Test
    fun `hasTrailingBrowserSelectionFragment detects trailing text fragment marker`() {
        val shared = "\"Quote\" https://news.ycombinator.com/item?id=1#:~:text=Quote"

        assertTrue(hasTrailingBrowserSelectionFragment(shared))
    }

    @Test
    fun `hasTrailingBrowserSelectionFragment ignores plain trailing urls`() {
        val shared = "\"Quote\"\nhttps://www.bbc.co.uk/news/example"

        assertEquals(false, hasTrailingBrowserSelectionFragment(shared))
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
    fun `derivePlainTextSourceUrl returns normalized trailing standalone source url`() {
        val shared = "\"Quote text\" https://example.com/story#:~:text=foo"
        val extracted = extractFirstHttpUrl(shared)

        val source = derivePlainTextSourceUrl(sharedText = shared, extractedUrl = extracted)

        assertEquals("https://example.com/story", source)
    }

    @Test
    fun `derivePlainTextSourceUrl keeps browser source when trailing marker exists with inline links`() {
        val shared = """
            Reference one: https://first.example/inside
            Reference two: https://second.example/inside

            https://source.example/article#:~:text=selected%20quote
        """.trimIndent()
        val extracted = extractFirstHttpUrl(shared)

        val source = derivePlainTextSourceUrl(sharedText = shared, extractedUrl = extracted)

        assertEquals("https://source.example/article", source)
    }

    @Test
    fun `derivePlainTextSourceUrl ignores multi-url bodies without explicit trailing source marker`() {
        val shared = """
            Links: https://first.example/a and https://second.example/b in body text continues.

            https://source.example/article
        """.trimIndent()
        val extracted = extractFirstHttpUrl(shared)

        val source = derivePlainTextSourceUrl(sharedText = shared, extractedUrl = extracted)

        assertNull(source)
    }

    @Test
    fun `removeTrailingSourceUrlFromText removes only trailing source footer url`() {
        val shared = "\"Quote text with https://content.example/link\" https://source.example/article#:~:text=quote"

        val cleaned = removeTrailingSourceUrlFromText(
            sharedText = shared,
            sourceUrl = "https://source.example/article#:~:text=quote",
        )

        assertEquals("\"Quote text with https://content.example/link\"", cleaned)
    }

    @Test
    fun `removeTrailingSourceUrlFromText removes raw fragment url when source is normalized`() {
        val shared = """
            introduced the Gambling Act in 2005.
            https://www.bbc.co.uk/news/entertainment-arts-64510095
            https://news.ycombinator.com/item?id=41668905#:~:text=introduced%20the%20Gambling
        """.trimIndent()

        val cleaned = removeTrailingSourceUrlFromText(
            sharedText = shared,
            sourceUrl = "https://news.ycombinator.com/item?id=41668905",
        )

        assertEquals(
            "introduced the Gambling Act in 2005.\nhttps://www.bbc.co.uk/news/entertainment-arts-64510095",
            cleaned,
        )
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

    @Test
    fun `buildManualTextSourcePayload prefers app source for non-browser shares`() {
        val payload = buildManualTextSourcePayload(
            urlInput = "https://www.theguardian.com/us-news/2026/mar/24/example",
            captureKind = "shared_excerpt",
            explicitSourceUrl = "https://www.theguardian.com/us-news/2026/mar/24/example",
            sourceAppPackage = "com.google.android.keep",
            sourceAppLabel = "Google Keep",
            forceAppSource = true,
        )

        assertEquals("app", payload.sourceType)
        assertEquals("Google Keep", payload.sourceLabel)
        assertNull(payload.sourceUrl)
        assertEquals("shared_excerpt", payload.captureKind)
        assertEquals("com.google.android.keep", payload.sourceAppPackage)
    }
}
