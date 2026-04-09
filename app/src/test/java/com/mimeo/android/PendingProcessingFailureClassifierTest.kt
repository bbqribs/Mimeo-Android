package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingProcessingFailureClassifierTest {
    @Test
    fun `terminal processing status recognises blocked and unsupported outcomes`() {
        assertTrue(isTerminalPendingProcessingStatus("blocked"))
        assertTrue(isTerminalPendingProcessingStatus("paywall_blocked"))
        assertTrue(isTerminalPendingProcessingStatus("unsupported"))
        assertTrue(isTerminalPendingProcessingStatus("processing_failed"))
        assertFalse(isTerminalPendingProcessingStatus("ready"))
    }

    @Test
    fun `terminal processing message stays concise for blocked like outcomes`() {
        assertEquals("Source blocked access", resolveTerminalPendingProcessingMessage("blocked"))
        assertEquals("Source blocked access", resolveTerminalPendingProcessingMessage("paywall"))
        assertEquals("Article source unsupported", resolveTerminalPendingProcessingMessage("unsupported"))
        assertEquals("Article processing failed", resolveTerminalPendingProcessingMessage("processing_failed"))
    }

    @Test
    fun `pending processing failure message recognises blocked like wording`() {
        assertTrue(isPendingProcessingFailureMessage("Source blocked access"))
        assertTrue(isPendingProcessingFailureMessage("Article source unsupported"))
        assertTrue(isPendingProcessingFailureMessage("Article processing failed"))
        assertFalse(isPendingProcessingFailureMessage("Saving..."))
    }

    @Test
    fun `terminal processing message maps backend failure reasons`() {
        assertEquals(
            "Article blocked by paywall",
            resolveTerminalPendingProcessingMessage(
                status = "blocked",
                failureReason = "blocked_by_paywall",
                fetchHttpStatus = 403,
            ),
        )
        assertEquals(
            "Source blocked access",
            resolveTerminalPendingProcessingMessage(
                status = "blocked",
                failureReason = "blocked_by_bot_confirmed",
                fetchHttpStatus = 403,
            ),
        )
        assertEquals(
            "Article not found",
            resolveTerminalPendingProcessingMessage(
                status = "failed",
                failureReason = "article_not_found",
                fetchHttpStatus = 404,
            ),
        )
    }
}
