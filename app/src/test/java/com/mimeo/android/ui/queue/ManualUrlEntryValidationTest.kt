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
        assertFalse(canSubmitManualSave(ManualSaveMode.TEXT, "not a url", "Body", inProgress = false))
        assertTrue(canSubmitManualSave(ManualSaveMode.TEXT, "https://example.com", "Body", inProgress = false))
        assertFalse(canSubmitManualSave(ManualSaveMode.TEXT, "https://example.com", "Body", inProgress = true))
    }
}
