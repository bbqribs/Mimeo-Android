package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyCandidatePostContext

/**
 * The user-facing pieces of a Bluesky post excerpt rendered by [BlueskyPostPreviewCard].
 * The display name and handle are kept separate so the card can style them like Bluesky
 * does (bold name, muted handle). Every field is already sanitized for ordinary surfaces:
 * no raw `at://` URIs, DIDs, or other backend identifiers survive into these strings,
 * [handle] never carries a leading `@`, and [postUrl] is only populated when it is a safe
 * http(s) link.
 */
internal data class BlueskyPostPreview(
    val displayName: String?,
    val handle: String?,
    val timestamp: String?,
    val snippet: String?,
    val postUrl: String?,
)

/**
 * Builds a sanitized [BlueskyPostPreview] from the candidate's post context, or null when
 * there is nothing meaningful to show. A preview is only worth rendering when there is real
 * post content — an author (name or handle) or post text. A bare timestamp or link with no
 * attribution/snippet is not enough to justify a post box, so it degrades to null.
 */
internal fun buildBlueskyPostPreview(post: BlueskyCandidatePostContext): BlueskyPostPreview? {
    val displayName = sanitizeBlueskyDisplayName(post.authorDisplayName)
    val handle = sanitizeBlueskyHandle(post.authorHandle)
    val snippet = sanitizeBlueskyText(post.textSnippet)
    if (displayName == null && handle == null && snippet == null) return null
    val timestamp = post.indexedAt
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { formatCandidateTimestamp(it) }
    val postUrl = post.postUrl?.trim()?.takeIf { isSafeWebUrl(it) }
    return BlueskyPostPreview(
        displayName = displayName,
        handle = handle,
        timestamp = timestamp,
        snippet = snippet,
        postUrl = postUrl,
    )
}

/**
 * Returns the author display name, or null when blank or carrying a raw identifier (an
 * `at://` URI or `did:` value never belongs on an ordinary card).
 */
internal fun sanitizeBlueskyDisplayName(displayName: String?): String? =
    displayName?.trim()?.takeIf { it.isNotBlank() && !containsRawIdentifier(it) }

/**
 * Returns the author handle without a leading `@`, or null when blank, carrying a raw
 * identifier, or containing whitespace (a real Bluesky handle never has spaces, so an
 * embedded space signals an untrustworthy value).
 */
internal fun sanitizeBlueskyHandle(handle: String?): String? =
    handle?.trim()?.trimStart('@')
        ?.takeIf { it.isNotBlank() && !containsRawIdentifier(it) && it.none(Char::isWhitespace) }

/**
 * Strips raw `at://` URIs and bare `did:` identifiers out of post text so they never leak
 * onto ordinary cards, then collapses the whitespace they leave behind. Returns null when
 * nothing readable remains. This is a defensive scrub, not a rich-text/link parser:
 * mentions, hashtags, and plain links already present in the text pass through unchanged.
 */
internal fun sanitizeBlueskyText(text: String?): String? {
    val raw = text?.takeIf { it.isNotBlank() } ?: return null
    val scrubbed = raw
        .replace(Regex("at://\\S+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("did:[A-Za-z0-9:._-]+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
    return scrubbed.takeIf { it.isNotBlank() }
}

private fun containsRawIdentifier(value: String): Boolean {
    val lower = value.lowercase()
    return lower.contains("at://") || lower.contains("did:")
}

private fun isSafeWebUrl(url: String): Boolean {
    val lower = url.lowercase()
    return (lower.startsWith("http://") || lower.startsWith("https://")) && !lower.contains("at://")
}

internal fun cleanSourceLabel(label: String, sourceType: String?): String {
    val cleaned = label.trim()
    if (sourceType == "list_feed") {
        val withoutPrefix = cleaned.removePrefix("Pinned:").trim()
        val hasRawAddress = withoutPrefix.contains("at://", ignoreCase = true) ||
            withoutPrefix.contains("://", ignoreCase = true)
        if (hasRawAddress) return "Bluesky list"
    } else if (sourceType == "feed_generator") {
        val hasRawAddress = cleaned.contains("at://", ignoreCase = true) ||
            cleaned.contains("://", ignoreCase = true)
        if (hasRawAddress) return "Bluesky feed"
    } else if (cleaned.startsWith("at://", ignoreCase = true)) {
        return formatSourceType(sourceType)
    }
    return cleaned.takeIf { it.isNotBlank() } ?: formatSourceType(sourceType)
}

/**
 * Picks the best human-facing label for the picker header. The backend scan can return a
 * raw AT-URI as a list's `displayLabel` (which [cleanSourceLabel] would then collapse to a
 * generic "Bluesky List"). In that case the label the user picked from the source list —
 * which carries the real list/feed name — is preferred. Feeds and accounts already resolve
 * to a proper name in the scan response, so this keeps using the scan label for them.
 */
internal fun resolvePickerSourceLabel(
    scanLabel: String?,
    scanType: String?,
    selectedLabel: String?,
    selectedKind: String?,
): String {
    val type = scanType ?: selectedKind
    val scan = scanLabel?.trim()?.takeIf { it.isNotBlank() }
    val selected = selectedLabel?.trim()?.takeIf { it.isNotBlank() }
    val scanIsRaw = scan != null && scan.contains("://", ignoreCase = true)
    val selectedIsRaw = selected != null && selected.contains("://", ignoreCase = true)
    val preferred = when {
        scan != null && !scanIsRaw -> scan
        selected != null && !selectedIsRaw -> selected
        else -> scan ?: selected ?: "Selected source"
    }
    return cleanSourceLabel(preferred, type)
}

internal fun formatSourceType(sourceType: String?): String = when (sourceType) {
    "home_timeline" -> "Home Timeline"
    "list_feed" -> "List"
    "author_feed", "account" -> "Account"
    else -> "Bluesky"
}

internal fun blueskySourceDisplayName(resolvedName: String, typeLabel: String?): String {
    val candidate = resolvedName.trim()
    if (candidate.startsWith("at://", ignoreCase = true)) {
        return when {
            typeLabel?.contains("feed", ignoreCase = true) == true -> "Bluesky feed"
            typeLabel?.contains("list", ignoreCase = true) == true -> "Bluesky list"
            else -> "Bluesky Source"
        }
    }
    return candidate
}

/**
 * Strips raw AT-URIs (at://…) out of a possibly operator-formatted source label so
 * user-facing surfaces (player title/marquee, source chips) never show them. Keeps a
 * human prefix when present ("Bluesky List: at://…" → "Bluesky List") and otherwise
 * falls back to a generic Bluesky label. Non-AT labels (domains, titles) pass through
 * unchanged. Returns null/blank inputs as null.
 */
internal fun sanitizeUserFacingSourceLabel(label: String?): String? {
    val raw = label?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val atIndex = raw.indexOf("at://", ignoreCase = true)
    if (atIndex < 0) return raw
    val prefix = raw.substring(0, atIndex).trim().trimEnd(':', '-', '·', '—', '/', ' ').trim()
    return when {
        prefix.isNotBlank() -> prefix
        raw.contains("feed", ignoreCase = true) -> "Bluesky feed"
        raw.contains("list", ignoreCase = true) -> "Bluesky list"
        else -> "Bluesky"
    }
}

internal fun formatCandidateTimestamp(iso: String): String {
    return runCatching {
        val normalized = iso.replace("Z", "+00:00")
        val dt = java.time.OffsetDateTime.parse(normalized)
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val hours = java.time.Duration.between(dt, now).toHours()
        when {
            hours < 1 -> "just now"
            hours < 24 -> "${hours}h ago"
            hours < 24 * 7 -> "${hours / 24}d ago"
            else -> dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    }.getOrDefault(iso.take(10))
}
