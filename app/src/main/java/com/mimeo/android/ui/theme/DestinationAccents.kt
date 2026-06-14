package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.mimeo.android.ROUTE_BLUESKY_BROWSE
import com.mimeo.android.ROUTE_SMART_QUEUE
import com.mimeo.android.ROUTE_UP_NEXT
import com.mimeo.android.ROUTE_SETTINGS

/**
 * Lightweight per-destination accent mapping for screen titles and drawer icons.
 *
 * This is intentionally NOT a per-screen theme system: it only assigns a single accent
 * colour to a destination so the title and the drawer icon/selected-state can carry a
 * subtle identity. Screen backgrounds, cards, typography, navigation, and Material theme
 * generation are untouched and the selected app theme stays intact.
 *
 * Most library destinations (Inbox/Favorites/Archive/Bin) intentionally resolve to the
 * neutral high-contrast foreground rather than a literal colour, which keeps the drawer
 * calm and preserves readability/contrast in both light and dark mode.
 */
internal enum class DestinationAccentRole {
    /** High-contrast neutral foreground. Library / Inbox / Favorites / Archive / Bin. */
    NEUTRAL,

    /** App primary/lilac accent. Up Next / Now Playing, Settings (incl. AI Summaries). */
    PRIMARY,

    /** Distinct green. Smart Queue. */
    SMART_QUEUE,

    /** Distinct turquoise. Playlists and Smart Playlists. */
    PLAYLISTS,

    /** Official-ish Bluesky blue. */
    BLUESKY,
}

/**
 * Contrast-tuned custom accents. Library/primary destinations reuse existing Material/token
 * colours instead, so these are the only bespoke colours introduced.
 */
private val SmartQueueGreenLight = Color(0xFF2E7A4F)
private val SmartQueueGreenDark = Color(0xFF72C99A)
private val PlaylistsTurquoiseLight = Color(0xFF0E7C86)
private val PlaylistsTurquoiseDark = Color(0xFF5CD0D8)

/**
 * Official-ish Bluesky brand blue. Used in both light and dark mode: it clears the 3:1
 * contrast bar for large titles and graphical/icon content against both surfaces.
 */
internal val BlueskyBrandBlue = Color(0xFF1185FE)

/** Maps a navigation route (or selected-drawer route) to its accent role. */
internal fun destinationAccentRole(route: String): DestinationAccentRole = when {
    // Diagnostics resolves to the Settings drawer route upstream; its own screen body
    // keeps the neutral default styling the ticket calls for.
    route.startsWith(ROUTE_SETTINGS) -> DestinationAccentRole.PRIMARY
    route.startsWith("smartPlaylist/") -> DestinationAccentRole.PLAYLISTS
    route.startsWith("playlist/") -> DestinationAccentRole.PLAYLISTS
    route.startsWith(ROUTE_SMART_QUEUE) -> DestinationAccentRole.SMART_QUEUE
    route.startsWith(ROUTE_UP_NEXT) -> DestinationAccentRole.PRIMARY
    route.startsWith(ROUTE_BLUESKY_BROWSE) -> DestinationAccentRole.BLUESKY
    else -> DestinationAccentRole.NEUTRAL
}

/**
 * Resolves the accent colour for [route].
 *
 * [neutral], [primary], and [muted] are supplied by the caller from the active theme
 * (token-based V1 or Material legacy) so the mapping never reaches into a specific theme.
 * [darkSurface] selects the contrast-appropriate variant of the bespoke colours.
 */
internal fun destinationAccentColor(
    route: String,
    neutral: Color,
    primary: Color,
    darkSurface: Boolean,
): Color = when (destinationAccentRole(route)) {
    DestinationAccentRole.NEUTRAL -> neutral
    DestinationAccentRole.PRIMARY -> primary
    DestinationAccentRole.SMART_QUEUE -> if (darkSurface) SmartQueueGreenDark else SmartQueueGreenLight
    DestinationAccentRole.PLAYLISTS -> if (darkSurface) PlaylistsTurquoiseDark else PlaylistsTurquoiseLight
    DestinationAccentRole.BLUESKY -> BlueskyBrandBlue
}
