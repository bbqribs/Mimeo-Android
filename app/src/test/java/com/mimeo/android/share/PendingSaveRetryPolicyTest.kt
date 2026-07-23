package com.mimeo.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSaveRetryPolicyTest {

    @Test
    fun `saved with item id classifies as resolved`() {
        val outcome = classifyPendingRetryOutcome(
            ShareSaveResult.Saved(itemId = 42, destinationName = "Smart Queue"),
        )
        assertEquals(PendingRetryOutcome.Resolved(42), outcome)
    }

    @Test
    fun `saved without item id preserves the row instead of removing it`() {
        // Core defect fix: a duplicate resolution that yields Saved(itemId = null) must never be
        // treated as a completed removal.
        val outcome = classifyPendingRetryOutcome(
            ShareSaveResult.Saved(itemId = null, destinationName = "Smart Queue"),
        )
        assertEquals(PendingRetryOutcome.PreserveUnresolved, outcome)
    }

    @Test
    fun `transient failures classify as retry-failed`() {
        listOf(
            ShareSaveResult.NetworkError,
            ShareSaveResult.TimedOut,
            ShareSaveResult.ServerError,
            ShareSaveResult.SaveFailed,
        ).forEach { result ->
            assertEquals(PendingRetryOutcome.RetryFailed, classifyPendingRetryOutcome(result))
        }
    }

    @Test
    fun `auth failures are retained as retry-failed not removed`() {
        assertEquals(PendingRetryOutcome.RetryFailed, classifyPendingRetryOutcome(ShareSaveResult.MissingToken))
        assertEquals(PendingRetryOutcome.RetryFailed, classifyPendingRetryOutcome(ShareSaveResult.Unauthorized))
    }

    @Test
    fun `terminal non-savable results are removable`() {
        assertEquals(PendingRetryOutcome.Remove, classifyPendingRetryOutcome(ShareSaveResult.NoValidUrl))
    }

    @Test
    fun `parked-save notification hook fires only for auto-retryable transient failures`() {
        assertTrue(shouldSurfaceParkedSaveRetry(ShareSaveResult.NetworkError))
        assertTrue(shouldSurfaceParkedSaveRetry(ShareSaveResult.TimedOut))
        assertTrue(shouldSurfaceParkedSaveRetry(ShareSaveResult.ServerError))
        assertTrue(shouldSurfaceParkedSaveRetry(ShareSaveResult.SaveFailed))
        // Auth/token failures are retained but not auto-retried, so no "will retry" notification.
        assertFalse(shouldSurfaceParkedSaveRetry(ShareSaveResult.MissingToken))
        assertFalse(shouldSurfaceParkedSaveRetry(ShareSaveResult.Unauthorized))
        assertFalse(shouldSurfaceParkedSaveRetry(ShareSaveResult.NoValidUrl))
        assertFalse(shouldSurfaceParkedSaveRetry(ShareSaveResult.Saved(destinationName = "Smart Queue")))
    }

    @Test
    fun `parked-save notification text exposes no url token or body content`() {
        assertEquals(
            "Couldn't reach Mimeo — save pending. Will retry automatically.",
            PARKED_SAVE_NOTIFICATION_TEXT,
        )
    }

    @Test
    fun `retry failure message keeps a specific existing message over the generic SaveFailed text`() {
        assertEquals(
            "Source blocked access",
            pendingRetryFailureMessage("Source blocked access", ShareSaveResult.SaveFailed),
        )
        assertEquals(
            ShareSaveResult.NetworkError.notificationText,
            pendingRetryFailureMessage(null, ShareSaveResult.SaveFailed),
        )
        assertEquals(
            ShareSaveResult.TimedOut.notificationText,
            pendingRetryFailureMessage("anything", ShareSaveResult.TimedOut),
        )
    }
}
