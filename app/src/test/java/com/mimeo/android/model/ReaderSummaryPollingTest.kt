package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Summary UX v2 PR 1 — guards the polling schedule and predicate so that the
 * VM-owned loop stays bounded and only fires for the right shape of state.
 */
class ReaderSummaryPollingTest {

    @Test
    fun scheduleIsBoundedToEightTriesBetweenTwoAndThirtySeconds() {
        assertEquals(8, SUMMARY_POLL_DELAYS_MS.size)
        assertEquals(2_000L, SUMMARY_POLL_DELAYS_MS.first())
        assertEquals(30_000L, SUMMARY_POLL_DELAYS_MS.last())
        SUMMARY_POLL_DELAYS_MS.forEach { ms ->
            assertTrue("delay $ms below 2s floor", ms >= 2_000L)
            assertTrue("delay $ms above 30s cap", ms <= 30_000L)
        }
        // Monotonically non-decreasing — backoff never speeds up.
        SUMMARY_POLL_DELAYS_MS.zipWithNext().forEach { (a, b) ->
            assertTrue("schedule must not speed up: $a -> $b", b >= a)
        }
    }

    @Test
    fun isPendingForItemTrueOnlyForReadyPendingMatchingId() {
        val pending = ReaderSummaryState.Ready(itemId = 42, summary = summary("pending"))
        assertTrue(pending.isPendingForItem(42))
        assertFalse("wrong item id", pending.isPendingForItem(7))
    }

    @Test
    fun isPendingForItemFalseForReadyNonPending() {
        val states = listOf("ready", "missing", "failed", "stale")
        states.forEach { stateLabel ->
            val state = ReaderSummaryState.Ready(itemId = 1, summary = summary(stateLabel))
            assertFalse("ready+$stateLabel must not poll", state.isPendingForItem(1))
        }
    }

    @Test
    fun isPendingForItemFalseForIdleLoadingError() {
        assertFalse(ReaderSummaryState.Idle.isPendingForItem(1))
        assertFalse(ReaderSummaryState.Loading(itemId = 1).isPendingForItem(1))
        assertFalse(
            ReaderSummaryState.Error(
                itemId = 1,
                reason = ContentSummaryFailureReason.NETWORK,
                message = "x",
            ).isPendingForItem(1),
        )
    }

    @Test
    fun nonPollableReasonsCoverContractAndItemShapeProblems() {
        val expected = setOf(
            ContentSummaryFailureReason.SUMMARIES_DISABLED,
            ContentSummaryFailureReason.PROVIDER_NOT_CONFIGURED,
            ContentSummaryFailureReason.CONTENT_TOO_SHORT,
            ContentSummaryFailureReason.NO_ACTIVE_CONTENT,
            ContentSummaryFailureReason.UNAUTHORIZED,
            ContentSummaryFailureReason.NOT_FOUND,
        )
        assertEquals(expected, NON_POLLABLE_FAILURE_REASONS)
        // Transient classes must remain pollable.
        assertFalse(ContentSummaryFailureReason.NETWORK in NON_POLLABLE_FAILURE_REASONS)
        assertFalse(ContentSummaryFailureReason.UNKNOWN in NON_POLLABLE_FAILURE_REASONS)
        assertFalse(ContentSummaryFailureReason.DAILY_LIMIT_REACHED in NON_POLLABLE_FAILURE_REASONS)
    }

    private fun summary(state: String): ContentSummaryOut = ContentSummaryOut(
        itemId = 1,
        state = state,
    )
}
