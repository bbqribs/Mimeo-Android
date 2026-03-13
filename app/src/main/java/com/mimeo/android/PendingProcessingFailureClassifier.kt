package com.mimeo.android

internal fun isTerminalPendingProcessingStatus(status: String?): Boolean {
    val normalized = status?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return normalized.contains("fail") ||
        normalized.contains("error") ||
        normalized.contains("blocked") ||
        normalized.contains("paywall") ||
        normalized.contains("unsupported") ||
        normalized.contains("denied") ||
        normalized.contains("forbidden")
}

internal fun resolveTerminalPendingProcessingMessage(status: String?): String {
    val trimmed = status?.trim().orEmpty()
    val normalized = trimmed.lowercase()
    return when {
        normalized.isBlank() -> "Article processing failed"
        normalized.contains("blocked") || normalized.contains("paywall") -> "Article blocked by source"
        normalized.contains("unsupported") -> "Article source unsupported"
        normalized.contains("denied") || normalized.contains("forbidden") -> "Article access denied"
        normalized.contains("fail") || normalized.contains("error") -> "Article processing failed"
        else -> "Article processing failed: $trimmed"
    }
}

internal fun isPendingProcessingFailureMessage(message: String): Boolean {
    val normalized = message.trim().lowercase()
    if (normalized.isBlank()) return false
    return normalized.contains("failed") ||
        normalized.contains("error") ||
        normalized.contains("blocked") ||
        normalized.contains("paywall") ||
        normalized.contains("unsupported") ||
        normalized.contains("access denied")
}
