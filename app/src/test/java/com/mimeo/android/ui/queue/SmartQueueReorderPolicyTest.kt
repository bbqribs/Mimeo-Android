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

    @Test
    fun disablesWhileReorderIsSaving() {
        assertFalse(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
                reorderSaving = true,
            ),
        )
    }

    @Test
    fun statusLabelReportsSavingState() {
        assertEquals(
            "Reorder: saving",
            smartQueueReorderStatusLabel(
                dragReorderEnabled = false,
                backendReorderAllowed = true,
                unavailableReason = null,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
                loading = false,
                reorderSaving = true,
            ),
        )
    }

    @Test
    fun disablesWhenQueueExceedsItemLimit() {
        assertFalse(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = SMART_QUEUE_REORDER_ITEM_LIMIT + 1,
            ),
        )
    }

    @Test
    fun statusLabelReportsQueueTooLarge() {
        assertEquals(
            "Reorder: queue too large (limit is $SMART_QUEUE_REORDER_ITEM_LIMIT)",
            smartQueueReorderStatusLabel(
                dragReorderEnabled = false,
                backendReorderAllowed = true,
                unavailableReason = null,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = SMART_QUEUE_REORDER_ITEM_LIMIT + 1,
                loading = false,
                reorderSaving = false,
            ),
        )
    }

    @Test
    fun statusLabelReportsDisabledWhileSearching() {
        assertEquals(
            "Reorder: disabled while searching",
            smartQueueReorderStatusLabel(
                dragReorderEnabled = false,
                backendReorderAllowed = true,
                unavailableReason = null,
                searchQuery = "hello",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 3,
                loading = false,
                reorderSaving = false,
            ),
        )
    }
}
