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

    // --- Late state publication guard ---------------------------------------

    @Test
    fun canPublishOnlyWhenCompletedItemIsStillActive() {
        assertTrue(canPublishReaderSummary(activeItemId = 5, completedItemId = 5))
    }

    @Test
    fun lateCompletionForPreviousItemCannotPublish() {
        // Reader moved A(5) -> B(9); a late completion for 5 must be dropped.
        assertFalse(canPublishReaderSummary(activeItemId = 9, completedItemId = 5))
    }

    @Test
    fun publishBlockedWhenNoActiveItem() {
        assertFalse(canPublishReaderSummary(activeItemId = null, completedItemId = 5))
    }

    // --- Force-refresh dedupe ------------------------------------------------

    @Test
    fun noInFlightRequestProceeds() {
        assertEquals(
            SummaryRequestDecision.PROCEED,
            decideSummaryRequest(inFlight = null, requestedItemId = 1, requestedForce = false),
        )
        assertEquals(
            SummaryRequestDecision.PROCEED,
            decideSummaryRequest(inFlight = null, requestedItemId = 1, requestedForce = true),
        )
    }

    @Test
    fun differentItemInFlightProceeds() {
        val inFlight = InFlightSummaryRequest(itemId = 2, force = false)
        assertEquals(
            SummaryRequestDecision.PROCEED,
            decideSummaryRequest(inFlight = inFlight, requestedItemId = 1, requestedForce = true),
        )
    }

    @Test
    fun identicalRequestForSameItemIsDeduped() {
        val nonForce = InFlightSummaryRequest(itemId = 1, force = false)
        assertEquals(
            SummaryRequestDecision.DEDUPE_NO_OP,
            decideSummaryRequest(inFlight = nonForce, requestedItemId = 1, requestedForce = false),
        )
        val force = InFlightSummaryRequest(itemId = 1, force = true)
        assertEquals(
            SummaryRequestDecision.DEDUPE_NO_OP,
            decideSummaryRequest(inFlight = force, requestedItemId = 1, requestedForce = true),
        )
    }

    @Test
    fun nonForceOverInFlightForceIsDeduped() {
        // The stronger in-flight force already covers a weaker follow-up.
        val force = InFlightSummaryRequest(itemId = 1, force = true)
        assertEquals(
            SummaryRequestDecision.DEDUPE_NO_OP,
            decideSummaryRequest(inFlight = force, requestedItemId = 1, requestedForce = false),
        )
    }

    @Test
    fun forceOverInFlightNonForceIsExplicitDeferredNoOp() {
        // Force must NOT be silently swallowed by a non-force request; it is
        // recorded as a deferred no-op rather than dropped or auto-superseding.
        val nonForce = InFlightSummaryRequest(itemId = 1, force = false)
        assertEquals(
            SummaryRequestDecision.FORCE_DEFERRED_NO_OP,
            decideSummaryRequest(inFlight = nonForce, requestedItemId = 1, requestedForce = true),
        )
    }

    // --- T-AUX-3: per-kind request + polling -------------------------------

    @Test
    fun differentKindForSameItemIsNeverDeduped() {
        // A Brief request must proceed even while a Standard request is in flight
        // for the same item — they target distinct per-kind summaries.
        val abstractInFlight = InFlightSummaryRequest(itemId = 1, force = false, kind = "abstract")
        assertEquals(
            SummaryRequestDecision.PROCEED,
            decideSummaryRequest(
                inFlight = abstractInFlight,
                requestedItemId = 1,
                requestedForce = false,
                requestedKind = "brief",
            ),
        )
    }

    @Test
    fun sameKindSameForceIsStillDeduped() {
        val briefInFlight = InFlightSummaryRequest(itemId = 1, force = true, kind = "brief")
        assertEquals(
            SummaryRequestDecision.DEDUPE_NO_OP,
            decideSummaryRequest(
                inFlight = briefInFlight,
                requestedItemId = 1,
                requestedForce = true,
                requestedKind = "brief",
            ),
        )
    }

    @Test
    fun isPendingForItemIsKindScoped() {
        val pendingBrief = ReaderSummaryState.Ready(
            itemId = 7,
            summary = summary("pending", summaryKind = "brief"),
        )
        assertTrue(pendingBrief.isPendingForItem(7, "brief"))
        // Same item, wrong mode -> poll loop for "abstract" must not keep ticking.
        assertFalse(pendingBrief.isPendingForItem(7, "abstract"))
        assertFalse(pendingBrief.isPendingForItem(9, "brief"))
    }

    private fun summary(
        state: String,
        summaryKind: String = "abstract",
    ): ContentSummaryOut = ContentSummaryOut(
        itemId = 1,
        state = state,
        summaryKind = summaryKind,
    )
}
