package com.mimeo.android.share

internal fun isRetryablePendingSaveResult(result: ShareSaveResult): Boolean {
    return when (result) {
        ShareSaveResult.NetworkError,
        ShareSaveResult.TimedOut,
        ShareSaveResult.ServerError,
        ShareSaveResult.SaveFailed,
        -> true
        else -> false
    }
}
