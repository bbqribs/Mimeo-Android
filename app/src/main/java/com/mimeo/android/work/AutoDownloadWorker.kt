package com.mimeo.android.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.NoActiveContentStore
import com.mimeo.android.data.SettingsStore
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
        val settings = SettingsStore(applicationContext).settingsFlow.first()
        if (settings.baseUrl.isBlank() || settings.apiToken.isBlank()) {
            Log.d(TAG, "Skipping autodownload — no credentials configured")
            return Result.success()
        }
        if (!settings.autoDownloadSavedArticles) {
            Log.d(TAG, "Skipping autodownload — feature disabled")
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
            return Result.success()
        }

        Log.d(TAG, "Downloading ${targets.size} item(s) (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)")
        val attempts = runCatching {
            repository.prefetchItemTexts(
                baseUrl = settings.baseUrl,
                token = settings.apiToken,
                itemIds = targets,
            )
        }.getOrElse { error ->
            if (error is ApiException && error.statusCode == 401) {
                Log.w(TAG, "Autodownload stopped after 401 — stale token")
                return Result.success()
            }
            Log.w(TAG, "Autodownload fetch error: $error")
            return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.success()
        }

        persistNoActiveContentResults(attempts)
        return retryDecision(attempts)
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

    private fun retryDecision(attempts: List<ItemTextPrefetchAttempt>): Result {
        val hasRetryable = attempts.any { !it.success && it.retryable }
        return if (hasRetryable && runAttemptCount < MAX_ATTEMPTS - 1) {
            Log.d(TAG, "Retryable failures present — scheduling retry")
            Result.retry()
        } else {
            Result.success()
        }
    }
}
