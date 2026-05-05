package com.mimeo.android.ui.bluesky

data class BlueskyListInputResult(
    val uri: String? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = uri != null
}

fun normalizeBlueskyHandleInput(value: String): String? {
    val cleaned = value.trim().trimStart('@')
    return cleaned.takeIf { it.isNotBlank() }
}

fun parseBlueskyListIdentifierInput(value: String): BlueskyListInputResult {
    val cleaned = value.trim()
    if (cleaned.isBlank()) {
        return BlueskyListInputResult(error = "List URL or AT-URI is required.")
    }
    if (Regex("^https?://bsky[.]app/profile/[^/?#]+/feed/[^/?#]+/?$", RegexOption.IGNORE_CASE).matches(cleaned) ||
        cleaned.contains("/app.bsky.feed.generator/", ignoreCase = true)
    ) {
        return BlueskyListInputResult(error = "Feed generators are not supported yet.")
    }
    if (Regex("^at://[^/]+/app[.]bsky[.]graph[.]list/[^/\\s]+$", RegexOption.IGNORE_CASE).matches(cleaned)) {
        return BlueskyListInputResult(uri = cleaned)
    }
    val listUrlMatch = Regex("^https?://bsky[.]app/profile/([^/?#]+)/lists/([^/?#]+)/?$", RegexOption.IGNORE_CASE)
        .matchEntire(cleaned)
    if (listUrlMatch != null) {
        val (profile, listId) = listUrlMatch.destructured
        return BlueskyListInputResult(uri = "at://$profile/app.bsky.graph.list/$listId")
    }
    return BlueskyListInputResult(error = "Use a bsky.app list URL or an at:// list URI.")
}
