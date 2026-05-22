package com.mimeo.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTextActionsTest {

    @Test
    fun `bare text returned when all metadata is null`() {
        val result = buildArticleShareText("Hello world.", null, null, null)
        assertEquals("Hello world.", result)
    }

    @Test
    fun `bare text returned when all metadata is blank`() {
        val result = buildArticleShareText("Hello world.", "", "  ", "")
        assertEquals("Hello world.", result)
    }

    @Test
    fun `header prepends title above body`() {
        val result = buildArticleShareText("Body text.", "My Article", null, null)
        assertEquals("My Article\n\nBody text.", result)
    }

    @Test
    fun `header prepends source label when non-generic`() {
        val result = buildArticleShareText("Body text.", null, "The Atlantic", null)
        assertEquals("The Atlantic\n\nBody text.", result)
    }

    @Test
    fun `header prepends url when present`() {
        val result = buildArticleShareText("Body text.", null, null, "https://example.com/article")
        assertEquals("https://example.com/article\n\nBody text.", result)
    }

    @Test
    fun `header prepends title, source, and url in order when all present`() {
        val result = buildArticleShareText(
            articleText = "Body text.",
            title = "My Article",
            sourceLabel = "The Atlantic",
            url = "https://theatlantic.com/article",
        )
        assertEquals(
            "My Article\nThe Atlantic\nhttps://theatlantic.com/article\n\nBody text.",
            result,
        )
    }

    @Test
    fun `header prepends title and url when source label is absent`() {
        val result = buildArticleShareText(
            articleText = "Body text.",
            title = "My Article",
            sourceLabel = null,
            url = "https://theatlantic.com/article",
        )
        assertEquals(
            "My Article\nhttps://theatlantic.com/article\n\nBody text.",
            result,
        )
    }

    @Test
    fun `generic label android selection is excluded`() {
        val result = buildArticleShareText("Body text.", "Title", "Android selection", "https://example.com")
        assertEquals("Title\nhttps://example.com\n\nBody text.", result)
    }

    @Test
    fun `generic label unknown source is excluded`() {
        val result = buildArticleShareText("Body text.", null, "unknown source", null)
        assertEquals("Body text.", result)
    }

    @Test
    fun `generic label app share is excluded`() {
        val result = buildArticleShareText("Body text.", null, "app share", null)
        assertEquals("Body text.", result)
    }

    @Test
    fun `generic label shared-text synthetic url is excluded`() {
        val result = buildArticleShareText("Body text.", null, "shared-text.mimeo.local", null)
        assertEquals("Body text.", result)
    }

    @Test
    fun `generic label matching is case-insensitive`() {
        val result = buildArticleShareText("Body text.", null, "ANDROID SELECTION", null)
        assertEquals("Body text.", result)
    }

    @Test
    fun `non-generic source label with dots is preserved`() {
        val result = buildArticleShareText("Body text.", null, "arstechnica.com", null)
        assertEquals("arstechnica.com\n\nBody text.", result)
    }
}
