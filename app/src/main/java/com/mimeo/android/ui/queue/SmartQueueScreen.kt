package com.mimeo.android.ui.queue

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimeo.android.ACTION_KEY_UNDO_BATCH
import com.mimeo.android.AppViewModel
import com.mimeo.android.SMART_QUEUE_SESSION_CONTEXT_ID
import com.mimeo.android.ui.library.LibraryBatchAction
import com.mimeo.android.ui.library.LibraryItemsScreen
import com.mimeo.android.ui.library.LibrarySortOption
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
    LaunchedEffect(Unit) {
        if (settings.selectedPlaylistId == null) {
            vm.loadQueueIfNotRecent()
        } else {
            vm.selectPlaylist(null)
        }
    }

    LibraryItemsScreen(
        title = "Smart Queue",
        items = queueItems,
        loading = loading,
        emptyMessage = if (searchQuery.isBlank()) {
            "Smart Queue is empty."
        } else {
            "No Smart Queue items match this search."
        },
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
