package com.mimeo.android.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareSaveResultTest {
    @Test
    fun `saved message includes destination`() {
        val result = ShareSaveResult.Saved(destinationLabel = "Smart Queue")

        assertEquals("Saved to Smart Queue ✅", result.notificationText)
    }

    @Test
    fun `already saved message includes destination when available`() {
        val result = ShareSaveResult.AlreadySaved(destinationLabel = "Reading List")

        assertEquals("Already in Reading List ✅", result.notificationText)
    }

    @Test
    fun `already saved message falls back to generic text`() {
        val result = ShareSaveResult.AlreadySaved()

        assertEquals("Already saved ✅", result.notificationText)
    }
}
