package com.mimeo.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.repository.PlaybackRepository
import android.util.Log
import kotlinx.coroutines.flow.first

class ProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ProgressSyncWorker"
    }

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).settingsFlow.first()
        if (settings.baseUrl.isBlank() || settings.apiToken.isBlank()) {
            return Result.success()
        }

        val repository = PlaybackRepository(
            apiClient = ApiClient(),
            database = AppDatabase.getInstance(applicationContext),
            appContext = applicationContext,
        )

        return runCatching {
            val flush = repository.flushPendingProgress(settings.baseUrl, settings.apiToken)
            if (flush.retryableFailures > 0 && flush.pendingCount > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        }.getOrElse { error ->
            if (error is ApiException && error.statusCode == 401) {
                Log.w(TAG, "Progress sync stopped after 401 stale token response")
                Result.success()
            } else {
            Result.retry()
            }
        }
    }
}
