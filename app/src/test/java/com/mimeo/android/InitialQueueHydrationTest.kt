package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialQueueHydrationTest {
    @Test
    fun `selects first uncached queue items up to bounded limit`() {
        val queueItems = (1..10).map { itemId ->
            PlaybackQueueItem(itemId = itemId, url = "https://example.com/$itemId")
        }

        val targets = selectInitialQueueHydrationTargets(
            queueItems = queueItems,
            cachedItemIds = setOf(1, 3, 5),
            limit = 4,
        )

        assertEquals(listOf(2, 4, 6, 7), targets)
    }

    @Test
    fun `skips terminally failed queue items during initial hydration`() {
        val queueItems = listOf(
            PlaybackQueueItem(itemId = 1, url = "https://example.com/1", status = "blocked"),
            PlaybackQueueItem(itemId = 2, url = "https://example.com/2", status = "failed"),
            PlaybackQueueItem(itemId = 3, url = "https://example.com/3", status = "ready"),
            PlaybackQueueItem(itemId = 4, url = "https://example.com/4", status = null),
        )

        val targets = selectInitialQueueHydrationTargets(
            queueItems = queueItems,
            cachedItemIds = emptySet(),
            limit = 4,
        )

        assertEquals(listOf(3, 4), targets)
    }
}
