package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mimeo.android.ROUTE_ARCHIVE
import com.mimeo.android.ROUTE_BIN
import com.mimeo.android.ROUTE_BLUESKY_BROWSE
import com.mimeo.android.ROUTE_FAVORITES
import com.mimeo.android.ROUTE_INBOX
import com.mimeo.android.ROUTE_SETTINGS
import com.mimeo.android.ROUTE_SETTINGS_DIAGNOSTICS
import com.mimeo.android.ROUTE_SMART_QUEUE
import com.mimeo.android.ROUTE_UP_NEXT
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationAccentsTest {

    private val neutral = Color(0xFF111111)
    private val primary = Color(0xFF6B49CC)

    private fun accent(route: String, dark: Boolean) =
        destinationAccentColor(route, neutral = neutral, primary = primary, darkSurface = dark)

    @Test
    fun libraryDestinations_mapToNeutralRole() {
        for (route in listOf(ROUTE_INBOX, ROUTE_FAVORITES, ROUTE_ARCHIVE, ROUTE_BIN)) {
            assertEquals(DestinationAccentRole.NEUTRAL, destinationAccentRole(route))
        }
    }

    @Test
    fun smartQueue_mapsToSmartQueueRole() {
        assertEquals(DestinationAccentRole.SMART_QUEUE, destinationAccentRole(ROUTE_SMART_QUEUE))
    }

    @Test
    fun upNext_mapsToPrimaryRole() {
        assertEquals(DestinationAccentRole.PRIMARY, destinationAccentRole(ROUTE_UP_NEXT))
    }

    @Test
    fun bluesky_mapsToBlueskyRole() {
        assertEquals(DestinationAccentRole.BLUESKY, destinationAccentRole(ROUTE_BLUESKY_BROWSE))
    }

    @Test
    fun playlistAndSmartPlaylistRoutes_mapToPlaylistsRole() {
        assertEquals(DestinationAccentRole.PLAYLISTS, destinationAccentRole("playlist/12"))
        assertEquals(DestinationAccentRole.PLAYLISTS, destinationAccentRole("smartPlaylist/7"))
    }

    @Test
    fun settingsAndDiagnostics_mapToPrimaryRole() {
        // Diagnostics resolves to the Settings drawer route upstream.
        assertEquals(DestinationAccentRole.PRIMARY, destinationAccentRole(ROUTE_SETTINGS))
        assertEquals(DestinationAccentRole.PRIMARY, destinationAccentRole(ROUTE_SETTINGS_DIAGNOSTICS))
    }

    @Test
    fun neutralDestinations_resolveToSuppliedNeutral_keepingThemeIntact() {
        assertEquals(neutral, accent(ROUTE_INBOX, dark = false))
        assertEquals(neutral, accent(ROUTE_ARCHIVE, dark = true))
    }

    @Test
    fun primaryDestinations_resolveToSuppliedPrimary() {
        assertEquals(primary, accent(ROUTE_UP_NEXT, dark = false))
        assertEquals(primary, accent(ROUTE_SETTINGS, dark = true))
    }

    @Test
    fun smartQueueAndPlaylists_useDistinctLightAndDarkVariants() {
        assert(accent(ROUTE_SMART_QUEUE, dark = false) != accent(ROUTE_SMART_QUEUE, dark = true))
        assert(accent("playlist/1", dark = false) != accent("playlist/1", dark = true))
    }

    @Test
    fun bluesky_usesBrandBlueInBothModes() {
        assertEquals(BlueskyBrandBlue, accent(ROUTE_BLUESKY_BROWSE, dark = false))
        assertEquals(BlueskyBrandBlue, accent(ROUTE_BLUESKY_BROWSE, dark = true))
    }

    // --- VIS-2: contrast-aware PRIMARY mapping ---

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val emberLightAccent = Color(0xFFC25B2E)
    private val lightDrawerSurface = Color(0xFFFFFCF6)

    @Test
    fun primary_withoutSurface_returnsRawAccent_preservingBackCompat() {
        // No surface supplied => mapping is untouched (existing call sites / Lilac default).
        assertEquals(
            emberLightAccent,
            destinationAccentColor(ROUTE_UP_NEXT, neutral, emberLightAccent, darkSurface = false),
        )
    }

    @Test
    fun primary_emberLightOnLightSurface_isAdjustedToMeetContrast() {
        val raw = contrastRatio(emberLightAccent, lightDrawerSurface)
        // Precondition: the carried-forward issue — Ember light is borderline (< 4.5:1).
        assert(raw < 4.5f)
        val adjusted = destinationAccentColor(
            ROUTE_UP_NEXT, neutral, emberLightAccent, darkSurface = false, surface = lightDrawerSurface,
        )
        assert(adjusted != emberLightAccent)
        assert(contrastRatio(adjusted, lightDrawerSurface) >= 4.5f)
    }

    @Test
    fun primary_lilacLightOnLightSurface_isUnchanged_preservingDefault() {
        // Lilac already clears the bar, so it must pass through untouched.
        val lilac = Color(0xFF6B49CC)
        assert(contrastRatio(lilac, lightDrawerSurface) >= 4.5f)
        assertEquals(
            lilac,
            destinationAccentColor(
                ROUTE_SETTINGS, neutral, lilac, darkSurface = false, surface = lightDrawerSurface,
            ),
        )
    }

    @Test
    fun primary_adjustmentAppliesToSettingsToo() {
        val adjusted = destinationAccentColor(
            ROUTE_SETTINGS, neutral, emberLightAccent, darkSurface = false, surface = lightDrawerSurface,
        )
        assert(contrastRatio(adjusted, lightDrawerSurface) >= 4.5f)
    }

    @Test
    fun primary_nonPrimaryRolesIgnoreSurface() {
        // Smart Queue / Bluesky must not be touched by the contrast-safe primary path.
        assertEquals(
            BlueskyBrandBlue,
            destinationAccentColor(
                ROUTE_BLUESKY_BROWSE, neutral, emberLightAccent, darkSurface = false, surface = lightDrawerSurface,
            ),
        )
    }
}
