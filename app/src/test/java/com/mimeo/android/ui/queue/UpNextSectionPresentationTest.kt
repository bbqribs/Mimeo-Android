package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextSectionPresentationTest {

    // -----------------------------------------------------------------------------------------
    // NOW_PLAYING_SECTION_TITLE — regression guard on the heading copy
    // -----------------------------------------------------------------------------------------

    @Test
    fun `NOW_PLAYING_SECTION_TITLE is exactly Now Playing`() {
        assertEquals("Now Playing", NOW_PLAYING_SECTION_TITLE)
    }

    // -----------------------------------------------------------------------------------------
    // shouldShowJumpToNowPlayingPill
    // -----------------------------------------------------------------------------------------

    @Test
    fun `pill hidden when activeTopOffset is null`() {
        assertFalse(
            shouldShowJumpToNowPlayingPill(scrollOffsetPx = 300, activeTopOffsetPx = null),
        )
    }

    @Test
    fun `pill hidden when scroll is at the active item (within tolerance)`() {
        assertFalse(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 300,
                activeTopOffsetPx = 302f, // 2px diff < default 24px tolerance
            ),
        )
    }

    @Test
    fun `pill hidden when scroll exactly matches active item`() {
        assertFalse(
            shouldShowJumpToNowPlayingPill(scrollOffsetPx = 400, activeTopOffsetPx = 400f),
        )
    }

    @Test
    fun `pill shown when scrolled far below active item`() {
        assertTrue(
            shouldShowJumpToNowPlayingPill(scrollOffsetPx = 0, activeTopOffsetPx = 500f),
        )
    }

    @Test
    fun `pill shown when scrolled far above active item`() {
        assertTrue(
            shouldShowJumpToNowPlayingPill(scrollOffsetPx = 900, activeTopOffsetPx = 300f),
        )
    }

    @Test
    fun `custom tolerance tighter than offset shows pill`() {
        assertTrue(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 100,
                activeTopOffsetPx = 200f,
                anchorTolerancePx = 50f, // 100px diff > 50px tolerance
            ),
        )
    }

    @Test
    fun `custom tolerance wider than offset hides pill`() {
        assertFalse(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 100,
                activeTopOffsetPx = 120f,
                anchorTolerancePx = 50f, // 20px diff < 50px tolerance
            ),
        )
    }

    // -----------------------------------------------------------------------------------------
    // nowPlayingScrollTargetPx
    // -----------------------------------------------------------------------------------------

    @Test
    fun `scroll target is null when no active offset`() {
        assertNull(nowPlayingScrollTargetPx(null))
    }

    @Test
    fun `scroll target matches active offset truncated to int`() {
        assertEquals(342, nowPlayingScrollTargetPx(342.9f))
    }
}
