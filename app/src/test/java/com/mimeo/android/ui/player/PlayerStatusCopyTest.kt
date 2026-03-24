package com.mimeo.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStatusCopyTest {

    @Test
    fun offlineAvailabilityShowsAvailableWhenAnyCacheSignalPresent() {
        assertEquals(
            "Available offline",
            playerOfflineAvailabilityLabel(
                cachedItemIds = setOf(42),
                currentItemId = 42,
                usingCachedText = false,
                isItemCached = false,
            ),
        )
        assertEquals(
            "Available offline",
            playerOfflineAvailabilityLabel(
                cachedItemIds = emptySet(),
                currentItemId = 42,
                usingCachedText = true,
                isItemCached = false,
            ),
        )
        assertEquals(
            "Available offline",
            playerOfflineAvailabilityLabel(
                cachedItemIds = emptySet(),
                currentItemId = 42,
                usingCachedText = false,
                isItemCached = true,
            ),
        )
    }

    @Test
    fun offlineAvailabilityShowsNotCachedWhenItemNeedsNetwork() {
        assertEquals(
            "Not cached",
            playerOfflineAvailabilityLabel(
                cachedItemIds = emptySet(),
                currentItemId = 42,
                usingCachedText = false,
                isItemCached = false,
            ),
        )
    }
}
