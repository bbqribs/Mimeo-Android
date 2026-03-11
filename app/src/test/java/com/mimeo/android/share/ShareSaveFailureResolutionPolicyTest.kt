package com.mimeo.android.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSaveFailureResolutionPolicyTest {
    @Test
    fun `late resolution is enabled for transient share failures`() {
        assertTrue(shouldAttemptLateShareSaveResolution(ShareSaveResult.NetworkError))
        assertTrue(shouldAttemptLateShareSaveResolution(ShareSaveResult.TimedOut))
        assertTrue(shouldAttemptLateShareSaveResolution(ShareSaveResult.SaveFailed))
    }

    @Test
    fun `late resolution is disabled for non transient auth and input failures`() {
        assertFalse(shouldAttemptLateShareSaveResolution(ShareSaveResult.MissingToken))
        assertFalse(shouldAttemptLateShareSaveResolution(ShareSaveResult.Unauthorized))
        assertFalse(shouldAttemptLateShareSaveResolution(ShareSaveResult.NoValidUrl))
        assertFalse(
            shouldAttemptLateShareSaveResolution(
                ShareSaveResult.Saved(destinationName = "Smart Queue"),
            ),
        )
    }
}
