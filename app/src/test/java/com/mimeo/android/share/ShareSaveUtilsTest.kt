package com.mimeo.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
