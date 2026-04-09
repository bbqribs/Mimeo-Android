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
    return resolveTerminalPendingProcessingMessage(
        status = status,
        failureReason = null,
        fetchHttpStatus = null,
    )
}

internal fun resolveTerminalPendingProcessingMessage(
    status: String?,
    failureReason: String?,
    fetchHttpStatus: Int?,
): String {
    val trimmed = status?.trim().orEmpty()
    val normalized = trimmed.lowercase()
    val normalizedReason = failureReason?.trim()?.lowercase().orEmpty()
    return when {
        normalizedReason == "blocked_by_paywall" -> "Article blocked by paywall"
        normalizedReason == "blocked_by_bot" ||
            normalizedReason == "blocked_by_bot_confirmed" ||
            normalizedReason == "human_verification_required" ->
            "Source blocked access"
        normalizedReason == "consent_banner_ignored" -> "Article blocked by consent banner"
        normalizedReason == "article_not_found" -> "Article not found"
        normalizedReason == "cookie_decrypt_failed" -> "Article login needs refresh"
        normalizedReason == "http_request_failed" && fetchHttpStatus == 403 -> "Source blocked access"
        normalizedReason == "http_request_failed" && fetchHttpStatus == 404 -> "Article not found"
        normalizedReason.isNotBlank() -> "Article processing failed"
        normalized.isBlank() -> "Article processing failed"
        normalized.contains("blocked") || normalized.contains("paywall") -> "Source blocked access"
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
        normalized.contains("access denied") ||
        normalized.contains("not found") ||
        normalized.contains("login needs refresh")
}
