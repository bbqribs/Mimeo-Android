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
 * Kind-scoped variant of [isPendingForItem]. T-AUX-3 keys the visible reader
 * summary by (item, kind), so a poll loop started for one mode must stop once the
 * user switches to a different mode — even on the same item.
 */
internal fun ReaderSummaryState.isPendingForItem(itemId: Int, kind: String): Boolean {
    if (this !is ReaderSummaryState.Ready) return false
    if (this.itemId != itemId) return false
    if (this.summary.summaryKind != kind) return false
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

/**
 * Guard for late summary state publication.
 *
 * A summary GET/POST is a suspending call. By the time it resolves, the reader
 * may have moved to a different item. [activeItemId] is the item the VM most
 * recently committed to showing a summary for; [completedItemId] is the item a
 * just-resolved fetch/request belongs to. Only the active item may publish, so a
 * late completion for a previous item can never clobber the now-visible item's
 * loading/ready/error state. Publication is item-scoped, not data corruption,
 * but allowing it produces confusing stale/unavailable UI.
 */
internal fun canPublishReaderSummary(activeItemId: Int?, completedItemId: Int): Boolean =
    activeItemId != null && activeItemId == completedItemId

/**
 * A summary generation POST currently in flight, with the kind and force flag it
 * used. Kind is part of the identity: a different mode is a genuinely different
 * request and must never be deduped against an in-flight one for another mode.
 */
internal data class InFlightSummaryRequest(
    val itemId: Int,
    val force: Boolean,
    val kind: String = SUMMARY_KIND_ABSTRACT,
)

/**
 * Decision for a new [requestReaderSummary] call given what is already running.
 *
 * Force refresh must never be *silently* swallowed by an unrelated in-flight
 * request, but we also cannot cancel/supersede an active generation here (that
 * is a product decision, out of scope). Hence the explicit three-way outcome.
 */
internal enum class SummaryRequestDecision {
    /** No conflicting request for this item: issue the POST. */
    PROCEED,

    /**
     * An identical (same force) — or already-stronger (force) — request for the
     * same item is in flight. The new call is a true duplicate and is deduped.
     */
    DEDUPE_NO_OP,

    /**
     * A force=true refresh arrived while a *non-force* request for the same item
     * is still running. Force genuinely wants different behavior than the
     * in-flight non-force call, but superseding an active generation is a
     * product decision we do not make here. The force is recorded as an explicit
     * no-op rather than silently dropped, so the caller can re-issue once the
     * in-flight request settles.
     */
    FORCE_DEFERRED_NO_OP,
}

/**
 * Decide how to handle [requestReaderSummary] for [requestedItemId]/[requestedForce]
 * given the [inFlight] request (if any). Pure so it can be unit-tested without a VM.
 */
internal fun decideSummaryRequest(
    inFlight: InFlightSummaryRequest?,
    requestedItemId: Int,
    requestedForce: Boolean,
    requestedKind: String = SUMMARY_KIND_ABSTRACT,
): SummaryRequestDecision {
    // A request for a different item — or a different mode of the same item — is
    // never a duplicate of the in-flight one; it targets a distinct summary.
    if (inFlight == null || inFlight.itemId != requestedItemId || inFlight.kind != requestedKind) {
        return SummaryRequestDecision.PROCEED
    }
    return when {
        // Same force-ness → a genuine duplicate.
        inFlight.force == requestedForce -> SummaryRequestDecision.DEDUPE_NO_OP
        // In-flight force already covers a weaker (non-force) follow-up.
        inFlight.force && !requestedForce -> SummaryRequestDecision.DEDUPE_NO_OP
        // In-flight is non-force, incoming is force → cannot safely supersede.
        else -> SummaryRequestDecision.FORCE_DEFERRED_NO_OP
    }
}
