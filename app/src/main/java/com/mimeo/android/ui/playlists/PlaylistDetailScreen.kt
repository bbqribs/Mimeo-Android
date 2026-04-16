package com.mimeo.android.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlaylistEntrySummary
import com.mimeo.android.model.PlaybackQueueItem
import kotlinx.coroutines.launch
import kotlin.math.abs

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
) {
    val playlists by vm.playlists.collectAsState()
    val queueItems by vm.queueItems.collectAsState()
    val inboxItems by vm.inboxItems.collectAsState()
    val archivedItems by vm.archivedItems.collectAsState()
    val favoriteItems by vm.favoriteItems.collectAsState()
    val binItems by vm.binItems.collectAsState()
    val loading by vm.queueLoading.collectAsState()
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
    LaunchedEffect(serverEntryIds) {
        if (localEntries.map { it.id } != serverEntryIds) {
            localEntries.clear()
            localEntries.addAll(serverEntries)
        }
    }

    // Per-item Y positions in the Column for drag hit-testing.
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() }
    val itemHeights = remember { mutableMapOf<Int, Float>() }

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // Cached target index — updated on every onDrag so onDragEnd can read it reliably.
    var currentTargetIndex by remember { mutableIntStateOf(-1) }
    var isSaving by remember { mutableStateOf(false) }

    /** Average measured item height; falls back to a reasonable default. */
    fun avgItemHeight(): Float =
        if (itemHeights.isEmpty()) 72f else itemHeights.values.average().toFloat()

    /**
     * Compute which index the dragged item's visual midpoint is nearest to.
     */
    fun computeTargetIndex(from: Int, offsetY: Float): Int {
        if (localEntries.size <= 1) return from
        val h = itemHeights[from] ?: avgItemHeight()
        val top = itemTopOffsets[from] ?: (from * avgItemHeight())
        val midY = top + h / 2f + offsetY
        var best = from
        var bestDist = Float.MAX_VALUE
        localEntries.indices.forEach { i ->
            val t = itemTopOffsets[i] ?: (i * avgItemHeight())
            val iH = itemHeights[i] ?: avgItemHeight()
            val iMid = t + iH / 2f
            val d = abs(midY - iMid)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best.coerceIn(0, localEntries.lastIndex)
    }

    /**
     * Visual Y offset for a non-dragged item at [index], given the current drag
     * from [from] toward [target].  Items between from and target are shifted to
     * visually make room without mutating localEntries.
     */
    fun visualOffsetForItem(index: Int, from: Int, target: Int): Float {
        if (from < 0 || from == target || index == from) return 0f
        val draggedHeight = itemHeights[from] ?: avgItemHeight()
        return when {
            target > from && index in (from + 1)..target -> -draggedHeight
            target < from && index in target until from  ->  draggedHeight
            else -> 0f
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
        if (from < 0) return
        val entryIds = localEntries.map { it.id }
        isSaving = true
        actionScope.launch {
            vm.reorderPlaylistEntries(playlistId, entryIds)
                .onFailure { onShowSnackbar("Couldn't save order.", null, null) }
            isSaving = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
            }
        }

        when {
            playlist == null -> {
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
            }
            localEntries.isEmpty() && !loading -> {
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
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    localEntries.forEachIndexed { index, entry ->
                        val queueItem = allItemsMap[entry.articleId]
                        val isDragging = draggingIndex == index
                        val itemVisualOffsetY = when {
                            isDragging -> dragOffsetY
                            draggingIndex >= 0 -> visualOffsetForItem(
                                index, draggingIndex, currentTargetIndex
                            )
                            else -> 0f
                        }
                        // Wrap card + divider together so the divider moves with its card.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = itemVisualOffsetY }
                                .onGloballyPositioned { coords ->
                                    itemTopOffsets[index] = coords.positionInParent().y
                                    itemHeights[index] = coords.size.height.toFloat()
                                },
                        ) {
                            PlaylistDetailRow(
                                entry = entry,
                                queueItem = queueItem,
                                isDragging = isDragging,
                                onDragStart = { idx ->
                                    draggingIndex = idx
                                    dragOffsetY = 0f
                                    currentTargetIndex = idx
                                },
                                onDrag = { dy ->
                                    dragOffsetY += dy
                                    val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
                                    if (newTarget != currentTargetIndex) {
                                        currentTargetIndex = newTarget
                                    }
                                },
                                onDragEnd = { onDragEnd() },
                                index = index,
                                onTap = { onOpenPlayer(entry.articleId) },
                            )
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
}

@Composable
private fun PlaylistDetailRow(
    entry: PlaylistEntrySummary,
    queueItem: PlaybackQueueItem?,
    isDragging: Boolean,
    onDragStart: (index: Int) -> Unit,
    onDrag: (dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    index: Int,
    onTap: () -> Unit,
) {
    ElevatedCard(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Black,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isDragging) 8.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = queueItem?.title?.ifBlank { null }
                    ?: queueItem?.host
                    ?: queueItem?.url
                Text(
                    text = title ?: "Loading\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val host = queueItem?.host?.takeIf { queueItem.title != null }
                if (host != null) {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
