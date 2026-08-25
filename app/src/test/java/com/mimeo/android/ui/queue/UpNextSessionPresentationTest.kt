package com.mimeo.android.ui.queue

import com.mimeo.android.model.UpNextHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextSessionPresentationTest {
    @Test
    fun staticRowLifecycleActionsOfferArchiveOrUnarchiveAndBin() {
        assertEquals(
            listOf(SessionLifecycleAction.Archive, SessionLifecycleAction.MoveToBin),
            sessionLifecycleActionOrder(
                isArchived = false,
                canArchive = true,
                canUnarchive = true,
                canMoveToBin = true,
            ),
        )
        assertEquals(
            listOf(SessionLifecycleAction.Unarchive, SessionLifecycleAction.MoveToBin),
            sessionLifecycleActionOrder(
                isArchived = true,
                canArchive = true,
                canUnarchive = true,
                canMoveToBin = true,
            ),
        )
    }

    @Test
    fun nowPlayingHeadingCopyIsPlainNowPlaying() {
        assertEquals("Now Playing", NOW_PLAYING_SECTION_TITLE)
    }

    @Test
    fun missingActiveItemLeavesNoEarlierRows() {
        assertEquals(
            emptyList<Int>(),
            sessionPanelEarlierItems(localItems = listOf(7, 8), currentIndex = -1),
        )
    }

    @Test
    fun presentActiveItemKeepsOnlyPrecedingRowsEarlier() {
        assertEquals(
            listOf(7, 8),
            sessionPanelEarlierItems(localItems = listOf(7, 8, 9), currentIndex = 2),
        )
    }

    @Test
    fun historyDisplaysOldestAtTopAndNewestAtBottom() {
        assertEquals(
            listOf(7, 8, 9),
            sessionPanelHistoryItems(historyItems = listOf(9, 8, 7)),
        )
    }

    @Test
    fun canonicalHistoryKeepsRepeatedItemsAndProjectionOrder() {
        val rows = listOf(
            historyEntry(itemId = 7, playedAt = "2026-08-24T10:00:00Z", stillInSession = true),
            historyEntry(itemId = 7, playedAt = "2026-08-24T11:00:00Z", stillInSession = false),
        ).map { it.toSessionHistoryPresentationRow() }

        assertEquals(listOf(7, 7), rows.map { it.item.itemId })
        assertEquals(
            listOf("2026-08-24T10:00:00Z", "2026-08-24T11:00:00Z"),
            rows.map { it.playedAt },
        )
        assertTrue(rows.first().stillInSession)
        assertFalse(rows.last().stillInSession)
    }

    @Test
    fun historyCopyIsTruthfulAboutRecordingBoundaryAndBoundedPage() {
        assertEquals(
            "No History entries yet — recording since 2026-08-24T00:00:00Z.",
            historyEmptyCopy("2026-08-24T00:00:00Z"),
        )
        assertEquals(
            "Only the 50 most recent History entries are shown.",
            historyBoundedCopy(hasMore = true),
        )
        assertEquals(null, historyBoundedCopy(hasMore = false))
    }

    @Test
    fun explicitlyQueuedArchivedItemRemainsInUpNextPresentation() {
        assertEquals(
            listOf(20, 30),
            sessionPanelUpcomingItems(
                localItems = listOf(10, 20, 30),
                currentIndex = 0,
            ),
        )
    }

    @Test
    fun authoritativeArchiveRefreshPreservesOptimisticLocalOrder() {
        val presented = sessionPanelPresentationItems(
            localItems = listOf(20 to false, 10 to false),
            authoritativeItems = listOf(10 to false, 20 to true),
            itemKey = { it.first },
        )

        assertEquals(listOf(20 to true, 10 to false), presented)
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
    fun stickyHistoryHeaderStaysPinnedUntilSectionRowsLeave() {
        val presentation = activeSessionStickyHeader(
            scrollOffsetPx = 80,
            sections = listOf(
                SessionStickyHeaderBounds(
                    title = "History",
                    count = 2,
                    topPx = 0f,
                    headerHeightPx = 40f,
                    bottomPx = 180f,
                ),
            ),
        )

        assertEquals("History", presentation?.title)
        assertEquals(0f, presentation?.offsetYPx)
    }

    @Test
    fun stickyHeaderPushesAwayAtSectionEnd() {
        val presentation = activeSessionStickyHeader(
            scrollOffsetPx = 160,
            sections = listOf(
                SessionStickyHeaderBounds(
                    title = "Earlier in queue",
                    count = 1,
                    topPx = 0f,
                    headerHeightPx = 40f,
                    bottomPx = 180f,
                ),
            ),
        )

        assertEquals("Earlier in queue", presentation?.title)
        assertEquals(-20f, presentation?.offsetYPx)
    }

    @Test
    fun activeAnchorSpacerOnlyAppearsWhenRowsPrecedeNowPlaying() {
        assertEquals(
            0f,
            activeAnchorTailSpacerPx(
                hasRowsBeforeActive = false,
                viewportHeightPx = 640,
                activeHeightPx = 180f,
                belowActiveContentHeightPx = 80f,
            ),
        )
        assertEquals(
            380f,
            activeAnchorTailSpacerPx(
                hasRowsBeforeActive = true,
                viewportHeightPx = 640,
                activeHeightPx = 180f,
                belowActiveContentHeightPx = 80f,
            ),
        )
    }

    @Test
    fun activeAnchorSpacerNeverGoesNegativeWhenBelowContentFillsViewport() {
        assertEquals(
            0f,
            activeAnchorTailSpacerPx(
                hasRowsBeforeActive = true,
                viewportHeightPx = 360,
                activeHeightPx = 180f,
                belowActiveContentHeightPx = 320f,
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


    private fun historyEntry(
        itemId: Int,
        playedAt: String,
        stillInSession: Boolean,
    ) = UpNextHistoryEntry(
        itemId = itemId,
        playedAt = playedAt,
        title = "Item $itemId",
        url = "https://example.com/$itemId",
        host = "example.com",
        status = "ready",
        hasActiveContent = true,
        createdAt = "2026-07-17T10:00:00Z",
        isArchived = false,
        isMuted = false,
        stillInSession = stillInSession,
    )
}
