package com.mimeo.android.ui.common

/**
 * Presentation helpers for the discreet offline / last-sync indicator.
 *
 * Deliberately terse: the indicator is a single muted line in the drawer footer, shown only while
 * offline, so the user can tell at a glance that what they are looking at is a saved copy rather
 * than live data. Contains no titles, URLs or tokens — a relative time only.
 */

/** Formats a relative age like "just now", "3m ago", "5h ago", "2d ago". */
internal fun formatLastSyncAge(lastSyncAtMs: Long?, nowMs: Long): String? {
    if (lastSyncAtMs == null || lastSyncAtMs <= 0L) return null
    val elapsedMs = nowMs - lastSyncAtMs
    // A clock skew / future timestamp reads as "just now" rather than a negative age.
    if (elapsedMs < 0L) return "just now"
    val minutes = elapsedMs / 60_000L
    if (minutes < 1L) return "just now"
    if (minutes < 60L) return "${minutes}m ago"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}h ago"
    val days = hours / 24L
    return "${days}d ago"
}

/**
 * Full indicator line, or null when nothing should be shown.
 *
 * Only rendered while [isOffline]; when online the drawer stays clean. If the account has never
 * synced there is no age to report, so the line degrades to a bare "Offline".
 */
internal fun offlineIndicatorLabel(
    isOffline: Boolean,
    lastSyncAtMs: Long?,
    nowMs: Long,
): String? {
    if (!isOffline) return null
    val age = formatLastSyncAge(lastSyncAtMs, nowMs) ?: return "Offline"
    return "Offline · Last sync: $age"
}
