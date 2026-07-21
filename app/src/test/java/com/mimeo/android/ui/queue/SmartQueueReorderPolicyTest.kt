package com.mimeo.android.ui.queue

import com.mimeo.android.ui.library.LibrarySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueueReorderPolicyTest {
    @Test
    fun boundedActiveScopeAllowsReorder() {
        assertTrue(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 100,
            ),
        )
    }

    @Test
    fun totalCountGreaterThanActiveScopeDoesNotBlockReorder() {
        assertTrue(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 100,
            ),
        )
    }

    @Test
    fun disablesWhenSearchOrCustomSortOrIncompleteResponseIsActive() {
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
        assertEquals(listOf(2, 1, 3, 4), movedSmartQueueItemIds(listOf(1, 2, 3, 4), 1, 0))
        assertEquals(listOf(1, 3, 2, 4), movedSmartQueueItemIds(listOf(1, 2, 3, 4), 1, 2))
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
    fun noLegacyFiveHundredItemGateRemains() {
        assertTrue(
            smartQueueDragReorderEnabled(
                backendReorderAllowed = true,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = false,
                itemCount = 501,
            ),
        )
    }

    @Test
    fun statusLabelReportsIncompleteResponseWithoutFullQueueLoadingCopy() {
        assertEquals(
            "Reorder: unavailable until queue response is complete",
            smartQueueReorderStatusLabel(
                dragReorderEnabled = false,
                backendReorderAllowed = true,
                unavailableReason = null,
                searchQuery = "",
                sortOption = LibrarySortOption.SMART_QUEUE,
                hasMorePages = true,
                itemCount = 3,
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

    @Test
    fun scopeStatusLabelReportsActiveScope() {
        assertEquals(
            "First 100 of 930 items",
            smartQueueScopeStatusLabel(
                itemCount = 100,
                totalCount = 930,
                activeScopeLimit = 100,
            ),
        )
    }

    @Test
    fun scopeStatusLabelHiddenWhenTotalFitsVisibleItems() {
        assertEquals(
            "",
            smartQueueScopeStatusLabel(
                itemCount = 42,
                totalCount = 42,
                activeScopeLimit = 100,
            ),
        )
    }
}
