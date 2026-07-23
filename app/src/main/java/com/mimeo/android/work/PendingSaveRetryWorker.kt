package com.mimeo.android.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.share.PendingSaveRetryGate
import com.mimeo.android.share.runParkedSaveRetryPass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * WorkManager worker that flushes parked pending saves in the background so a save no longer depends
 * on the user reopening the app. Reuses the shared [runParkedSaveRetryPass] save path and serializes
 * with the foreground app-open flush via [PendingSaveRetryGate].
 *
 * Retry policy: network-constrained, exponential backoff, up to [MAX_ATTEMPTS] runs (~5 over ~24 h
 * via the scheduler's backoff). After exhaustion the parked rows remain with their latest failure
 * state — no infinite retries, no polling, no foreground service.
 *
 * Account/endpoint isolation: the base URL that parked the save is captured in [KEY_PARKED_BASE_URL];
 * if the active base URL no longer matches (account/endpoint change), the worker no-ops without
 * submitting one account's save under another.
 */
class PendingSaveRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "PendingSaveRetryWorker"
        const val KEY_PARKED_BASE_URL = "parked_base_url"
        internal const val MAX_ATTEMPTS = 5

        internal fun normalizeBaseUrl(baseUrl: String?): String = baseUrl.orEmpty().trim().trimEnd('/')
    }

    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.settingsFlow.first()
        if (settings.baseUrl.isBlank() || settings.apiToken.isBlank()) {
            Log.d(TAG, "Skipping pending-save retry — no credentials configured")
            return Result.success()
        }

        val parkedBaseUrl = inputData.getString(KEY_PARKED_BASE_URL)
        if (parkedBaseUrl != null && normalizeBaseUrl(parkedBaseUrl) != normalizeBaseUrl(settings.baseUrl)) {
            Log.d(TAG, "Skipping pending-save retry — account/endpoint changed since parking")
            return Result.success()
        }

        val pass = PendingSaveRetryGate.mutex.withLock {
            runParkedSaveRetryPass(context = applicationContext, settingsStore = settingsStore)
        }
        Log.d(
            TAG,
            "pending-save retry pass attempted=${pass.attempted} resolved=${pass.resolved} " +
                "remainingRetryable=${pass.remainingRetryable} attempt=${runAttemptCount + 1}/$MAX_ATTEMPTS",
        )

        val shouldRetry = pass.remainingRetryable && runAttemptCount < MAX_ATTEMPTS - 1
        return if (shouldRetry) Result.retry() else Result.success()
    }
}
