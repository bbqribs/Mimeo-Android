package com.mimeo.android.share

/**
 * Pure decision logic for how a pending-save retry result should mutate the parked row, and
 * whether a parked save should surface a user-visible "will retry" notification.
 *
 * Kept free of Android/coroutine dependencies so both [com.mimeo.android.AppViewModel] retry
 * paths, the background retry worker, and the share receiver can share identical semantics and
 * so the behaviour is unit-testable on the JVM without Robolectric.
 */

/** Message shown when a save resolved to a duplicate whose item is not yet visible to the user. */
const val SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE = "Saved earlier — item not yet visible"

/** User-visible text for a parked, auto-retryable save. Contains no URL, token or body text. */
const val PARKED_SAVE_NOTIFICATION_TEXT =
    "Couldn't reach Mimeo — save pending. Will retry automatically."

/** How a completed retry attempt should update the parked pending-save row. */
sealed interface PendingRetryOutcome {
    /** The save resolved to a concrete server item; mark the row resolved with [itemId]. */
    data class Resolved(val itemId: Int) : PendingRetryOutcome

    /**
     * The save reported success but no resolved item id is available to the user (e.g. a 409
     * duplicate resolved against the ready-only queue). The row MUST be retained as an
     * unresolved failure — never removed and never given a synthetic id.
     */
    data object PreserveUnresolved : PendingRetryOutcome

    /** A transient/auth failure; retain the row with an updated failure message. */
    data object RetryFailed : PendingRetryOutcome

    /** A genuinely terminal, non-savable result; the row may be removed. */
    data object Remove : PendingRetryOutcome
}

/**
 * Classifies a retry [result] into the row mutation it should trigger.
 *
 * Crucially, a [ShareSaveResult.Saved] with a null item id maps to [PendingRetryOutcome.PreserveUnresolved]
 * rather than removal, closing the silent-loss defect where a duplicate resolution that returns
 * `Saved(itemId = null)` deleted the pending row with no item the user could actually open.
 */
fun classifyPendingRetryOutcome(result: ShareSaveResult): PendingRetryOutcome = when {
    result is ShareSaveResult.Saved && result.itemId != null -> PendingRetryOutcome.Resolved(result.itemId)
    result is ShareSaveResult.Saved -> PendingRetryOutcome.PreserveUnresolved
    isRetryablePendingSaveResult(result) -> PendingRetryOutcome.RetryFailed
    else -> PendingRetryOutcome.Remove
}

/**
 * Whether a parked save (retained in Pending Saves because the initial attempt failed for an
 * auto-retryable reason) should surface the "will retry automatically" notification and schedule
 * bounded background retry. True only for transient results — auth/token failures are retained but
 * are not auto-retried and keep their existing (settings-directing) surfacing.
 */
fun shouldSurfaceParkedSaveRetry(result: ShareSaveResult): Boolean =
    isAutoRetryEligiblePendingSaveResult(result)

/**
 * Resolves the failure message to persist on a parked row after a failed retry. A generic
 * [ShareSaveResult.SaveFailed] keeps any more specific existing message rather than overwriting it.
 */
fun pendingRetryFailureMessage(existingMessage: String?, result: ShareSaveResult): String = when (result) {
    ShareSaveResult.SaveFailed -> existingMessage ?: ShareSaveResult.NetworkError.notificationText
    else -> result.notificationText
}
