package com.mimeo.android.model

/**
 * Summary UX v2 — PR 1.
 *
 * Polling schedule used by [com.mimeo.android.AppViewModel] when an active reader
 * summary lands in [ContentSummaryState.PENDING]. Tries are intentionally bounded
 * to ~8 attempts (~94s total wall time) so a stuck or slow generation degrades to
 * "still working — recheck later" rather than spinning forever. Polling is GET
 * only; the POST that started generation is never retried automatically.
 *
 * Backoff is roughly geometric, clamped at 30s per the v2 ticket:
 *   2s, 3s, 5s, 7s, 10s, 15s, 22s, 30s.
 */
internal val SUMMARY_POLL_DELAYS_MS: List<Long> = listOf(
    2_000L,
    3_000L,
    5_000L,
    7_000L,
    10_000L,
    15_000L,
    22_000L,
    30_000L,
)

/**
 * True when [this] represents a pending summary still owned by [itemId] and the
 * VM-owned polling loop should keep ticking. Any other shape (Ready+non-pending,
 * Error, Loading, Idle, or a state for a different item) terminates polling.
 */
internal fun ReaderSummaryState.isPendingForItem(itemId: Int): Boolean {
    if (this !is ReaderSummaryState.Ready) return false
    if (this.itemId != itemId) return false
    return this.summary.normalizedState() == ContentSummaryState.PENDING
}

/**
 * Failure reasons that should never trigger or continue polling. These describe
 * the item or the deployment, not a transient server condition, so retrying is
 * pointless and would just waste requests.
 */
internal val NON_POLLABLE_FAILURE_REASONS: Set<ContentSummaryFailureReason> = setOf(
    ContentSummaryFailureReason.SUMMARIES_DISABLED,
    ContentSummaryFailureReason.PROVIDER_NOT_CONFIGURED,
    ContentSummaryFailureReason.CONTENT_TOO_SHORT,
    ContentSummaryFailureReason.NO_ACTIVE_CONTENT,
    ContentSummaryFailureReason.UNAUTHORIZED,
    ContentSummaryFailureReason.NOT_FOUND,
)
