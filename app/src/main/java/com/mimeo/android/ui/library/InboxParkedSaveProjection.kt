package com.mimeo.android.ui.library

import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.share.normalizePendingComparisonUrlKey

/**
 * Selects the client-side parked saves that should render in the Inbox "Pending"
 * section.
 *
 * A parked save exists only on-device: the share/manual save never reached the
 * server, so it has no item ID and cannot appear in a server-fetched list. Without
 * this projection an offline share is invisible in the Inbox, which reads as data
 * loss.
 *
 * A parked row is hidden once the server already represents it — by resolved item ID
 * first, then by loose URL match. "Represented" means present anywhere in the Inbox
 * response, ready or still processing: either way the user can see it, so showing the
 * parked row too would duplicate it.
 *
 * Newest first: a parked save is the most recent thing the user did.
 */
internal fun projectParkedSavesForInbox(
    parkedSaves: List<PendingManualSaveItem>,
    serverItems: List<PlaybackQueueItem>,
): List<PendingManualSaveItem> {
    if (parkedSaves.isEmpty()) return emptyList()
    val serverItemIds = serverItems.mapTo(mutableSetOf()) { it.itemId }
    val serverUrlKeys = serverItems.mapNotNullTo(mutableSetOf()) {
        normalizePendingComparisonUrlKey(it.url)
    }
    return parkedSaves
        .filter { parked ->
            val resolvedItemId = parked.resolvedItemId
            if (resolvedItemId != null) {
                return@filter resolvedItemId !in serverItemIds
            }
            val urlKey = normalizePendingComparisonUrlKey(parked.urlInput)
                ?: return@filter true
            urlKey !in serverUrlKeys
        }
        .sortedByDescending { it.createdAtMs }
}

/**
 * Whether the Inbox "Pending" section should be expanded.
 *
 * Parked rows force it open so an offline share is visible on arrival, but a user who
 * collapsed the section stays collapsed until a *new* parked save shows up — this is
 * only ever consulted on the empty -> non-empty edge, never to auto-collapse.
 */
internal fun shouldAutoExpandPending(parkedCount: Int, previouslyExpanded: Boolean): Boolean =
    parkedCount > 0 || previouslyExpanded
