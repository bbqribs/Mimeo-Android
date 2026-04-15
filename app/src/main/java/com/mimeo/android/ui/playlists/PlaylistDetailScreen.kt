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

/**
 * Ordered list of playlist entries with drag-to-reorder support.
 * Each row shows a drag handle (≡) on the left; touch and drag vertically to reorder.
 * Reorder is persisted to the server via PUT /playlists/{id}/entries/reorder on drop.
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

    // selectPlaylist() already triggers loadQueue() asynchronously; refreshPlaylists() ensures
    // entries/positions are current. We don't call loadQueueIfNotRecent() here because the
    // playlist switch may have been very recent and that call would skip on stale items.
    LaunchedEffect(playlistId) {
        vm.refreshPlaylists()
    }

    val playlist = playlists.firstOrNull { it.id == playlistId }
    val serverEntries = remember(playlist) {
        playlist?.entries?.sortedBy { it.position ?: Double.MAX_VALUE } ?: emptyList()
    }

    // Build a lookup map from all currently-loaded item sources.
    // This lets us show titles for archived/bin items if those views have been opened.
    val allItemsMap = remember(queueItems, inboxItems, archivedItems, favoriteItems, binItems) {
        (queueItems + inboxItems + archivedItems + favoriteItems + binItems)
            .associateBy { it.itemId }
    }

    // Local reorderable list; reset whenever server state changes (e.g. after a refresh).
    val localEntries = remember(serverEntries) { serverEntries.toMutableStateList() }

    // Per-item Y positions within the scrollable Column for drag hit-testing.
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() }  // index -> top px
    val itemHeights = remember { mutableMapOf<Int, Float>() }      // index -> height px

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    fun computeTargetIndex(fromIndex: Int, currentOffsetY: Float): Int {
        if (localEntries.isEmpty()) return fromIndex
        val draggedHeight = itemHeights[fromIndex] ?: 72f
        val fromTop = itemTopOffsets[fromIndex] ?: (fromIndex * draggedHeight)
        val draggedMidY = fromTop + draggedHeight / 2f + currentOffsetY
        var best = fromIndex
        var bestDist = Float.MAX_VALUE
        localEntries.indices.forEach { i ->
            val top = itemTopOffsets[i] ?: (i * (itemHeights[i] ?: 72f))
            val h = itemHeights[i] ?: 72f
            val mid = top + h / 2f
            val dist = kotlin.math.abs(draggedMidY - mid)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best.coerceIn(0, localEntries.lastIndex)
    }

    fun onDragEnd() {
        val from = draggingIndex
        if (from < 0) return
        val target = computeTargetIndex(from, dragOffsetY)
        if (target != from) {
            val moved = localEntries.removeAt(from)
            localEntries.add(target, moved)
        }
        val entryIds = localEntries.map { it.id }
        draggingIndex = -1
        dragOffsetY = 0f
        isSaving = true
        actionScope.launch {
            vm.reorderPlaylistEntries(playlistId, entryIds)
                .onFailure { onShowSnackbar("Couldn't save order. Pull to refresh.", null, null) }
            isSaving = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Header card
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
                        text = "Playlist not found.",
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
                        text = "No items in this playlist yet.",
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
                    verticalArrangement = Arrangement.spacedBy(0.dp),
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
                            onDragStart = { localIndex ->
                                draggingIndex = localIndex
                                dragOffsetY = 0f
                            },
                            onDrag = { dy -> dragOffsetY += dy },
                            onDragEnd = { onDragEnd() },
                            index = index,
                            // Always open by article ID — archived items still have readable content.
                            onTap = { onOpenPlayer(entry.articleId) },
                        )
                        if (index < localEntries.lastIndex) {
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
    val elevation = if (isDragging) 8.dp else 0.dp
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
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag handle — touch here to start dragging.
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
                    text = title ?: "Loading…",
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
