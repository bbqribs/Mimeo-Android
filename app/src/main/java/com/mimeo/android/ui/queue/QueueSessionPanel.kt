package com.mimeo.android.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.ui.common.DragHandleIcon
import com.mimeo.android.ui.common.ItemActionMenuEntry
import com.mimeo.android.ui.common.ItemRow
import com.mimeo.android.ui.common.ItemRowPlayRemoveActions
import com.mimeo.android.ui.common.JumpPill
import com.mimeo.android.ui.common.RowDivider
import com.mimeo.android.ui.common.buildItemMetadata
import com.mimeo.android.ui.common.dragContainerColorFor
import com.mimeo.android.ui.common.jumpPillBottomPadding
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoDensityTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val NOW_PLAYING_SECTION_TITLE = "Now Playing"

internal enum class SessionRowAction {
    JumpPlay,
    Remove,
}

internal fun shouldShowJumpToNowPlayingPill(
    scrollOffsetPx: Int,
    activeTopOffsetPx: Float?,
    anchorTolerancePx: Float = 24f,
): Boolean {
    val activeTop = activeTopOffsetPx ?: return false
    return kotlin.math.abs(scrollOffsetPx.toFloat() - activeTop) > anchorTolerancePx
}

internal fun nowPlayingScrollTargetPx(activeTopOffsetPx: Float?): Int? {
    return activeTopOffsetPx?.toInt()
}

internal data class SessionStickyHeaderBounds(
    val title: String,
    val count: Int,
    val topPx: Float,
    val headerHeightPx: Float,
    val bottomPx: Float,
)

internal data class SessionStickyHeaderPresentation(
    val title: String,
    val count: Int,
    val offsetYPx: Float,
)

internal fun activeSessionStickyHeader(
    scrollOffsetPx: Int,
    sections: List<SessionStickyHeaderBounds>,
): SessionStickyHeaderPresentation? {
    val scrollTop = scrollOffsetPx.toFloat()
    val section = sections.lastOrNull { bounds ->
        scrollTop >= bounds.topPx && scrollTop < bounds.bottomPx && bounds.headerHeightPx > 0f
    } ?: return null
    val offsetY = (section.bottomPx - scrollTop - section.headerHeightPx).coerceAtMost(0f)
    return SessionStickyHeaderPresentation(
        title = section.title,
        count = section.count,
        offsetYPx = offsetY,
    )
}

internal fun activeAnchorTailSpacerPx(
    hasRowsBeforeActive: Boolean,
    viewportHeightPx: Int,
    activeHeightPx: Float,
    belowActiveContentHeightPx: Float,
): Float {
    if (!hasRowsBeforeActive || viewportHeightPx <= 0 || activeHeightPx <= 0f) return 0f
    return (viewportHeightPx.toFloat() - activeHeightPx - belowActiveContentHeightPx).coerceAtLeast(0f)
}

internal fun sessionRowTrailingActionOrder(
    showJumpPlay: Boolean,
    showRemove: Boolean,
): List<SessionRowAction> {
    return buildList {
        if (showJumpPlay) add(SessionRowAction.JumpPlay)
        if (showRemove) add(SessionRowAction.Remove)
    }
}


@Composable
private fun SessionSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val densityTokens = LocalMimeoDensityTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = if (isV1) densityTokens.sectionGap else 10.dp,
                bottom = 4.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title · $count",
            style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SessionStaticItemRow(
    item: NowPlayingSessionItem,
    onOpenItem: (Int) -> Unit,
    onJumpToItem: (Int) -> Unit,
    onArchiveItem: ((Int) -> Unit)? = null,
    onUnarchiveItem: ((Int) -> Unit)? = null,
    onBinItem: ((Int) -> Unit)? = null,
    showArchivedIndicator: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val title = item.title?.ifBlank { null } ?: item.url
    val source = item.host
        ?: item.sourceLabel?.takeIf { it.isNotBlank() }
        ?: item.sourceType?.takeIf { it.isNotBlank() }
    val metadata = buildItemMetadata(source, showArchived = showArchivedIndicator)
    val menuEntries = buildList {
        if (showArchivedIndicator && onUnarchiveItem != null) {
            add(ItemActionMenuEntry.Action("Unarchive") { onUnarchiveItem(item.itemId) })
        } else if (!showArchivedIndicator && onArchiveItem != null) {
            add(ItemActionMenuEntry.Action("Archive") { onArchiveItem(item.itemId) })
        }
        if (onBinItem != null) {
            add(ItemActionMenuEntry.Action("Move to Bin") { onBinItem(item.itemId) })
        }
    }
    ItemRow(
        title = title,
        metadata = metadata,
        status = null,
        modifier = modifier,
        onOpen = { onOpenItem(item.itemId) },
        onPlayNow = { onJumpToItem(item.itemId) },
        menuEntries = menuEntries,
    )
}

@Composable
internal fun NowPlayingSessionPanel(
    session: NowPlayingSession,
    seededFromLabel: String,
    onOpenItem: (Int) -> Unit,
    onJumpToQueueItem: (Int) -> Unit,
    onJumpToHistoryItem: (Int) -> Unit,
    onReorderItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
    snapBottomClearance: Dp = 0.dp,
    snapToActiveSignal: Int = 0,
    renderSnapPillLocally: Boolean = true,
    onSnapPillVisibilityChange: (Boolean) -> Unit = {},
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
    onArchiveSessionItem: (Int) -> Unit = {},
    onUnarchiveSessionHistoryItem: (Int) -> Unit = {},
    onBinSessionHistoryItem: (Int) -> Unit = {},
    onBinSessionEarlierItem: (Int) -> Unit = {},
    archivedHistoryItemIds: Set<Int> = emptySet(),
) {
    val densityTokens = LocalMimeoDensityTokens.current
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current

    // Local item list for optimistic drag reorder — only mutated on drop.
    // Keyed by itemId so position-only updates from the VM do not reset local order.
    val localItems = remember { mutableStateListOf<NowPlayingSessionItem>() }
    val serverItemIds = remember(session.items) { session.items.map { it.itemId } }
    LaunchedEffect(serverItemIds) {
        if (localItems.map { it.itemId } != serverItemIds) {
            localItems.clear()
            localItems.addAll(session.items)
        }
    }

    // Per-item measured bounds for drag hit-testing (itemId -> px).
    val itemTopOffsets = remember { mutableMapOf<Int, Float>() }
    val itemHeights = remember { mutableMapOf<Int, Float>() }
    var dragStartTopOffsets by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var dragStartHeights by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableIntStateOf(-1) }
    val listScrollState = rememberScrollState()
    val snapScope = rememberCoroutineScope()
    var listViewportHeight by remember { mutableIntStateOf(0) }
    var activeTopOffset by remember { mutableStateOf<Float?>(null) }
    var activeMeasuredHeight by remember { mutableFloatStateOf(0f) }

    fun avgItemHeight(): Float =
        if (itemHeights.isEmpty()) 72f else itemHeights.values.average().toFloat()

    fun activeIndex(): Int =
        session.currentItem?.itemId?.let { currentItemId ->
            localItems.indexOfFirst { it.itemId == currentItemId }
        }?.takeIf { it >= 0 }
            ?: session.currentIndex.coerceIn(0, (localItems.size - 1).coerceAtLeast(0))

    fun upcomingStartIndex(): Int = (activeIndex() + 1).coerceIn(0, localItems.size)

    fun upcomingItems(): List<NowPlayingSessionItem> = localItems.drop(upcomingStartIndex())

    fun absoluteIndexForUpcoming(upcomingIndex: Int): Int = upcomingStartIndex() + upcomingIndex

    fun scrollDraggedItemNearEdge(from: Int) {
        val upcoming = upcomingItems()
        if (from !in upcoming.indices || listViewportHeight <= 0) return
        val tops = dragStartTopOffsets.takeIf { it.isNotEmpty() } ?: itemTopOffsets
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val itemId = upcoming[from].itemId
        val itemTop = (tops[itemId] ?: (from * avgItemHeight())) + dragOffsetY
        val itemBottom = itemTop + (heights[itemId] ?: avgItemHeight())
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
        val upcoming = upcomingItems()
        if (upcoming.size <= 1 || from !in upcoming.indices) return from
        val tops = dragStartTopOffsets.takeIf { it.isNotEmpty() } ?: itemTopOffsets
        val heights = dragStartHeights.takeIf { it.isNotEmpty() } ?: itemHeights
        val fromItemId = upcoming[from].itemId
        val h = heights[fromItemId] ?: avgItemHeight()
        val top = tops[fromItemId] ?: (from * avgItemHeight())
        val draggedTopY = top + offsetY
        val draggedBottomY = draggedTopY + h
        var target = from
        upcoming.indices.forEach { i ->
            if (i == from) return@forEach
            val itemId = upcoming[i].itemId
            val t = tops[itemId] ?: (i * avgItemHeight())
            val iH = heights[itemId] ?: avgItemHeight()
            val iMidY = t + iH / 2f
            if (from < i && draggedBottomY > iMidY) target = i
            if (from > i && draggedTopY < iMidY && i < target) target = i
        }
        return target.coerceIn(0, upcoming.lastIndex)
    }

    fun visualOffsetForItem(index: Int, from: Int, target: Int): Float {
        if (from < 0 || from == target || index == from) return 0f
        val upcoming = upcomingItems()
        if (from !in upcoming.indices) return 0f
        val draggedItemId = upcoming[from].itemId
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

    fun onDragEnd() {
        val from = draggingIndex
        val target = currentTargetIndex
        val absoluteFrom = if (from >= 0) absoluteIndexForUpcoming(from) else -1
        val absoluteTarget = if (target >= 0) absoluteIndexForUpcoming(target) else -1
        val shouldReorder =
            absoluteFrom in localItems.indices &&
            absoluteTarget in localItems.indices &&
            absoluteTarget != absoluteFrom
        if (shouldReorder) {
            val moved = localItems.removeAt(absoluteFrom)
            localItems.add(absoluteTarget, moved)
        }
        draggingIndex = -1
        dragOffsetY = 0f
        currentTargetIndex = -1
        dragStartTopOffsets = emptyMap()
        dragStartHeights = emptyMap()
        if (shouldReorder) {
            onReorderItem(absoluteFrom, absoluteTarget)
        }
    }

    val currentItemId = session.currentItem?.itemId
    val currentIndex = localItems.indexOfFirst { it.itemId == currentItemId }
        .takeIf { it >= 0 }
        ?: session.currentIndex.coerceIn(0, (localItems.size - 1).coerceAtLeast(0))
    val activeItem = localItems.getOrNull(currentIndex)
    val historyItems = session.historyItems
    val earlierItems = localItems.take(currentIndex)
    val hasRowsBeforeActive = historyItems.isNotEmpty() || earlierItems.isNotEmpty()
    val upcomingStartIndex = (currentIndex + 1).coerceIn(0, localItems.size)
    val upcomingItems = localItems.drop(upcomingStartIndex)
    val upcomingItemIds = remember(upcomingItems) { upcomingItems.map { it.itemId } }
    val density = LocalDensity.current
    val minVisibleActiveHeightPx = with(density) { 24.dp.toPx() }
    var historyStickyBounds by remember { mutableStateOf<SessionStickyHeaderBounds?>(null) }
    var earlierStickyBounds by remember { mutableStateOf<SessionStickyHeaderBounds?>(null) }
    var historyHeaderHeightPx by remember { mutableFloatStateOf(0f) }
    var earlierHeaderHeightPx by remember { mutableFloatStateOf(0f) }
    var upcomingSectionTopOffset by remember(currentItemId, upcomingItemIds) { mutableStateOf<Float?>(null) }
    var upcomingSectionBottomOffset by remember(currentItemId, upcomingItemIds) { mutableStateOf<Float?>(null) }
    val upcomingSectionHeightPx = remember(upcomingSectionTopOffset, upcomingSectionBottomOffset) {
        val top = upcomingSectionTopOffset
        val bottom = upcomingSectionBottomOffset
        if (top != null && bottom != null && bottom >= top) bottom - top else null
    }
    val activeTailSpacerPx = activeAnchorTailSpacerPx(
        hasRowsBeforeActive = hasRowsBeforeActive,
        viewportHeightPx = listViewportHeight,
        activeHeightPx = activeMeasuredHeight,
        belowActiveContentHeightPx = upcomingSectionHeightPx ?: 0f,
    )
    var initialActiveAnchorReady by remember(currentItemId) {
        mutableStateOf(currentItemId == null)
    }
    LaunchedEffect(historyItems.isEmpty()) {
        if (historyItems.isEmpty()) {
            historyStickyBounds = null
            historyHeaderHeightPx = 0f
        }
    }
    LaunchedEffect(earlierItems.isEmpty()) {
        if (earlierItems.isEmpty()) {
            earlierStickyBounds = null
            earlierHeaderHeightPx = 0f
        }
    }
    // Track measured heights of Earlier rows so scroll compensation uses exact values.
    val earlierItemHeights = remember { mutableMapOf<Int, Float>() }
    // When items are re-inserted into Earlier in Queue (e.g. bin undo), compensate the
    // scroll position in the same composition frame so layout sees the corrected offset
    // before draw. SideEffect runs synchronously after composition, before layout/draw.
    val prevEarlierIds = remember { mutableListOf<Int>() }
    SideEffect {
        val curIds = earlierItems.map { it.itemId }
        val prevSet = prevEarlierIds.toHashSet()
        val added = curIds.filter { it !in prevSet }
        // Only compensate when the viewport is already scrolled past 0. When scroll == 0
        // everything is visible at the top; dispatchRawDelta would push content above the
        // viewport even though it was visible before the undo.
        if (added.isNotEmpty() && listScrollState.value > 0) {
            var totalHeight = added.sumOf {
                (earlierItemHeights[it] ?: avgItemHeight()).toDouble()
            }.toFloat()
            // If the Earlier section just appeared (was absent before), the section header
            // is also new content that pushes items below it downward.
            if (prevSet.isEmpty()) totalHeight += earlierHeaderHeightPx
            listScrollState.dispatchRawDelta(totalHeight)
        }
        prevEarlierIds.clear()
        prevEarlierIds.addAll(curIds)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = seededFromLabel,
                style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
                color = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailingActions?.invoke(this)
        }
        LaunchedEffect(
            currentItemId,
            activeTopOffset,
            activeMeasuredHeight,
            listViewportHeight,
            hasRowsBeforeActive,
            upcomingSectionHeightPx,
            initialActiveAnchorReady,
        ) {
            if (currentItemId == null) {
                initialActiveAnchorReady = true
                return@LaunchedEffect
            }
            if (initialActiveAnchorReady) return@LaunchedEffect
            if (hasRowsBeforeActive && (listViewportHeight <= 0 || activeMeasuredHeight <= 0f)) {
                return@LaunchedEffect
            }
            if (hasRowsBeforeActive && upcomingSectionHeightPx == null) {
                return@LaunchedEffect
            }
            val target = nowPlayingScrollTargetPx(activeTopOffset) ?: return@LaunchedEffect
            listScrollState.scrollTo(target)
            initialActiveAnchorReady = true
        }
        LaunchedEffect(snapToActiveSignal) {
            if (snapToActiveSignal > 0 && initialActiveAnchorReady) {
                nowPlayingScrollTargetPx(activeTopOffset)?.let { target ->
                    listScrollState.animateScrollTo(target)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { listViewportHeight = it.height }
                .clipToBounds()
                .passiveVerticalScrollIndicator(
                    scrollState = listScrollState,
                    color = if (isV1) mColors.fg4 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (initialActiveAnchorReady) 1f else 0f }
                    .verticalScroll(listScrollState),
            ) {
                if (historyItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInParent().y
                                historyStickyBounds = SessionStickyHeaderBounds(
                                    title = "History",
                                    count = historyItems.size,
                                    topPx = top,
                                    headerHeightPx = historyHeaderHeightPx,
                                    bottomPx = top + coords.size.height,
                                )
                            },
                    ) {
                        SessionSectionHeader(
                            title = "History",
                            count = historyItems.size,
                            modifier = Modifier.onSizeChanged { size ->
                                historyHeaderHeightPx = size.height.toFloat()
                            },
                        )
                        historyItems.forEachIndexed { index, item ->
                            SessionStaticItemRow(
                                item = item,
                                onOpenItem = onOpenItem,
                                onJumpToItem = onJumpToHistoryItem,
                                onArchiveItem = onArchiveSessionItem,
                                onUnarchiveItem = onUnarchiveSessionHistoryItem,
                                onBinItem = onBinSessionHistoryItem,
                                showArchivedIndicator = item.itemId in archivedHistoryItemIds,
                            )
                            if (index < historyItems.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
                if (earlierItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInParent().y
                                earlierStickyBounds = SessionStickyHeaderBounds(
                                    title = "Earlier in queue",
                                    count = earlierItems.size,
                                    topPx = top,
                                    headerHeightPx = earlierHeaderHeightPx,
                                    bottomPx = top + coords.size.height,
                                )
                            },
                    ) {
                        SessionSectionHeader(
                            title = "Earlier in queue",
                            count = earlierItems.size,
                            modifier = Modifier.onSizeChanged { size ->
                                earlierHeaderHeightPx = size.height.toFloat()
                            },
                        )
                        earlierItems.forEachIndexed { index, item ->
                            SessionStaticItemRow(
                                item = item,
                                onOpenItem = onOpenItem,
                                onJumpToItem = onJumpToQueueItem,
                                onArchiveItem = onArchiveSessionItem,
                                onUnarchiveItem = onUnarchiveSessionHistoryItem,
                                onBinItem = onBinSessionEarlierItem,
                                showArchivedIndicator = item.itemId in archivedHistoryItemIds,
                                modifier = Modifier.onSizeChanged { size ->
                                    earlierItemHeights[item.itemId] = size.height.toFloat()
                                },
                            )
                            if (index < earlierItems.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
                activeItem?.let { item ->
                    val sourceLabel = item.host
                        ?: item.sourceLabel?.takeIf { it.isNotBlank() }
                        ?: item.sourceType?.takeIf { it.isNotBlank() }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInParent().y
                                val height = coords.size.height.toFloat()
                                itemTopOffsets[item.itemId] = top
                                itemHeights[item.itemId] = height
                                activeTopOffset = top
                                activeMeasuredHeight = height
                            },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = NOW_PLAYING_SECTION_TITLE,
                            style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
                            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenItem(item.itemId) }
                                .background(
                                    color = if (isV1) mColors.nowTint else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    shape = if (isV1) mShapes.card else RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isV1) mColors.accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = if (isV1) mShapes.card else RoundedCornerShape(8.dp),
                                )
                                .padding(start = 12.dp, end = 12.dp, top = densityTokens.rowPadV, bottom = densityTokens.rowPadV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title?.ifBlank { null } ?: item.url,
                                    style = if (isV1) mTypography.row else MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isV1) mColors.fg else MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (sourceLabel != null) {
                                    Text(
                                        text = sourceLabel,
                                        style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val top = coords.positionInParent().y
                            upcomingSectionTopOffset = top
                            upcomingSectionBottomOffset = top + coords.size.height
                        }
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Up Next · ${upcomingItems.size}",
                        style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        enabled = upcomingItems.isNotEmpty(),
                        onClick = onClearUpcoming,
                    ) {
                        Text(
                            text = "Clear upcoming",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (upcomingItems.isEmpty()) {
                    Text(
                        text = "No upcoming items.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInParent().y
                                upcomingSectionBottomOffset = top + coords.size.height
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                upcomingItems.forEachIndexed { index, item ->
                    key(item.itemId) {
                        val absoluteIndex = upcomingStartIndex + index
                        val isDragging = draggingIndex == index
                        val itemVisualOffsetY = when {
                            isDragging -> dragOffsetY
                            draggingIndex >= 0 -> visualOffsetForItem(index, draggingIndex, currentTargetIndex)
                            else -> 0f
                        }
                        val sourceLabel = item.host
                            ?: item.sourceLabel?.takeIf { it.isNotBlank() }
                            ?: item.sourceType?.takeIf { it.isNotBlank() }
                        val rowTitle = item.title?.ifBlank { null } ?: item.url
                        val dragContainerColor = dragContainerColorFor(isDragging)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = itemVisualOffsetY }
                                .onGloballyPositioned { coords ->
                                    val top = coords.positionInParent().y
                                    itemTopOffsets[item.itemId] = top
                                    itemHeights[item.itemId] = coords.size.height.toFloat()
                                    upcomingSectionBottomOffset = (top + coords.size.height)
                                        .coerceAtLeast(upcomingSectionBottomOffset ?: top)
                                },
                        ) {
                            ItemRow(
                                title = rowTitle,
                                metadata = sourceLabel,
                                status = null,
                                modifier = Modifier.semantics {
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move up") {
                                            onReorderItem(absoluteIndex, absoluteIndex - 1); true
                                        })
                                        if (index < upcomingItems.lastIndex) add(CustomAccessibilityAction("Move down") {
                                            onReorderItem(absoluteIndex, absoluteIndex + 1); true
                                        })
                                    }
                                },
                                containerColor = dragContainerColor,
                                onOpen = { onOpenItem(item.itemId) },
                                leadingContent = {
                                    DragHandleIcon(
                                        contentDescription = "Drag to reorder",
                                        modifier = Modifier.pointerInput(item.itemId, index) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragStartTopOffsets = itemTopOffsets.toMap()
                                                    dragStartHeights = itemHeights.toMap()
                                                    draggingIndex = index
                                                    dragOffsetY = 0f
                                                    currentTargetIndex = index
                                                },
                                                onDrag = { _, dragAmount ->
                                                    dragOffsetY += dragAmount.y
                                                    scrollDraggedItemNearEdge(draggingIndex)
                                                    val newTarget = computeTargetIndex(draggingIndex, dragOffsetY)
                                                    if (newTarget != currentTargetIndex) currentTargetIndex = newTarget
                                                },
                                                onDragEnd = { onDragEnd() },
                                                onDragCancel = { onDragEnd() },
                                            )
                                        },
                                    )
                                },
                                trailingContent = {
                                    ItemRowPlayRemoveActions(
                                        title = rowTitle,
                                        onPlayNow = { onJumpToQueueItem(item.itemId) },
                                        onRemove = { onRemoveItem(item.itemId) },
                                        playContentDescription = "Jump/Play $rowTitle",
                                        removeContentDescription = "Remove from session",
                                    )
                                },
                            )
                            if (index < upcomingItems.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
                if (activeTailSpacerPx > 0f) {
                    Spacer(modifier = Modifier.height(with(density) { activeTailSpacerPx.toDp() }))
                }
            }
            activeSessionStickyHeader(
                scrollOffsetPx = listScrollState.value,
                sections = listOfNotNull(historyStickyBounds, earlierStickyBounds),
            )?.let { stickyHeader ->
                SessionSectionHeader(
                    title = stickyHeader.title,
                    count = stickyHeader.count,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .zIndex(2f)
                        .graphicsLayer { translationY = stickyHeader.offsetYPx }
                        .background(if (isV1) mColors.surface else MaterialTheme.colorScheme.surface),
                )
            }
            val showSnapToActive = activeItem != null &&
                initialActiveAnchorReady &&
                listViewportHeight > 0 &&
                activeMeasuredHeight > 0f &&
                shouldShowJumpToNowPlayingPill(
                    scrollOffsetPx = listScrollState.value,
                    activeTopOffsetPx = activeTopOffset,
                    anchorTolerancePx = minVisibleActiveHeightPx,
                )
            LaunchedEffect(showSnapToActive) {
                onSnapPillVisibilityChange(showSnapToActive)
            }
            if (renderSnapPillLocally && showSnapToActive) {
                JumpToNowPlayingPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpPillBottomPadding(snapBottomClearance)),
                    onClick = {
                        snapScope.launch {
                            nowPlayingScrollTargetPx(activeTopOffset)?.let { target ->
                                listScrollState.animateScrollTo(target)
                            }
                        }
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
fun JumpToNowPlayingPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    JumpPill(label = "Jump to Now Playing", modifier = modifier, onClick = onClick)
}
