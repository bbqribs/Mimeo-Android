package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.repository.NowPlayingSessionItem

internal enum class ArchiveUndoSessionRestoreTarget {
    NONE,
    SESSION_ITEMS,
    HISTORY,
}

internal fun queueArchiveUndoSnapshot(
    queueItems: List<PlaybackQueueItem>,
    itemId: Int,
    wasCached: Boolean,
    wasNoActiveContent: Boolean,
    source: ArchiveActionSource,
    actionType: UndoableActionType,
): ArchiveUndoSnapshot? {
    val queueIndex = queueItems.indexOfFirst { it.itemId == itemId }
    val queueItemSnapshot = queueItems.getOrNull(queueIndex) ?: return null
    return ArchiveUndoSnapshot(
        item = queueItemSnapshot,
        originalIndex = queueIndex,
        wasCached = wasCached,
        wasNoActiveContent = wasNoActiveContent,
        source = source,
        actionType = actionType,
    )
}

internal fun sessionArchiveUndoSnapshot(
    item: NowPlayingSessionItem,
    actionType: UndoableActionType,
    wasCached: Boolean,
    wasNoActiveContent: Boolean,
): ArchiveUndoSnapshot =
    ArchiveUndoSnapshot(
        item = PlaybackQueueItem(
            itemId = item.itemId,
            title = item.title,
            url = item.url,
            host = item.host,
            sourceType = item.sourceType,
            sourceLabel = item.sourceLabel,
            sourceUrl = item.sourceUrl,
            captureKind = item.captureKind,
            sourceAppPackage = item.sourceAppPackage,
            status = item.status,
            activeContentVersionId = item.activeContentVersionId,
        ),
        originalIndex = -1,
        wasCached = wasCached,
        wasNoActiveContent = wasNoActiveContent,
        source = ArchiveActionSource.HISTORY_EARLIER,
        actionType = actionType,
    )

internal fun composeSessionArchiveUndoSnapshot(
    baseSnapshot: ArchiveUndoSnapshot?,
    sessionSnapshot: ArchiveUndoSnapshot?,
    originalSessionIndex: Int,
    isSessionHistoryItem: Boolean,
): ArchiveUndoSnapshot? =
    (baseSnapshot ?: sessionSnapshot)?.copy(
        source = ArchiveActionSource.HISTORY_EARLIER,
        originalSessionIndex = originalSessionIndex,
        isSessionHistoryItem = isSessionHistoryItem,
    )

internal fun shouldReinsertQueueOnArchiveUndo(snapshot: ArchiveUndoSnapshot): Boolean =
    snapshot.source != ArchiveActionSource.HISTORY_EARLIER

internal fun archiveUndoSessionRestoreTarget(snapshot: ArchiveUndoSnapshot): ArchiveUndoSessionRestoreTarget =
    when {
        snapshot.source != ArchiveActionSource.HISTORY_EARLIER -> ArchiveUndoSessionRestoreTarget.NONE
        snapshot.isSessionHistoryItem -> ArchiveUndoSessionRestoreTarget.HISTORY
        snapshot.originalSessionIndex >= 0 -> ArchiveUndoSessionRestoreTarget.SESSION_ITEMS
        else -> ArchiveUndoSessionRestoreTarget.NONE
    }

internal fun archiveUndoReopenItemId(snapshot: ArchiveUndoSnapshot): Int? =
    when (snapshot.source) {
        ArchiveActionSource.LOCUS -> snapshot.item.itemId
        ArchiveActionSource.UP_NEXT, ArchiveActionSource.HISTORY_EARLIER -> null
    }
