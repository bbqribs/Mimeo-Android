package com.mimeo.android.ui.bluesky

internal fun cleanSourceLabel(label: String, sourceType: String?): String {
    val cleaned = label.trim()
    if (sourceType == "list_feed") {
        val withoutPrefix = cleaned.removePrefix("Pinned:").trim()
        val hasRawAddress = withoutPrefix.contains("at://", ignoreCase = true) ||
            withoutPrefix.contains("://", ignoreCase = true)
        if (hasRawAddress) return "Bluesky List"
    } else if (sourceType == "feed_generator") {
        val hasRawAddress = cleaned.contains("at://", ignoreCase = true) ||
            cleaned.contains("://", ignoreCase = true)
        if (hasRawAddress) return "Bluesky Feed"
    } else if (cleaned.startsWith("at://", ignoreCase = true)) {
        return formatSourceType(sourceType)
    }
    return cleaned.takeIf { it.isNotBlank() } ?: formatSourceType(sourceType)
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
            typeLabel?.contains("feed", ignoreCase = true) == true -> "Bluesky Feed"
            typeLabel?.contains("list", ignoreCase = true) == true -> "Bluesky List"
            else -> "Bluesky Source"
        }
    }
    return candidate
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
