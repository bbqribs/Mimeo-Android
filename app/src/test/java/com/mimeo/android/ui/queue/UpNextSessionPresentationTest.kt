package com.mimeo.android.ui.queue

import com.mimeo.android.projectHistoryArchiveState
import com.mimeo.android.model.UpNextHistoryEntry
import com.mimeo.android.model.UpNextHistoryProjection
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
    fun canonicalHistoryKeepsServerProjectionOrderWithoutClientSorting() {
        val rows = listOf(
            historyEntry(itemId = 8, playedAt = "2026-08-24T10:00:00Z", stillInSession = true),
            historyEntry(itemId = 7, playedAt = "2026-08-24T11:00:00Z", stillInSession = false),
        ).map { it.toSessionHistoryPresentationRow() }

        assertEquals(listOf(8, 7), rows.map { it.item.itemId })
        assertEquals(
            listOf("2026-08-24T10:00:00Z", "2026-08-24T11:00:00Z"),
            rows.map { it.playedAt },
        )
        assertTrue(rows.first().stillInSession)
        assertFalse(rows.last().stillInSession)
    }

    @Test
    fun canonicalHistoryArchiveStateUpdatesEveryOccurrenceOnlyForThatItem() {
        val projection = UpNextHistoryProjection(
            entries = listOf(
                historyEntry(itemId = 7, playedAt = "2026-08-24T10:00:00Z", stillInSession = true),
                historyEntry(itemId = 8, playedAt = "2026-08-24T10:30:00Z", stillInSession = false),
                historyEntry(itemId = 9, playedAt = "2026-08-24T11:00:00Z", stillInSession = false),
            ),
            hasMore = false,
            recordingSince = "2026-08-24T00:00:00Z",
            snapshotThroughEntryId = 9,
            configuredLimit = 10,
            effectiveLimit = 10,
        )

        val archived = projectHistoryArchiveState(projection, itemId = 7, archived = true)

        assertEquals(listOf(true, false, false), archived.entries.map { it.isArchived })
        assertEquals(projection.entries.map { it.playedAt }, archived.entries.map { it.playedAt })
    }

    @Test
    fun historyBatchActionsUseArticleIdentityAndCurrentArchiveState() {
        val selectedIds = setOf(7, 8)
        val archivedByItemId = mapOf(7 to false, 8 to true)

        assertEquals(
            setOf(7),
            selectedSessionArchiveActionIds(selectedIds, archivedByItemId, archive = true),
        )
        assertEquals(
            setOf(8),
            selectedSessionArchiveActionIds(selectedIds, archivedByItemId, archive = false),
        )
    }

    @Test
    fun clearQueueCopyNamesItsScopeAndRetainedHistory() {
        assertEquals("Clear queue", CLEAR_QUEUE_LABEL)
        assertEquals(
            "This clears Earlier in queue, Now Playing, and Up Next. History is kept.",
            CLEAR_QUEUE_CONFIRMATION_COPY,
        )
    }

    @Test
    fun historyCopyIsTruthfulAboutRecordingBoundaryAndBoundedPage() {
        assertEquals(
            "No History entries yet — recording since 2026-08-24T00:00:00Z.",
            historyEmptyCopy("2026-08-24T00:00:00Z"),
        )
        assertEquals(
            "Only the 50 most recent unique History articles are shown.",
            historyBoundedCopy(hasMore = true, effectiveLimit = 50),
        )
        assertEquals(null, historyBoundedCopy(hasMore = false, effectiveLimit = 10))
        assertTrue(
            historyEditedCopy("2026-08-25T00:00:00Z")
                .orEmpty()
                .contains("edited or cleared since recording began"),
        )
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

    @Test
    fun historyAndEarlierSelectionKeysDoNotConflateTheSameArticle() {
        val history = SessionRowSelectionKey(SessionSelectionSection.HISTORY, rowId = 91, itemId = 7)
        val earlier = SessionRowSelectionKey(SessionSelectionSection.EARLIER, rowId = 7, itemId = 7)

        assertFalse(history == earlier)
        assertEquals(2, setOf(history, earlier).size)
    }

    @Test
    fun historyBatchActionsRequireHomogeneousLifecycleState() {
        val ordinary = historyEntry(7, "2026-08-24T10:00:00Z", false)
            .toSessionHistoryPresentationRow()
        val binned = historyEntry(8, "2026-08-24T11:00:00Z", false, isTrashed = true)
            .toSessionHistoryPresentationRow()

        val ordinaryActions = historySelectionActions(listOf(ordinary), managementEnabled = true)
        assertTrue(ordinaryActions.canRemove)
        assertTrue(ordinaryActions.canMoveToBin)
        assertFalse(ordinaryActions.canRestore)

        val binnedActions = historySelectionActions(listOf(binned), managementEnabled = true)
        assertTrue(binnedActions.canRestore)
        assertFalse(binnedActions.canMoveToBin)
        assertFalse(binnedActions.canArchive)

        val mixedActions = historySelectionActions(listOf(ordinary, binned), managementEnabled = true)
        assertTrue(mixedActions.canRemove)
        assertFalse(mixedActions.canMoveToBin)
        assertFalse(mixedActions.canRestore)
        assertTrue(mixedActions.explanation.orEmpty().contains("same lifecycle state"))
    }

    @Test
    fun binnedHistoryRowRetainsExactRemovalFenceAndBinnedState() {
        val row = historyEntry(
            itemId = 7,
            playedAt = "2026-08-24T10:00:00Z",
            stillInSession = false,
            isTrashed = true,
        ).toSessionHistoryPresentationRow()

        assertTrue(row.isTrashed)
        assertEquals(7, row.historyRemovalTarget()?.itemId)
        assertEquals(7L, row.historyRemovalTarget()?.throughEntryId)
    }

    @Test
    fun canonicalHistoryManagementRequiresOnlineAuthoritativeProjection() {
        assertTrue(
            canonicalHistoryManagementEnabled(
                offline = false,
                projectionLoaded = true,
                awaitingRefresh = false,
            ),
        )
        assertFalse(
            canonicalHistoryManagementEnabled(
                offline = true,
                projectionLoaded = true,
                awaitingRefresh = false,
            ),
        )
        assertFalse(
            canonicalHistoryManagementEnabled(
                offline = false,
                projectionLoaded = false,
                awaitingRefresh = false,
            ),
        )
        assertFalse(
            canonicalHistoryManagementEnabled(
                offline = false,
                projectionLoaded = true,
                awaitingRefresh = true,
            ),
        )
    }


    private fun historyEntry(
        itemId: Int,
        playedAt: String,
        stillInSession: Boolean,
        isTrashed: Boolean = false,
    ) = UpNextHistoryEntry(
        entryId = itemId.toLong(),
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
        isTrashed = isTrashed,
    )
}
