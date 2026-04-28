package com.mimeo.android.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.ACTION_KEY_OPEN_PLAYLIST_PREFIX
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.SmartPlaylistDetail
import com.mimeo.android.ui.common.DefaultListSurfaceMessage
import com.mimeo.android.ui.common.LibraryItemRow
import com.mimeo.android.ui.common.ListStatusPill
import com.mimeo.android.ui.common.ListSurfaceScaffold
import com.mimeo.android.ui.common.SelectionAffordance
import com.mimeo.android.ui.common.queueCapturePresentation
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
fun SmartPlaylistDetailScreen(
    playlistId: Int,
    vm: AppViewModel,
    onOpenPlayer: (Int) -> Unit,
    onNavigateAfterDelete: () -> Unit,
) {
    val actionScope = rememberCoroutineScope()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    var detail by remember { mutableStateOf<SmartPlaylistDetail?>(null) }
    var pinnedItems by remember { mutableStateOf<List<PlaybackQueueItem>>(emptyList()) }
    var liveItems by remember { mutableStateOf<List<PlaybackQueueItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var selectionActive by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }
    var showSeedConfirmation by remember { mutableStateOf(false) }
    var seedInProgress by remember { mutableStateOf(false) }
    var showFreezeDialog by remember { mutableStateOf(false) }
    var freezeName by remember { mutableStateOf("") }
    var freezeInProgress by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var pinActionInProgress by remember { mutableStateOf(false) }

    fun displayedItems(): List<PlaybackQueueItem> = pinnedItems + liveItems

    fun applyContent(content: AppViewModel.SmartPlaylistContent) {
        detail = content.detail
        pinnedItems = content.pinnedItems
        liveItems = content.liveItems
        selectedIds = selectedIds.intersect(content.items.map { it.itemId }.toSet())
        if (selectedIds.isEmpty()) selectionActive = false
    }

    fun clearSelection() {
        selectionActive = false
        selectedIds = emptySet()
    }

    fun enterSelectionMode(itemId: Int) {
        selectionActive = true
        selectedIds = setOf(itemId)
    }

    fun toggleSelection(itemId: Int) {
        val next = if (itemId in selectedIds) selectedIds - itemId else selectedIds + itemId
        selectedIds = next
        if (next.isEmpty()) selectionActive = false
    }

    suspend fun loadContent(showSpinner: Boolean) {
        if (showSpinner) loading = true
        errorMessage = null
        val result = vm.loadSmartPlaylistContent(playlistId)
        result
            .onSuccess { content ->
                applyContent(content)
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Couldn't load smart playlist."
            }
        loading = false
    }

    suspend fun refreshContent() {
        if (refreshActionState == RefreshActionVisualState.Refreshing) return
        refreshActionState = RefreshActionVisualState.Refreshing
        val result = vm.loadSmartPlaylistContent(playlistId)
        refreshActionState = if (result.isSuccess) {
            val content = result.getOrThrow()
            applyContent(content)
            errorMessage = null
            RefreshActionVisualState.Success
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Couldn't refresh smart playlist."
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

    suspend fun seedUpNextFromSnapshot() {
        val currentDetail = detail ?: return
        if (seedInProgress) return
        seedInProgress = true
        try {
            vm.seedNowPlayingSessionFromSmartPlaylist(
                playlistId = currentDetail.id,
                playlistName = currentDetail.name,
                items = displayedItems(),
            )
                .onSuccess { result ->
                    if (result.rebuiltItemCount > 0) {
                        vm.showSnackbar("Seeded Up Next from ${result.sourceLabel}.")
                    } else {
                        vm.showSnackbar("Cleared Up Next because ${result.sourceLabel} is empty.")
                    }
                }
                .onFailure { error ->
                    vm.showSnackbar(error.message ?: "Couldn't seed Up Next from smart playlist.")
                }
        } finally {
            seedInProgress = false
        }
    }

    suspend fun freezeAsManualSnapshot() {
        val currentDetail = detail ?: return
        if (freezeInProgress) return
        freezeInProgress = true
        try {
            vm.freezeSmartPlaylistAsManual(
                playlistId = currentDetail.id,
                name = freezeName,
            )
                .onSuccess { created ->
                    showFreezeDialog = false
                    freezeName = ""
                    vm.showSnackbar(
                        message = "Created manual snapshot \"${created.name}\". Smart playlist unchanged.",
                        actionLabel = "Open",
                        actionKey = "$ACTION_KEY_OPEN_PLAYLIST_PREFIX${created.id}",
                        duration = SnackbarDuration.Long,
                    )
                }
                .onFailure { error ->
                    vm.showSnackbar(error.message ?: "Couldn't freeze smart playlist.")
                }
        } finally {
            freezeInProgress = false
        }
    }

    suspend fun runPinMutation(
        fallbackMessage: String,
        action: suspend () -> Result<AppViewModel.SmartPlaylistContent>,
    ) {
        if (pinActionInProgress) return
        pinActionInProgress = true
        try {
            action()
                .onSuccess { content ->
                    applyContent(content)
                    errorMessage = null
                }
                .onFailure { error ->
                    val message = error.message ?: fallbackMessage
                    errorMessage = message
                    vm.showSnackbar(message)
                }
        } finally {
            pinActionInProgress = false
        }
    }

    LaunchedEffect(playlistId) {
        vm.refreshSmartPlaylists()
        loadContent(showSpinner = true)
    }

    BackHandler(enabled = selectionActive) { clearSelection() }

    ListSurfaceScaffold(
        modifier = Modifier.fillMaxSize(),
        header = {
            SmartPlaylistHeader(
                detail = detail,
                itemCount = displayedItems().size,
                pinnedCount = pinnedItems.size,
                loading = loading,
                refreshActionState = refreshActionState,
                onRefresh = { actionScope.launch { refreshContent() } },
                seedEnabled = detail != null && !loading && !seedInProgress,
                onSeedUpNext = {
                    if (nowPlayingSession?.items?.isNotEmpty() == true) {
                        showSeedConfirmation = true
                    } else {
                        actionScope.launch { seedUpNextFromSnapshot() }
                    }
                },
                freezeEnabled = detail != null && !loading && !freezeInProgress,
                onFreezeManual = { showFreezeDialog = true },
                editEnabled = detail != null && !loading,
                onEdit = { showEditDialog = true },
                deleteEnabled = detail != null && !loading && !deleteInProgress,
                onDelete = { showDeleteDialog = true },
            )
        },
        selectionBar = if (selectionActive) {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::clearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            val orderedIds = displayedItems()
                                .filter { it.itemId in selectedIds }
                                .map { it.itemId }
                            clearSelection()
                            vm.playLastBatch(orderedIds)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add selected to Up Next",
                        )
                    }
                }
            }
        } else {
            null
        },
        loading = loading,
        empty = displayedItems().isEmpty(),
        loadingContent = { DefaultListSurfaceMessage("Loading smart playlist...") },
        emptyContent = {
            DefaultListSurfaceMessage(
                errorMessage ?: "No items match this live filter yet.",
            )
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (pinnedItems.isNotEmpty()) {
                item(key = "pinned-header") {
                    SmartPlaylistSectionHeader(
                        title = "Pinned",
                        count = pinnedItems.size,
                    )
                }
                items(items = pinnedItems, key = { "pinned-${it.itemId}" }) { item ->
                    val index = pinnedItems.indexOfFirst { it.itemId == item.itemId }
                    SmartPlaylistItemRow(
                        item = item,
                        isPinned = true,
                        isSelectionActive = selectionActive,
                        isSelected = item.itemId in selectedIds,
                        pinActionEnabled = !pinActionInProgress,
                        canMoveUp = index > 0,
                        canMoveDown = index >= 0 && index < pinnedItems.lastIndex,
                        onOpen = { onOpenPlayer(item.itemId) },
                        onToggleSelect = { toggleSelection(item.itemId) },
                        onEnterSelection = { enterSelectionMode(item.itemId) },
                        onPlayNow = { vm.playNow(item.itemId) },
                        onPlayNext = { vm.playNext(item.itemId) },
                        onPlayLast = { vm.playLast(item.itemId) },
                        onPin = {},
                        onUnpin = {
                            actionScope.launch {
                                runPinMutation("Couldn't unpin item.") {
                                    vm.unpinSmartPlaylistItem(playlistId, item.itemId)
                                }
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val nextIds = pinnedItems.map { it.itemId }.toMutableList()
                                val moved = nextIds.removeAt(index)
                                nextIds.add(index - 1, moved)
                                actionScope.launch {
                                    runPinMutation("Couldn't reorder pinned items.") {
                                        vm.reorderSmartPlaylistPins(playlistId, nextIds)
                                    }
                                }
                            }
                        },
                        onMoveDown = {
                            if (index >= 0 && index < pinnedItems.lastIndex) {
                                val nextIds = pinnedItems.map { it.itemId }.toMutableList()
                                val moved = nextIds.removeAt(index)
                                nextIds.add(index + 1, moved)
                                actionScope.launch {
                                    runPinMutation("Couldn't reorder pinned items.") {
                                        vm.reorderSmartPlaylistPins(playlistId, nextIds)
                                    }
                                }
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    )
                }
            }

            item(key = "live-header") {
                SmartPlaylistSectionHeader(
                    title = "Live results",
                    count = liveItems.size,
                )
            }
            if (liveItems.isEmpty() && pinnedItems.isNotEmpty()) {
                item(key = "live-empty") {
                    DefaultListSurfaceMessage("No additional live results below pinned items.")
                }
            }
            items(items = liveItems, key = { "live-${it.itemId}" }) { item ->
                SmartPlaylistItemRow(
                    item = item,
                    isPinned = false,
                    isSelectionActive = selectionActive,
                    isSelected = item.itemId in selectedIds,
                    pinActionEnabled = !pinActionInProgress,
                    canMoveUp = false,
                    canMoveDown = false,
                    onOpen = { onOpenPlayer(item.itemId) },
                    onToggleSelect = { toggleSelection(item.itemId) },
                    onEnterSelection = { enterSelectionMode(item.itemId) },
                    onPlayNow = { vm.playNow(item.itemId) },
                    onPlayNext = { vm.playNext(item.itemId) },
                    onPlayLast = { vm.playLast(item.itemId) },
                    onPin = {
                        actionScope.launch {
                            runPinMutation("Couldn't pin item.") {
                                vm.pinSmartPlaylistItem(playlistId, item.itemId)
                            }
                        }
                    },
                    onUnpin = {},
                    onMoveUp = {},
                    onMoveDown = {},
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
            }
        }
    }

    if (showSeedConfirmation) {
        val playlistName = detail?.name ?: "this smart playlist"
        AlertDialog(
            onDismissRequest = { showSeedConfirmation = false },
            title = { Text("Replace Up Next?") },
            text = {
                Text(
                    "This replaces the current local Up Next session with a snapshot of \"$playlistName\" as it appears now. It will not stay synced if the smart playlist changes.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSeedConfirmation = false
                        actionScope.launch { seedUpNextFromSnapshot() }
                    },
                ) {
                    Text("Use snapshot")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeedConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showFreezeDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!freezeInProgress) showFreezeDialog = false
            },
            title = { Text("Freeze as manual playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This creates a manual snapshot of the current smart playlist. The smart playlist stays live; future smart playlist changes will not update the frozen manual playlist.",
                    )
                    OutlinedTextField(
                        value = freezeName,
                        onValueChange = { freezeName = it },
                        label = { Text("Playlist name (optional)") },
                        singleLine = true,
                        enabled = !freezeInProgress,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !freezeInProgress,
                    onClick = { actionScope.launch { freezeAsManualSnapshot() } },
                ) {
                    Text(if (freezeInProgress) "Freezing..." else "Freeze snapshot")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !freezeInProgress,
                    onClick = { showFreezeDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    val editDetail = detail
    if (showEditDialog && editDetail != null) {
        SmartPlaylistFormDialog(
            title = "Edit smart playlist",
            initialState = SmartPlaylistFormState.fromDetail(editDetail),
            confirmLabel = "Save",
            onDismiss = { showEditDialog = false },
            onSubmit = { request ->
                vm.updateSmartPlaylist(editDetail.id, request)
            },
            onSaved = { updated ->
                detail = updated
                showEditDialog = false
                vm.showSnackbar("Updated smart playlist \"${updated.name}\".")
                actionScope.launch { loadContent(showSpinner = false) }
            },
        )
    }

    if (showDeleteDialog && editDetail != null) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) showDeleteDialog = false
            },
            title = { Text("Delete smart playlist?") },
            text = {
                Text(
                    "Delete \"${editDetail.name}\" and its pins. Matching saved items are not deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleteInProgress,
                    onClick = {
                        deleteInProgress = true
                        actionScope.launch {
                            vm.deleteSmartPlaylist(editDetail.id)
                                .onSuccess {
                                    showDeleteDialog = false
                                    vm.showSnackbar("Deleted smart playlist \"${editDetail.name}\".")
                                    onNavigateAfterDelete()
                                }
                                .onFailure { error ->
                                    vm.showSnackbar(error.message ?: "Couldn't delete smart playlist.")
                                }
                            deleteInProgress = false
                        }
                    },
                ) {
                    Text(if (deleteInProgress) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleteInProgress,
                    onClick = { showDeleteDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SmartPlaylistHeader(
    detail: SmartPlaylistDetail?,
    itemCount: Int,
    pinnedCount: Int,
    loading: Boolean,
    refreshActionState: RefreshActionVisualState,
    onRefresh: () -> Unit,
    seedEnabled: Boolean,
    onSeedUpNext: () -> Unit,
    freezeEnabled: Boolean,
    onFreezeManual: () -> Unit,
    editEnabled: Boolean,
    onEdit: () -> Unit,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail?.name ?: "Smart playlist",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("Live dynamic view - ")
                            append(itemCount)
                            append(" item")
                            if (itemCount != 1) append("s")
                            if (pinnedCount > 0) {
                                append(", ")
                                append(pinnedCount)
                                append(" pinned")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                TextButton(
                    enabled = seedEnabled,
                    onClick = onSeedUpNext,
                ) {
                    Text("Use as Up Next")
                }
                RefreshActionButton(
                    state = refreshActionState,
                    showConnectivityIssue = false,
                    onClick = onRefresh,
                    contentDescription = "Refresh smart playlist",
                    pullProgress = 0f,
                )
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Smart playlist actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            enabled = freezeEnabled,
                            text = { Text("Freeze as manual playlist") },
                            onClick = {
                                menuExpanded = false
                                onFreezeManual()
                            },
                        )
                        DropdownMenuItem(
                            enabled = editEnabled,
                            text = { Text("Edit filters") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            enabled = deleteEnabled,
                            text = { Text("Delete smart playlist") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (detail != null) {
                Text(
                    text = "${smartSortLabel(detail.sort)} - ${smartFilterSummary(detail.filterDefinition)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistSectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartPlaylistItemRow(
    item: PlaybackQueueItem,
    isPinned: Boolean,
    isSelectionActive: Boolean,
    isSelected: Boolean,
    pinActionEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val presentation = remember(item) { queueCapturePresentation(item) }
    val statusForLine = item.status?.takeIf { it != "ready" }

    LibraryItemRow(
        title = presentation.title,
        metadata = presentation.sourceLabel ?: item.host ?: item.url,
        isSelected = isSelected,
        onClick = if (isSelectionActive) onToggleSelect else onOpen,
        onLongClick = if (!isSelectionActive) onEnterSelection else null,
        leadingContent = if (isSelectionActive) {
            { SelectionAffordance(isSelected = isSelected) }
        } else {
            null
        },
        progressStateLine = if (statusForLine != null) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ListStatusPill(status = statusForLine)
                }
            }
        } else {
            null
        },
        trailingContent = if (!isSelectionActive) {
            {
                var menuExpanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        IconButton(
                            enabled = pinActionEnabled && canMoveUp,
                            onClick = onMoveUp,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move pinned item up",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            enabled = pinActionEnabled && canMoveDown,
                            onClick = onMoveDown,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move pinned item down",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions for ${presentation.title}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Now") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNow()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Play Last") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayLast()
                                },
                            )
                            DropdownMenuItem(
                                enabled = pinActionEnabled,
                                text = { Text(if (isPinned) "Unpin" else "Pin") },
                                onClick = {
                                    menuExpanded = false
                                    if (isPinned) onUnpin() else onPin()
                                },
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

private fun smartSortLabel(sort: String): String =
    when (sort) {
        "saved_asc" -> "Oldest first"
        else -> "Newest first"
    }

private fun smartFilterSummary(filter: JsonObject): String {
    val parts = mutableListOf<String>()
    filter.stringValue("keyword")?.let { parts += "keyword: $it" }
    filter.stringList("source_labels").takeIf { it.isNotEmpty() }?.let { parts += "source: ${it.joinToString()}" }
    filter.stringList("domains").takeIf { it.isNotEmpty() }?.let { parts += "domain: ${it.joinToString()}" }
    filter.stringList("capture_kinds").takeIf { it.isNotEmpty() }?.let { parts += "kind: ${it.joinToString()}" }
    filter.stringValue("date_window")?.let { parts += "window: ${it.replace('_', ' ')}" }
    val after = filter.stringValue("saved_after")
    val before = filter.stringValue("saved_before")
    if (after != null || before != null) {
        parts += listOfNotNull(
            after?.take(10)?.let { "after $it" },
            before?.take(10)?.let { "before $it" },
        ).joinToString(prefix = "saved ", separator = " and ")
    }
    when (filter.stringValue("include_archived") ?: "false") {
        "true" -> parts += "includes archived"
        "only" -> parts += "archived only"
    }
    if (filter.booleanValue("favorites_only") == true) parts += "favorites only"
    filter.stringValue("read_status")
        ?.takeIf { it != "any" }
        ?.let { parts += "read: ${it.replace('_', ' ')}" }
    return parts.joinToString(" - ").ifBlank { "all non-trashed, non-archived items" }
}

private fun JsonObject.stringValue(key: String): String? =
    (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun JsonObject.booleanValue(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.stringList(key: String): List<String> =
    (get(key) as? JsonArray)
        ?.mapNotNull { it.stringContentOrNull() }
        .orEmpty()

private fun JsonElement.stringContentOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
