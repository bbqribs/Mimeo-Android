package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorActivePlacementPolicyTest {

    @Test
    fun belowBothThresholdsGoesToEarlierInQueue() {
        assertFalse(priorActiveShouldGoToHistory(progressDelta = 0, playedMs = 0L))
        assertFalse(priorActiveShouldGoToHistory(progressDelta = 4, playedMs = 29_999L))
    }

    @Test
    fun progressDeltaAtOrAbove5PercentGoesToHistoryRegardlessOfTime() {
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 5, playedMs = 0L))
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 10, playedMs = 5_000L))
    }

    @Test
    fun playedTimeAtOrAbove30sGoesToHistoryRegardlessOfDelta() {
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 0, playedMs = 30_000L))
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 0, playedMs = 60_000L))
    }

    @Test
    fun combinedThresholdBothMet() {
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 5, playedMs = 30_000L))
    }

    @Test
    fun itemWithPriorProgressButZeroDeltaAndShortPlayGoesToEarlierInQueue() {
        // Regression: item with 10% prior progress, played 5 seconds this activation.
        // progressDelta = current(10) - atActivation(10) = 0 → Earlier in queue.
        assertFalse(priorActiveShouldGoToHistory(progressDelta = 0, playedMs = 5_000L))
    }

    @Test
    fun boundaryProgressDelta4And29sIsEarlier() {
        assertFalse(priorActiveShouldGoToHistory(progressDelta = 4, playedMs = 29_000L))
    }

    @Test
    fun boundaryProgressDelta5And29sIsHistory() {
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 5, playedMs = 29_000L))
    }

    @Test
    fun boundaryProgressDelta4And30sIsHistory() {
        assertTrue(priorActiveShouldGoToHistory(progressDelta = 4, playedMs = 30_000L))
    }
}
