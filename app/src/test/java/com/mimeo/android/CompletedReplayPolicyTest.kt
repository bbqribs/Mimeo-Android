package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedReplayPolicyTest {

    @Test
    fun completedThresholdDoesNotReplayBelowDonePercent() {
        assertFalse(shouldReplayCompletedItem(97))
    }

    @Test
    fun completedThresholdReplaysAtDonePercentAndAbove() {
        assertTrue(shouldReplayCompletedItem(98))
        assertTrue(shouldReplayCompletedItem(100))
    }
}
