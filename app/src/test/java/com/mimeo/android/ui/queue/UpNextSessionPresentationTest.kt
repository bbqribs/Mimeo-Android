package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextSessionPresentationTest {
    @Test
    fun nowPlayingHeadingCopyIsPlainNowPlaying() {
        assertEquals("Now Playing", NOW_PLAYING_SECTION_TITLE)
    }

    @Test
    fun initialScrollTargetUsesMeasuredNowPlayingTop() {
        assertEquals(480, nowPlayingScrollTargetPx(480.8f))
    }

    @Test
    fun jumpPillShowsWhenScrolledAwayFromNowPlayingAnchor() {
        assertFalse(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 400,
                activeTopOffsetPx = 410f,
                anchorTolerancePx = 24f,
            ),
        )
        assertTrue(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 80,
                activeTopOffsetPx = 410f,
                anchorTolerancePx = 24f,
            ),
        )
        assertTrue(
            shouldShowJumpToNowPlayingPill(
                scrollOffsetPx = 620,
                activeTopOffsetPx = 410f,
                anchorTolerancePx = 24f,
            ),
        )
    }

    @Test
    fun rowTrailingActionOrderPlacesJumpImmediatelyBeforeRemove() {
        assertEquals(
            listOf(SessionRowAction.JumpPlay, SessionRowAction.Remove),
            sessionRowTrailingActionOrder(showJumpPlay = true, showRemove = true),
        )
    }
}
