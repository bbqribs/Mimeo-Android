package com.mimeo.android.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSaveCoordinatorStatusTest {

    @Test
    fun processingStatusMatcherHandlesExpectedValues() {
        assertTrue(isProcessingItemStatus("pending"))
        assertTrue(isProcessingItemStatus("processing"))
        assertTrue(isProcessingItemStatus("queued"))
        assertTrue(isProcessingItemStatus("extracting"))
        assertFalse(isProcessingItemStatus("ready"))
        assertFalse(isProcessingItemStatus("failed"))
        assertFalse(isProcessingItemStatus(null))
    }
}
