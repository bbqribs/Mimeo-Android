package com.mimeo.android.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import kotlinx.coroutines.launch

private const val DONE_PERCENT_THRESHOLD = 98

private enum class QueueFilterChip(val label: String, val enabled: Boolean = true) {
    ALL("All"),
    UNREAD("Unread"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
    ARCHIVED("Archived", enabled = false),
}

@Composable
fun QueueScreen(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    focusItemId: Int? = null,
    onOpenPlayer: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val items by vm.queueItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val actionScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(QueueFilterChip.ALL) }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.loadQueue()
        vm.flushPendingProgress()
    }

    LaunchedEffect(focusItemId) {
        pendingFocusId = focusItemId ?: -1
    }

    val selectedPlaylistName = settings.selectedPlaylistId?.let { id ->
        playlists.firstOrNull { it.id == id }?.name
    } ?: "All"
    val playlistChoices = playlistPickerItem?.let { target ->
        playlists.map { playlist ->
            PlaylistPickerChoice(
                playlistId = playlist.id,
                playlistName = playlist.name,
                isMember = vm.isItemInPlaylist(target.itemId, playlist.id),
            )
        }
    }.orEmpty()
    val displayedItems = items.filter { item ->
        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            val query = searchQuery.trim().lowercase()
            item.title.orEmpty().lowercase().contains(query) ||
                item.host.orEmpty().lowercase().contains(query) ||
                item.url.lowercase().contains(query)
        }
        val matchesFilter = when (selectedFilter) {
            QueueFilterChip.ALL -> true
            QueueFilterChip.UNREAD -> item.furthestPercent <= 0
            QueueFilterChip.IN_PROGRESS -> item.furthestPercent in 1 until DONE_PERCENT_THRESHOLD
            QueueFilterChip.DONE -> item.furthestPercent >= DONE_PERCENT_THRESHOLD
            QueueFilterChip.ARCHIVED -> false
        }
        matchesSearch && matchesFilter
    }

    LaunchedEffect(displayedItems, pendingFocusId) {
        if (pendingFocusId <= 0) return@LaunchedEffect
        val index = displayedItems.indexOfFirst { it.itemId == pendingFocusId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            pendingFocusId = -1
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        playlistMutationMessage?.let { message ->
            StatusBanner(
                stateLabel = if (message.contains("Unauthorized", ignoreCase = true)) "Auth" else "Offline",
                summary = message,
                detail = null,
                onRetry = { playlistMutationMessage = null },
                onDiagnostics = onOpenDiagnostics,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                AssistChip(
                    onClick = { playlistMenuExpanded = true },
                    label = { Text(selectedPlaylistName) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(android.R.drawable.arrow_down_float),
                            contentDescription = "Choose playlist",
                        )
                    },
                )
                DropdownMenu(
                    expanded = playlistMenuExpanded,
                    onDismissRequest = { playlistMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = {
                            playlistMenuExpanded = false
                            vm.selectPlaylist(null)
                        },
                    )
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text(playlist.name) },
                            onClick = {
                                playlistMenuExpanded = false
                                vm.selectPlaylist(playlist.id)
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_search),
                        contentDescription = if (searchExpanded) "Close search" else "Search queue",
                    )
                }
            }
        }

        if (searchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Search Up Next") },
                )
                TextButton(
                    modifier = Modifier.width(72.dp),
                    onClick = {
                        searchQuery = ""
                        searchExpanded = false
                    },
                ) {
                    Text("Close")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueFilterChip.entries.forEach { chip ->
                FilterChip(
                    selected = selectedFilter == chip,
                    onClick = { selectedFilter = chip },
                    enabled = chip.enabled,
                    label = { Text(chip.label) },
                )
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }
        if (items.isEmpty() && settings.selectedPlaylistId != null && !loading) {
            Text("No items yet in this playlist.")
        }
        if (displayedItems.isEmpty() && items.isNotEmpty() && !loading) {
            Text("No items match the current search/filter.")
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(displayedItems, key = { it.itemId }) { item ->
                QueueItemCard(
                    item = item,
                    cached = cachedItemIds.contains(item.itemId),
                    onOpenPlayer = {
                        vm.startNowPlayingSession(item.itemId)
                        onOpenPlayer(item.itemId)
                    },
                    onOpenPlaylistPicker = {
                        vm.refreshPlaylists()
                        playlistPickerItem = item
                    },
                    isMenuExpanded = rowMenuItemId == item.itemId,
                    onDismissMenu = { rowMenuItemId = -1 },
                    onExpandMenu = { rowMenuItemId = item.itemId },
                )
            }
        }
    }

    playlistPickerItem?.let { target ->
        PlaylistPickerDialog(
            itemTitle = target.title?.ifBlank { null } ?: target.url,
            playlistChoices = playlistChoices,
            isLoading = false,
            onDismiss = { playlistPickerItem = null },
            onTogglePlaylist = { choice ->
                actionScope.launch {
                    vm.togglePlaylistMembership(target.itemId, choice.playlistId)
                        .onSuccess { result ->
                            val verb = if (result.added) "Added to" else "Removed from"
                            playlistPickerItem = null
                            onShowSnackbar("$verb ${choice.playlistName}", null, null)
                            playlistMutationMessage = null
                        }
                        .onFailure { error ->
                            playlistPickerItem = null
                            playlistMutationMessage = friendlyPlaylistError(error)
                            onShowSnackbar(
                                friendlyPlaylistError(error),
                                "Diagnostics",
                                "open_diagnostics",
                            )
                        }
                }
            },
        )
    }
}

@Composable
private fun QueueItemCard(
    item: PlaybackQueueItem,
    cached: Boolean,
    onOpenPlayer: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onExpandMenu: () -> Unit,
) {
    val title = item.title?.ifBlank { null } ?: item.url
    val progress = item.progressPercent
    val doneMarker = if (item.furthestPercent >= DONE_PERCENT_THRESHOLD) "done" else "active"
    val cacheMarker = if (cached) "offline-ready" else "needs-network"

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = onExpandMenu) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Item actions",
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Playlists...") },
                            onClick = {
                                onDismissMenu()
                                onOpenPlaylistPicker()
                            },
                        )
                    }
                }
            }
            Text(
                text = "${item.host ?: "-"}  $progress%  $doneMarker  $cacheMarker",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun friendlyPlaylistError(error: Throwable): String {
    return when (error) {
        is ApiException -> {
            when (error.statusCode) {
                401, 403 -> "Unauthorized. Check token, then open Diagnostics."
                else -> "Could not update playlist. Open Diagnostics and retry."
            }
        }
        else -> "Couldn't update playlist. Check connection, then open Diagnostics."
    }
}
