package com.mimeo.android.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSavePolicyTest {
    @Test
    fun `queueable pending save results include retryable and auth failures`() {
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.NetworkError))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.TimedOut))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.ServerError))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.SaveFailed))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.MissingToken))
        assertTrue(isRetryablePendingSaveResult(ShareSaveResult.Unauthorized))
    }

    @Test
    fun `non queueable pending save results are no-url and saved`() {
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.NoValidUrl))
        assertFalse(isRetryablePendingSaveResult(ShareSaveResult.Saved(destinationName = "Smart Queue")))
    }

    @Test
    fun `auto retry eligibility remains transient-only`() {
        assertTrue(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.NetworkError))
        assertTrue(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.TimedOut))
        assertTrue(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.ServerError))
        assertTrue(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.SaveFailed))
        assertFalse(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.MissingToken))
        assertFalse(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.Unauthorized))
        assertFalse(isAutoRetryEligiblePendingSaveResult(ShareSaveResult.NoValidUrl))
    }
}
