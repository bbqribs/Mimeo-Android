package com.mimeo.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareSaveResultMessageSemanticsTest {
    @Test
    fun `smart queue success text is destination-specific`() {
        val result = ShareSaveResult.Saved(destinationName = "Smart Queue")

        assertEquals("Saved to Smart Queue ✅", result.notificationText)
    }

    @Test
    fun `named playlist success text is destination-specific`() {
        val result = ShareSaveResult.Saved(destinationName = "Weekend Reads")

        assertEquals("Saved to Weekend Reads ✅", result.notificationText)
    }

    @Test
    fun `success text does not include duplicate-specific wording`() {
        val result = ShareSaveResult.Saved(destinationName = "Smart Queue")

        assertFalse(result.notificationText.contains("Already", ignoreCase = true))
    }

    @Test
    fun `notification title includes saved item title when available`() {
        val result = ShareSaveResult.Saved(
            destinationName = "Smart Queue",
            itemTitle = "My Saved Story",
        )

        assertEquals("Saved: My Saved Story", result.notificationTitle)
    }
}
