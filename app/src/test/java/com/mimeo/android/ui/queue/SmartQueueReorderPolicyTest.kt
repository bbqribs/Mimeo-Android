package com.mimeo.android.ui.queue

import com.mimeo.android.ui.library.LibrarySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueueReorderPolicyTest {
    @Test
    fun enablesOnlyDefaultCompleteUnfilteredSmartQueue() {
        assertTrue(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
            ),
        )
    }

    @Test
    fun disablesWhenSearchOrCustomSortOrPaginationIsActive() {
        assertFalse(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "example",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
            ),
        )
        assertFalse(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.NEWEST,
                hasMorePages = false,
                itemCount = 3,
            ),
        )
        assertFalse(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = true,
                itemCount = 3,
            ),
        )
    }

    @Test
    fun movedSmartQueueItemIdsKeepsFullOrderedMembership() {
        assertEquals(listOf(1, 3, 4, 2), movedSmartQueueItemIds(listOf(1, 2, 3, 4), 1, 3))
        assertEquals(listOf(1, 2, 3, 4), movedSmartQueueItemIds(listOf(1, 2, 3, 4), -1, 3))
    }

    @Test
    fun statusLabelReportsBackendUnavailableReason() {
        assertEquals(
            "Reorder: unavailable (filtered_or_sorted)",
            smartQueueReorderStatusLabel(
                dragReorderEnabled = false,
                backendReorderAllowed = false,
                unavailableReason = "filtered_or_sorted",
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
                loading = false,
                reorderSaving = false,
            ),
        )
    }
}
