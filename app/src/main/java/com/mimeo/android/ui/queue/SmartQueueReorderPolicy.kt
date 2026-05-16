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
        hasMorePages -> "Reorder: unavailable until queue response is complete"
        !backendReorderAllowed -> "Reorder: unavailable (${unavailableReason ?: "backend unavailable"})"
        else -> "Reorder: unavailable"
    }
}

internal fun smartQueueScopeStatusLabel(
    itemCount: Int,
    totalCount: Int,
    activeScopeLimit: Int?,
): String {
    if (activeScopeLimit == null || itemCount <= 0 || totalCount <= itemCount) {
        return ""
    }
    val visibleScopeCount = itemCount.coerceAtMost(activeScopeLimit)
    return "First $visibleScopeCount of $totalCount items"
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
