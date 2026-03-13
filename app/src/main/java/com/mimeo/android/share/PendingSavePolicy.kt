package com.mimeo.android.share

internal fun isRetryablePendingSaveResult(result: ShareSaveResult): Boolean {
    return when (result) {
        ShareSaveResult.NetworkError,
        ShareSaveResult.TimedOut,
        ShareSaveResult.ServerError,
        ShareSaveResult.SaveFailed,
        ShareSaveResult.MissingToken,
        ShareSaveResult.Unauthorized,
        -> true
        else -> false
    }
}

internal fun isAutoRetryEligiblePendingSaveResult(result: ShareSaveResult): Boolean {
    return when (result) {
        ShareSaveResult.NetworkError,
        ShareSaveResult.TimedOut,
        ShareSaveResult.ServerError,
        ShareSaveResult.SaveFailed,
        -> true
        else -> false
    }
}
