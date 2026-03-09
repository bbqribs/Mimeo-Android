package com.mimeo.android.repository

data class OfflineReadyCandidate(
    val itemId: Int,
    val activeContentVersionId: Int?,
)

fun resolveOfflineReadyItemIds(
    candidates: List<OfflineReadyCandidate>,
    cachedItemIds: Set<Int>,
    cachedActiveContentVersionIds: Set<Int>,
): Set<Int> {
    if (candidates.isEmpty()) return emptySet()
    if (cachedItemIds.isEmpty() && cachedActiveContentVersionIds.isEmpty()) return emptySet()

    return candidates
        .asSequence()
        .filter { candidate ->
            cachedItemIds.contains(candidate.itemId) ||
                candidate.activeContentVersionId?.let { cachedActiveContentVersionIds.contains(it) } == true
        }
        .map { it.itemId }
        .toSet()
}
