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
}
