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
    fun `citation includes title line when title is present`() {
        val result = buildArticleShareText("Body text.", "My Article", null, null)
        assertEquals("Body text.\n\n— \"My Article\"", result)
    }

    @Test
    fun `citation includes source label when non-generic`() {
        val result = buildArticleShareText("Body text.", null, "The Atlantic", null)
        assertEquals("Body text.\n\nThe Atlantic", result)
    }

    @Test
    fun `citation includes url line when url is present`() {
        val result = buildArticleShareText("Body text.", null, null, "https://example.com/article")
        assertEquals("Body text.\n\nhttps://example.com/article", result)
    }

    @Test
    fun `citation includes all three lines when all present`() {
        val result = buildArticleShareText(
            articleText = "Body text.",
            title = "My Article",
            sourceLabel = "The Atlantic",
            url = "https://theatlantic.com/article",
        )
        assertEquals(
            "Body text.\n\n— \"My Article\"\nThe Atlantic\nhttps://theatlantic.com/article",
            result,
        )
    }

    @Test
    fun `citation includes title and url when source label is absent`() {
        val result = buildArticleShareText(
            articleText = "Body text.",
            title = "My Article",
            sourceLabel = null,
            url = "https://theatlantic.com/article",
        )
        assertEquals(
            "Body text.\n\n— \"My Article\"\nhttps://theatlantic.com/article",
            result,
        )
    }

    @Test
    fun `generic label android selection is excluded`() {
        val result = buildArticleShareText("Body text.", "Title", "Android selection", "https://example.com")
        assertEquals("Body text.\n\n— \"Title\"\nhttps://example.com", result)
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
        assertEquals("Body text.\n\narstechnica.com", result)
    }
}
