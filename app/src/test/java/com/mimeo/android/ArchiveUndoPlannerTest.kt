package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.repository.NowPlayingSessionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveUndoPlannerTest {

    @Test
    fun archiveFromUpNextBuildsQueueSnapshotThatReinsertsQueue() {
        val snapshot = queueArchiveUndoSnapshot(
            queueItems = listOf(queueItem(10), queueItem(20), queueItem(30)),
            itemId = 20,
            wasCached = true,
            wasNoActiveContent = false,
            source = ArchiveActionSource.UP_NEXT,
            actionType = UndoableActionType.ARCHIVE,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(20, actual.item.itemId)
        assertEquals(1, actual.originalIndex)
        assertEquals(ArchiveActionSource.UP_NEXT, actual.source)
        assertEquals(UndoableActionType.ARCHIVE, actual.actionType)
        assertTrue(actual.wasCached)
        assertTrue(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.NONE, archiveUndoSessionRestoreTarget(actual))
        assertNull(archiveUndoReopenItemId(actual))
    }

    @Test
    fun archiveFromLocusBuildsQueueSnapshotThatReopensInsteadOfSessionRestore() {
        val snapshot = queueArchiveUndoSnapshot(
            queueItems = listOf(queueItem(7)),
            itemId = 7,
            wasCached = false,
            wasNoActiveContent = true,
            source = ArchiveActionSource.LOCUS,
            actionType = UndoableActionType.ARCHIVE,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(ArchiveActionSource.LOCUS, actual.source)
        assertEquals(0, actual.originalIndex)
        assertTrue(actual.wasNoActiveContent)
        assertTrue(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.NONE, archiveUndoSessionRestoreTarget(actual))
        assertEquals(7, archiveUndoReopenItemId(actual))
    }

    @Test
    fun archiveFromEarlierUsesHistoryEarlierRoutingEvenWhenQueueBacked() {
        val baseSnapshot = queueArchiveUndoSnapshot(
            queueItems = listOf(queueItem(1), queueItem(2)),
            itemId = 1,
            wasCached = false,
            wasNoActiveContent = false,
            source = ArchiveActionSource.UP_NEXT,
            actionType = UndoableActionType.ARCHIVE,
        )

        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = baseSnapshot,
            sessionSnapshot = sessionArchiveUndoSnapshot(
                item = sessionItem(1),
                actionType = UndoableActionType.ARCHIVE,
                wasCached = false,
                wasNoActiveContent = false,
            ),
            originalSessionIndex = 0,
            isSessionHistoryItem = false,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(ArchiveActionSource.HISTORY_EARLIER, actual.source)
        assertEquals(0, actual.originalIndex)
        assertEquals(0, actual.originalSessionIndex)
        assertFalse(actual.isSessionHistoryItem)
        assertFalse(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.SESSION_ITEMS, archiveUndoSessionRestoreTarget(actual))
        assertNull(archiveUndoReopenItemId(actual))
    }

    @Test
    fun archiveFromHistoryUsesHistoryRestoreRoutingWhenQueueBacked() {
        val baseSnapshot = queueArchiveUndoSnapshot(
            queueItems = listOf(queueItem(5), queueItem(6)),
            itemId = 6,
            wasCached = true,
            wasNoActiveContent = false,
            source = ArchiveActionSource.UP_NEXT,
            actionType = UndoableActionType.ARCHIVE,
        )

        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = baseSnapshot,
            sessionSnapshot = sessionArchiveUndoSnapshot(
                item = sessionItem(6),
                actionType = UndoableActionType.ARCHIVE,
                wasCached = true,
                wasNoActiveContent = false,
            ),
            originalSessionIndex = 2,
            isSessionHistoryItem = true,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(ArchiveActionSource.HISTORY_EARLIER, actual.source)
        assertEquals(1, actual.originalIndex)
        assertEquals(2, actual.originalSessionIndex)
        assertTrue(actual.isSessionHistoryItem)
        assertFalse(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.HISTORY, archiveUndoSessionRestoreTarget(actual))
    }

    @Test
    fun archiveFromHistoryUsesSessionOnlySnapshotWhenQueueSnapshotMissing() {
        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = null,
            sessionSnapshot = sessionArchiveUndoSnapshot(
                item = sessionItem(60),
                actionType = UndoableActionType.ARCHIVE,
                wasCached = true,
                wasNoActiveContent = true,
            ),
            originalSessionIndex = 3,
            isSessionHistoryItem = true,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(60, actual.item.itemId)
        assertEquals(-1, actual.originalIndex)
        assertEquals(3, actual.originalSessionIndex)
        assertTrue(actual.wasCached)
        assertTrue(actual.wasNoActiveContent)
        assertFalse(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.HISTORY, archiveUndoSessionRestoreTarget(actual))
    }

    @Test
    fun binFromEarlierUsesSessionRestoreRoutingWhenQueueSnapshotMissing() {
        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = null,
            sessionSnapshot = sessionArchiveUndoSnapshot(
                item = sessionItem(70),
                actionType = UndoableActionType.BIN,
                wasCached = false,
                wasNoActiveContent = false,
            ),
            originalSessionIndex = 1,
            isSessionHistoryItem = false,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(UndoableActionType.BIN, actual.actionType)
        assertEquals(ArchiveActionSource.HISTORY_EARLIER, actual.source)
        assertEquals(1, actual.originalSessionIndex)
        assertFalse(actual.isSessionHistoryItem)
        assertFalse(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.SESSION_ITEMS, archiveUndoSessionRestoreTarget(actual))
    }

    @Test
    fun binFromHistoryUsesHistoryRestoreRoutingWhenQueueSnapshotMissing() {
        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = null,
            sessionSnapshot = sessionArchiveUndoSnapshot(
                item = sessionItem(80),
                actionType = UndoableActionType.BIN,
                wasCached = false,
                wasNoActiveContent = false,
            ),
            originalSessionIndex = 4,
            isSessionHistoryItem = true,
        )

        assertNotNull(snapshot)
        val actual = snapshot!!
        assertEquals(UndoableActionType.BIN, actual.actionType)
        assertEquals(4, actual.originalSessionIndex)
        assertTrue(actual.isSessionHistoryItem)
        assertFalse(shouldReinsertQueueOnArchiveUndo(actual))
        assertEquals(ArchiveUndoSessionRestoreTarget.HISTORY, archiveUndoSessionRestoreTarget(actual))
    }

    @Test
    fun missingQueueAndSessionSnapshotsProduceNoUndo() {
        val snapshot = composeSessionArchiveUndoSnapshot(
            baseSnapshot = null,
            sessionSnapshot = null,
            originalSessionIndex = -1,
            isSessionHistoryItem = false,
        )

        assertNull(snapshot)
    }

    private fun queueItem(itemId: Int): PlaybackQueueItem =
        PlaybackQueueItem(
            itemId = itemId,
            url = "https://example.com/$itemId",
        )

    private fun sessionItem(itemId: Int): NowPlayingSessionItem =
        NowPlayingSessionItem(
            itemId = itemId,
            title = "Item $itemId",
            url = "https://example.com/$itemId",
            host = "example.com",
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
            status = null,
            activeContentVersionId = null,
            lastReadPercent = null,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
        )
}
