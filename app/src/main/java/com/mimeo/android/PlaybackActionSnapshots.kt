package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem

internal fun playbackActionSnapshot(sourceItems: List<PlaybackQueueItem>): List<PlaybackQueueItem> =
    sourceItems.distinctBy { it.itemId }

internal fun playbackActionSnapshotFromHere(
    sourceItems: List<PlaybackQueueItem>,
    selectedItemId: Int,
): List<PlaybackQueueItem> =
    playbackActionSnapshot(sourceItems.dropWhile { it.itemId != selectedItemId })
