package com.mimeo.android.ui.common

import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.repository.NowPlayingSessionItem
import java.net.URI

data class CapturePresentation(
    val title: String,
    val sourceLabel: String?,
    val sourceUrl: String?,
)

fun queueCapturePresentation(item: PlaybackQueueItem): CapturePresentation {
    return capturePresentation(
        rawTitle = item.title,
        rawUrl = item.url,
        rawHost = item.host,
        sourceType = item.sourceType,
        sourceLabel = item.sourceLabel,
        sourceUrl = item.sourceUrl,
        captureKind = item.captureKind,
        sourceAppPackage = item.sourceAppPackage,
    )
}

fun locusCapturePresentation(text: ItemTextResponse?): CapturePresentation {
    if (text == null) {
        return CapturePresentation(
            title = "",
            sourceLabel = null,
            sourceUrl = null,
        )
    }
    return capturePresentation(
        rawTitle = text.title,
        rawUrl = text.url,
        rawHost = text.host,
        sourceType = text.sourceType,
        sourceLabel = text.sourceLabel,
        sourceUrl = text.sourceUrl,
        captureKind = text.captureKind,
        sourceAppPackage = text.sourceAppPackage,
    )
}

fun nowPlayingCapturePresentation(item: NowPlayingSessionItem?): CapturePresentation {
    if (item == null) {
        return CapturePresentation(
            title = "",
            sourceLabel = null,
            sourceUrl = null,
        )
    }
    return capturePresentation(
        rawTitle = item.title,
        rawUrl = item.url,
        rawHost = item.host,
        sourceType = item.sourceType,
        sourceLabel = item.sourceLabel,
        sourceUrl = item.sourceUrl,
        captureKind = item.captureKind,
        sourceAppPackage = item.sourceAppPackage,
    )
}

private fun capturePresentation(
    rawTitle: String?,
    rawUrl: String,
    rawHost: String?,
    sourceType: String?,
    sourceLabel: String?,
    sourceUrl: String?,
    captureKind: String?,
    sourceAppPackage: String?,
): CapturePresentation {
    val excerptLike = isExcerptLikeCapture(captureKind = captureKind, url = rawUrl)
    val title = if (excerptLike) {
        formatExcerptTitle(rawTitle = rawTitle, fallbackUrl = rawUrl)
    } else {
        rawTitle?.trim()?.takeIf { it.isNotEmpty() } ?: rawUrl
    }
    val trustedLink = sourceUrl?.trim()?.takeIf { it.startsWithHttp() }
    val normalizedLabel = if (excerptLike) {
        val provenanceLabel = trustedLink
            ?.let(::extractHost)
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
        val originLabel = sourceLabel?.normalizedSourceLabel(sourceType)
            ?: sourceAppPackage?.trim()?.takeIf { it.isNotEmpty() }
        provenanceLabel ?: originLabel ?: "Android selection"
    } else {
        val label = sourceLabel?.normalizedSourceLabel(sourceType)
            ?: rawHost?.trim()?.takeIf { it.isNotEmpty() }
            ?: sourceAppPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (label != null) {
            if (label.contains('.')) label.removePrefix("www.") else label
        } else if (sourceType == "app") {
            "App share"
        } else {
            null
        }
    }
    val link = if (excerptLike) {
        trustedLink
    } else {
        trustedLink ?: rawUrl.takeIf { it.startsWithHttp() && !isSyntheticSharedTextUrl(it) }
    }
    return CapturePresentation(
        title = title,
        sourceLabel = normalizedLabel,
        sourceUrl = link,
    )
}

private fun isExcerptLikeCapture(captureKind: String?, url: String): Boolean {
    val normalizedKind = captureKind?.trim()?.lowercase()
    if (normalizedKind in setOf("shared_excerpt", "manual_text", "plain_text", "excerpt")) return true
    return isSyntheticSharedTextUrl(url)
}

private fun formatExcerptTitle(rawTitle: String?, fallbackUrl: String): String {
    val candidate = rawTitle
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
        ?: fallbackUrl
    val cleaned = candidate
        .removePrefix("Excerpt:")
        .trim()
        .trim('"')
        .trim()
        .ifBlank { "Shared excerpt" }
    val snippet = cleaned.take(96).let { if (it.length < cleaned.length) "$it…" else it }
    return "Excerpt: \"$snippet\""
}

private fun String.startsWithHttp(): Boolean {
    return startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}

private fun String.normalizedSourceLabel(sourceType: String?): String? {
    val cleaned = trim()
    if (cleaned.isEmpty()) return null
    val normalizedType = sourceType?.trim()?.lowercase()
    if (normalizedType in setOf("list_feed", "feed_generator")) {
        val withoutPrefix = cleaned.removePrefix("Pinned:").trim()
        val nameCandidate = withoutPrefix
            .removeRawAddressSuffix("at://")
            .removeRawAddressSuffix("http://")
            .removeRawAddressSuffix("https://")
            .trim()
            .trim('(', ')', '-', '·', '|')
            .trim()
        return nameCandidate.takeIf { it.isNotBlank() && !it.startsWithRawAddress() }
            ?: withoutPrefix.takeIf { it.isNotBlank() }
    }
    return cleaned
}

private fun String.removeRawAddressSuffix(marker: String): String {
    val index = indexOf(marker, ignoreCase = true)
    return if (index > 0) substring(0, index) else this
}

private fun String.startsWithRawAddress(): Boolean =
    startsWith("at://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)

private fun extractHost(url: String): String? {
    return runCatching { URI(url).host }.getOrNull()
}

private fun isSyntheticSharedTextUrl(url: String): Boolean {
    if (!url.startsWithHttp()) return false
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "shared-text.mimeo.local"
}
