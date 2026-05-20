package com.mimeo.android.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.ui.common.DefaultListSurfaceMessage
import com.mimeo.android.ui.common.ItemActionMenuEntry
import com.mimeo.android.ui.common.ItemRow
import com.mimeo.android.ui.common.SelectionState
import com.mimeo.android.ui.common.JumpPill
import com.mimeo.android.ui.common.DragHandleIcon
import com.mimeo.android.ui.common.ListSurfaceScaffold
import com.mimeo.android.ui.common.RowDivider
import com.mimeo.android.ui.common.SectionLabelHeader
import com.mimeo.android.ui.common.rowDragContainerColor
import com.mimeo.android.ui.common.jumpPillBottomPadding
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.common.queueCapturePresentation
import com.mimeo.android.ui.common.shouldShowJumpToTopLazy
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.playlists.BatchPlaylistPickerDialog
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoDensityTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
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

private fun PlaybackQueueItem.matchesLibrarySearch(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return listOfNotNull(title, sourceLabel, host, url)
        .any { value -> value.contains(needle, ignoreCase = true) }
}

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
    header: (@Composable () -> Unit)? = null,
    sortOption: LibrarySortOption,
    availableSorts: List<LibrarySortOption> = LibrarySortOption.entries,
    searchQuery: String,
    searchPlaceholder: String? = null,
    clientSideSearch: Boolean = false,
    isInbox: Boolean = false,
    isBin: Boolean = false,
    showSourceListRule: Boolean = false,
    showDragReorderHandle: Boolean = false,
    dragReorderEnabled: Boolean = false,
    dragReorderUnavailableReason: String? = null,
    dragReorderStatusLabel: String? = null,
    onDragReorder: (suspend (orderedItemIds: List<Int>) -> Result<Unit>)? = null,
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
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    jumpPillBottomClearance: Dp = 0.dp,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            if (onLoadMore == null) return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore?.invoke()
    }
    val showJumpToTop by remember {
        derivedStateOf {
            shouldShowJumpToTopLazy(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
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

    val searchedItems = remember(items, searchQuery, clientSideSearch) {
        if (!clientSideSearch || searchQuery.isBlank()) {
            items
        } else {
            items.filter { it.matchesLibrarySearch(searchQuery) }
        }
    }

    val sortedItems = remember(searchedItems, sortOption) {
        when (sortOption) {
            LibrarySortOption.SMART_QUEUE -> searchedItems
            LibrarySortOption.NEWEST -> searchedItems.sortedByDescending { it.createdAt }
            LibrarySortOption.OLDEST -> searchedItems.sortedBy { it.createdAt }
            LibrarySortOption.OPENED -> searchedItems.sortedWith(
                compareByDescending<PlaybackQueueItem> { it.lastOpenedAt != null }
                    .thenByDescending { it.lastOpenedAt }
            )
            LibrarySortOption.PROGRESS -> searchedItems.sortedByDescending { it.progressPercent }
            else -> searchedItems // ARCHIVED_AT, TRASHED_AT - server-side only
        }
    }

    var localReorderItems by remember { mutableStateOf<List<PlaybackQueueItem>?>(null) }
    // Live drag measurement maps. Plain mutable (non-snapshot) maps mutated in place so
    // onGloballyPositioned does not copy a full immutable map every layout pass on long
    // lists — that copy churned per-frame allocations/GC during scroll. Reads happen only
    // in drag-computation helpers invoked from event handlers / LaunchedEffect (never as
    // composition state reads), and drag snapshots are taken via .toMap() at onDragStart.
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() }
    val itemHeights = remember { mutableMapOf<Int, Float>() }
    var dragStartTopOffsets by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var dragStartHeights by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableIntStateOf(-1) }
    // Total px the list has been edge-auto-scrolled during the current drag.
    // Item offsets are viewport-relative (LazyColumn), so this is used to keep
    // target computation correct for rows revealed by the auto-scroll.
    var dragAccumScroll by remember { mutableFloatStateOf(0f) }
    val reorderActive = dragReorderEnabled &&
        !selectionActive &&
        !isInbox &&
        !isBin &&
        searchQuery.isBlank() &&
        sortOption == LibrarySortOption.SMART_QUEUE &&
        onDragReorder != null
    val showReorderHandle = showDragReorderHandle && !selectionActive && !isInbox && !isBin

    // Keyed on item IDs, sort, search, and backend-unavailable reason — but NOT on
    // dragReorderEnabled itself, because that goes false while reorderSaving=true (the VM sets
    // _smartQueueReorderSaving synchronously before the network call). Keying on it here would
    // fire this reset before the backend responds and revert the optimistic local order.
    LaunchedEffect(items.map { it.itemId }, sortOption, searchQuery, dragReorderUnavailableReason) {
        localReorderItems = null
        draggingIndex = -1
        dragOffsetY = 0f
        dragAccumScroll = 0f
        currentTargetIndex = -1
        dragStartTopOffsets = emptyMap()
        dragStartHeights = emptyMap()
    }

    val pendingItems = remember(sortedItems, isInbox) {
        if (isInbox) sortedItems.filter { it.status in PENDING_STATUSES } else emptyList()
    }
    val baseReadyItems = remember(sortedItems, isInbox) {
        if (isInbox) sortedItems.filter { it.status !in PENDING_STATUSES } else sortedItems
    }
    val shouldShowLocalReorderItems = localReorderItems != null &&
        !selectionActive &&
        !isInbox &&
        !isBin &&
        searchQuery.isBlank() &&
        sortOption == LibrarySortOption.SMART_QUEUE
    val readyItems = if (shouldShowLocalReorderItems) localReorderItems ?: baseReadyItems else baseReadyItems
    // O(1) itemId -> index lookup for the ready list. Replaces a per-row O(n)
    // indexOfFirst inside the LazyColumn item body that made rendering O(n^2).
    val readyIndexById = remember(readyItems) {
        readyItems.withIndex().associate { (index, item) -> item.itemId to index }
    }
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

    fun avgItemHeight(): Float =
        if (itemHeights.isEmpty()) 72f else itemHeights.values.average().toFloat()

    // Auto-scroll the list while a dragged row is held near the top/bottom edge.
    // Speed ramps with how deep the row has pushed into the 96px edge band, so it
    // stays gentle and controllable. Driven solely by the drag loop below (not
    // onDrag) so the rate cannot compound with finger movement. dispatchRawDelta
    // is fed back into dragOffsetY (row stays under the finger) and dragAccumScroll
    // (so computeTargetIndex can place rows revealed by the scroll).
    fun scrollDraggedItemNearEdge(from: Int) {
        if (from !in readyItems.indices) return
        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (viewportHeight <= 0) return
        val fromItemId = readyItems[from].itemId
        val frozenTop = dragStartTopOffsets[fromItemId] ?: itemTopOffsets[fromItemId] ?: return
        val height = dragStartHeights[fromItemId] ?: itemHeights[fromItemId] ?: avgItemHeight()
        val itemTop = frozenTop + dragOffsetY - dragAccumScroll
        val itemBottom = itemTop + height
        val edgeSize = 96f
        val maxStep = 14f
        val topOverlap = edgeSize - itemTop
        val bottomOverlap = itemBottom - (viewportHeight - edgeSize)
        // The dragged row is a LazyColumn item: if its natural slot scrolls off
        // the composed range the row is disposed and the gesture detaches. Gate
        // auto-scroll so the slot stays on screen — the scroll then stops
        // gracefully at the list extent instead of detaching the drag.
        val slotTop = frozenTop - dragAccumScroll
        val slotBottom = slotTop + height
        val desiredDelta = when {
            topOverlap > 0f && slotBottom < viewportHeight ->
                -maxStep * (topOverlap / edgeSize).coerceIn(0f, 1f)
            bottomOverlap > 0f && slotTop > 0f ->
                maxStep * (bottomOverlap / edgeSize).coerceIn(0f, 1f)
            else -> 0f
        }
        if (desiredDelta == 0f) return
        val consumed = listState.dispatchRawDelta(desiredDelta)
        if (consumed != 0f) {
            dragOffsetY += consumed
            dragAccumScroll += consumed
        }
    }

    fun computeTargetIndex(from: Int, offsetY: Float): Int {
        if (readyItems.size <= 1 || from !in readyItems.indices) return from
        val tops = dragStartTopOffsets.takeIf { it.isNotEmpty() } ?: itemTopOffsets
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val fromItemId = readyItems[from].itemId
        val height = heights[fromItemId] ?: avgItemHeight()
        val top = tops[fromItemId] ?: (from * avgItemHeight())
        val draggedTopY = top + offsetY
        val draggedBottomY = draggedTopY + height
        var target = from
        readyItems.indices.forEach { index ->
            if (index == from) return@forEach
            val itemId = readyItems[index].itemId
            // Rows revealed by edge auto-scroll are absent from the frozen drag
            // snapshot; recover their snapshot-frame offset from the live
            // viewport-relative map by undoing the accumulated scroll.
            val itemTop = tops[itemId]
                ?: itemTopOffsets[itemId]?.let { it + dragAccumScroll }
                ?: (index * avgItemHeight())
            val itemHeight = heights[itemId] ?: itemHeights[itemId] ?: avgItemHeight()
            val itemMidY = itemTop + itemHeight / 2f
            if (from < index && draggedBottomY > itemMidY) target = index
            if (from > index && draggedTopY < itemMidY && index < target) target = index
        }
        return target.coerceIn(0, readyItems.lastIndex)
    }

    fun visualOffsetForItem(index: Int, from: Int, target: Int): Float {
        if (from < 0 || from == target || index == from || from !in readyItems.indices) return 0f
        val draggedItemId = readyItems[from].itemId
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val draggedHeight = heights[draggedItemId] ?: avgItemHeight()
        return when {
            target > from && index in (from + 1)..target -> -draggedHeight
            target < from && index in target until from -> draggedHeight
            else -> 0f
        }
    }

    LaunchedEffect(draggingIndex) {
        while (draggingIndex >= 0) {
            scrollDraggedItemNearEdge(draggingIndex)
            val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
            if (newTarget != currentTargetIndex) currentTargetIndex = newTarget
            delay(16)
        }
    }

    fun finishDrag() {
        val from = draggingIndex
        val target = currentTargetIndex
        val shouldReorder = reorderActive &&
            from in readyItems.indices &&
            target in readyItems.indices &&
            target != from
        val reorderedItems = if (shouldReorder) {
            readyItems.toMutableList().apply {
                val moved = removeAt(from)
                add(target, moved)
            }
        } else {
            readyItems
        }
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset
        draggingIndex = -1
        dragOffsetY = 0f
        dragAccumScroll = 0f
        currentTargetIndex = -1
        dragStartTopOffsets = emptyMap()
        dragStartHeights = emptyMap()
        if (!shouldReorder) return
        listState.requestScrollToItem(firstVisibleIndex, firstVisibleOffset)
        localReorderItems = reorderedItems
        val persistReorder = onDragReorder
        actionScope.launch {
            val result = persistReorder(reorderedItems.map { it.itemId })
            if (result.isFailure) {
                // Revert on failure. On success we do NOT clear localReorderItems here —
                // the LaunchedEffect (keyed on items.map { it.itemId }) will clear it when
                // the ViewModel's _queueItems updates with the confirmed order. If the backend
                // returns the same item IDs in the same order, the LaunchedEffect won't fire
                // and localReorderItems stays non-null, preserving the user's drag order.
                localReorderItems = null
                refreshActionState = RefreshActionVisualState.Failure
                delay(700)
                if (refreshActionState == RefreshActionVisualState.Failure) {
                    refreshActionState = RefreshActionVisualState.Idle
                }
            }
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
        modifier = Modifier.fillMaxSize(),
        header = header,
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
                        placeholder = {
                            Text(
                                text = searchPlaceholder ?: "Search $title...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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
                        LibrarySelectionIconButton(
                            label = "Play all",
                            enabled = visiblePlaybackItems.isNotEmpty(),
                            onClick = {
                                val snapshot = visiblePlaybackItems
                                if (snapshot.isNotEmpty()) {
                                    if (nowPlayingHasItems) {
                                        pendingPlayAllSnapshot = snapshot
                                    } else {
                                        onPlayAll(snapshot)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play all",
                            )
                        }
                    }
                }

                // Sort chips row
                if (availableSorts.isNotEmpty()) {
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
                                colors = if (isV1) FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = mColors.accentDim,
                                    selectedLabelColor = mColors.accent,
                                ) else FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                }
                if (dragReorderStatusLabel != null) {
                    Text(
                        text = dragReorderStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            null
        },
        loading = loading,
        empty = if (clientSideSearch) searchedItems.isEmpty() else items.isEmpty(),
        emptyContent = { DefaultListSurfaceMessage(emptyMessage) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .passiveVerticalScrollIndicator(
                    listState = listState,
                    color = if (isV1) mColors.fg4 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                // Pending section (inbox only)
                if (pendingItems.isNotEmpty()) {
                    stickyHeader(key = "pending_header") {
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
                                selection = SelectionState.Available(
                                    isActive = selectionActive,
                                    isSelected = item.itemId in selectedIds,
                                    onToggle = { toggleSelection(item.itemId) },
                                    onEnter = { enterSelectionMode(item.itemId) },
                                ),
                                onOpen = { onOpenItem(item.itemId) },
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
                            RowDivider()
                        }
                    }
                }

                // Ready / main items — with optional date section headers for NEWEST sort.
                sectionedReadyItems.forEach { entry ->
                    when (entry) {
                        is LibraryListEntry.Header -> {
                            stickyHeader(key = entry.key) {
                                DateSectionHeader(label = entry.label)
                            }
                        }
                        is LibraryListEntry.Item -> {
                            item(key = entry.item.itemId) {
                                val readyIndex = readyIndexById[entry.item.itemId] ?: -1
                                val isDragging = reorderActive && draggingIndex == readyIndex
                                val visualOffset = when {
                                    isDragging -> dragOffsetY
                                    reorderActive && draggingIndex >= 0 -> visualOffsetForItem(
                                        index = readyIndex,
                                        from = draggingIndex,
                                        target = currentTargetIndex,
                                    )
                                    else -> 0f
                                }
                                val ruleColor = if (isV1) mColors.fg4 else MaterialTheme.colorScheme.outlineVariant
                                val dragBgColor = rowDragContainerColor()
                                val itemModifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        itemTopOffsets[entry.item.itemId] = coordinates.positionInParent().y
                                        itemHeights[entry.item.itemId] = coordinates.size.height.toFloat()
                                    }
                                    .graphicsLayer { translationY = visualOffset }
                                    .zIndex(if (isDragging) 1f else 0f)
                                val rowModifier = Modifier
                                    .then(
                                        if (isDragging) {
                                            Modifier.background(dragBgColor).drawBehind {
                                                if (showSourceListRule) {
                                                    drawRect(ruleColor, Offset.Zero, Size(3.dp.toPx(), size.height))
                                                }
                                            }
                                        } else if (showSourceListRule) {
                                            Modifier.drawBehind {
                                                drawRect(ruleColor, Offset.Zero, Size(3.dp.toPx(), size.height))
                                            }
                                        } else Modifier,
                                    )
                                val dragHandleModifier = if (showReorderHandle && readyIndex >= 0) {
                                    if (reorderActive) {
                                        Modifier.pointerInput(entry.item.itemId, readyIndex) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragStartTopOffsets = itemTopOffsets.toMap()
                                                dragStartHeights = itemHeights.toMap()
                                                draggingIndex = readyIndex
                                                dragOffsetY = 0f
                                                dragAccumScroll = 0f
                                                currentTargetIndex = readyIndex
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
                                                if (newTarget != currentTargetIndex) {
                                                    currentTargetIndex = newTarget
                                                }
                                            },
                                            onDragEnd = { finishDrag() },
                                            onDragCancel = { finishDrag() },
                                        )
                                    }
                                    } else {
                                        Modifier
                                    }
                                } else {
                                    null
                                }
                                Column(modifier = itemModifier) {
                                    LibraryQueueItemRow(
                                        item = entry.item,
                                        selection = SelectionState.Available(
                                            isActive = selectionActive,
                                            isSelected = entry.item.itemId in selectedIds,
                                            onToggle = { toggleSelection(entry.item.itemId) },
                                            onEnter = { enterSelectionMode(entry.item.itemId) },
                                        ),
                                        modifier = rowModifier,
                                        dragHandleModifier = dragHandleModifier,
                                        dragHandleContentDescription = if (reorderActive) {
                                            "Drag to reorder"
                                        } else {
                                            dragReorderUnavailableReason?.let { "Reorder unavailable: $it" }
                                                ?: "Reorder unavailable"
                                        },
                                        onOpen = { onOpenItem(entry.item.itemId) },
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
                                    RowDivider()
                                }
                            }
                        }
                    }
                }
                if (loadingMore) {
                    item(key = "load_more_spinner") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (showJumpToTop) {
                JumpPill(
                    label = "Jump to top",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpPillBottomPadding(jumpPillBottomClearance)),
                    onClick = {
                        actionScope.launch { listState.animateScrollToItem(index = 0) }
                    },
                )
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
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
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
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isV1) mColors.surface else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Pending ($count)",
            style = if (isV1) mTypography.section else MaterialTheme.typography.labelLarge,
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateSectionHeader(label: String) {
    SectionLabelHeader(label = label)
}

@Composable
private fun LibraryQueueItemRow(
    item: PlaybackQueueItem,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier? = null,
    dragHandleContentDescription: String = "Drag to reorder",
    onOpen: () -> Unit,
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

    val isSelectionActive = (selection as? SelectionState.Available)?.isActive == true
    val hasTrailingActions = !isSelectionActive && (
        onPlayNow != null ||
            onPlayNext != null ||
            onPlayLast != null ||
            onPlayFromHere != null ||
            onAddToPlaylist != null ||
            onFavoriteToggle != null ||
            onArchiveToggle != null ||
            onBin != null
        )

    val menuEntries = buildList {
        if (onPlayNext != null) {
            add(ItemActionMenuEntry.Action("Play Next") { onPlayNext() })
        }
        if (onPlayLast != null) {
            add(ItemActionMenuEntry.Action("Play Last") { onPlayLast() })
        }
        if (onPlayFromHere != null) {
            add(ItemActionMenuEntry.Action("Play from Here") { onPlayFromHere() })
        }
        val hasQueueActions = onPlayNext != null || onPlayLast != null || onPlayFromHere != null
        val hasItemActions = onAddToPlaylist != null ||
            onFavoriteToggle != null ||
            onArchiveToggle != null ||
            onBin != null
        if (hasQueueActions && hasItemActions) {
            add(ItemActionMenuEntry.Divider)
        }
        if (onAddToPlaylist != null) {
            add(ItemActionMenuEntry.Action("Add to Playlist") { onAddToPlaylist() })
        }
        if (onFavoriteToggle != null) {
            add(ItemActionMenuEntry.Action(if (item.isFavorited) "Unfavorite" else "Favorite") { onFavoriteToggle() })
        }
        if (onArchiveToggle != null) {
            add(ItemActionMenuEntry.Action(archiveToggleLabel) { onArchiveToggle() })
        }
        if (onBin != null) {
            add(ItemActionMenuEntry.Action("Bin") { onBin() })
        }
    }

    ItemRow(
        title = title,
        metadata = source,
        status = status,
        selection = selection,
        modifier = modifier,
        onOpen = onOpen,
        leadingContent = dragHandleModifier?.let { handleModifier ->
            {
                DragHandleIcon(
                    contentDescription = dragHandleContentDescription,
                    modifier = handleModifier,
                )
            }
        },
        onPlayNow = onPlayNow,
        menuEntries = if (hasTrailingActions) menuEntries else emptyList(),
    )
}
