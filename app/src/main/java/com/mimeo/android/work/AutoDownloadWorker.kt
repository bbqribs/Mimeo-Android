package com.mimeo.android.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AutoDownloadStatusStore
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.NoActiveContentStore
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AutoDownloadWorkerState
import com.mimeo.android.repository.ItemTextPrefetchAttempt
import com.mimeo.android.repository.PlaybackRepository
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker that downloads and caches item texts for autodownload targets.
 *
 * Receives a list of item IDs via [KEY_ITEM_IDS] input data. Before downloading, checks the
 * local cache and skips already-cached items (dedup against DB state). Persists no-active-content
 * results to [NoActiveContentStore] so the next ViewModel queue load can exclude those items from
 * future target selection without re-attempting permanently failed items.
 *
 * Retry policy: exponential backoff, up to [MAX_ATTEMPTS] total runs. Only network/server-5xx
 * failures trigger a retry; 404 and 409-no-active-content are treated as terminal successes.
 * A 401 causes immediate success (stale token — no value in retrying).
 */
class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AutoDownloadWorker"
        const val KEY_ITEM_IDS = "item_ids"
        internal const val MAX_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val statusStore = AutoDownloadStatusStore(applicationContext)
        val settings = SettingsStore(applicationContext).settingsFlow.first()
        if (settings.baseUrl.isBlank() || settings.apiToken.isBlank()) {
            Log.d(TAG, "Skipping autodownload — no credentials configured")
            statusStore.recordWorkerResult(
                state = AutoDownloadWorkerState.SKIPPED_NO_TOKEN,
                attemptedCount = 0,
                successCount = 0,
                retryableFailureCount = 0,
                terminalFailureCount = 0,
                noActiveContentCount = 0,
            )
            return Result.success()
        }
        if (!settings.autoDownloadSavedArticles) {
            Log.d(TAG, "Skipping autodownload — feature disabled")
            statusStore.recordWorkerResult(
                state = AutoDownloadWorkerState.SKIPPED_DISABLED,
                attemptedCount = 0,
                successCount = 0,
                retryableFailureCount = 0,
                terminalFailureCount = 0,
                noActiveContentCount = 0,
            )
            return Result.success()
        }

        val itemIds = inputData.getIntArray(KEY_ITEM_IDS)?.toList() ?: return Result.success()
        if (itemIds.isEmpty()) return Result.success()

        val repository = PlaybackRepository(
            apiClient = ApiClient(),
            database = AppDatabase.getInstance(applicationContext),
            appContext = applicationContext,
        )

        // Dedup: skip items already present in the local cache.
        val alreadyCachedIds = repository.getCachedItemIds(itemIds)
        val targets = itemIds.filterNot { alreadyCachedIds.contains(it) }
        if (targets.isEmpty()) {
            Log.d(TAG, "All ${itemIds.size} target(s) already cached — nothing to do")
            statusStore.recordWorkerResult(
                state = AutoDownloadWorkerState.SKIPPED_ALREADY_CACHED,
                attemptedCount = 0,
                successCount = 0,
                retryableFailureCount = 0,
                terminalFailureCount = 0,
                noActiveContentCount = 0,
            )
            return Result.success()
        }

        Log.d(TAG, "Downloading ${targets.size} item(s) (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")
        statusStore.recordWorkerStart(attemptedCount = targets.size)
        val attempts = runCatching {
            repository.prefetchItemTexts(
                baseUrl = settings.baseUrl,
                token = settings.apiToken,
                itemIds = targets,
            )
        }.getOrElse { error ->
            if (error is ApiException && error.statusCode == 401) {
                Log.w(TAG, "Autodownload stopped after 401 — stale token")
                statusStore.recordWorkerResult(
                    state = AutoDownloadWorkerState.SKIPPED_NO_TOKEN,
                    attemptedCount = targets.size,
                    successCount = 0,
                    retryableFailureCount = 0,
                    terminalFailureCount = targets.size,
                    noActiveContentCount = 0,
                )
                return Result.success()
            }
            Log.w(TAG, "Autodownload fetch error: $error")
            val shouldRetry = runAttemptCount < MAX_ATTEMPTS - 1
            statusStore.recordWorkerResult(
                state = if (shouldRetry) {
                    AutoDownloadWorkerState.RETRY_PENDING
                } else {
                    AutoDownloadWorkerState.COMPLETED_WITH_FAILURES
                },
                attemptedCount = targets.size,
                successCount = 0,
                retryableFailureCount = targets.size,
                terminalFailureCount = 0,
                noActiveContentCount = 0,
            )
            return if (shouldRetry) Result.retry() else Result.success()
        }

        persistNoActiveContentResults(attempts)
        val summary = summarizeAttempts(attempts)
        val shouldRetry = summary.retryableFailureCount > 0 && runAttemptCount < MAX_ATTEMPTS - 1
        statusStore.recordWorkerResult(
            state = when {
                shouldRetry -> AutoDownloadWorkerState.RETRY_PENDING
                summary.terminalFailureCount > 0 -> AutoDownloadWorkerState.COMPLETED_WITH_FAILURES
                else -> AutoDownloadWorkerState.SUCCEEDED
            },
            attemptedCount = attempts.size,
            successCount = summary.successCount,
            retryableFailureCount = summary.retryableFailureCount,
            terminalFailureCount = summary.terminalFailureCount,
            noActiveContentCount = summary.noActiveContentCount,
        )
        return if (shouldRetry) Result.retry() else Result.success()
    }

    private fun persistNoActiveContentResults(attempts: List<ItemTextPrefetchAttempt>) {
        val noContentIds = attempts
            .filter { !it.success && !it.retryable &&
                it.errorSummary.orEmpty().contains("No active content", ignoreCase = true) }
            .map { it.itemId }
            .toSet()
        if (noContentIds.isNotEmpty()) {
            Log.d(TAG, "Persisting ${noContentIds.size} no-active-content item(s)")
            NoActiveContentStore(applicationContext).add(noContentIds)
        }
    }

    private data class AttemptSummary(
        val successCount: Int,
        val retryableFailureCount: Int,
        val terminalFailureCount: Int,
        val noActiveContentCount: Int,
    )

    private fun summarizeAttempts(attempts: List<ItemTextPrefetchAttempt>): AttemptSummary {
        val successCount = attempts.count { it.success }
        val retryableFailureCount = attempts.count { !it.success && it.retryable }
        val terminalFailureCount = attempts.count { !it.success && !it.retryable }
        val noActiveContentCount = attempts.count {
            !it.success &&
                !it.retryable &&
                it.errorSummary.orEmpty().contains("No active content", ignoreCase = true)
        }
        return AttemptSummary(
            successCount = successCount,
            retryableFailureCount = retryableFailureCount,
            terminalFailureCount = terminalFailureCount,
            noActiveContentCount = noActiveContentCount,
        )
    }
}
