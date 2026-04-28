package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistPinSectionsTest {
    @Test
    fun pinnedIdsComeFromLeadingBackendRows() {
        val items = listOf(item(10), item(20), item(30))

        val pinnedIds = smartPlaylistPinnedItemIds(items, pinCount = 2)

        assertEquals(setOf(10, 20), pinnedIds)
    }

    @Test
    fun pinCountIsClampedToReturnedRows() {
        val items = listOf(item(10))

        val pinnedIds = smartPlaylistPinnedItemIds(items, pinCount = 4)

        assertEquals(setOf(10), pinnedIds)
    }

    @Test
    fun zeroPinCountLeavesAllRowsLive() {
        val items = listOf(item(10), item(20))

        val pinnedIds = smartPlaylistPinnedItemIds(items, pinCount = 0)

        assertEquals(emptySet<Int>(), pinnedIds)
    }

    private fun item(id: Int): PlaybackQueueItem =
        PlaybackQueueItem(
            itemId = id,
            title = "Item $id",
            url = "https://example.com/$id",
        )
}
