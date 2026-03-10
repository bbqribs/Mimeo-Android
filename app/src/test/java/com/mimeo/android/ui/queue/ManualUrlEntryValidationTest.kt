package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualUrlEntryValidationTest {
    @Test
    fun `resolveManualSaveUrl extracts a valid url from plain input`() {
        val result = resolveManualSaveUrl("https://example.com/article")

        assertEquals("https://example.com/article", result)
    }

    @Test
    fun `resolveManualSaveUrl trims wrapping spaces and punctuation`() {
        val result = resolveManualSaveUrl("  https://example.com/story?id=42).  ")

        assertEquals("https://example.com/story?id=42", result)
    }

    @Test
    fun `resolveManualSaveUrl rejects non-http input`() {
        assertNull(resolveManualSaveUrl("not a url"))
    }

    @Test
    fun `normalizeManualTextBody trims and rejects blank text`() {
        assertEquals("Line one", normalizeManualTextBody("  Line one  "))
        assertNull(normalizeManualTextBody("   "))
    }

    @Test
    fun `canSubmitManualSave enforces mode-specific required fields`() {
        assertTrue(canSubmitManualSave(ManualSaveMode.URL, "https://example.com", "", inProgress = false))
        assertFalse(canSubmitManualSave(ManualSaveMode.URL, "", "", inProgress = false))
        assertFalse(canSubmitManualSave(ManualSaveMode.URL, "not a url", "", inProgress = false))
        assertFalse(canSubmitManualSave(ManualSaveMode.TEXT, "https://example.com", "", inProgress = false))
        assertTrue(canSubmitManualSave(ManualSaveMode.TEXT, "not a url", "Body", inProgress = false))
        assertTrue(canSubmitManualSave(ManualSaveMode.TEXT, "", "Body", inProgress = false))
        assertTrue(canSubmitManualSave(ManualSaveMode.TEXT, "https://example.com", "Body", inProgress = false))
        assertFalse(canSubmitManualSave(ManualSaveMode.TEXT, "https://example.com", "Body", inProgress = true))
    }

    @Test
    fun `resolveManualTextSaveUrl keeps valid url and synthesizes one when missing`() {
        val fromUrl = resolveManualTextSaveUrl(
            urlInput = "https://example.com/source",
            titleInput = "Any title",
            bodyInput = "Any body",
        )
        val fallback = resolveManualTextSaveUrl(
            urlInput = "",
            titleInput = "Manual Title",
            bodyInput = "Body text",
        )

        assertEquals("https://example.com/source", fromUrl)
        assertTrue(fallback.startsWith("https://manual.mimeo.local/"))
    }

    @Test
    fun `buildManualSavePrefill prefers url when clipboard contains one`() {
        val prefill = buildManualSavePrefill("Read this https://example.com/a")

        assertEquals("https://example.com/a", prefill.urlInput)
        assertEquals("", prefill.bodyInput)
    }

    @Test
    fun `buildManualSavePrefill uses body when clipboard has non-url text`() {
        val prefill = buildManualSavePrefill("Plain copied paragraph")

        assertEquals("", prefill.urlInput)
        assertEquals("Plain copied paragraph", prefill.bodyInput)
    }

    @Test
    fun `buildManualSavePrefill leaves inputs empty when clipboard is blank`() {
        val prefill = buildManualSavePrefill("   ")

        assertEquals("", prefill.urlInput)
        assertEquals("", prefill.bodyInput)
    }
}
