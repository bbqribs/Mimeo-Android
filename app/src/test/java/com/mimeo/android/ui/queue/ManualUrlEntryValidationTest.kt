package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
