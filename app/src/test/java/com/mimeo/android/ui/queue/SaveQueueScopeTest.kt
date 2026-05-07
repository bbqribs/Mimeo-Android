package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveQueueScopeTest {

    @Test
    fun defaultScopeReturnsNowPlayingAndUpNext() {
        val ids = buildSaveQueueItemIds(
            historyIds = listOf(6, 7),
            earlierIds = listOf(4, 5),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = false,
            includeHistory = false,
        )
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun includeEarlierPrependsBeforeNowPlaying() {
        val ids = buildSaveQueueItemIds(
            historyIds = emptyList(),
            earlierIds = listOf(4, 5),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = true,
            includeHistory = false,
        )
        assertEquals(listOf(4, 5, 1, 2, 3), ids)
    }

    @Test
    fun includeHistoryOnlyPrependsHistoryBeforeNowPlaying() {
        val ids = buildSaveQueueItemIds(
            historyIds = listOf(6, 7),
            earlierIds = listOf(4, 5),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = false,
            includeHistory = true,
        )
        assertEquals(listOf(6, 7, 1, 2, 3), ids)
    }

    @Test
    fun includeBothFollowsHistoryEarlierNowPlayingUpNextOrder() {
        val ids = buildSaveQueueItemIds(
            historyIds = listOf(6, 7),
            earlierIds = listOf(4, 5),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = true,
            includeHistory = true,
        )
        assertEquals(listOf(6, 7, 4, 5, 1, 2, 3), ids)
    }

    @Test
    fun duplicateIdsAreDeduplicatedKeepingFirstOccurrence() {
        val ids = buildSaveQueueItemIds(
            historyIds = listOf(1, 6),
            earlierIds = listOf(2, 6),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = true,
            includeHistory = true,
        )
        assertEquals(listOf(1, 6, 2, 3), ids)
    }

    @Test
    fun nullNowPlayingIsSkipped() {
        val ids = buildSaveQueueItemIds(
            historyIds = emptyList(),
            earlierIds = emptyList(),
            nowPlayingId = null,
            upNextIds = listOf(1, 2),
            includeEarlier = false,
            includeHistory = false,
        )
        assertEquals(listOf(1, 2), ids)
    }

    @Test
    fun emptySessionReturnsEmpty() {
        val ids = buildSaveQueueItemIds(
            historyIds = emptyList(),
            earlierIds = emptyList(),
            nowPlayingId = null,
            upNextIds = emptyList(),
            includeEarlier = false,
            includeHistory = false,
        )
        assertEquals(emptyList<Int>(), ids)
    }

    @Test
    fun flagsFalseIgnoreEarlierAndHistory() {
        val ids = buildSaveQueueItemIds(
            historyIds = listOf(99, 100),
            earlierIds = listOf(50, 51),
            nowPlayingId = 1,
            upNextIds = listOf(2, 3),
            includeEarlier = false,
            includeHistory = false,
        )
        assertEquals(listOf(1, 2, 3), ids)
    }
}
