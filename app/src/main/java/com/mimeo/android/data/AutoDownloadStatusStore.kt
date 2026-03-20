package com.mimeo.android.data

import android.content.Context
import com.mimeo.android.model.AutoDownloadDiagnostics
import com.mimeo.android.model.AutoDownloadWorkerState

class AutoDownloadStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AutoDownloadDiagnostics {
        val stateName = prefs.getString(KEY_WORKER_STATE, null)
        val parsedState = stateName
            ?.let { runCatching { AutoDownloadWorkerState.valueOf(it) }.getOrNull() }
            ?: AutoDownloadWorkerState.IDLE
        return AutoDownloadDiagnostics(
            autoDownloadEnabled = prefs.getBoolean(KEY_AUTODOWNLOAD_ENABLED, false),
            queueItemCount = prefs.getInt(KEY_QUEUE_ITEM_COUNT, 0),
            offlineReadyCount = prefs.getInt(KEY_OFFLINE_READY_COUNT, 0),
            knownNoActiveCount = prefs.getInt(KEY_KNOWN_NO_ACTIVE_COUNT, 0),
            lastScheduledAtMs = prefs.getLong(KEY_LAST_SCHEDULED_AT, 0L).takeIf { it > 0L },
            candidateCount = prefs.getInt(KEY_CANDIDATE_COUNT, 0),
            queuedCount = prefs.getInt(KEY_QUEUED_COUNT, 0),
            skippedCachedCount = prefs.getInt(KEY_SKIPPED_CACHED_COUNT, 0),
            skippedNoActiveCount = prefs.getInt(KEY_SKIPPED_NO_ACTIVE_COUNT, 0),
            includeAllVisibleUncached = prefs.getBoolean(KEY_INCLUDE_ALL_VISIBLE_UNCACHED, false),
            workerState = parsedState,
            workerUpdatedAtMs = prefs.getLong(KEY_WORKER_UPDATED_AT, 0L).takeIf { it > 0L },
            attemptedCount = prefs.getInt(KEY_ATTEMPTED_COUNT, 0),
            successCount = prefs.getInt(KEY_SUCCESS_COUNT, 0),
            retryableFailureCount = prefs.getInt(KEY_RETRYABLE_FAILURE_COUNT, 0),
            terminalFailureCount = prefs.getInt(KEY_TERMINAL_FAILURE_COUNT, 0),
            noActiveContentCount = prefs.getInt(KEY_NO_ACTIVE_CONTENT_COUNT, 0),
        )
    }

    fun recordQueueSnapshot(
        autoDownloadEnabled: Boolean,
        queueItemCount: Int,
        offlineReadyCount: Int,
        knownNoActiveCount: Int,
    ) {
        prefs.edit()
            .putBoolean(KEY_AUTODOWNLOAD_ENABLED, autoDownloadEnabled)
            .putInt(KEY_QUEUE_ITEM_COUNT, queueItemCount.coerceAtLeast(0))
            .putInt(KEY_OFFLINE_READY_COUNT, offlineReadyCount.coerceAtLeast(0))
            .putInt(KEY_KNOWN_NO_ACTIVE_COUNT, knownNoActiveCount.coerceAtLeast(0))
            .apply()
    }

    fun recordSchedule(
        candidateCount: Int,
        queuedCount: Int,
        skippedCachedCount: Int,
        skippedNoActiveCount: Int,
        includeAllVisibleUncached: Boolean,
    ) {
        prefs.edit()
            .putLong(KEY_LAST_SCHEDULED_AT, System.currentTimeMillis())
            .putInt(KEY_CANDIDATE_COUNT, candidateCount.coerceAtLeast(0))
            .putInt(KEY_QUEUED_COUNT, queuedCount.coerceAtLeast(0))
            .putInt(KEY_SKIPPED_CACHED_COUNT, skippedCachedCount.coerceAtLeast(0))
            .putInt(KEY_SKIPPED_NO_ACTIVE_COUNT, skippedNoActiveCount.coerceAtLeast(0))
            .putBoolean(KEY_INCLUDE_ALL_VISIBLE_UNCACHED, includeAllVisibleUncached)
            .putString(KEY_WORKER_STATE, AutoDownloadWorkerState.QUEUED.name)
            .putLong(KEY_WORKER_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordWorkerStart(attemptedCount: Int) {
        prefs.edit()
            .putString(KEY_WORKER_STATE, AutoDownloadWorkerState.RUNNING.name)
            .putLong(KEY_WORKER_UPDATED_AT, System.currentTimeMillis())
            .putInt(KEY_ATTEMPTED_COUNT, attemptedCount.coerceAtLeast(0))
            .apply()
    }

    fun recordWorkerResult(
        state: AutoDownloadWorkerState,
        attemptedCount: Int,
        successCount: Int,
        retryableFailureCount: Int,
        terminalFailureCount: Int,
        noActiveContentCount: Int,
    ) {
        prefs.edit()
            .putString(KEY_WORKER_STATE, state.name)
            .putLong(KEY_WORKER_UPDATED_AT, System.currentTimeMillis())
            .putInt(KEY_ATTEMPTED_COUNT, attemptedCount.coerceAtLeast(0))
            .putInt(KEY_SUCCESS_COUNT, successCount.coerceAtLeast(0))
            .putInt(KEY_RETRYABLE_FAILURE_COUNT, retryableFailureCount.coerceAtLeast(0))
            .putInt(KEY_TERMINAL_FAILURE_COUNT, terminalFailureCount.coerceAtLeast(0))
            .putInt(KEY_NO_ACTIVE_CONTENT_COUNT, noActiveContentCount.coerceAtLeast(0))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "mimeo_autodownload_status"
        private const val KEY_AUTODOWNLOAD_ENABLED = "auto_download_enabled"
        private const val KEY_QUEUE_ITEM_COUNT = "queue_item_count"
        private const val KEY_OFFLINE_READY_COUNT = "offline_ready_count"
        private const val KEY_KNOWN_NO_ACTIVE_COUNT = "known_no_active_count"
        private const val KEY_LAST_SCHEDULED_AT = "last_scheduled_at"
        private const val KEY_CANDIDATE_COUNT = "candidate_count"
        private const val KEY_QUEUED_COUNT = "queued_count"
        private const val KEY_SKIPPED_CACHED_COUNT = "skipped_cached_count"
        private const val KEY_SKIPPED_NO_ACTIVE_COUNT = "skipped_no_active_count"
        private const val KEY_INCLUDE_ALL_VISIBLE_UNCACHED = "include_all_visible_uncached"
        private const val KEY_WORKER_STATE = "worker_state"
        private const val KEY_WORKER_UPDATED_AT = "worker_updated_at"
        private const val KEY_ATTEMPTED_COUNT = "attempted_count"
        private const val KEY_SUCCESS_COUNT = "success_count"
        private const val KEY_RETRYABLE_FAILURE_COUNT = "retryable_failure_count"
        private const val KEY_TERMINAL_FAILURE_COUNT = "terminal_failure_count"
        private const val KEY_NO_ACTIVE_CONTENT_COUNT = "no_active_content_count"
    }
}
