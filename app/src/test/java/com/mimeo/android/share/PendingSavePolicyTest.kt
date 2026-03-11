package com.mimeo.android.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSavePolicyTest {
    @Test
    fun `retryable pending save results are network timeout server and generic save failures`() {
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.NetworkError))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.TimedOut))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.ServerError))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.SaveFailed))
    }

    @Test
    fun `non retryable pending save results are auth token no-url and saved`() {
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.MissingToken))
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.Unauthorized))
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.NoValidUrl))
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.Saved(destinationName = "Smart Queue")))
    }
}
