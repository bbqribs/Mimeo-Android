package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
// Light turquoise is tuned to clear 4.5:1 against the light drawer surface so it is
// safe for normal-weight label text (the drawer playlist titles), not just icons.
private val PlaylistsTurquoiseLight = Color(0xFF0C7480)
private val PlaylistsTurquoiseDark = Color(0xFF5CD0D8)

/**
 * Official-ish Bluesky brand blue. Used in both light and dark mode: it clears the 3:1
 * contrast bar for large titles and graphical/icon content against both surfaces.
 */
internal val BlueskyBrandBlue = Color(0xFF1185FE)

/**
 * Minimum WCAG contrast ratio the PRIMARY (app-accent) destination label must clear against
 * its surface. 4.5:1 is the AA bar for normal-weight body text, which the small drawer labels
 * use. Some schemes (notably Ember light, a burnt orange at ~4.1:1 on the near-white drawer)
 * land just under this; [contrastSafePrimary] nudges only those into range.
 */
private const val MinPrimaryContrast = 4.5f

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * Returns [primary] unchanged when it already clears [MinPrimaryContrast] against [surface]
 * (the common case — e.g. Lilac default), otherwise blends it toward black (on light surfaces)
 * or white (on dark surfaces) just far enough to meet the bar. Blending toward black/white
 * preserves the accent hue, so Ember stays recognisably ember, only deeper.
 */
internal fun contrastSafePrimary(primary: Color, surface: Color): Color {
    if (contrastRatio(primary, surface) >= MinPrimaryContrast) return primary
    val target = if (surface.luminance() >= 0.5f) Color.Black else Color.White
    var t = 0.05f
    while (t <= 1f) {
        val candidate = lerp(primary, target, t)
        if (contrastRatio(candidate, surface) >= MinPrimaryContrast) return candidate
        t += 0.05f
    }
    return target
}

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
 *
 * [surface] is the actual background the accent is drawn on. When supplied, the PRIMARY
 * (app-accent) role is run through [contrastSafePrimary] so borderline schemes (Ember light)
 * stay legible; when null the raw [primary] is returned unchanged (back-compat default).
 */
internal fun destinationAccentColor(
    route: String,
    neutral: Color,
    primary: Color,
    darkSurface: Boolean,
    surface: Color? = null,
): Color = when (destinationAccentRole(route)) {
    DestinationAccentRole.NEUTRAL -> neutral
    DestinationAccentRole.PRIMARY -> if (surface != null) contrastSafePrimary(primary, surface) else primary
    DestinationAccentRole.SMART_QUEUE -> if (darkSurface) SmartQueueGreenDark else SmartQueueGreenLight
    DestinationAccentRole.PLAYLISTS -> if (darkSurface) PlaylistsTurquoiseDark else PlaylistsTurquoiseLight
    DestinationAccentRole.BLUESKY -> BlueskyBrandBlue
}
