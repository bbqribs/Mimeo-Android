package com.mimeo.android

import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistContinuationTest {

    @Test
    fun advancesToNextItemForPlaylistScopedSession() {
        val session = session(sourcePlaylistId = 42, currentIndex = 0, itemIds = listOf(10, 20, 30))
        assertEquals(1, resolveNextPlaylistScopedSessionIndex(session, currentId = 10))
    }

    @Test
    fun stopsAtEndOfPlaylistScopedSession() {
        val session = session(sourcePlaylistId = 42, currentIndex = 2, itemIds = listOf(10, 20, 30))
        assertNull(resolveNextPlaylistScopedSessionIndex(session, currentId = 30))
    }

    @Test
    fun doesNotAutoAdvanceForNonPlaylistSession() {
        val session = session(sourcePlaylistId = null, currentIndex = 0, itemIds = listOf(10, 20, 30))
        assertNull(resolveNextPlaylistScopedSessionIndex(session, currentId = 10))
    }

    @Test
    fun doesNotAutoAdvanceForSmartQueueSessionWhenPlaylistScopedModeIsRequired() {
        val session = session(
            sourcePlaylistId = resolveSessionSourcePlaylistId(null),
            currentIndex = 0,
            itemIds = listOf(10, 20, 30),
        )
        assertNull(resolveNextPlaylistScopedSessionIndex(session, currentId = 10))
    }

    private fun session(
        sourcePlaylistId: Int?,
        currentIndex: Int,
        itemIds: List<Int>,
    ): NowPlayingSession {
        return NowPlayingSession(
            items = itemIds.map { itemId ->
                NowPlayingSessionItem(
                    itemId = itemId,
                    title = null,
                    url = "https://example.com/$itemId",
                    host = "example.com",
                    status = null,
                    activeContentVersionId = null,
                    lastReadPercent = 0,
                    chunkIndex = 0,
                    offsetInChunkChars = 0,
                )
            },
            currentIndex = currentIndex,
            updatedAt = 0L,
            sourcePlaylistId = sourcePlaylistId,
        )
    }
}
