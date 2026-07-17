package com.mimeo.android.ui.common

import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.resolveSmartPlaylistIdFromSessionSourceId
import com.mimeo.android.resolveSessionSourcePlaylistId
import com.mimeo.android.smartPlaylistSessionSourceLabel

internal data class SessionSeedSourcePresentation(
    val seededFromLabel: String,
    val currentSourceLabel: String,
)

internal fun resolveSessionSeedSourcePresentation(
    sessionSourcePlaylistId: Int?,
    sessionSeedSourceLabel: String? = null,
    selectedPlaylistId: Int?,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary> = emptyList(),
): SessionSeedSourcePresentation {
    val seededFrom = sessionSeedSourceLabel
        ?.takeIf { it.isNotBlank() }
        ?: if (sessionSourcePlaylistId == null) {
            "Unknown source"
        } else {
            resolveQueueSourceLabel(sessionSourcePlaylistId, playlists, smartPlaylists)
        }
    val currentSource = resolveQueueSourceLabel(
        selectedPlaylistId = resolveSessionSourcePlaylistId(selectedPlaylistId),
        playlists = playlists,
        smartPlaylists = smartPlaylists,
    )
    return SessionSeedSourcePresentation(
        seededFromLabel = seededFrom,
        currentSourceLabel = currentSource,
    )
}

internal fun shouldConfirmReseedFromCurrentSource(
    session: NowPlayingSession?,
    sourceItems: List<PlaybackQueueItem>,
    selectedPlaylistId: Int?,
): Boolean {
    val activeSession = session ?: return false
    val sessionItemIds = activeSession.items.map { it.itemId }
    val sourceItemIds = sourceItems.map { it.itemId }
    val currentSourceId = resolveSessionSourcePlaylistId(selectedPlaylistId)
    return activeSession.sourcePlaylistId != currentSourceId || sessionItemIds != sourceItemIds
}

internal fun replaceUpNextFromHerePromptBody(
    sourceKind: String,
    hasActiveSessionItems: Boolean,
): String {
    return buildString {
        append("This replaces Up Next with a snapshot from the selected $sourceKind through the end of the current order.")
        if (hasActiveSessionItems) {
            append("\n\nCurrently playing item will exit Up Next; its progress is kept.")
        }
    }
}

private fun resolveQueueSourceLabel(
    selectedPlaylistId: Int,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary> = emptyList(),
): String {
    resolveSmartPlaylistIdFromSessionSourceId(selectedPlaylistId)?.let { smartPlaylistId ->
        val smartName = smartPlaylists.firstOrNull { it.id == smartPlaylistId }?.name
        return if (smartName.isNullOrBlank()) {
            "Smart view ($smartPlaylistId)"
        } else {
            smartPlaylistSessionSourceLabel(smartName)
        }
    }
    if (selectedPlaylistId < 0) return "Smart queue"
    return playlists.firstOrNull { it.id == selectedPlaylistId }?.name
        ?: "Playlist ($selectedPlaylistId)"
}
