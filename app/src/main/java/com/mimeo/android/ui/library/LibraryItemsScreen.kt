package com.mimeo.android.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.PlaylistAdd
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.ui.playlists.BatchPlaylistPickerDialog

data class LibraryBatchAction(
    val label: String,
    val icon: ImageVector,
    val action: String,
)

private val PENDING_STATUSES = setOf("extracting", "saved", "failed", "blocked")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemsScreen(
    title: String,
    items: List<PlaybackQueueItem>,
    loading: Boolean,
    emptyMessage: String,
    sortOption: LibrarySortOption,
    availableSorts: List<LibrarySortOption> = LibrarySortOption.entries,
    searchQuery: String,
    isInbox: Boolean = false,
    batchActions: List<LibraryBatchAction> = emptyList(),
    playlists: List<PlaylistSummary> = emptyList(),
    onBatchAddToPlaylist: ((playlistId: Int, playlistName: String, itemIds: Set<Int>) -> Unit)? = null,
    onSortChange: (LibrarySortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (Int) -> Unit,
    onBatchAction: (action: String, itemIds: Set<Int>) -> Unit = { _, _ -> },
) {
    var pendingExpanded by rememberSaveable { mutableStateOf(false) }

    // Batch add-to-playlist dialog state. Captures selected IDs before selection is cleared.
    var batchPlaylistPickerIds by remember { mutableStateOf(emptySet<Int>()) }
    var showBatchPlaylistPicker by remember { mutableStateOf(false) }

    // Selection state — local, ephemeral. Clears automatically when the composable
    // leaves the back stack (drawer navigation away, etc.). No ViewModel needed for
    // Phase 4A; will elevate if batch-action dispatching requires it in Phase 4B.
    var selectionActive by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }

    fun enterSelectionMode(itemId: Int) {
        selectionActive = true
        selectedIds = setOf(itemId)
    }

    fun toggleSelection(itemId: Int) {
        val next = if (itemId in selectedIds) selectedIds - itemId else selectedIds + itemId
        selectedIds = next
        if (next.isEmpty()) selectionActive = false
    }

    fun clearSelection() {
        selectionActive = false
        selectedIds = emptySet()
    }

    // Exit selection mode on sort or search change (spec §8 "filter/search change → exit").
    // Calling clearSelection() when already inactive is a no-op (same-value state write).
    LaunchedEffect(sortOption) { clearSelection() }
    LaunchedEffect(searchQuery) { clearSelection() }

    // Exit selection mode on back press.
    BackHandler(enabled = selectionActive) { clearSelection() }

    val sortedItems = remember(items, sortOption) {
        when (sortOption) {
            LibrarySortOption.NEWEST -> items.sortedByDescending { it.createdAt }
            LibrarySortOption.OLDEST -> items.sortedBy { it.createdAt }
            LibrarySortOption.OPENED -> items.sortedWith(
                compareByDescending<PlaybackQueueItem> { it.lastOpenedAt != null }
                    .thenByDescending { it.lastOpenedAt }
            )
            LibrarySortOption.PROGRESS -> items.sortedByDescending { it.progressPercent }
            else -> items // ARCHIVED_AT, TRASHED_AT — server-side only
        }
    }

    val pendingItems = if (isInbox) sortedItems.filter { it.status in PENDING_STATUSES } else emptyList()
    val readyItems = if (isInbox) sortedItems.filter { it.status !in PENDING_STATUSES } else sortedItems

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selectionActive) {
            // Contextual action bar — replaces search/sort row while in selection mode.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::clearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit selection mode",
                    )
                }
                Text(
                    text = "${selectedIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                batchActions.forEach { batchAction ->
                    // "favorite_toggle" is resolved at render time based on selected items' state.
                    val resolvedAction = if (batchAction.action == "favorite_toggle") {
                        val allFavorited = selectedIds.isNotEmpty() &&
                            selectedIds.all { id -> items.firstOrNull { it.itemId == id }?.isFavorited == true }
                        if (allFavorited) "unfavorite" else "favorite"
                    } else {
                        batchAction.action
                    }
                    val resolvedIcon = if (batchAction.action == "favorite_toggle") {
                        if (resolvedAction == "unfavorite") Icons.Default.Favorite else Icons.Default.FavoriteBorder
                    } else {
                        batchAction.icon
                    }
                    val resolvedLabel = if (batchAction.action == "favorite_toggle") {
                        if (resolvedAction == "unfavorite") "Unfavorite" else "Favorite"
                    } else {
                        batchAction.label
                    }
                    IconButton(
                        onClick = {
                            onBatchAction(resolvedAction, selectedIds)
                            clearSelection()
                        },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = resolvedIcon,
                            contentDescription = resolvedLabel,
                        )
                    }
                }
                if (onBatchAddToPlaylist != null) {
                    IconButton(
                        onClick = {
                            batchPlaylistPickerIds = selectedIds
                            showBatchPlaylistPicker = true
                            clearSelection()
                        },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                        )
                    }
                }
            }
        } else {
            // Search row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search $title…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                onSearchQueryChange("")
                                onSearchSubmit()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    shape = RoundedCornerShape(8.dp),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            // Sort chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableSorts.forEach { option ->
                    FilterChip(
                        selected = option == sortOption,
                        onClick = { onSortChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        when {
            loading && items.isEmpty() -> {
                Text(
                    text = "Loading…",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items.isEmpty() -> {
                Text(
                    text = emptyMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // Pending section (inbox only)
                    if (pendingItems.isNotEmpty()) {
                        item(key = "pending_header") {
                            PendingSectionHeader(
                                count = pendingItems.size,
                                expanded = pendingExpanded,
                                onToggle = { pendingExpanded = !pendingExpanded },
                            )
                        }
                        if (pendingExpanded) {
                            items(items = pendingItems, key = { "p_${it.itemId}" }) { item ->
                                LibraryItemRow(
                                    item = item,
                                    isSelectionActive = selectionActive,
                                    isSelected = item.itemId in selectedIds,
                                    onOpen = { onOpenItem(item.itemId) },
                                    onToggleSelect = { toggleSelection(item.itemId) },
                                    onEnterSelection = { enterSelectionMode(item.itemId) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }

                    // Ready / main items
                    items(items = readyItems, key = { it.itemId }) { item ->
                        LibraryItemRow(
                            item = item,
                            isSelectionActive = selectionActive,
                            isSelected = item.itemId in selectedIds,
                            onOpen = { onOpenItem(item.itemId) },
                            onToggleSelect = { toggleSelection(item.itemId) },
                            onEnterSelection = { enterSelectionMode(item.itemId) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }

    if (showBatchPlaylistPicker && onBatchAddToPlaylist != null) {
        BatchPlaylistPickerDialog(
            itemCount = batchPlaylistPickerIds.size,
            playlists = playlists,
            onDismiss = {
                showBatchPlaylistPicker = false
                batchPlaylistPickerIds = emptySet()
            },
            onSelectPlaylist = { playlist ->
                onBatchAddToPlaylist(playlist.id, playlist.name, batchPlaylistPickerIds)
                showBatchPlaylistPicker = false
                batchPlaylistPickerIds = emptySet()
            },
        )
    }
}

@Composable
private fun PendingSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Pending ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryItemRow(
    item: PlaybackQueueItem,
    isSelectionActive: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val title = item.title?.takeIf { it.isNotBlank() } ?: item.url
    val source = item.host?.takeIf { it.isNotBlank() } ?: item.url
    val progress = item.progressPercent.coerceIn(0, 100)
    val progressLabel = if (progress > 0) " · $progress%" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = if (isSelectionActive) onToggleSelect else onOpen,
                onLongClick = if (!isSelectionActive) onEnterSelection else null,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isSelectionActive) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "$source$progressLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val status = item.status
                if (status != null && status != "ready") {
                    StatusPill(status = status)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, containerColor, contentColor) = when (status) {
        "extracting", "saved" -> Triple(
            "Extracting",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        "failed" -> Triple(
            "Failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        "blocked" -> Triple(
            "Blocked",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}
