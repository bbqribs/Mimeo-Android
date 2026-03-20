package com.mimeo.android.ui.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueAutoScrollBehaviorTest {

    @Test
    fun `newly surfaced item in default view scrolls to top`() {
        val shouldScroll = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = listOf(10, 9, 8),
            currentDisplayedItemIds = listOf(11, 10, 9, 8),
            pendingFocusId = -1,
            hasSearchQuery = false,
            isDefaultFilterAndSort = true,
        )

        assertTrue(shouldScroll)
    }

    @Test
    fun `first load does not force additional top scroll`() {
        val shouldScroll = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = emptyList(),
            currentDisplayedItemIds = listOf(11, 10, 9, 8),
            pendingFocusId = -1,
            hasSearchQuery = false,
            isDefaultFilterAndSort = true,
        )

        assertFalse(shouldScroll)
    }

    @Test
    fun `focus-driven scroll takes precedence`() {
        val shouldScroll = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = listOf(10, 9, 8),
            currentDisplayedItemIds = listOf(11, 10, 9, 8),
            pendingFocusId = 9,
            hasSearchQuery = false,
            isDefaultFilterAndSort = true,
        )

        assertFalse(shouldScroll)
    }

    @Test
    fun `non-default filtered or searched views do not auto-jump`() {
        val filtered = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = listOf(10, 9, 8),
            currentDisplayedItemIds = listOf(11, 10, 9, 8),
            pendingFocusId = -1,
            hasSearchQuery = false,
            isDefaultFilterAndSort = false,
        )
        val searched = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = listOf(10, 9, 8),
            currentDisplayedItemIds = listOf(11, 10, 9, 8),
            pendingFocusId = -1,
            hasSearchQuery = true,
            isDefaultFilterAndSort = true,
        )

        assertFalse(filtered)
        assertFalse(searched)
    }
}
