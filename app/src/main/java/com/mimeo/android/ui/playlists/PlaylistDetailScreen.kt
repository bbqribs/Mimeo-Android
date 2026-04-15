package com.mimeo.android.ui.playlists

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
import androidx.compose.runtime.toMutableStateList
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
import kotlin.math.roundToInt

/**
 * Ordered list of playlist entries with drag-to-reorder support.
 *
 * Each row has an explicit drag handle (≡) on the left. Touch and drag the handle
 * vertically; items swap in real time as the dragged item crosses their midpoints.
 * On drop the new order is persisted via PUT /playlists/{id}/entries/reorder.
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

    // selectPlaylist() already starts an async queue load; we also load archived items
    // so playlist entries that have been archived show their titles here.
    LaunchedEffect(playlistId) {
        vm.refreshPlaylists()
        vm.loadArchivedItems()
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

    // Local mutable list that gets swapped in real time during drag.
    val localEntries = remember(serverEntries) { serverEntries.toMutableStateList() }

    // Per-item Y positions in the Column for drag hit-testing.
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() }
    val itemHeights = remember { mutableMapOf<Int, Float>() }

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    /** Average measured item height; falls back to a reasonable default. */
    fun avgItemHeight(): Float =
        if (itemHeights.isEmpty()) 72f else itemHeights.values.average().toFloat()

    /**
     * Compute which index the dragged item's visual midpoint is nearest to.
     * Uses measured top-offsets when available; falls back to index × avgHeight.
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
     * During drag: swap items if the drag has moved enough to cross a neighbour's midpoint.
     * Adjusts dragOffsetY after each swap so the item visually stays under the finger.
     */
    fun maybeSwap() {
        val from = draggingIndex
        if (from < 0) return
        val target = computeTargetIndex(from, dragOffsetY)
        if (target == from) return

        val direction = if (target > from) 1 else -1
        val steps = abs(target - from)
        val moved = localEntries.removeAt(from)
        localEntries.add(target, moved)

        // Compensate dragOffsetY so the item's visual position stays stable after the swap.
        val compensate = (1..steps).sumOf { step ->
            val neighbourIndex = if (direction > 0) from + step - 1 else from - step
            (itemHeights[neighbourIndex] ?: avgItemHeight()).toDouble()
        }.toFloat()
        dragOffsetY -= direction * compensate

        draggingIndex = target
    }

    fun onDragEnd() {
        if (draggingIndex < 0) return
        val entryIds = localEntries.map { it.id }
        draggingIndex = -1
        dragOffsetY = 0f
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
                        PlaylistDetailRow(
                            entry = entry,
                            queueItem = queueItem,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onPositioned = { top, height ->
                                itemTopOffsets[index] = top
                                itemHeights[index] = height
                            },
                            onDragStart = { idx ->
                                draggingIndex = idx
                                dragOffsetY = 0f
                            },
                            onDrag = { dy ->
                                dragOffsetY += dy
                                maybeSwap()
                            },
                            onDragEnd = { onDragEnd() },
                            index = index,
                            onTap = { onOpenPlayer(entry.articleId) },
                        )
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        )
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
    dragOffsetY: Float,
    onPositioned: (top: Float, height: Float) -> Unit,
    onDragStart: (index: Int) -> Unit,
    onDrag: (dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    index: Int,
    onTap: () -> Unit,
) {
    ElevatedCard(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onGloballyPositioned { coords ->
                onPositioned(coords.positionInParent().y, coords.size.height.toFloat())
            },
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
