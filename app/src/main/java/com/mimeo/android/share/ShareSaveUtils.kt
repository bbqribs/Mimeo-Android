package com.mimeo.android.share

import com.mimeo.android.data.ManualTextSourcePayload
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

private val HTTP_URL_REGEX = """https?://[^\s<>()]+""".toRegex(RegexOption.IGNORE_CASE)
private val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
private const val PLAIN_TEXT_SHARE_TITLE_MAX_CHARS = 96

fun extractFirstHttpUrl(sharedText: String?): String? {
    if (sharedText.isNullOrBlank()) return null
    val candidate = HTTP_URL_REGEX.find(sharedText)?.value ?: return null
    val trimmed = candidate.trim().trimTrailingUrlPunctuation()
    return trimmed.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
}

fun extractHttpUrls(sharedText: String?): List<String> {
    if (sharedText.isNullOrBlank()) return emptyList()
    return HTTP_URL_REGEX.findAll(sharedText)
        .mapNotNull { match ->
            match.value.trim().trimTrailingUrlPunctuation()
                .takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
        .toList()
}

fun shouldTreatShareAsUrlCapture(
    sharedText: String?,
    extractedUrl: String?,
): Boolean {
    val normalizedUrl = extractedUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val normalizedText = extractPlainTextShareBody(sharedText) ?: return true
    val remainder = normalizedText
        .replaceFirst(normalizedUrl, "")
        .trim()
        .trim(
            '(', ')', '[', ']', '{', '}', '"', '\'', '.', ',', ';', ':', '!', '?', '-', '–', '—',
        )
    if (remainder.isBlank()) return true
    val remainderUrls = extractHttpUrls(remainder)
    if (remainderUrls.size == 1 && remainderUrls.first().contains("#:~:text=", ignoreCase = true)) {
        val leftover = remainder
            .replaceFirst(remainderUrls.first(), "")
            .trim()
            .trim(
                '(', ')', '[', ']', '{', '}', '"', '\'', '.', ',', ';', ':', '!', '?', '-', '–', '—',
            )
        if (leftover.isBlank()) return true
    }
    return false
}

fun normalizeSharedSourceUrl(url: String): String {
    val trimmed = url.trim().trimTrailingUrlPunctuation()
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
    return runCatching {
        URI(
            parsed.scheme,
            parsed.rawUserInfo,
            parsed.host,
            parsed.port,
            parsed.rawPath,
            parsed.rawQuery,
            null, // strip fragment (e.g. :~:text browser fragments)
        ).toASCIIString()
    }.getOrDefault(trimmed)
}

fun removeSharedUrlFromText(sharedText: String, url: String): String {
    val raw = sharedText.trim()
    val normalizedUrl = url.trim().trimTrailingUrlPunctuation()
    if (normalizedUrl.isBlank()) return raw
    return raw
        .replace(normalizedUrl, "")
        .replace(url, "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

fun derivePlainTextSourceUrl(
    sharedText: String?,
    extractedUrl: String?,
): String? {
    val body = extractPlainTextShareBody(sharedText) ?: return null
    val candidate = extractTrailingSourceUrl(body) ?: return null
    val hasStandaloneText = removeSharedUrlFromText(body, candidate).isNotBlank()
    if (!hasStandaloneText) return null
    val remainingBody = removeSharedUrlFromText(body, candidate)
    val hasOtherUrls = extractHttpUrls(remainingBody).isNotEmpty()
    val hasExplicitSelectionFragment = candidate.contains("#:~:text=", ignoreCase = true)
    if (hasOtherUrls && !hasExplicitSelectionFragment) return null
    return normalizeSharedSourceUrl(candidate)
}

fun removeTrailingSourceUrlFromText(
    sharedText: String,
    sourceUrl: String,
): String {
    val body = sharedText.trim()
    val normalized = sourceUrl.trim().trimTrailingUrlPunctuation()
    if (normalized.isBlank()) return body
    val lastIndex = body.lastIndexOf(normalized)
    if (lastIndex < 0) return body
    val tail = body.substring(lastIndex + normalized.length).trim()
    if (tail.isNotEmpty() && tail.any { !it.isWhitespace() && it !in TRAILING_URL_PUNCTUATION }) return body
    val prefix = body.substring(0, lastIndex).trimEnd()
    return prefix
}

fun appendOriginalArticleFooter(
    body: String,
    sourceUrl: String?,
): String {
    val normalizedBody = body.trim()
    val normalizedSource = sourceUrl?.trim().orEmpty()
    if (normalizedSource.isBlank()) return normalizedBody
    if (normalizedBody.isBlank()) {
        return "To see the original article, open: $normalizedSource"
    }
    return "$normalizedBody\n\nTo see the original article, open: $normalizedSource"
}

fun buildManualTextSourcePayload(
    urlInput: String,
    captureKind: String,
    explicitSourceUrl: String? = null,
    sourceAppPackage: String? = null,
    sourceAppLabel: String? = null,
    forceAppSource: Boolean = false,
): ManualTextSourcePayload {
    val appPackage = sourceAppPackage
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("com.mimeo.android", ignoreCase = true) }
    val appLabel = sourceAppLabel?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedSourceUrl = explicitSourceUrl
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::normalizeSharedSourceUrl)
        ?: extractFirstHttpUrl(urlInput)
            ?.let(::normalizeSharedSourceUrl)
            ?.takeUnless(::isSyntheticSharedTextUrl)
    val isWebSource = !forceAppSource && normalizedSourceUrl != null
    val sourceLabel = if (isWebSource) {
        runCatching { URI(normalizedSourceUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
            ?: normalizedSourceUrl
    } else {
        appLabel ?: appPackage ?: "Android selection"
    }
    return ManualTextSourcePayload(
        sourceType = if (isWebSource) "web" else "app",
        sourceLabel = sourceLabel,
        sourceUrl = if (isWebSource) normalizedSourceUrl else null,
        captureKind = captureKind,
        sourceAppPackage = appPackage,
    )
}

private fun isSyntheticSharedTextUrl(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US) ?: return false
    return host == "shared-text.mimeo.local"
}

private fun extractTrailingSourceUrl(body: String): String? {
    extractStandaloneTrailingSourceUrl(body)?.let { return it }
    val allUrls = extractHttpUrls(body)
    val trailing = allUrls.lastOrNull() ?: return null
    if (!trailing.contains("#:~:text=", ignoreCase = true)) return null
    val trimmedTail = body.trimEnd().trimEnd(*TRAILING_URL_PUNCTUATION)
    if (!trimmedTail.endsWith(trailing, ignoreCase = true)) return null
    return trailing
}

private fun extractStandaloneTrailingSourceUrl(body: String): String? {
    val lines = body.lines()
    val lastNonBlankIndex = lines.indexOfLast { it.isNotBlank() }
    if (lastNonBlankIndex < 0) return null
    val trailingLineRaw = lines[lastNonBlankIndex].trim()
    val trailingLine = trailingLineRaw.trimTrailingUrlPunctuation()
    val url = extractFirstHttpUrl(trailingLine) ?: return null
    if (!trailingLine.equals(url, ignoreCase = true)) return null
    if (lastNonBlankIndex > 0) {
        val priorLine = lines[lastNonBlankIndex - 1]
        if (priorLine.isNotBlank()) return null
    }
    return url
}

fun buildShareIdempotencyKey(url: String): String {
    val normalized = normalizeUrlForIdempotency(url)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "android-share-${digest.take(24)}"
}

fun extractPlainTextShareBody(sharedText: String?): String? {
    val normalized = sharedText?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return normalized
}

fun derivePlainTextShareTitle(sharedTitle: String?, plainTextBody: String): String {
    val subject = sharedTitle?.trim().orEmpty().takeIf { it.isNotEmpty() }
        ?.takeUnless(::looksLikeShareBoilerplateTitle)
    val firstMeaningfulLine = plainTextBody
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: "Shared text"
    val seed = (subject ?: firstMeaningfulLine)
        .removePrefix("Excerpt:")
        .trim()
        .trim('"')
        .trim()
        .ifBlank { "Shared text" }
    val snippet = seed.truncateWithEllipsis(PLAIN_TEXT_SHARE_TITLE_MAX_CHARS)
    return "Excerpt: \"$snippet\""
}

private fun looksLikeShareBoilerplateTitle(title: String): Boolean {
    val normalized = title.trim().lowercase(Locale.US)
    if (normalized.startsWith("including link:")) return true
    if (normalized.startsWith("link:")) return true
    if (normalized.startsWith("shared from")) return true
    if (normalized.contains("http://") || normalized.contains("https://")) return true
    return false
}

fun buildPlainTextShareSyntheticUrl(title: String, plainTextBody: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$title\n$plainTextBody".toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "https://shared-text.mimeo.local/${digest.take(20)}"
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

private fun String.truncateWithEllipsis(maxChars: Int): String {
    if (length <= maxChars) return this
    return take((maxChars - 1).coerceAtLeast(1)) + "…"
}

