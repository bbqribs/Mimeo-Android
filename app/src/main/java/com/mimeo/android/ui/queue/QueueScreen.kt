package com.mimeo.android.ui.queue

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.extractFirstHttpUrl
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import java.util.UUID

private const val DONE_PERCENT_THRESHOLD = 98
private const val ACTION_KEY_OPEN_SETTINGS = "open_settings"
internal enum class ManualSaveMode {
    URL,
    TEXT,
}

internal data class ManualSavePrefill(
    val urlInput: String = "",
    val bodyInput: String = "",
)

private data class ManualSavePayload(
    val type: PendingManualSaveType,
    val urlInput: String,
    val titleInput: String?,
    val bodyInput: String?,
    val destinationPlaylistId: Int?,
)

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
    val context = LocalContext.current
    val items by vm.queueItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val offline by vm.queueOffline.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val pendingManualSaves by vm.pendingManualSaves.collectAsState()
    val pendingManualRetryInProgress by vm.pendingManualRetryInProgress.collectAsState()
    val pendingShareFocusItemId by vm.pendingQueueFocusItemId.collectAsState()
    val lastQueueFetchDebug by vm.lastQueueFetchDebug.collectAsState()
    val actionScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val listState = rememberLazyListState()
    val pullRefreshMaxPx = with(density) { 96.dp.toPx() }
    val pullRefreshThresholdPx = pullRefreshMaxPx
    var pullRefreshDistancePx by remember { mutableFloatStateOf(0f) }
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var showPendingSavesHub by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(QueueFilterChip.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(QueueSortOption.NEWEST) }
    var showQueueFetchDebug by rememberSaveable { mutableStateOf(false) }
    var hasRefreshProblem by rememberSaveable { mutableStateOf(false) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var showSaveEntryDialog by remember { mutableStateOf(false) }
    var manualSaveMode by rememberSaveable { mutableStateOf(ManualSaveMode.URL) }
    var manualUrlInput by rememberSaveable { mutableStateOf("") }
    var manualTitleInput by rememberSaveable { mutableStateOf("") }
    var manualBodyInput by rememberSaveable { mutableStateOf("") }
    var manualUrlError by remember { mutableStateOf<String?>(null) }
    var manualBodyError by remember { mutableStateOf<String?>(null) }
    var manualSubmitError by remember { mutableStateOf<String?>(null) }
    var manualSaveInProgress by remember { mutableStateOf(false) }
    var manualSaveJob by remember { mutableStateOf<Job?>(null) }
    var manualActivePayload by remember { mutableStateOf<ManualSavePayload?>(null) }
    var manualSaveAttemptVersion by remember { mutableIntStateOf(0) }
    val manualUrlFocusRequester = remember { FocusRequester() }
    val manualBodyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.loadQueue()
        vm.flushPendingProgress()
    }

    LaunchedEffect(focusItemId, pendingShareFocusItemId) {
        pendingFocusId = pendingShareFocusItemId ?: (focusItemId ?: -1)
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
    val pullRefreshProgress = (pullRefreshDistancePx / pullRefreshThresholdPx).coerceIn(0f, 1f)
    val emptyStateMessage = when {
        loading -> null
        items.isEmpty() && offline && settings.selectedPlaylistId != null ->
            "Offline. Can't refresh \"$selectedPlaylistName\" right now."
        items.isEmpty() && offline ->
            "Offline. Can't refresh Smart Queue right now."
        items.isEmpty() && settings.selectedPlaylistId != null -> "No items yet in \"$selectedPlaylistName\"."
        items.isEmpty() -> "No items in Smart queue yet. Share a link to add one."
        displayedItems.isEmpty() && searchQuery.isNotBlank() ->
            "No results for \"$searchQuery\" in $selectedPlaylistName."
        displayedItems.isEmpty() && selectedFilter != QueueFilterChip.ALL ->
            "No items match the ${selectedFilter.label.lowercase()} filter."
        displayedItems.isEmpty() -> "No items match the current search/filter."
        else -> null
    }
    suspend fun refreshQueueContent() {
        if (refreshActionState == RefreshActionVisualState.Refreshing) return
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
    val pullToRefreshConnection = remember(
        listState,
        refreshActionState,
        pullRefreshMaxPx,
        pullRefreshThresholdPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (refreshActionState == RefreshActionVisualState.Refreshing) return Offset.Zero
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!isAtTop) return Offset.Zero

                val nextDistance = (pullRefreshDistancePx + available.y).coerceIn(0f, pullRefreshMaxPx)
                val consumedY = nextDistance - pullRefreshDistancePx
                if (consumedY == 0f) return Offset.Zero
                pullRefreshDistancePx = nextDistance
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (
                    refreshActionState != RefreshActionVisualState.Refreshing &&
                    pullRefreshDistancePx >= pullRefreshThresholdPx
                ) {
                    actionScope.launch { refreshQueueContent() }
                }
                pullRefreshDistancePx = 0f
                return Velocity.Zero
            }
        }
    }

    val retryPendingItem: (PendingManualSaveItem) -> Unit = { item ->
        actionScope.launch {
            if (offline) {
                onShowSnackbar("Still offline. Pending saves kept.", null, null)
                return@launch
            }
            val retryResult = vm.retryPendingManualSave(item.id) ?: return@launch
            if (shouldSurfacePendingRetrySnackbar(retryResult)) {
                val actionLabel = if (retryResult.opensSettings) "Open Settings" else null
                val actionKey = if (retryResult.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                onShowSnackbar(retryResult.notificationText, actionLabel, actionKey)
            }
        }
    }
    val retryAllPendingItems: () -> Unit = {
        actionScope.launch {
            if (offline) {
                onShowSnackbar("Still offline. Pending saves kept.", null, null)
                return@launch
            }
            val retrySuccessCount = vm.retryAllPendingManualSaves()
            if (retrySuccessCount > 0) {
                onShowSnackbar("Retried $retrySuccessCount pending saves", null, null)
            }
        }
    }

    LaunchedEffect(displayedItems, pendingFocusId) {
        if (pendingFocusId <= 0) return@LaunchedEffect
        val index = displayedItems.indexOfFirst { it.itemId == pendingFocusId }
        if (index >= 0) {
            val focusedItemId = pendingFocusId
            listState.animateScrollToItem(index)
            vm.consumePendingQueueFocusItemId(focusedItemId)
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
                                actionScope.launch { refreshQueueContent() }
                            },
                            contentDescription = "Refresh queue and sync progress",
                            pullProgress = pullRefreshProgress,
                        )
                        IconButton(
                            enabled = !manualSaveInProgress,
                            onClick = {
                                val prefill = buildManualSavePrefill(readClipboardText(context))
                                manualSaveMode = ManualSaveMode.URL
                                manualUrlInput = prefill.urlInput
                                manualTitleInput = ""
                                manualBodyInput = prefill.bodyInput
                                manualUrlError = null
                                manualBodyError = null
                                manualSubmitError = null
                                showSaveEntryDialog = true
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.msr_add_24),
                                contentDescription = "Save URL",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Box {
                            IconButton(onClick = { playlistMenuExpanded = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.msr_list_layers_24),
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
                                    painter = painterResource(id = R.drawable.msr_sort_layers_24),
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
                                            if (pendingManualSaves.isEmpty()) {
                                                "Pending saves"
                                            } else {
                                                "Pending saves (${pendingManualSaves.size})"
                                            },
                                        )
                                    },
                                    onClick = {
                                        showPendingSavesHub = true
                                        topActionsMenuExpanded = false
                                    },
                                )
                                if (BuildConfig.DEBUG) {
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
        if (pendingManualSaves.isNotEmpty()) {
            PendingManualRetryCard(
                pendingItems = pendingManualSaves,
                retryInProgress = pendingManualRetryInProgress,
                onRetry = retryPendingItem,
                onRetryAll = retryAllPendingItems,
                onDismiss = { item -> vm.removePendingManualSave(item.id) },
                onClearAll = { vm.clearPendingManualSaves() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f, fill = true)
                .nestedScroll(pullToRefreshConnection),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (pullRefreshDistancePx > 0f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pullRefreshDistancePx.toDp() }),
                )
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

    if (showSaveEntryDialog) {
        val canSubmit = canSubmitManualSave(
            mode = manualSaveMode,
            urlInput = manualUrlInput,
            bodyInput = manualBodyInput,
            inProgress = manualSaveInProgress,
        )
        suspend fun submitManualEntry() {
            manualSubmitError = null
            manualSaveJob = currentCoroutineContext()[Job]
            val extractedUrl = resolveManualSaveUrl(manualUrlInput)
            if (manualSaveMode == ManualSaveMode.URL && extractedUrl == null) {
                manualUrlError = "Enter a valid http(s) URL"
                manualSaveJob = null
                return
            }
            manualUrlError = null

            val normalizedBody = if (manualSaveMode == ManualSaveMode.TEXT) {
                normalizeManualTextBody(manualBodyInput).also {
                    if (it == null) {
                        manualBodyError = "Paste text is required"
                    }
                }
            } else {
                null
            }
            if (manualSaveMode == ManualSaveMode.TEXT && normalizedBody == null) {
                manualSaveJob = null
                return
            }
            manualBodyError = null
            val payload = if (manualSaveMode == ManualSaveMode.TEXT) {
                ManualSavePayload(
                    type = PendingManualSaveType.TEXT,
                    urlInput = resolveManualTextSaveUrl(
                        urlInput = manualUrlInput,
                        titleInput = manualTitleInput,
                        bodyInput = normalizedBody.orEmpty(),
                    ),
                    titleInput = manualTitleInput.trim().takeIf { it.isNotEmpty() },
                    bodyInput = normalizedBody,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                )
            } else {
                ManualSavePayload(
                    type = PendingManualSaveType.URL,
                    urlInput = extractedUrl.orEmpty(),
                    titleInput = null,
                    bodyInput = null,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                )
            }
            manualActivePayload = payload
            fun queueCurrentManualSaveAndClose(statusMessage: String) {
                vm.queueFailedManualSave(
                    type = payload.type,
                    urlInput = payload.urlInput,
                    titleInput = payload.titleInput,
                    bodyInput = payload.bodyInput,
                    result = ShareSaveResult.NetworkError,
                    destinationPlaylistId = payload.destinationPlaylistId,
                )
                showSaveEntryDialog = false
                manualUrlInput = ""
                manualTitleInput = ""
                manualBodyInput = ""
                manualUrlError = null
                manualBodyError = null
                manualSubmitError = null
                onShowSnackbar(statusMessage, null, null)
            }
            if (offline) {
                queueCurrentManualSaveAndClose("Added to pending manual saves")
                manualSaveJob = null
                return
            }
            val attemptVersion = manualSaveAttemptVersion + 1
            manualSaveAttemptVersion = attemptVersion
            manualSaveInProgress = true
            try {
                val result = if (manualSaveMode == ManualSaveMode.TEXT) {
                    vm.saveManualTextFromUpNext(
                        urlInput = payload.urlInput,
                        titleInput = payload.titleInput,
                        bodyInput = payload.bodyInput.orEmpty(),
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                } else {
                    vm.saveUrlFromUpNext(
                        rawInput = payload.urlInput,
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                }
                if (manualSaveAttemptVersion != attemptVersion) {
                    return
                }
                val actionLabel = if (result.opensSettings) "Open Settings" else null
                val actionKey = if (result.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                onShowSnackbar(result.notificationText, actionLabel, actionKey)
                if (isManualSaveSuccess(result)) {
                    vm.removeMatchingPendingManualSave(
                        type = payload.type,
                        urlInput = payload.urlInput,
                        titleInput = payload.titleInput,
                        bodyInput = payload.bodyInput,
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                    showSaveEntryDialog = false
                    manualUrlInput = ""
                    manualTitleInput = ""
                    manualBodyInput = ""
                    manualSubmitError = null
                } else {
                    vm.queueFailedManualSave(
                        type = payload.type,
                        urlInput = payload.urlInput,
                        titleInput = payload.titleInput,
                        bodyInput = payload.bodyInput,
                        result = result,
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                    manualSubmitError = result.notificationText
                }
            } catch (_: CancellationException) {
                return
            } finally {
                if (manualSaveAttemptVersion == attemptVersion) {
                    manualSaveInProgress = false
                    manualSaveJob = null
                    manualActivePayload = null
                }
            }
        }

        LaunchedEffect(showSaveEntryDialog) {
            if (showSaveEntryDialog) {
                manualUrlFocusRequester.requestFocus()
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!manualSaveInProgress) {
                    showSaveEntryDialog = false
                    manualUrlError = null
                    manualBodyError = null
                    manualSubmitError = null
                }
            },
            title = { Text("Save Item") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = manualSaveMode == ManualSaveMode.URL,
                            enabled = !manualSaveInProgress,
                            onClick = {
                                manualSaveMode = ManualSaveMode.URL
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            label = { Text("Save URL") },
                        )
                        FilterChip(
                            selected = manualSaveMode == ManualSaveMode.TEXT,
                            enabled = !manualSaveInProgress,
                            onClick = {
                                manualSaveMode = ManualSaveMode.TEXT
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            label = { Text("Paste Text") },
                        )
                    }
                    OutlinedTextField(
                        value = manualUrlInput,
                        onValueChange = {
                            manualUrlInput = it
                            manualUrlError = null
                            manualSubmitError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(manualUrlFocusRequester),
                        singleLine = true,
                        enabled = !manualSaveInProgress,
                        label = {
                            Text(
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    "Article URL (optional)"
                                } else {
                                    "Article URL"
                                },
                            )
                        },
                        placeholder = {
                            Text(
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    "https://example.com/article (optional)"
                                } else {
                                    "https://example.com/article"
                                },
                            )
                        },
                        isError = manualUrlError != null,
                        supportingText = {
                            manualUrlError?.let { Text(it) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = if (manualSaveMode == ManualSaveMode.URL) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    manualBodyFocusRequester.requestFocus()
                                }
                            },
                            onDone = {
                                if (canSubmit) {
                                    actionScope.launch { submitManualEntry() }
                                }
                            },
                        ),
                    )
                    manualSubmitError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (manualSaveMode == ManualSaveMode.TEXT) {
                        OutlinedTextField(
                            value = manualTitleInput,
                            onValueChange = { manualTitleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !manualSaveInProgress,
                            label = { Text("Title (optional)") },
                        )
                        OutlinedTextField(
                            value = manualBodyInput,
                            onValueChange = {
                                manualBodyInput = it
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 320.dp)
                                .focusRequester(manualBodyFocusRequester),
                            minLines = 8,
                            maxLines = 16,
                            enabled = !manualSaveInProgress,
                            label = { Text("Body text") },
                            placeholder = { Text("Paste article text here") },
                            isError = manualBodyError != null,
                            supportingText = {
                                manualBodyError?.let { Text(it) }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canSubmit) {
                                        actionScope.launch { submitManualEntry() }
                                    }
                                },
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        actionScope.launch { submitManualEntry() }
                    },
                ) {
                    Text(if (manualSaveInProgress) "Saving..." else "Save")
                }
            },
            dismissButton = {
                if (manualSaveInProgress && offline) {
                    TextButton(
                        onClick = {
                            val payload = manualActivePayload ?: return@TextButton
                            vm.queueFailedManualSave(
                                type = payload.type,
                                urlInput = payload.urlInput,
                                titleInput = payload.titleInput,
                                bodyInput = payload.bodyInput,
                                result = ShareSaveResult.NetworkError,
                                destinationPlaylistId = payload.destinationPlaylistId,
                            )
                            manualSaveAttemptVersion += 1
                            manualSaveJob?.cancel()
                            manualSaveInProgress = false
                            showSaveEntryDialog = false
                            manualUrlInput = ""
                            manualTitleInput = ""
                            manualBodyInput = ""
                            manualActivePayload = null
                            manualUrlError = null
                            manualBodyError = null
                            manualSubmitError = null
                            onShowSnackbar("Added to pending manual saves", null, null)
                        },
                    ) {
                        Text("Queue now")
                    }
                } else {
                    TextButton(
                        enabled = !manualSaveInProgress,
                        onClick = {
                            showSaveEntryDialog = false
                            manualUrlError = null
                            manualBodyError = null
                            manualSubmitError = null
                        },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    if (showPendingSavesHub) {
        AlertDialog(
            onDismissRequest = { showPendingSavesHub = false },
            title = { Text("Pending saves") },
            text = {
                if (pendingManualSaves.isEmpty()) {
                    Text("No pending saves.")
                } else {
                    PendingManualRetryCard(
                        pendingItems = pendingManualSaves,
                        retryInProgress = pendingManualRetryInProgress,
                        onRetry = retryPendingItem,
                        onRetryAll = retryAllPendingItems,
                        onDismiss = { item -> vm.removePendingManualSave(item.id) },
                        onClearAll = { vm.clearPendingManualSaves() },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPendingSavesHub = false }) {
                    Text("Close")
                }
            },
        )
    }

}

private fun normalizeSearchText(value: String): String {
    return value.filter { it.isLetterOrDigit() }
}

internal fun resolveManualSaveUrl(input: String): String? {
    return extractFirstHttpUrl(input.trim())
}

internal fun normalizeManualTextBody(input: String): String? {
    return input.trim().takeIf { it.isNotEmpty() }
}

internal fun canSubmitManualSave(
    mode: ManualSaveMode,
    urlInput: String,
    bodyInput: String,
    inProgress: Boolean,
): Boolean {
    if (inProgress) return false
    return when (mode) {
        ManualSaveMode.URL -> resolveManualSaveUrl(urlInput) != null
        ManualSaveMode.TEXT -> bodyInput.isNotBlank()
    }
}

internal fun isManualSaveSuccess(result: ShareSaveResult): Boolean {
    return result is ShareSaveResult.Saved
}

internal fun shouldSurfacePendingRetrySnackbar(result: ShareSaveResult): Boolean {
    return when (result) {
        is ShareSaveResult.Saved,
        ShareSaveResult.MissingToken,
        ShareSaveResult.Unauthorized,
        ShareSaveResult.NoValidUrl,
        -> true
        else -> false
    }
}

internal fun buildManualSavePrefill(clipboardText: String?): ManualSavePrefill {
    val raw = clipboardText?.trim().orEmpty()
    if (raw.isEmpty()) {
        return ManualSavePrefill()
    }
    val url = resolveManualSaveUrl(raw)
    return if (url != null) {
        ManualSavePrefill(urlInput = url)
    } else {
        ManualSavePrefill(bodyInput = raw)
    }
}

internal fun resolveManualTextSaveUrl(
    urlInput: String,
    titleInput: String,
    bodyInput: String,
): String {
    return resolveManualSaveUrl(urlInput) ?: buildManualTextFallbackUrl(titleInput, bodyInput)
}

private fun buildManualTextFallbackUrl(titleInput: String, bodyInput: String): String {
    val seedSource = titleInput.trim().ifBlank {
        bodyInput.trim().lineSequence().firstOrNull().orEmpty()
    }
    val slug = seedSource
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
        .ifBlank { "manual-text" }
    val suffix = UUID.randomUUID().toString().substring(0, 8)
    return "https://manual.mimeo.local/$slug-$suffix"
}

private fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val item = manager.primaryClip?.getItemAt(0) ?: return null
    return item.coerceToText(context)?.toString()
}

@Composable
private fun PendingManualRetryCard(
    pendingItems: List<PendingManualSaveItem>,
    retryInProgress: Boolean,
    onRetry: (PendingManualSaveItem) -> Unit,
    onRetryAll: () -> Unit,
    onDismiss: (PendingManualSaveItem) -> Unit,
    onClearAll: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pending saves (${pendingItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !retryInProgress, onClick = onRetryAll) {
                        Text("Retry all")
                    }
                    TextButton(enabled = !retryInProgress, onClick = onClearAll) {
                        Text("Clear all")
                    }
                }
            }
            pendingItems.forEach { item ->
                val titleLine = when (item.type) {
                    PendingManualSaveType.TEXT -> item.titleInput?.takeIf { it.isNotBlank() } ?: "Paste Text"
                    PendingManualSaveType.URL -> if (item.source == PendingSaveSource.SHARE) {
                        "Shared URL"
                    } else {
                        "Save URL"
                    }
                }
                val bodyPreview = item.bodyInput?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = titleLine, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = item.urlInput.ifBlank { "(no URL provided)" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.destinationPlaylistId?.let { "Destination: Playlist $it" } ?: "Destination: Smart Queue",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    bodyPreview?.let { preview ->
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${item.lastFailureMessage} (retries: ${item.retryCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(enabled = !retryInProgress, onClick = { onRetry(item) }) {
                            Text("Retry")
                        }
                        TextButton(enabled = !retryInProgress, onClick = { onDismiss(item) }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
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
    val source = item.host?.ifBlank { null } ?: "Unknown source"
    val progress = item.progressPercent
    val isDone = item.furthestPercent >= DONE_PERCENT_THRESHOLD
    val primaryTextColor = if (cached) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    }
    val secondaryTextColor = if (cached) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
    }

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
                    color = primaryTextColor,
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
                            tint = secondaryTextColor,
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
                    color = secondaryTextColor,
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
                        color = secondaryTextColor,
                    )
                    Icon(
                        painter = painterResource(
                            id = if (isDone) R.drawable.ic_book_closed_24 else R.drawable.ic_book_open_24,
                        ),
                        contentDescription = if (isDone) "Done" else "Not done",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp),
                    )
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
