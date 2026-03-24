package com.mimeo.android.ui.queue

import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingProjectionTransitionTest {
    @Test
    fun keepsResolvedPendingVisibleUntilCached() {
        val pending = pendingItem(resolvedItemId = 42)
        val queue = listOf(queueItem(itemId = 42, status = "saved"))

        val projected = projectPendingItemsForDestination(
            pendingItems = listOf(pending),
            selectedPlaylistId = null,
            queueItems = queue,
            cachedItemIds = emptySet(),
            noActiveContentItemIds = emptySet(),
        )

        assertEquals(listOf(pending), projected)
    }

    @Test
    fun hidesResolvedPendingAfterCached() {
        val pending = pendingItem(resolvedItemId = 42)
        val queue = listOf(queueItem(itemId = 42, status = "saved"))

        val projected = projectPendingItemsForDestination(
            pendingItems = listOf(pending),
            selectedPlaylistId = null,
            queueItems = queue,
            cachedItemIds = setOf(42),
            noActiveContentItemIds = emptySet(),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun hidesResolvedPendingWhenMarkedNoActiveContent() {
        val pending = pendingItem(resolvedItemId = 42)
        val queue = listOf(queueItem(itemId = 42, status = "saved"))

        val projected = projectPendingItemsForDestination(
            pendingItems = listOf(pending),
            selectedPlaylistId = null,
            queueItems = queue,
            cachedItemIds = emptySet(),
            noActiveContentItemIds = setOf(42),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun keepsResolvedPendingWhenQueueStatusIsFailedProcessing() {
        val pending = pendingItem(resolvedItemId = 42)
        val queue = listOf(queueItem(itemId = 42, status = "failed_processing"))

        val projected = projectPendingItemsForDestination(
            pendingItems = listOf(pending),
            selectedPlaylistId = null,
            queueItems = queue,
            cachedItemIds = setOf(42),
            noActiveContentItemIds = emptySet(),
        )

        assertEquals(listOf(pending), projected)
    }

    @Test
    fun summarizesResolvedPendingAsCached() {
        val previous = listOf(pendingItem(resolvedItemId = 42))
        val current = emptyList<PendingManualSaveItem>()
        val queue = listOf(queueItem(itemId = 42, status = "saved"))

        val summary = summarizePendingProjectionResolutions(
            previousProjectedItems = previous,
            currentProjectedItems = current,
            queueItems = queue,
            cachedItemIds = setOf(42),
            noActiveContentItemIds = emptySet(),
        )

        assertEquals(PendingProjectionResolutionSummary(cachedCount = 1), summary)
        assertEquals("Saved and downloaded for offline reading.", pendingProjectionResolutionMessage(summary))
    }

    @Test
    fun summarizesResolvedPendingAsNoActiveContent() {
        val previous = listOf(pendingItem(resolvedItemId = 42))
        val current = emptyList<PendingManualSaveItem>()
        val queue = listOf(queueItem(itemId = 42, status = "saved"))

        val summary = summarizePendingProjectionResolutions(
            previousProjectedItems = previous,
            currentProjectedItems = current,
            queueItems = queue,
            cachedItemIds = emptySet(),
            noActiveContentItemIds = setOf(42),
        )

        assertEquals(PendingProjectionResolutionSummary(noActiveContentCount = 1), summary)
        assertEquals("Saved, but not available offline for this item.", pendingProjectionResolutionMessage(summary))
    }

    @Test
    fun returnsNullMessageWhenNoResolutionDetected() {
        val summary = summarizePendingProjectionResolutions(
            previousProjectedItems = listOf(pendingItem(resolvedItemId = 42)),
            currentProjectedItems = emptyList(),
            queueItems = emptyList(),
            cachedItemIds = emptySet(),
            noActiveContentItemIds = emptySet(),
        )

        assertEquals(PendingProjectionResolutionSummary(), summary)
        assertNull(pendingProjectionResolutionMessage(summary))
    }

    private fun pendingItem(resolvedItemId: Int?): PendingManualSaveItem {
        return PendingManualSaveItem(
            id = 1L,
            source = PendingSaveSource.MANUAL,
            type = PendingManualSaveType.URL,
            urlInput = "https://example.com/article",
            destinationPlaylistId = null,
            lastFailureMessage = "Processing...",
            autoRetryEligible = false,
            resolvedItemId = resolvedItemId,
        )
    }

    private fun queueItem(itemId: Int, status: String): PlaybackQueueItem {
        return PlaybackQueueItem(
            itemId = itemId,
            title = "Example",
            url = "https://example.com/article",
            host = "example.com",
            status = status,
            apiProgressPercent = 0,
            apiFurthestPercent = 0,
        )
    }
}
