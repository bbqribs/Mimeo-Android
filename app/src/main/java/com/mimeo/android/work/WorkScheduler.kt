package com.mimeo.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val UNIQUE_PROGRESS_SYNC_WORK = "progress-sync"
    private const val UNIQUE_AUTO_DOWNLOAD_WORK = "autodownload"
    private const val UNIQUE_PENDING_SAVE_RETRY_WORK = "pending-save-retry"

    fun enqueueProgressSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_PROGRESS_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedules a [AutoDownloadWorker] run for the given [itemIds].
     *
     * Policy: [ExistingWorkPolicy.APPEND_OR_REPLACE] — replaces any pending/enqueued work with
     * the new target list, but does not cancel an already-running worker (which will dedup via
     * cache check on its own). Requires an active network connection. Retries with exponential
     * backoff (30 s initial) up to [AutoDownloadWorker.MAX_ATTEMPTS] total attempts.
     */
    fun enqueueAutoDownload(context: Context, itemIds: List<Int>) {
        if (itemIds.isEmpty()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to itemIds.toIntArray())

        val request = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_AUTO_DOWNLOAD_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    /**
     * Schedules a single serialized [PendingSaveRetryWorker] to flush parked saves in the background.
     *
     * Policy: one unique aggregate job ([ExistingWorkPolicy.KEEP] — a save parked while a retry job
     * is already queued joins it rather than stacking a second job). Network-constrained, exponential
     * backoff (30 s initial) up to [PendingSaveRetryWorker.MAX_ATTEMPTS] total attempts (~5 over ~24 h).
     * [parkedBaseUrl] is captured so the worker can no-op if the account/endpoint changes.
     */
    fun enqueuePendingSaveRetry(context: Context, parkedBaseUrl: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(PendingSaveRetryWorker.KEY_PARKED_BASE_URL to parkedBaseUrl)

        val request = OneTimeWorkRequestBuilder<PendingSaveRetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_PENDING_SAVE_RETRY_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Cancels any scheduled pending-save retry work (e.g. after an app-open flush resolved the rows). */
    fun cancelPendingSaveRetry(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_PENDING_SAVE_RETRY_WORK)
    }
}
