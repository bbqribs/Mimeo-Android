package com.mimeo.android.share

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

private val HTTP_URL_REGEX = """https?://[^\s<>()]+""".toRegex(RegexOption.IGNORE_CASE)
private val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

fun extractFirstHttpUrl(sharedText: String?): String? {
    if (sharedText.isNullOrBlank()) return null
    val candidate = HTTP_URL_REGEX.find(sharedText)?.value ?: return null
    val trimmed = candidate.trim().trimTrailingUrlPunctuation()
    return trimmed.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
}

fun buildShareIdempotencyKey(url: String): String {
    val normalized = normalizeUrlForIdempotency(url)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "android-share-${digest.take(24)}"
}

private fun normalizeUrlForIdempotency(url: String): String {
    val trimmed = url.trim()
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
    val scheme = parsed.scheme?.lowercase(Locale.US) ?: return trimmed
    val host = parsed.host?.lowercase(Locale.US)
    val port = when {
        parsed.port == 80 && scheme == "http" -> -1
        parsed.port == 443 && scheme == "https" -> -1
        else -> parsed.port
    }
    val rawPath = parsed.rawPath?.ifBlank { "/" } ?: "/"
    return runCatching {
        URI(
            scheme,
            parsed.rawUserInfo,
            host,
            port,
            rawPath,
            parsed.rawQuery,
            null,
        ).toASCIIString()
    }.getOrDefault(trimmed)
}

private fun String.trimTrailingUrlPunctuation(): String {
    var endIndex = length
    while (endIndex > 0 && this[endIndex - 1] in TRAILING_URL_PUNCTUATION) {
        endIndex -= 1
    }
    return substring(0, endIndex)
}
