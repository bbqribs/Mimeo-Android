package com.mimeo.android.share

import android.content.Context
import com.mimeo.android.ShareResultNotifications
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide gate that serializes every pending-save retry — the ViewModel's app-open flush and
 * the background [com.mimeo.android.work.PendingSaveRetryWorker] both acquire this single mutex, so
 * a foreground retry and a WorkManager retry can never run the same save concurrently.
 */
object PendingSaveRetryGate {
    val mutex = Mutex()
}

/** Summary of one background retry pass. [remainingRetryable] drives whether the worker reschedules. */
internal data class ParkedSaveRetryPassResult(
    val attempted: Int,
    val resolved: Int,
    val remainingRetryable: Boolean,
)

/**
 * Runs one retry pass over the auto-retry-eligible parked saves using the shared [ShareSaveCoordinator]
 * save path (no second save implementation) and applies the shared outcome semantics. Callers MUST
 * hold [PendingSaveRetryGate.mutex]. Returns whether any row remains retryable so the worker can
 * decide to back off and retry again.
 */
internal suspend fun runParkedSaveRetryPass(
    context: Context,
    settingsStore: SettingsStore,
    coordinator: ShareSaveCoordinator = ShareSaveCoordinator(context.applicationContext),
    notifications: ShareResultNotifications = ShareResultNotifications(context.applicationContext),
): ParkedSaveRetryPassResult {
    val parked = settingsStore.pendingManualSavesFlow.first().filter { it.autoRetryEligible }
    if (parked.isEmpty()) return ParkedSaveRetryPassResult(attempted = 0, resolved = 0, remainingRetryable = false)

    var resolved = 0
    var remainingRetryable = false
    for (item in parked) {
        val result = performParkedSaveRetry(coordinator, item)
        val stillRetryable = applyParkedRetryOutcome(settingsStore, notifications, item, result)
        if (result is ShareSaveResult.Saved && result.itemId != null) resolved += 1
        if (stillRetryable) remainingRetryable = true
    }
    return ParkedSaveRetryPassResult(
        attempted = parked.size,
        resolved = resolved,
        remainingRetryable = remainingRetryable,
    )
}

/** Re-runs the original save for a parked row via the shared coordinator. */
private suspend fun performParkedSaveRetry(
    coordinator: ShareSaveCoordinator,
    item: PendingManualSaveItem,
): ShareSaveResult = when (item.type) {
    PendingManualSaveType.URL -> coordinator.saveSharedText(
        sharedText = item.urlInput,
        sharedTitle = if (item.source == PendingSaveSource.SHARE) item.titleInput else null,
        destinationPlaylistIdOverride = item.destinationPlaylistId,
    )
    PendingManualSaveType.TEXT -> coordinator.saveManualText(
        urlInput = item.urlInput,
        titleInput = item.titleInput,
        bodyInput = item.bodyInput.orEmpty(),
        destinationPlaylistIdOverride = item.destinationPlaylistId,
    )
}

/**
 * Applies a background retry [result] to the parked [item], mirroring the ViewModel retry semantics
 * exactly (including the null-item-id preservation fix). Returns true if the row is still an
 * auto-retryable failure and the worker should reschedule.
 */
internal suspend fun applyParkedRetryOutcome(
    settingsStore: SettingsStore,
    notifications: ShareResultNotifications,
    item: PendingManualSaveItem,
    result: ShareSaveResult,
): Boolean {
    return when (val outcome = classifyPendingRetryOutcome(result)) {
        is PendingRetryOutcome.Resolved -> {
            settingsStore.markPendingManualSaveResolved(
                itemId = item.id,
                resolvedItemId = outcome.itemId,
                statusMessage = "Processing...",
            )
            val destination = (result as? ShareSaveResult.Saved)?.destinationName ?: "Mimeo"
            notifications.postPendingSaveResolved(item.id, destination)
            false
        }
        PendingRetryOutcome.PreserveUnresolved -> {
            settingsStore.updatePendingManualSaveStatus(
                itemId = item.id,
                statusMessage = SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE,
                autoRetryEligible = false,
            )
            notifications.cancelPendingSave(item.id)
            false
        }
        PendingRetryOutcome.RetryFailed -> {
            val stillAutoRetry = isAutoRetryEligiblePendingSaveResult(result)
            settingsStore.markPendingManualSaveRetryFailure(
                itemId = item.id,
                failureMessage = pendingRetryFailureMessage(item.lastFailureMessage, result),
                autoRetryEligible = stillAutoRetry,
            )
            stillAutoRetry
        }
        PendingRetryOutcome.Remove -> {
            settingsStore.removePendingManualSave(item.id)
            notifications.cancelPendingSave(item.id)
            false
        }
    }
}
