package com.mimeo.android.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.ACTION_KEY_UNDO_PLAYLIST_REMOVE
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlaylistEntrySummary
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.ui.common.LibraryItemRow
import com.mimeo.android.ui.common.ListStatusPill
import com.mimeo.android.ui.common.ListSurfaceScaffold
import com.mimeo.android.ui.common.SelectionAffordance
import com.mimeo.android.ui.common.queueCapturePresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ordered list of playlist entries with drag-to-reorder support.
 *
 * Each row has an explicit drag handle (≡) on the left. Touch and drag the handle
 * vertically; items shift in real time as the dragged item crosses their midpoints.
 * On drop the new order is persisted via PUT /playlists/{id}/entries/reorder.
 *
 * During drag, [localEntries] is NOT mutated — only visual offsets change.
 * The actual list reorder happens only on drop, preventing the "text-swap" artifact.
 * [currentTargetIndex] is maintained as Compose state updated on every onDrag event,
 * so onDragEnd reads a pre-computed value rather than recomputing from scratch.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    vm: AppViewModel,
    onOpenPlayer: (Int) -> Unit,
    onShowSnackbar: (String, String?, String?) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val playlists by vm.playlists.collectAsState()
    val queueItems by vm.queueItems.collectAsState()
    val inboxItems by vm.inboxItems.collectAsState()
    val archivedItems by vm.archivedItems.collectAsState()
    val favoriteItems by vm.favoriteItems.collectAsState()
    val binItems by vm.binItems.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val playbackEngineState by vm.playbackEngineState.collectAsState()
    val actionScope = rememberCoroutineScope()

    LaunchedEffect(playlistId) {
        vm.refreshPlaylists()
        vm.loadArchivedItems()
        vm.loadBinItems()
    }

    val playlist = playlists.firstOrNull { it.id == playlistId }
    val serverEntries = remember(playlist) {
        playlist?.entries?.sortedBy { it.position ?: Double.MAX_VALUE } ?: emptyList()
    }

    // Unified lookup across all item sources loaded in this session.
    val allItemsMap = remember(queueItems, inboxItems, archivedItems, favoriteItems, binItems) {
        (queueItems + inboxItems + archivedItems + favoriteItems + binItems)
            .associateBy { it.itemId }
    }

    // Local mutable list — only mutated on drop, not during drag.
    // Keyed by entry IDs rather than the full serverEntries list so that position-only
    // changes from refreshPlaylists do NOT reset localEntries and cause a post-drop flicker.
    val localEntries = remember { mutableStateListOf<PlaylistEntrySummary>() }
    val serverEntryIds = remember(serverEntries) { serverEntries.map { it.id } }
    var selectionActive by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(serverEntryIds) {
        if (localEntries.map { it.id } != serverEntryIds) {
            localEntries.clear()
            localEntries.addAll(serverEntries)
            selectedEntryIds = selectedEntryIds.intersect(serverEntryIds.toSet())
            if (selectedEntryIds.isEmpty()) selectionActive = false
        }
    }

    // Per-item Y positions in the Column for drag hit-testing.
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() } // entryId -> top px
    val itemHeights = remember { mutableMapOf<Int, Float>() } // entryId -> height px
    var dragStartTopOffsets by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var dragStartHeights by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // Cached target index — updated on every onDrag so onDragEnd can read it reliably.
    var currentTargetIndex by remember { mutableIntStateOf(-1) }
    var isSaving by remember { mutableStateOf(false) }
    val listScrollState = rememberScrollState()
    var listViewportHeight by remember { mutableIntStateOf(0) }

    // Rename/delete dialog state
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun enterSelectionMode(entryId: Int) {
        selectionActive = true
        selectedEntryIds = setOf(entryId)
    }

    fun toggleSelection(entryId: Int) {
        val next = if (entryId in selectedEntryIds) selectedEntryIds - entryId else selectedEntryIds + entryId
        selectedEntryIds = next
        if (next.isEmpty()) selectionActive = false
    }

    fun clearSelection() {
        selectionActive = false
        selectedEntryIds = emptySet()
    }

    fun selectedArticleIdsInOrder(): List<Int> =
        localEntries.filter { it.id in selectedEntryIds }.map { it.articleId }

    BackHandler(enabled = selectionActive) { clearSelection() }

    /** Average measured item height; falls back to a reasonable default. */
    fun avgItemHeight(): Float =
        if (itemHeights.isEmpty()) 72f else itemHeights.values.average().toFloat()

    fun scrollDraggedItemNearEdge(from: Int) {
        if (from !in localEntries.indices || listViewportHeight <= 0) return
        val tops = dragStartTopOffsets.takeIf { it.isNotEmpty() } ?: itemTopOffsets
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val entryId = localEntries[from].id
        val itemTop = (tops[entryId] ?: (from * avgItemHeight())) + dragOffsetY
        val itemBottom = itemTop + (heights[entryId] ?: avgItemHeight())
        val viewportTop = listScrollState.value.toFloat()
        val viewportBottom = viewportTop + listViewportHeight
        val edgeSize = 96f
        val maxStep = 28f
        val desiredDelta = when {
            itemTop < viewportTop + edgeSize -> -maxStep
            itemBottom > viewportBottom - edgeSize -> maxStep
            else -> 0f
        }
        if (desiredDelta == 0f) return
        val before = listScrollState.value.toFloat()
        listScrollState.dispatchRawDelta(desiredDelta)
        val consumed = listScrollState.value.toFloat() - before
        if (consumed != 0f) {
            dragOffsetY += consumed
        }
    }

    fun computeTargetIndex(from: Int, offsetY: Float): Int {
        if (localEntries.size <= 1 || from !in localEntries.indices) return from
        val tops = dragStartTopOffsets.takeIf { it.isNotEmpty() } ?: itemTopOffsets
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val fromEntryId = localEntries[from].id
        val h = heights[fromEntryId] ?: avgItemHeight()
        val top = tops[fromEntryId] ?: (from * avgItemHeight())
        val draggedTopY = top + offsetY
        val draggedBottomY = draggedTopY + h
        var target = from
        localEntries.indices.forEach { i ->
            if (i == from) return@forEach
            val entryId = localEntries[i].id
            val t = tops[entryId] ?: (i * avgItemHeight())
            val iH = heights[entryId] ?: avgItemHeight()
            val iMidY = t + iH / 2f
            if (from < i && draggedBottomY > iMidY) target = i
            if (from > i && draggedTopY < iMidY) target = i
        }
        return target.coerceIn(0, localEntries.lastIndex)
    }

    /**
     * Visual Y offset for a non-dragged item at [index], given the current drag
     * from [from] toward [target].  Items between from and target are shifted to
     * visually make room without mutating localEntries.
     */
    fun visualOffsetForItem(index: Int, from: Int, target: Int): Float {
        if (from < 0 || from == target || index == from) return 0f
        val draggedEntryId = localEntries[from].id
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val draggedHeight = heights[draggedEntryId] ?: avgItemHeight()
        return when {
            target > from && index in (from + 1)..target -> -draggedHeight
            target < from && index in target until from  ->  draggedHeight
            else -> 0f
        }
    }

    LaunchedEffect(draggingIndex) {
        while (draggingIndex >= 0) {
            scrollDraggedItemNearEdge(draggingIndex)
            val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
            if (newTarget != currentTargetIndex) {
                currentTargetIndex = newTarget
            }
            delay(16)
        }
    }

    fun onDragEnd() {
        val from = draggingIndex
        val target = currentTargetIndex   // read the pre-computed, cached value
        // Mutate localEntries BEFORE clearing drag state so both changes land in the
        // same Compose snapshot batch — avoids a frame where items snap back then re-settle.
        if (from >= 0 && target >= 0 && target != from) {
            val moved = localEntries.removeAt(from)
            localEntries.add(target, moved)
        }
        draggingIndex = -1
        dragOffsetY = 0f
        currentTargetIndex = -1
        dragStartTopOffsets = emptyMap()
        dragStartHeights = emptyMap()
        if (from < 0) return
        val entryIds = localEntries.map { it.id }
        isSaving = true
        actionScope.launch {
            vm.reorderPlaylistEntries(playlistId, entryIds)
                .onFailure { onShowSnackbar("Couldn't save order.", null, null) }
            isSaving = false
        }
    }

    fun persistCurrentOrder() {
        val entryIds = localEntries.map { it.id }
        isSaving = true
        actionScope.launch {
            vm.reorderPlaylistEntries(playlistId, entryIds)
                .onFailure { onShowSnackbar("Couldn't save order.", null, null) }
            isSaving = false
        }
    }

    fun moveEntry(index: Int, target: Int) {
        if (index !in localEntries.indices || target !in localEntries.indices || index == target) return
        val moved = localEntries.removeAt(index)
        localEntries.add(target, moved)
        persistCurrentOrder()
    }

    fun removeArticleFromPlaylist(articleId: Int) {
        val previousArticleOrder = localEntries.map { it.articleId }
        actionScope.launch {
            vm.togglePlaylistMembership(articleId, playlistId)
                .onSuccess {
                    vm.rememberLastPlaylistRemoval(playlistId, listOf(articleId), previousArticleOrder)
                    onShowSnackbar("Removed from playlist.", "Undo", ACTION_KEY_UNDO_PLAYLIST_REMOVE)
                }
                .onFailure { onShowSnackbar("Couldn't remove from playlist.", null, null) }
        }
    }

    fun removeSelectedFromPlaylist() {
        val articleIds = selectedArticleIdsInOrder()
        val previousArticleOrder = localEntries.map { it.articleId }
        clearSelection()
        actionScope.launch {
            val removedIds = mutableListOf<Int>()
            articleIds.forEach { articleId ->
                vm.togglePlaylistMembership(articleId, playlistId)
                    .onSuccess { removedIds += articleId }
            }
            if (removedIds.isNotEmpty()) {
                vm.rememberLastPlaylistRemoval(playlistId, removedIds, previousArticleOrder)
                onShowSnackbar(
                    if (removedIds.size == articleIds.size) {
                        "Removed ${removedIds.size} from playlist."
                    } else {
                        "Removed ${removedIds.size}; some failed."
                    },
                    "Undo",
                    ACTION_KEY_UNDO_PLAYLIST_REMOVE,
                )
            } else {
                onShowSnackbar("Couldn't remove selected items.", null, null)
            }
        }
    }

    fun queueSelectedAtEnd() {
        val articleIds = selectedArticleIdsInOrder()
        clearSelection()
        articleIds.forEach { vm.playLast(it) }
    }

    ListSurfaceScaffold(
        modifier = Modifier.fillMaxSize(),
        header = {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSaving || (loading && localEntries.isEmpty())) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    IconButton(
                        onClick = vm::refreshPlaylists,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (playlist != null) {
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Playlist options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showOverflowMenu = false
                                        renameText = playlist.name
                                        showRenameDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
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
                        text = "${selectedEntryIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = ::queueSelectedAtEnd,
                        enabled = selectedEntryIds.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add selected to Up Next")
                    }
                    IconButton(
                        onClick = ::removeSelectedFromPlaylist,
                        enabled = selectedEntryIds.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remove selected from playlist",
                        )
                    }
                }
            }
        } else {
            null
        },
        loading = false,
        empty = playlist == null || (localEntries.isEmpty() && !loading),
        loadingContent = null,
        emptyContent = {
            if (playlist == null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        "Playlist not found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            } else {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        "No items in this playlist yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { listViewportHeight = it.height }
                .verticalScroll(listScrollState),
        ) {
            localEntries.forEachIndexed { index, entry ->
                key(entry.id) {
                    val queueItem = allItemsMap[entry.articleId]
                    val isDragging = draggingIndex == index
                    val itemVisualOffsetY = when {
                        isDragging -> dragOffsetY
                        draggingIndex >= 0 -> visualOffsetForItem(
                            index, draggingIndex, currentTargetIndex
                        )
                        else -> 0f
                    }
                    // Wrap row + divider together so the divider moves with its row.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = itemVisualOffsetY }
                            .onGloballyPositioned { coords ->
                                itemTopOffsets[entry.id] = coords.positionInParent().y
                                itemHeights[entry.id] = coords.size.height.toFloat()
                            },
                    ) {
                        PlaylistDetailRow(
                            queueItem = queueItem,
                            isDragging = isDragging,
                            isSelectionActive = selectionActive,
                            isSelected = entry.id in selectedEntryIds,
                            onDragStart = { idx ->
                                dragStartTopOffsets = itemTopOffsets.toMap()
                                dragStartHeights = itemHeights.toMap()
                                draggingIndex = idx
                                dragOffsetY = 0f
                                currentTargetIndex = idx
                            },
                            onDrag = { dy ->
                                dragOffsetY += dy
                                scrollDraggedItemNearEdge(draggingIndex)
                                val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
                                if (newTarget != currentTargetIndex) {
                                    currentTargetIndex = newTarget
                                }
                            },
                            onDragEnd = { onDragEnd() },
                            index = index,
                            onTap = {
                                val sessionItemId = nowPlayingSession?.currentItem?.itemId ?: -1
                                val playbackActive = playbackEngineState.isSpeaking || playbackEngineState.isAutoPlaying
                                val shouldStartSession = entry.articleId > 0 &&
                                    (sessionItemId <= 0 || entry.articleId == sessionItemId || !playbackActive)
                                if (shouldStartSession) {
                                    vm.startNowPlayingSession(startItemId = entry.articleId)
                                }
                                onOpenPlayer(entry.articleId)
                            },
                            onToggleSelect = { toggleSelection(entry.id) },
                            onEnterSelection = { enterSelectionMode(entry.id) },
                            onPlayNext = { vm.playNext(entry.articleId) },
                            onPlayLast = { vm.playLast(entry.articleId) },
                            onMoveToTop = { moveEntry(index, 0) },
                            onMoveToBottom = { moveEntry(index, localEntries.lastIndex) },
                            onRemoveFromPlaylist = { removeArticleFromPlaylist(entry.articleId) },
                        )
                        if (!isDragging) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            vm.renamePlaylist(playlist.id, trimmed)
                        }
                        showRenameDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("Delete \u2018${playlist.name}\u2019 and remove its item links.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist(playlist.id)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

}

@Composable
private fun PlaylistDetailRow(
    queueItem: PlaybackQueueItem?,
    isDragging: Boolean,
    isSelectionActive: Boolean,
    isSelected: Boolean,
    onDragStart: (index: Int) -> Unit,
    onDrag: (dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    index: Int,
    onTap: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
) {
    var rowMenuExpanded by remember { mutableStateOf(false) }
    val presentation = remember(queueItem) { queueItem?.let(::queueCapturePresentation) }
    val title = presentation?.title ?: "Loading..."
    val status = queueItem?.status
    val metadata = presentation?.sourceLabel ?: queueItem?.host?.takeIf { queueItem.title != null } ?: queueItem?.url
    val statusForLine = status?.takeIf { it != "ready" }

    LibraryItemRow(
        title = title,
        metadata = metadata,
        isSelected = isSelected,
        containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Black,
        titleColor = if (queueItem == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        onClick = if (isSelectionActive) onToggleSelect else onTap,
        onLongClick = if (!isSelectionActive) onEnterSelection else null,
        leadingContent = {
            if (isSelectionActive) {
                SelectionAffordance(isSelected = isSelected)
            } else {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(index) {
                            detectDragGestures(
                                onDragStart = { onDragStart(index) },
                                onDrag = { _, dragAmount -> onDrag(dragAmount.y) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        },
                )
            }
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
        trailingContent = {
            Box {
                IconButton(
                    onClick = { rowMenuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = rowMenuExpanded,
                    onDismissRequest = { rowMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        onClick = {
                            rowMenuExpanded = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Play Last") },
                        onClick = {
                            rowMenuExpanded = false
                            onPlayLast()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to top") },
                        onClick = {
                            rowMenuExpanded = false
                            onMoveToTop()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to bottom") },
                        onClick = {
                            rowMenuExpanded = false
                            onMoveToBottom()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            rowMenuExpanded = false
                            onRemoveFromPlaylist()
                        },
                    )
                }
            }
        },
    )
}
