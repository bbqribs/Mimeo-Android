package com.mimeo.android.ui.queue

import com.mimeo.android.ui.library.LibrarySortOption

// Backend SmartQueueReorderRequest enforces max_length=500 on item_ids.
internal const val SMART_QUEUE_REORDER_ITEM_LIMIT = 500

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
        itemCount <= SMART_QUEUE_REORDER_ITEM_LIMIT &&
        !reorderSaving
}

internal fun smartQueueReorderStatusLabel(
    dragReorderEnabled: Boolean,
    backendReorderAllowed: Boolean,
    unavailableReason: String?,
    searchQuery: String,
    sortOption: LibrarySortOption,
    hasMorePages: Boolean,
    itemCount: Int,
    loading: Boolean,
    reorderSaving: Boolean,
): String {
    return when {
        reorderSaving -> "Reorder: saving"
        dragReorderEnabled -> "Reorder: ready"
        loading -> "Reorder: loading queue"
        searchQuery.isNotBlank() -> "Reorder: disabled while searching"
        sortOption != LibrarySortOption.SMART_QUEUE -> "Reorder: disabled for custom sort"
        itemCount <= 1 -> "Reorder: needs at least two items"
        itemCount > SMART_QUEUE_REORDER_ITEM_LIMIT -> "Reorder: queue too large (limit is $SMART_QUEUE_REORDER_ITEM_LIMIT)"
        hasMorePages -> "Reorder: loading complete queue"
        !backendReorderAllowed -> "Reorder: unavailable (${unavailableReason ?: "backend unavailable"})"
        else -> "Reorder: unavailable"
    }
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
