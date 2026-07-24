package com.mimeo.android.ui.common

/**
 * Presentation helpers for the discreet sync indicator beneath the drawer's account block.
 *
 * The indicator is always present; the *icon* carries connected/offline state and the *text*
 * carries freshness. Keeping it visible in both states means going offline reads as a change in
 * something familiar rather than a new element appearing, and the sync age stays useful online.
 *
 * Contains no titles, URLs or tokens — a relative time only.
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
 * The indicator's text, shown identically whether online or offline.
 *
 * Keeps the "Last sync:" prefix rather than a bare age — a naked "3h ago" invites "3h ago *what*?".
 * An account that has never completed a sync has no age to report and says so instead.
 */
internal fun syncIndicatorLabel(lastSyncAtMs: Long?, nowMs: Long): String {
    val age = formatLastSyncAge(lastSyncAtMs, nowMs) ?: return "Never synced"
    return "Last sync: $age"
}

/**
 * Screen-reader description for the indicator icon.
 *
 * The icon is the only visual carrier of connected/offline state, so it must be announced —
 * otherwise a TalkBack user hears the age with no indication of connectivity at all.
 */
internal fun syncIndicatorStateDescription(isOffline: Boolean): String =
    if (isOffline) "Offline" else "Connected"
