package com.mimeo.android.ui.queue

import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueReseedRulesTest {

    @Test
    fun resolvesSeedPresentation_whenSessionSeedAndCurrentSourceDiffer() {
        val presentation = resolveSessionSeedSourcePresentation(
            sessionSourcePlaylistId = 10,
            selectedPlaylistId = null,
            playlists = listOf(PlaylistSummary(id = 10, name = "Podcasts")),
        )

        assertEquals("Podcasts", presentation.seededFromLabel)
        assertEquals("Smart queue", presentation.currentSourceLabel)
    }

    @Test
    fun skipsReseedConfirmation_whenSessionAlreadyMatchesCurrentSource() {
        val session = session(
            sourcePlaylistId = -1,
            itemIds = listOf(1, 2, 3),
        )

        assertFalse(
            shouldConfirmReseedFromCurrentSource(
                session = session,
                sourceItems = listOf(queueItem(1), queueItem(2), queueItem(3)),
                selectedPlaylistId = null,
            ),
        )
    }

    @Test
    fun requiresReseedConfirmation_whenSessionOrderDiffersFromCurrentSource() {
        val session = session(
            sourcePlaylistId = -1,
            itemIds = listOf(1, 3, 2),
        )

        assertTrue(
            shouldConfirmReseedFromCurrentSource(
                session = session,
                sourceItems = listOf(queueItem(1), queueItem(2), queueItem(3)),
                selectedPlaylistId = null,
            ),
        )
    }

    @Test
    fun requiresReseedConfirmation_whenSeedContextDiffersFromCurrentSourceContext() {
        val session = session(
            sourcePlaylistId = 12,
            itemIds = listOf(1, 2, 3),
        )

        assertTrue(
            shouldConfirmReseedFromCurrentSource(
                session = session,
                sourceItems = listOf(queueItem(1), queueItem(2), queueItem(3)),
                selectedPlaylistId = null,
            ),
        )
    }

    private fun session(sourcePlaylistId: Int, itemIds: List<Int>): NowPlayingSession {
        return NowPlayingSession(
            items = itemIds.map { id ->
                NowPlayingSessionItem(
                    itemId = id,
                    title = "Item $id",
                    url = "https://example.com/$id",
                    host = null,
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
            },
            currentIndex = 0,
            updatedAt = 0L,
            sourcePlaylistId = sourcePlaylistId,
        )
    }

    private fun queueItem(itemId: Int): PlaybackQueueItem {
        return PlaybackQueueItem(
            itemId = itemId,
            url = "https://example.com/$itemId",
        )
    }
}

