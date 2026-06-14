package com.mimeo.android.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimeo.android.ACTION_KEY_UNDO_BATCH
import com.mimeo.android.AppViewModel
import com.mimeo.android.SMART_QUEUE_SESSION_CONTEXT_ID
import com.mimeo.android.ui.library.LibraryBatchAction
import com.mimeo.android.ui.library.LibraryItemsScreen
import com.mimeo.android.ui.library.LibrarySortOption
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import kotlinx.coroutines.launch

@Composable
fun SmartQueueScreen(
    vm: AppViewModel,
    onOpenPlayer: (Int) -> Unit,
    jumpPillBottomClearance: Dp = 0.dp,
) {
    val settings by vm.settings.collectAsState()
    val queueItems by vm.queueItems.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val hasMorePages by vm.queueHasMorePages.collectAsState()
    val queueTotalCount by vm.queueTotalCount.collectAsState()
    val activeScopeLimit by vm.queueActiveScopeLimit.collectAsState()
    val reorderAllowed by vm.smartQueueReorderAllowed.collectAsState()
    val reorderUnavailableReason by vm.smartQueueReorderUnavailableReason.collectAsState()
    val reorderSaving by vm.smartQueueReorderSaving.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val actionScope = rememberCoroutineScope()
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.SMART_QUEUE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val dragReorderEnabled = smartQueueDragReorderEnabled(
        backendReorderAllowed = reorderAllowed,
        searchQuery = searchQuery,
        sortOption = sortOption,
        hasMorePages = hasMorePages,
        itemCount = queueItems.size,
        reorderSaving = reorderSaving,
    )
    val showReorderHandle = sortOption == LibrarySortOption.SMART_QUEUE &&
        searchQuery.isBlank() &&
        queueItems.size > 1
    val scopeStatusLabel = smartQueueScopeStatusLabel(
        itemCount = queueItems.size,
        totalCount = queueTotalCount,
        activeScopeLimit = activeScopeLimit,
    )
    val reorderStatusLabel = if (dragReorderEnabled && !reorderSaving) {
        ""
    } else {
        smartQueueReorderStatusLabel(
            dragReorderEnabled = dragReorderEnabled,
            backendReorderAllowed = reorderAllowed,
            unavailableReason = reorderUnavailableReason,
            searchQuery = searchQuery,
            sortOption = sortOption,
            hasMorePages = hasMorePages,
            itemCount = queueItems.size,
            loading = loading,
            reorderSaving = reorderSaving,
        )
    }
    val headerStatusLabel = listOf(scopeStatusLabel, reorderStatusLabel)
        .filter { it.isNotBlank() }
        .joinToString(" | ")
    LaunchedEffect(Unit) {
        if (settings.selectedPlaylistId == null) {
            vm.loadQueueIfNotRecent()
        } else {
            vm.selectPlaylist(null)
        }
    }

    LibraryItemsScreen(
        title = "Smart Queue",
        pullToRefreshEnabled = true,
        items = queueItems,
        loading = loading,
        emptyMessage = if (searchQuery.isBlank()) {
            "Smart Queue is empty."
        } else {
            "No Smart Queue items match this search."
        },
        header = { SmartQueueSourceHeader(statusLabel = headerStatusLabel) },
        sortOption = sortOption,
        availableSorts = emptyList(),
        searchQuery = searchQuery,
        searchPlaceholder = "Search...",
        clientSideSearch = true,
        showDragReorderHandle = showReorderHandle,
        dragReorderEnabled = dragReorderEnabled,
        dragReorderUnavailableReason = reorderUnavailableReason,
        onDragReorder = { orderedItemIds -> vm.reorderSmartQueueItems(orderedItemIds) },
        batchActions = listOf(
            LibraryBatchAction("Archive", Icons.Default.Archive, "archive"),
            LibraryBatchAction("Move to Bin", Icons.Default.Delete, "bin"),
            LibraryBatchAction("Favorite", Icons.Default.FavoriteBorder, "favorite_toggle"),
        ),
        playlists = playlists,
        onBatchAddToPlaylist = { playlistId, playlistName, itemIds ->
            actionScope.launch {
                vm.batchAddToPlaylist(playlistId, playlistName, itemIds.toList())
            }
        },
        onBatchAddToUpNext = { itemIds -> vm.playLastBatch(itemIds) },
        onSortChange = { option ->
            sortOption = option
            vm.updateQueueServerSort(option.sortField, option.sortDir)
        },
        onSearchQueryChange = { query -> searchQuery = query },
        onSearchSubmit = {},
        onRefresh = { vm.loadQueueOnce(autoRetryPendingSaves = false) },
        onOpenItem = onOpenPlayer,
        onBatchAction = { action, itemIds ->
            actionScope.launch {
                vm.batchLibraryItems(action, itemIds.toList(), ACTION_KEY_UNDO_BATCH)
                vm.loadQueueOnce(autoRetryPendingSaves = false)
            }
        },
        nowPlayingHasItems = nowPlayingSession?.items?.isNotEmpty() == true,
        onPlayAll = { items ->
            vm.playAllFromSnapshot(
                sourceItems = items,
                sourcePlaylistId = SMART_QUEUE_SESSION_CONTEXT_ID,
                sourceLabel = "Smart Queue",
            )
        },
        onPlayNow = { vm.playNow(it) },
        onPlayNext = { vm.playNext(it) },
        onPlayLast = { vm.playLast(it) },
        onPlayFromHere = { items, selectedItemId ->
            vm.playFromHereSnapshot(
                sourceItems = items,
                selectedItemId = selectedItemId,
                sourcePlaylistId = SMART_QUEUE_SESSION_CONTEXT_ID,
                sourceLabel = "Smart Queue",
            )
        },
        jumpPillBottomClearance = jumpPillBottomClearance,
    )
}

@Composable
private fun SmartQueueSourceHeader(statusLabel: String) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val ruleColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isV1) mColors.surface else MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(
                    color = ruleColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Smart Queue",
            style = if (isV1) mTypography.section else MaterialTheme.typography.labelLarge,
            color = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurface,
        )
        if (statusLabel.isNotBlank()) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
