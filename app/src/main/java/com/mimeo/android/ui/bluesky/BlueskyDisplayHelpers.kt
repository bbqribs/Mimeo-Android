package com.mimeo.android.ui.bluesky

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
