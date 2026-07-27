package com.mimeo.android

import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.repository.NowPlayingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackArchiveHistoryTest {

    @Test
    fun completedArchivedItemEntersHistoryExactlyOnce() {
        val archivedCompletion = item(itemId = 7, progress = 100, archived = true)

        val first = mergeTransientHistoryItems(emptyList(), archivedCompletion)
        val repeated = mergeTransientHistoryItems(first, archivedCompletion)

        assertEquals(listOf(7), repeated.map { it.itemId })
        assertEquals(100, repeated.single().lastReadPercent)
        assertTrue(repeated.single().isArchived)
    }

    @Test
    fun unarchivePreservesHistoryAndCompletionEvidence() {
        val completed = item(itemId = 7, progress = 100, archived = true)
        val history = mergeTransientHistoryItems(emptyList(), completed)

        val afterUnarchive = mergeTransientHistoryItems(history, completed.copy(isArchived = false))

        assertEquals(listOf(7), afterUnarchive.map { it.itemId })
        assertEquals(100, afterUnarchive.single().lastReadPercent)
        assertFalse(afterUnarchive.single().isArchived)
    }

    @Test
    fun refreshedArchiveIdsFollowCurrentSessionTruth() {
        val refreshed = NowPlayingSession(
            items = listOf(
                item(itemId = 7, progress = 64, archived = false),
                item(itemId = 8, progress = 0, archived = true),
            ),
            currentIndex = 0,
            updatedAt = 0L,
            sourcePlaylistId = null,
            historyItems = listOf(item(itemId = 9, progress = 100, archived = true)),
        )

        assertEquals(setOf(8, 9), archivedSessionItemIds(refreshed))
    }

    private fun item(itemId: Int, progress: Int, archived: Boolean) = NowPlayingSessionItem(
        itemId = itemId,
        title = "Item $itemId",
        url = "https://example.com/$itemId",
        host = "example.com",
        sourceType = null,
        sourceLabel = null,
        sourceUrl = null,
        captureKind = null,
        sourceAppPackage = null,
        status = "ready",
        activeContentVersionId = null,
        lastReadPercent = progress,
        chunkIndex = 0,
        offsetInChunkChars = 0,
        readerScrollOffset = 0,
        isArchived = archived,
    )
}
