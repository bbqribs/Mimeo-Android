package com.mimeo.android.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.ui.common.DefaultListSurfaceMessage
import com.mimeo.android.ui.common.LibraryItemRow
import com.mimeo.android.ui.common.ListStatusPill
import com.mimeo.android.ui.common.ListSurfaceScaffold
import com.mimeo.android.ui.common.SelectionAffordance
import com.mimeo.android.ui.common.queueCapturePresentation
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.playlists.BatchPlaylistPickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private sealed interface LibraryListEntry {
    data class Header(val label: String, val key: String) : LibraryListEntry
    data class Item(val item: PlaybackQueueItem) : LibraryListEntry
}

private fun dateBucketLabel(createdAt: String?, today: LocalDate): String {
    val date = parseCreatedAtDate(createdAt) ?: return "Older"
    val thisWeekMonday = today.with(DayOfWeek.MONDAY)
    val lastMonth = today.minusMonths(1)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        !date.isBefore(thisWeekMonday) -> "This Week"
        date.year == today.year && date.month == today.month -> "This Month"
        date.year == lastMonth.year && date.month == lastMonth.month -> "Last Month"
        else -> "Older"
    }
}

private fun parseCreatedAtDate(createdAt: String?): LocalDate? {
    val value = createdAt?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val zone = ZoneId.systemDefault()
    return parseDateOrNull { Instant.parse(value).atZone(zone).toLocalDate() }
        ?: parseDateOrNull { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }
        ?: parseDateOrNull { LocalDateTime.parse(value).atZone(zone).toLocalDate() }
        ?: parseDateOrNull { LocalDate.parse(value) }
}

private inline fun parseDateOrNull(parse: () -> LocalDate): LocalDate? =
    try {
        parse()
    } catch (_: DateTimeParseException) {
        null
    }

data class LibraryBatchAction(
    val label: String,
    val icon: ImageVector,
    val action: String,
)

private val PENDING_STATUSES = setOf("extracting", "saved", "failed", "blocked")

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
    isBin: Boolean = false,
    batchActions: List<LibraryBatchAction> = emptyList(),
    playlists: List<PlaylistSummary> = emptyList(),
    onBatchAddToPlaylist: ((playlistId: Int, playlistName: String, itemIds: Set<Int>) -> Unit)? = null,
    onBatchAddToUpNext: ((itemIds: List<Int>) -> Unit)? = null,
    onSortChange: (LibrarySortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onRefresh: suspend () -> Result<Unit>,
    onOpenItem: (Int) -> Unit,
    onBatchAction: (action: String, itemIds: Set<Int>) -> Unit = { _, _ -> },
    nowPlayingHasItems: Boolean = false,
    onPlayAll: ((items: List<PlaybackQueueItem>) -> Unit)? = null,
    onPlayNow: ((itemId: Int) -> Unit)? = null,
    onPlayNext: ((itemId: Int) -> Unit)? = null,
    onPlayLast: ((itemId: Int) -> Unit)? = null,
    onPlayFromHere: ((itemsFromHere: List<PlaybackQueueItem>, selectedItemId: Int) -> Unit)? = null,
) {
    var pendingExpanded by rememberSaveable { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var pendingPlayAllSnapshot by remember { mutableStateOf<List<PlaybackQueueItem>?>(null) }
    var pendingPlayFromHereItemId by remember { mutableStateOf<Int?>(null) }

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

    suspend fun refreshListContent() {
        if (refreshActionState == RefreshActionVisualState.Refreshing) return
        refreshActionState = RefreshActionVisualState.Refreshing
        val result = onRefresh()
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
    val visiblePlaybackItems = remember(isInbox, pendingExpanded, pendingItems, readyItems, sortedItems) {
        if (isInbox) {
            buildList {
                if (pendingExpanded) addAll(pendingItems)
                addAll(readyItems)
            }
        } else {
            sortedItems
        }
    }

    val sectionedReadyItems: List<LibraryListEntry> = remember(readyItems, sortOption, searchQuery, isBin) {
        if (isBin || sortOption != LibrarySortOption.NEWEST || searchQuery.isNotBlank()) {
            readyItems.map { LibraryListEntry.Item(it) }
        } else {
            val today = LocalDate.now()
            var lastBucket: String? = null
            var headerIndex = 0
            buildList {
                readyItems.forEach { item ->
                    val bucket = dateBucketLabel(item.createdAt, today)
                    if (bucket != lastBucket) {
                        add(LibraryListEntry.Header(label = bucket, key = "section_header_${headerIndex}_$bucket"))
                        headerIndex += 1
                        lastBucket = bucket
                    }
                    add(LibraryListEntry.Item(item))
                }
            }
        }
    }

    ListSurfaceScaffold(
        modifier = Modifier.fillMaxWidth(),
        selectionBar = if (selectionActive) {
            {
                // Contextual action bar — replaces search/sort row while in selection mode.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibrarySelectionIconButton(
                        label = "Exit selection mode",
                        onClick = ::clearSelection,
                    ) {
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
                        LibrarySelectionIconButton(
                            label = resolvedLabel,
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
                        LibrarySelectionIconButton(
                            label = "Add to Playlist",
                            onClick = {
                                batchPlaylistPickerIds = selectedIds
                                showBatchPlaylistPicker = true
                                clearSelection()
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Playlist",
                            )
                        }
                    }
                    if (onBatchAddToUpNext != null) {
                        val capturedSelectedIds = selectedIds
                        LibrarySelectionIconButton(
                            label = "Add to Up Next",
                            onClick = {
                                val orderedIds = sortedItems
                                    .filter { it.itemId in capturedSelectedIds }
                                    .map { it.itemId }
                                clearSelection()
                                onBatchAddToUpNext(orderedIds)
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add selected to Up Next",
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        controls = if (!selectionActive) {
            {
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
                        placeholder = { Text("Search $title...") },
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
                    RefreshActionButton(
                        state = refreshActionState,
                        showConnectivityIssue = false,
                        onClick = { actionScope.launch { refreshListContent() } },
                        contentDescription = "Refresh $title",
                        pullProgress = 0f,
                    )
                    if (!isBin && onPlayAll != null) {
                        TextButton(
                            enabled = visiblePlaybackItems.isNotEmpty(),
                            onClick = {
                                val snapshot = visiblePlaybackItems
                                if (snapshot.isEmpty()) return@TextButton
                                if (nowPlayingHasItems) {
                                    pendingPlayAllSnapshot = snapshot
                                } else {
                                    onPlayAll(snapshot)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                            Text("Play All")
                        }
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
        } else {
            null
        },
        loading = loading,
        empty = items.isEmpty(),
        emptyContent = { DefaultListSurfaceMessage(emptyMessage) },
    ) {
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
                        LibraryQueueItemRow(
                            item = item,
                            isSelectionActive = selectionActive,
                            isSelected = item.itemId in selectedIds,
                            onOpen = { onOpenItem(item.itemId) },
                            onToggleSelect = { toggleSelection(item.itemId) },
                            onEnterSelection = { enterSelectionMode(item.itemId) },
                            onPlayNow = onPlayNow?.let { cb -> { cb(item.itemId) } },
                            onPlayNext = onPlayNext?.let { cb -> { cb(item.itemId) } },
                            onPlayLast = onPlayLast?.let { cb -> { cb(item.itemId) } },
                            onPlayFromHere = onPlayFromHere?.let { { pendingPlayFromHereItemId = item.itemId } },
                            onAddToPlaylist = if (!isBin) onBatchAddToPlaylist?.let {
                                {
                                    batchPlaylistPickerIds = setOf(item.itemId)
                                    showBatchPlaylistPicker = true
                                }
                            } else null,
                            onFavoriteToggle = if (!isBin) ({
                                onBatchAction(if (item.isFavorited) "unfavorite" else "favorite", setOf(item.itemId))
                            }) else null,
                            onArchiveToggle = if (!isBin) ({
                                onBatchAction(if (title == "Archive") "unarchive" else "archive", setOf(item.itemId))
                            }) else null,
                            archiveToggleLabel = if (title == "Archive") "Unarchive" else "Archive",
                            onBin = if (!isBin) ({ onBatchAction("bin", setOf(item.itemId)) }) else null,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            // Ready / main items — with optional date section headers for NEWEST sort.
            items(
                items = sectionedReadyItems,
                key = { entry ->
                    when (entry) {
                        is LibraryListEntry.Header -> entry.key
                        is LibraryListEntry.Item -> entry.item.itemId
                    }
                },
                contentType = { entry ->
                    when (entry) {
                        is LibraryListEntry.Header -> "header"
                        is LibraryListEntry.Item -> "item"
                    }
                },
            ) { entry ->
                when (entry) {
                    is LibraryListEntry.Header -> DateSectionHeader(label = entry.label)
                    is LibraryListEntry.Item -> {
                        LibraryQueueItemRow(
                            item = entry.item,
                            isSelectionActive = selectionActive,
                            isSelected = entry.item.itemId in selectedIds,
                            onOpen = { onOpenItem(entry.item.itemId) },
                            onToggleSelect = { toggleSelection(entry.item.itemId) },
                            onEnterSelection = { enterSelectionMode(entry.item.itemId) },
                            onPlayNow = onPlayNow?.let { cb -> { cb(entry.item.itemId) } },
                            onPlayNext = onPlayNext?.let { cb -> { cb(entry.item.itemId) } },
                            onPlayLast = onPlayLast?.let { cb -> { cb(entry.item.itemId) } },
                            onPlayFromHere = onPlayFromHere?.let { { pendingPlayFromHereItemId = entry.item.itemId } },
                            onAddToPlaylist = if (!isBin) onBatchAddToPlaylist?.let {
                                {
                                    batchPlaylistPickerIds = setOf(entry.item.itemId)
                                    showBatchPlaylistPicker = true
                                }
                            } else null,
                            onFavoriteToggle = if (!isBin) ({
                                onBatchAction(if (entry.item.isFavorited) "unfavorite" else "favorite", setOf(entry.item.itemId))
                            }) else null,
                            onArchiveToggle = if (!isBin) ({
                                onBatchAction(if (title == "Archive") "unarchive" else "archive", setOf(entry.item.itemId))
                            }) else null,
                            archiveToggleLabel = if (title == "Archive") "Unarchive" else "Archive",
                            onBin = if (!isBin) ({ onBatchAction("bin", setOf(entry.item.itemId)) }) else null,
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

    pendingPlayAllSnapshot?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingPlayAllSnapshot = null },
            title = { Text("Replace Up Next?") },
            text = {
                Text(
                    "From: $title\n\nThis replaces the current Up Next with a snapshot of the visible items in this view.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPlayAllSnapshot = null
                        onPlayAll?.invoke(snapshot)
                    },
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayAllSnapshot = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingPlayFromHereItemId?.let { selectedItemId ->
        AlertDialog(
            onDismissRequest = { pendingPlayFromHereItemId = null },
            title = { Text("Replace Up Next with items from here down?") },
            text = {
                Text(
                    buildString {
                        append("This replaces Up Next with a snapshot from the selected item through the end of the visible list.")
                        if (nowPlayingHasItems) {
                            append("\n\nCurrently playing item will exit Up Next; its progress is kept.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snapshot = visiblePlaybackItems.dropWhile { it.itemId != selectedItemId }
                        pendingPlayFromHereItemId = null
                        if (snapshot.isNotEmpty()) {
                            onPlayFromHere?.invoke(snapshot, selectedItemId)
                        }
                    },
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayFromHereItemId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySelectionIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
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

@Composable
private fun DateSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LibraryQueueItemRow(
    item: PlaybackQueueItem,
    isSelectionActive: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlayNow: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onPlayLast: (() -> Unit)? = null,
    onPlayFromHere: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    onArchiveToggle: (() -> Unit)? = null,
    archiveToggleLabel: String = "Archive",
    onBin: (() -> Unit)? = null,
) {
    val presentation = remember(item) { queueCapturePresentation(item) }
    val title = presentation.title
    val source = presentation.sourceLabel ?: item.url
    val status = item.status
    val statusForLine = status?.takeIf { it != "ready" }

    LibraryItemRow(
        title = title,
        metadata = source,
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
        trailingContent = if (!isSelectionActive && (
                onPlayNow != null ||
                    onPlayNext != null ||
                    onPlayLast != null ||
                    onPlayFromHere != null ||
                    onAddToPlaylist != null ||
                    onFavoriteToggle != null ||
                    onArchiveToggle != null ||
                    onBin != null
                )
        ) {
            {
                var menuExpanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onPlayNow != null) {
                        IconButton(
                            onClick = onPlayNow,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play $title",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions for $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                        if (onPlayNext != null) {
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext()
                                },
                            )
                        }
                        if (onPlayLast != null) {
                            DropdownMenuItem(
                                text = { Text("Play Last") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayLast()
                                },
                            )
                        }
                        if (onPlayFromHere != null) {
                            DropdownMenuItem(
                                text = { Text("Play from Here") },
                                onClick = {
                                    menuExpanded = false
                                    onPlayFromHere()
                                },
                            )
                        }
                        val hasQueueActions = onPlayNext != null || onPlayLast != null || onPlayFromHere != null
                        val hasItemActions = onAddToPlaylist != null ||
                            onFavoriteToggle != null ||
                            onArchiveToggle != null ||
                            onBin != null
                        if (hasQueueActions && hasItemActions) {
                            HorizontalDivider()
                        }
                        if (onAddToPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = {
                                    menuExpanded = false
                                    onAddToPlaylist()
                                },
                            )
                        }
                        if (onFavoriteToggle != null) {
                            DropdownMenuItem(
                                text = { Text(if (item.isFavorited) "Unfavorite" else "Favorite") },
                                onClick = {
                                    menuExpanded = false
                                    onFavoriteToggle()
                                },
                            )
                        }
                        if (onArchiveToggle != null) {
                            DropdownMenuItem(
                                text = { Text(archiveToggleLabel) },
                                onClick = {
                                    menuExpanded = false
                                    onArchiveToggle()
                                },
                            )
                        }
                        if (onBin != null) {
                            DropdownMenuItem(
                                text = { Text("Bin") },
                                onClick = {
                                    menuExpanded = false
                                    onBin()
                                },
                            )
                        }
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}
