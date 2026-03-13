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
        assertEquals("Article blocked by source", resolveTerminalPendingProcessingMessage("blocked"))
        assertEquals("Article blocked by source", resolveTerminalPendingProcessingMessage("paywall"))
        assertEquals("Article source unsupported", resolveTerminalPendingProcessingMessage("unsupported"))
        assertEquals("Article processing failed", resolveTerminalPendingProcessingMessage("processing_failed"))
    }

    @Test
    fun `pending processing failure message recognises blocked like wording`() {
        assertTrue(isPendingProcessingFailureMessage("Article blocked by source"))
        assertTrue(isPendingProcessingFailureMessage("Article source unsupported"))
        assertTrue(isPendingProcessingFailureMessage("Article processing failed"))
        assertFalse(isPendingProcessingFailureMessage("Saving..."))
    }
}
