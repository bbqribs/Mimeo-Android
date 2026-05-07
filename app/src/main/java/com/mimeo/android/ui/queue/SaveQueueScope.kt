package com.mimeo.android.ui.queue

internal fun buildSaveQueueItemIds(
    historyIds: List<Int>,
    earlierIds: List<Int>,
    nowPlayingId: Int?,
    upNextIds: List<Int>,
    includeEarlier: Boolean,
    includeHistory: Boolean,
): List<Int> {
    val result = mutableListOf<Int>()
    val seen = mutableSetOf<Int>()
    fun addIfNew(id: Int) { if (seen.add(id)) result.add(id) }
    if (includeHistory) historyIds.forEach(::addIfNew)
    if (includeEarlier) earlierIds.forEach(::addIfNew)
    nowPlayingId?.let(::addIfNew)
    upNextIds.forEach(::addIfNew)
    return result
}
