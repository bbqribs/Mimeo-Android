package com.mimeo.android.ui.queue

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.PlaybackQueueItem
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

private enum class QueueSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    PROGRESS_HIGH("Progress"),
    PROGRESS_LOW("Least progress"),
    TITLE_AZ("Title A-Z"),
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
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val lastQueueFetchDebug by vm.lastQueueFetchDebug.collectAsState()
    val actionScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(QueueFilterChip.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(QueueSortOption.NEWEST) }
    var showQueueFetchDebug by rememberSaveable { mutableStateOf(false) }
    var hasRefreshProblem by rememberSaveable { mutableStateOf(false) }
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
    val playlistChoices = playlistPickerItem?.let { target ->
        playlists.map { playlist ->
            PlaylistPickerChoice(
                playlistId = playlist.id,
                playlistName = playlist.name,
                isMember = vm.isItemInPlaylist(target.itemId, playlist.id),
            )
        }
    }.orEmpty()
    val filteredItems = items.filter { item ->
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
    val displayedItems = when (selectedSort) {
        QueueSortOption.NEWEST -> filteredItems.sortedByDescending { it.createdAt ?: "" }
        QueueSortOption.OLDEST -> filteredItems.sortedBy { it.createdAt ?: "" }
        QueueSortOption.PROGRESS_HIGH -> filteredItems.sortedByDescending { it.furthestPercent }
        QueueSortOption.PROGRESS_LOW -> filteredItems.sortedBy { it.furthestPercent }
        QueueSortOption.TITLE_AZ -> filteredItems.sortedBy { (it.title ?: it.url).lowercase() }
    }
    val emptyStateMessage = when {
        loading -> null
        items.isEmpty() && settings.selectedPlaylistId != null -> "No items yet in \"$selectedPlaylistName\"."
        items.isEmpty() -> "No items in Smart queue yet. Share a link to add one."
        displayedItems.isEmpty() && searchQuery.isNotBlank() ->
            "No results for \"$searchQuery\" in $selectedPlaylistName."
        displayedItems.isEmpty() && selectedFilter != QueueFilterChip.ALL ->
            "No items match the ${selectedFilter.label.lowercase()} filter."
        displayedItems.isEmpty() -> "No items match the current search/filter."
        else -> null
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
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Queue: $selectedPlaylistName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(
                                painter = painterResource(id = R.drawable.msr_search_24),
                                contentDescription = if (searchExpanded) "Close search" else "Search queue",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        RefreshActionButton(
                            state = refreshActionState,
                            showConnectivityIssue = offline || hasRefreshProblem,
                            onClick = {
                                if (refreshActionState == RefreshActionVisualState.Refreshing) return@RefreshActionButton
                                actionScope.launch {
                                    refreshActionState = RefreshActionVisualState.Refreshing
                                    val result = vm.loadQueueOnce()
                                    hasRefreshProblem = result.isFailure
                                    refreshActionState = if (result.isSuccess) {
                                        RefreshActionVisualState.Success
                                    } else {
                                        RefreshActionVisualState.Failure
                                    }
                                    delay(700)
                                    if (
                                        refreshActionState == RefreshActionVisualState.Success ||
                                        refreshActionState == RefreshActionVisualState.Failure
                                    ) {
                                        refreshActionState = RefreshActionVisualState.Idle
                                    }
                                }
                            },
                            contentDescription = "Refresh queue and sync progress",
                        )
                        Box {
                            IconButton(onClick = { playlistMenuExpanded = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.msr_swap_horiz_24),
                                    contentDescription = "Switch queue",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.msr_sort_24),
                                    contentDescription = "Sort queue: ${selectedSort.label}",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                QueueSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            selectedSort = option
                                            sortMenuExpanded = false
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
                                        modifier = Modifier.size(24.dp),
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
                }
            }
        }
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

        if (loading) {
            CircularProgressIndicator()
        }
        emptyStateMessage?.let { message ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(
                items = displayedItems,
                key = { _, item -> item.itemId },
            ) { index, item ->
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
                if (index < displayedItems.lastIndex) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        )
                    }
                }
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
    val source = item.host?.ifBlank { null } ?: "Unknown source"
    val progress = item.progressPercent
    val isDone = item.furthestPercent >= DONE_PERCENT_THRESHOLD

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onExpandMenu,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Item actions",
                            modifier = Modifier.size(20.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = source,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        painter = painterResource(
                            id = if (isDone) R.drawable.ic_book_closed_24 else R.drawable.ic_book_open_24,
                        ),
                        contentDescription = if (isDone) "Done" else "Not done",
                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                    if (cached) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_check_circle_24),
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
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
