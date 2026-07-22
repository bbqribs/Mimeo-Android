package com.mimeo.android.repository

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredNowPlayingItemMappingTest {

    @Test
    fun authoritativeAcknowledgementRetainsProcessLocalHistoryForSameProjection() {
        assertEquals(
            true,
            shouldRetainTransientHistoryAfterAuthoritativeApply(
                localItemIds = listOf(20, 30, 40),
                localCurrentItemId = 30,
                authoritativeItemIds = listOf(20, 30, 40),
                authoritativeCurrentItemId = 30,
            ),
        )
    }

    @Test
    fun authoritativeReplacementDropsProcessLocalHistory() {
        assertEquals(
            false,
            shouldRetainTransientHistoryAfterAuthoritativeApply(
                localItemIds = listOf(20, 30, 40),
                localCurrentItemId = 30,
                authoritativeItemIds = listOf(20, 40, 50),
                authoritativeCurrentItemId = 40,
            ),
        )
    }

    @Test
    fun restoreHistoryItemToSessionMappingPreservesLastReadPercent() {
        val item = queueItem(itemId = 42, lastReadPercent = 37)

        val stored = item.toStoredNowPlayingItem()

        assertEquals(37, stored.lastReadPercent)
    }

    @Test
    fun insertItemAtIndexInSessionMappingPreservesLastReadPercent() {
        val item = queueItem(itemId = 84, lastReadPercent = 63)

        val stored = item.toStoredNowPlayingItem()

        assertEquals(63, stored.lastReadPercent)
    }

    @Test
    fun newSessionMappingKeepsIntentionalFreshCursorFields() {
        val item = queueItem(itemId = 126, lastReadPercent = 45)

        val stored = item.toStoredNowPlayingItem()

        assertEquals(45, stored.lastReadPercent)
        assertEquals(0, stored.chunkIndex)
        assertEquals(0, stored.offsetInChunkChars)
        assertEquals(0, stored.readerScrollOffset)
    }

    private fun queueItem(itemId: Int, lastReadPercent: Int): PlaybackQueueItem =
        PlaybackQueueItem(
            itemId = itemId,
            title = "Item $itemId",
            url = "https://example.com/$itemId",
            host = "example.com",
            lastReadPercent = lastReadPercent,
        )
}
