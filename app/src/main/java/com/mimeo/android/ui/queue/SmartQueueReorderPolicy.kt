package com.mimeo.android.ui.queue

import com.mimeo.android.ui.library.LibrarySortOption

internal fun smartQueueDragReorderEnabled(
    backendReorderAllowed: Boolean,
    searchQuery: String,
    sortOption: LibrarySortOption,
    hasMorePages: Boolean,
    itemCount: Int,
    reorderSaving: Boolean = false,
): Boolean {
    return backendReorderAllowed &&
        searchQuery.isBlank() &&
        sortOption == LibrarySortOption.SMART_QUEUE &&
        !hasMorePages &&
        itemCount > 1 &&
        !reorderSaving
}

internal fun movedSmartQueueItemIds(itemIds: List<Int>, fromIndex: Int, toIndex: Int): List<Int> {
    if (fromIndex !in itemIds.indices || toIndex !in itemIds.indices || fromIndex == toIndex) {
        return itemIds
    }
    return itemIds.toMutableList().apply {
        val moved = removeAt(fromIndex)
        add(toIndex, moved)
    }
}
