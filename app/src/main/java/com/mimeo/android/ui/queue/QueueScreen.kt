package com.mimeo.android.ui.queue

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import kotlinx.coroutines.delay
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
    val offline by vm.queueOffline.collectAsState()
    val pendingCount by vm.pendingProgressCount.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val sessionIssueMessage by vm.sessionIssueMessage.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val syncBadgeState by vm.progressSyncBadgeState.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val lastQueueFetchDebug by vm.lastQueueFetchDebug.collectAsState()
    val actionScope = rememberCoroutineScope()

    var showClearSessionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(QueueFilterChip.ALL) }
    var showQueueFetchDebug by rememberSaveable { mutableStateOf(false) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }

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
    } ?: "Smart queue"
    val syncLabel = when (syncBadgeState) {
        ProgressSyncBadgeState.SYNCED -> "Synced"
        ProgressSyncBadgeState.QUEUED -> "Queued"
        ProgressSyncBadgeState.OFFLINE -> "Offline"
    }
    val resumeSummary = vm.nowPlayingSummaryText()
    val resumeItemId = vm.currentNowPlayingItemId()
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
            val needle = searchQuery.trim().lowercase()
            val normalizedNeedle = normalizeSearchText(needle)
            listOf(
                item.title.orEmpty(),
                item.host.orEmpty(),
                item.url,
            ).any { candidate ->
                val lowered = candidate.lowercase()
                lowered.contains(needle) || normalizeSearchText(lowered).contains(normalizedNeedle)
            }
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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_search),
                    contentDescription = if (searchExpanded) "Close search" else "Search queue",
                )
            }
            RefreshActionButton(
                state = refreshActionState,
                onClick = {
                    if (refreshActionState == RefreshActionVisualState.Refreshing) return@RefreshActionButton
                    actionScope.launch {
                        refreshActionState = RefreshActionVisualState.Refreshing
                        val result = vm.loadQueueOnce()
                        refreshActionState = if (result.isSuccess) {
                            RefreshActionVisualState.Success
                        } else {
                            RefreshActionVisualState.Idle
                        }
                        if (result.isSuccess) {
                            delay(700)
                            if (refreshActionState == RefreshActionVisualState.Success) {
                                refreshActionState = RefreshActionVisualState.Idle
                            }
                        }
                    }
                },
                contentDescription = "Refresh queue and sync progress",
            )
            Box {
                AssistChip(
                    onClick = { playlistMenuExpanded = true },
                    label = { Text("Queue: $selectedPlaylistName") },
                )
                DropdownMenu(
                    expanded = playlistMenuExpanded,
                    onDismissRequest = { playlistMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Smart queue") },
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
            if (BuildConfig.DEBUG) {
                Box {
                    IconButton(onClick = { topActionsMenuExpanded = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Queue actions",
                        )
                    }
                    DropdownMenu(
                        expanded = topActionsMenuExpanded,
                        onDismissRequest = { topActionsMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showQueueFetchDebug) {
                                        "Hide debug fetch"
                                    } else {
                                        "Show debug fetch"
                                    },
                                )
                            },
                            onClick = {
                                showQueueFetchDebug = !showQueueFetchDebug
                                topActionsMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Sync: $syncLabel  Pending: $pendingCount",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (BuildConfig.DEBUG && showQueueFetchDebug && lastQueueFetchDebug.statusCode != null) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Debug queue fetch")
                    Text(
                        text = "playlistId=${lastQueueFetchDebug.selectedPlaylistId ?: "smart"} status=${lastQueueFetchDebug.statusCode} responseCount=${lastQueueFetchDebug.responseItemCount} responseContains409=${lastQueueFetchDebug.responseContains409}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "appliedCount=${lastQueueFetchDebug.appliedItemCount} appliedContains409=${lastQueueFetchDebug.appliedContains409} bytes=${lastQueueFetchDebug.responseBytes} hash=${lastQueueFetchDebug.responseHash}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "at=${lastQueueFetchDebug.lastFetchAt}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lastQueueFetchDebug.requestUrl,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "renderedIds=${displayedItems.take(3).joinToString { it.itemId.toString() }}${if (displayedItems.size > 6) " … " else ""}${displayedItems.takeLast(3).joinToString { it.itemId.toString() }}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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

        nowPlayingSession?.let { session ->
            val current = session.currentItem ?: session.items.firstOrNull()
            val title = current?.title?.ifBlank { null } ?: current?.url ?: "Session item"
            val progress = current?.itemId?.let { vm.knownProgressForItem(it) } ?: 0
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Now Playing ${session.currentIndex + 1}/${session.items.size} - $progress%")
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { resumeItemId?.let { onOpenPlayer(it) } },
                            enabled = resumeItemId != null,
                        ) { Text("Resume") }
                        TextButton(
                            onClick = {
                                vm.restartNowPlayingSession()
                                onShowSnackbar("Now Playing session restarted.", null, null)
                            },
                        ) { Text("Restart") }
                        TextButton(onClick = { showClearSessionDialog = true }) { Text("Clear") }
                    }
                    if (resumeSummary != null && resumeItemId != null && displayedItems.none { it.itemId == resumeItemId }) {
                        Text(
                            text = "Current item hidden by queue filters; Resume still works.",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        sessionIssueMessage?.let {
            Text(
                text = it,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
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

    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Clear session?") },
            text = { Text("This removes the persisted Now Playing snapshot.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearNowPlayingSession()
                        onShowSnackbar("Now Playing session cleared.", null, null)
                        showClearSessionDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun normalizeSearchText(value: String): String {
    return value.filter { it.isLetterOrDigit() }
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

private fun isLikelyPhysicalDevice(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val brand = Build.BRAND.lowercase()
    return !(fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        brand.startsWith("generic"))
}
