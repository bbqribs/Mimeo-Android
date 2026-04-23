package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NoActiveContentWorkerMergeTest {

    @Test
    fun mergesWorkerPersistedIdsIntoKnownNoActiveContentIdsForCurrentQueue() {
        val queueItems = listOf(
            queueItem(itemId = 42, url = "https://example.com/42"),
            queueItem(itemId = 84, url = "https://example.com/84"),
        )

        val merged = mergeWorkerPersistedNoActiveContentIdsOnQueueLoad(
            queueItems = queueItems,
            existingKnownIds = setOf(84),
            persistedNoContentIds = setOf(42),
        )

        assertEquals(setOf(42, 84), merged)
    }

    @Test
    fun dropsWorkerPersistedIdsThatAreNotInCurrentQueue() {
        val queueItems = listOf(queueItem(itemId = 42, url = "https://example.com/42"))

        val merged = mergeWorkerPersistedNoActiveContentIdsOnQueueLoad(
            queueItems = queueItems,
            existingKnownIds = setOf(42),
            persistedNoContentIds = setOf(999),
        )

        assertEquals(setOf(42), merged)
    }

    private fun queueItem(itemId: Int, url: String): PlaybackQueueItem {
        return PlaybackQueueItem(
            itemId = itemId,
            url = url,
        )
    }
}
