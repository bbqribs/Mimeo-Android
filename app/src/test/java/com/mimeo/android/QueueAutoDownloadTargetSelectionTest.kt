package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAutoDownloadTargetSelectionTest {

    @Test
    fun `autodownload on initial load selects uncached surfaced items`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(1), item(2), item(3)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = setOf(2),
            knownNoActiveContentItemIds = emptySet(),
        )

        assertEquals(listOf(1, 3), targets)
    }

    @Test
    fun `autodownload on refresh selects only newly surfaced items`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(2), item(3), item(4)),
            previousVisibleItemIds = linkedSetOf(2, 3),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
        )

        assertEquals(listOf(4), targets)
    }

    @Test
    fun `autodownload on newly surfaced queue state change includes new item`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(10), item(11), item(12)),
            previousVisibleItemIds = linkedSetOf(10, 11),
            cachedItemIds = setOf(11),
            knownNoActiveContentItemIds = emptySet(),
        )

        assertEquals(listOf(12), targets)
    }

    @Test
    fun `autodownload off yields no targets`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = false,
            queueItems = listOf(item(1), item(2), item(3)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
        )

        assertEquals(emptyList<Int>(), targets)
    }

    @Test
    fun `duplicate refresh with same visible set yields no additional targets`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(7), item(8), item(9)),
            previousVisibleItemIds = linkedSetOf(7, 8, 9),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
        )

        assertEquals(emptyList<Int>(), targets)
    }

    @Test
    fun `refresh mode can include all visible uncached items`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(7), item(8), item(9)),
            previousVisibleItemIds = linkedSetOf(7, 8, 9),
            cachedItemIds = setOf(8),
            knownNoActiveContentItemIds = setOf(9),
            includeAllVisibleUncached = true,
        )

        assertEquals(listOf(7), targets)
    }

    private fun item(itemId: Int): PlaybackQueueItem {
        return PlaybackQueueItem(
            itemId = itemId,
            title = "Item $itemId",
            url = "https://example.com/$itemId",
            host = "example.com",
            status = "processed",
            activeContentVersionId = itemId,
            strategyUsed = null,
            wordCount = null,
            resumeReadPercent = 0,
            lastReadPercent = 0,
            apiProgressPercent = 0,
            apiFurthestPercent = 0,
            lastOpenedAt = null,
            createdAt = "2026-01-01T00:00:00Z",
        )
    }
}
