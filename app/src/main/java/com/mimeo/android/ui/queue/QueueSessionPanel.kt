package com.mimeo.android.ui.queue

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.model.UpNextHistoryEntry
import com.mimeo.android.model.UpNextHistoryProjection
import com.mimeo.android.model.UpNextHistoryRemovalTarget
import com.mimeo.android.ui.common.DragHandleIcon
import com.mimeo.android.ui.common.ItemActionMenuEntry
import com.mimeo.android.ui.common.ItemRow
import com.mimeo.android.ui.common.ItemRowPlayRemoveActions
import com.mimeo.android.ui.common.JumpPill
import com.mimeo.android.ui.common.RowDivider
import com.mimeo.android.ui.common.SectionLabelChip
import com.mimeo.android.ui.common.SectionLabelHeader
import com.mimeo.android.ui.common.SelectionState
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

internal enum class SessionLifecycleAction {
    Archive,
    Unarchive,
    MoveToBin,
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

internal fun sessionPanelActiveIndex(
    currentItemId: Int?,
    localItemIds: List<Int>,
): Int = currentItemId?.let(localItemIds::indexOf)?.takeIf { it >= 0 } ?: -1

internal fun <T> sessionPanelEarlierItems(
    localItems: List<T>,
    currentIndex: Int,
): List<T> = if (currentIndex >= 0) localItems.take(currentIndex) else emptyList()

internal fun <T> sessionPanelHistoryItems(historyItems: List<T>): List<T> =
    historyItems.asReversed()

internal data class SessionHistoryPresentationRow(
    val item: NowPlayingSessionItem,
    val entryId: Long? = null,
    val playedAt: String? = null,
    val stillInSession: Boolean = false,
    val isTrashed: Boolean = false,
)

internal fun UpNextHistoryEntry.toSessionHistoryPresentationRow() =
    SessionHistoryPresentationRow(
        item = NowPlayingSessionItem(
            itemId = itemId,
            title = title,
            url = url,
            host = host,
            sourceType = null,
            sourceLabel = null,
            sourceUrl = null,
            captureKind = null,
            sourceAppPackage = null,
            status = status,
            activeContentVersionId = activeContentVersionId,
            lastReadPercent = lastReadPercent,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
            isArchived = isArchived,
        ),
        entryId = entryId,
        playedAt = playedAt,
        stillInSession = stillInSession,
        isTrashed = isTrashed,
    )

internal fun historyEmptyCopy(recordingSince: String?, lastRemovalAt: String? = null): String = recordingSince
    ?.let {
        if (lastRemovalAt != null) {
            "No History entries are currently shown — recording since $it; History was edited or cleared at $lastRemovalAt."
        } else {
            "No History entries yet — recording since $it."
        }
    }
    ?: "History will appear after this account connects to the server."

internal fun historyBoundedCopy(hasMore: Boolean, effectiveLimit: Int): String? =
    if (hasMore) "Only the $effectiveLimit most recent unique History articles are shown." else null

internal fun historyEditedCopy(lastRemovalAt: String?): String? = lastRemovalAt?.let {
    "History has been edited or cleared since recording began (last change $it)."
}

internal enum class SessionSelectionSection { HISTORY, EARLIER }

internal data class SessionRowSelectionKey(
    val section: SessionSelectionSection,
    val rowId: Long,
    val itemId: Int,
)

internal data class HistorySelectionActions(
    val canRemove: Boolean,
    val canMoveToBin: Boolean,
    val canRestore: Boolean,
    val canArchive: Boolean,
    val canUnarchive: Boolean,
    val explanation: String? = null,
)

internal fun historySelectionActions(
    selectedRows: List<SessionHistoryPresentationRow>,
    managementEnabled: Boolean,
): HistorySelectionActions {
    if (selectedRows.isEmpty()) return HistorySelectionActions(false, false, false, false, false)
    val allBinned = selectedRows.all { it.isTrashed }
    val allUnbinned = selectedRows.all { !it.isTrashed }
    val allArchived = allUnbinned && selectedRows.all { it.item.isArchived }
    val allUnarchived = allUnbinned && selectedRows.all { !it.item.isArchived }
    val mixedLifecycle = !allBinned && !allUnbinned
    return HistorySelectionActions(
        canRemove = managementEnabled,
        canMoveToBin = managementEnabled && allUnbinned,
        canRestore = managementEnabled && allBinned,
        canArchive = allUnarchived,
        canUnarchive = allArchived,
        explanation = when {
            !managementEnabled -> "History management is unavailable offline or until History finishes loading."
            mixedLifecycle -> "Bin, Restore, Archive, and Unarchive require rows with the same lifecycle state."
            allUnbinned && !allArchived && !allUnarchived ->
                "Archive and Unarchive require rows with the same archive state."
            else -> null
        },
    )
}

internal fun SessionHistoryPresentationRow.historyRemovalTarget(): UpNextHistoryRemovalTarget? =
    entryId?.takeIf { it > 0 }?.let { fence ->
        UpNextHistoryRemovalTarget(itemId = item.itemId, throughEntryId = fence)
    }

internal fun <T, K> sessionPanelPresentationItems(
    localItems: List<T>,
    authoritativeItems: List<T>,
    itemKey: (T) -> K,
): List<T> {
    val authoritativeByKey = authoritativeItems.associateBy(itemKey)
    return localItems.map { localItem ->
        authoritativeByKey[itemKey(localItem)] ?: localItem
    }
}

internal fun <T> sessionPanelUpcomingItems(
    localItems: List<T>,
    currentIndex: Int,
): List<T> {
    val startIndex = (currentIndex + 1).coerceIn(0, localItems.size)
    return localItems.drop(startIndex)
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

internal fun sessionLifecycleActionOrder(
    isArchived: Boolean,
    canArchive: Boolean,
    canUnarchive: Boolean,
    canMoveToBin: Boolean,
): List<SessionLifecycleAction> = buildList {
    if (isArchived && canUnarchive) {
        add(SessionLifecycleAction.Unarchive)
    } else if (!isArchived && canArchive) {
        add(SessionLifecycleAction.Archive)
    }
    if (canMoveToBin) add(SessionLifecycleAction.MoveToBin)
}

internal fun selectedSessionArchiveActionIds(
    selectedIds: Set<Int>,
    archivedByItemId: Map<Int, Boolean>,
    archive: Boolean,
): Set<Int> = selectedIds.filterTo(linkedSetOf()) { itemId ->
    archivedByItemId[itemId] == !archive
}

@Composable
private fun SessionSelectionBar(
    selectedCount: Int,
    canArchive: Boolean,
    canUnarchive: Boolean,
    onClearSelection: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    historyMode: Boolean = false,
    canRemoveHistory: Boolean = false,
    canMoveToBin: Boolean = false,
    canRestore: Boolean = false,
    explanation: String? = null,
    onRemoveHistory: () -> Unit = {},
    onMoveToBin: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val exitFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { exitFocusRequester.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.focusRequester(exitFocusRequester),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode",
                )
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onArchive, enabled = canArchive) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = if (canArchive || !historyMode) {
                        "Archive selected"
                    } else {
                        "Archive unavailable for this selection"
                    },
                )
            }
            IconButton(onClick = onUnarchive, enabled = canUnarchive) {
                Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = if (canUnarchive || !historyMode) {
                        "Unarchive selected"
                    } else {
                        "Unarchive unavailable for this selection"
                    },
                )
            }
            if (historyMode) {
                IconButton(onClick = onMoveToBin, enabled = canMoveToBin) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (canMoveToBin) {
                            "Move selected History articles to Bin"
                        } else {
                            "Move to Bin unavailable for this selection"
                        },
                    )
                }
                IconButton(onClick = onRestore, enabled = canRestore) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = if (canRestore) {
                            "Restore selected History articles"
                        } else {
                            "Restore unavailable for this selection"
                        },
                    )
                }
                IconButton(onClick = onRemoveHistory, enabled = canRemoveHistory) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (canRemoveHistory) {
                            "Remove selected articles from History"
                        } else {
                            "Remove from History unavailable offline"
                        },
                    )
                }
            }
        }
        explanation?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SessionSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SectionLabelHeader(
        label = "$title · $count",
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun SessionStaticItemRow(
    item: NowPlayingSessionItem,
    onOpenItem: (Int) -> Unit,
    onJumpToItem: ((Int) -> Unit)?,
    onArchiveItem: ((Int) -> Unit)? = null,
    onUnarchiveItem: ((Int) -> Unit)? = null,
    onBinItem: ((Int) -> Unit)? = null,
    onRestoreItem: ((Int) -> Unit)? = null,
    onRemoveHistory: (() -> Unit)? = null,
    showArchivedIndicator: Boolean = false,
    stillInSession: Boolean = false,
    isBinned: Boolean = false,
    muted: Boolean = false,
    selection: SelectionState = SelectionState.None,
    modifier: Modifier = Modifier,
) {
    val title = item.title?.ifBlank { null } ?: item.url
    val source = item.host
        ?: item.sourceLabel?.takeIf { it.isNotBlank() }
        ?: item.sourceType?.takeIf { it.isNotBlank() }
    val baseMetadata = buildItemMetadata(source, showArchived = showArchivedIndicator)
    val metadata = listOfNotNull(
        baseMetadata,
        "Still in session".takeIf { stillInSession },
        "Binned".takeIf { isBinned },
    ).joinToString(" · ").ifBlank { null }
    val menuEntries = buildList {
        if (isBinned) {
            if (onRestoreItem != null) {
                add(ItemActionMenuEntry.Action("Restore") { onRestoreItem(item.itemId) })
            }
        } else {
            addAll(
                sessionLifecycleActionOrder(
                    isArchived = showArchivedIndicator,
                    canArchive = onArchiveItem != null,
                    canUnarchive = onUnarchiveItem != null,
                    canMoveToBin = onBinItem != null,
                ).map { action ->
                    when (action) {
                        SessionLifecycleAction.Archive ->
                            ItemActionMenuEntry.Action("Archive") { onArchiveItem?.invoke(item.itemId) }
                        SessionLifecycleAction.Unarchive ->
                            ItemActionMenuEntry.Action("Unarchive") { onUnarchiveItem?.invoke(item.itemId) }
                        SessionLifecycleAction.MoveToBin ->
                            ItemActionMenuEntry.Action("Move to Bin") { onBinItem?.invoke(item.itemId) }
                    }
                },
            )
        }
        if (onRemoveHistory != null) {
            add(ItemActionMenuEntry.Action("Remove from History", onClick = onRemoveHistory))
        }
    }
    ItemRow(
        title = title,
        metadata = metadata,
        status = null,
        selection = selection,
        modifier = modifier,
        titleColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        onOpen = { if (!isBinned) onOpenItem(item.itemId) },
        openClickLabel = if (isBinned) "Binned article content is unavailable" else null,
        onPlayNow = onJumpToItem?.let { jump -> { jump(item.itemId) } },
        menuEntries = menuEntries,
    )
}

@Composable
internal fun UpNextHistoryOnlyPanel(
    historyProjection: UpNextHistoryProjection?,
    onOpenItem: (Int) -> Unit,
    onArchiveItem: (Int) -> Unit,
    onUnarchiveItem: (Int) -> Unit,
    historyManagementEnabled: Boolean = true,
    historyAwaitingRefresh: Boolean = false,
    onMoveToBin: (Set<Int>) -> Unit = {},
    onRestore: (Set<Int>) -> Unit = {},
    onRemoveHistory: (List<UpNextHistoryRemovalTarget>) -> Unit = {},
    onBatchArchiveItems: (Set<Int>) -> Unit = {},
    onBatchUnarchiveItems: (Set<Int>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = historyProjection?.entries?.map { it.toSessionHistoryPresentationRow() }.orEmpty()
    val rowsByKey = rows.associateBy { row ->
        SessionRowSelectionKey(
            section = SessionSelectionSection.HISTORY,
            rowId = row.entryId ?: -row.item.itemId.toLong(),
            itemId = row.item.itemId,
        )
    }
    val selectableKeys = rowsByKey.keys
    var selectionActive by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<SessionRowSelectionKey>()) }

    fun clearSelection() {
        selectionActive = false
        selectedKeys = emptySet()
    }

    fun enterSelection(key: SessionRowSelectionKey) {
        selectionActive = true
        selectedKeys = setOf(key)
    }

    fun toggleSelection(key: SessionRowSelectionKey) {
        val next = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        selectedKeys = next
        if (next.isEmpty()) selectionActive = false
    }

    LaunchedEffect(selectableKeys) {
        selectedKeys = selectedKeys.intersect(selectableKeys)
        if (selectedKeys.isEmpty()) selectionActive = false
    }
    BackHandler(enabled = selectionActive) { clearSelection() }

    val selectedRows = selectedKeys.mapNotNull(rowsByKey::get)
    val actions = historySelectionActions(selectedRows, historyManagementEnabled)
    val selectedItemIds = selectedRows.mapTo(linkedSetOf()) { it.item.itemId }
    val selectedTargets = selectedRows.mapNotNull { it.historyRemovalTarget() }
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (selectionActive) {
                SessionSelectionBar(
                    selectedCount = selectedKeys.size,
                    canArchive = actions.canArchive,
                    canUnarchive = actions.canUnarchive,
                    onClearSelection = ::clearSelection,
                    onArchive = {
                        onBatchArchiveItems(selectedItemIds)
                        clearSelection()
                    },
                    onUnarchive = {
                        onBatchUnarchiveItems(selectedItemIds)
                        clearSelection()
                    },
                    historyMode = true,
                    canRemoveHistory = actions.canRemove && selectedTargets.size == selectedRows.size,
                    canMoveToBin = actions.canMoveToBin,
                    canRestore = actions.canRestore,
                    explanation = actions.explanation,
                    onRemoveHistory = {
                        onRemoveHistory(selectedTargets)
                        clearSelection()
                    },
                    onMoveToBin = {
                        onMoveToBin(selectedItemIds)
                        clearSelection()
                    },
                    onRestore = {
                        onRestore(selectedItemIds)
                        clearSelection()
                    },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SessionSectionHeader(title = "History", count = rows.size)
                if (rows.isEmpty()) {
                    Text(
                        text = historyEmptyCopy(
                            recordingSince = historyProjection?.recordingSince,
                            lastRemovalAt = historyProjection?.lastRemovalAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    rows.forEachIndexed { index, row ->
                        val key = SessionRowSelectionKey(
                            section = SessionSelectionSection.HISTORY,
                            rowId = row.entryId ?: -row.item.itemId.toLong(),
                            itemId = row.item.itemId,
                        )
                        SessionStaticItemRow(
                            item = row.item,
                            onOpenItem = onOpenItem,
                            onJumpToItem = null,
                            onArchiveItem = onArchiveItem.takeIf { !row.isTrashed },
                            onUnarchiveItem = onUnarchiveItem.takeIf { !row.isTrashed },
                            onBinItem = ({ itemId: Int -> onMoveToBin(setOf(itemId)) })
                                .takeIf { historyManagementEnabled && !row.isTrashed },
                            onRestoreItem = ({ itemId: Int -> onRestore(setOf(itemId)) })
                                .takeIf { historyManagementEnabled && row.isTrashed },
                            onRemoveHistory = row.historyRemovalTarget()?.let { target ->
                                ({ onRemoveHistory(listOf(target)) }).takeIf { historyManagementEnabled }
                            },
                            showArchivedIndicator = row.item.isArchived,
                            stillInSession = row.stillInSession,
                            isBinned = row.isTrashed,
                            muted = true,
                            selection = SelectionState.Available(
                                isActive = selectionActive,
                                isSelected = key in selectedKeys,
                                onToggle = { toggleSelection(key) },
                                onEnter = { enterSelection(key) },
                            ),
                        )
                        if (index < rows.lastIndex) RowDivider()
                    }
                }
                historyBoundedCopy(
                    hasMore = historyProjection?.hasMore == true,
                    effectiveLimit = historyProjection?.effectiveLimit ?: 0,
                )?.let { copy ->
                    Text(
                        text = copy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                historyEditedCopy(historyProjection?.lastRemovalAt)?.let { copy ->
                    Text(
                        text = copy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (historyAwaitingRefresh) {
                    Text(
                        text = "History is awaiting an authoritative refresh before another management action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "No active session. Open an item to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingSessionPanel(
    session: NowPlayingSession,
    historyProjection: UpNextHistoryProjection?,
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
    historyManagementEnabled: Boolean = true,
    historyAwaitingRefresh: Boolean = false,
    onMoveHistoryToBin: (Set<Int>) -> Unit = {},
    onRestoreHistory: (Set<Int>) -> Unit = {},
    onRemoveHistory: (List<UpNextHistoryRemovalTarget>) -> Unit = {},
    onBatchArchiveItems: (Set<Int>) -> Unit = {},
    onBatchUnarchiveItems: (Set<Int>) -> Unit = {},
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

    fun activeIndex(): Int = sessionPanelActiveIndex(
        currentItemId = session.currentItem?.itemId,
        localItemIds = localItems.map { it.itemId },
    )

    fun presentationItems(): List<NowPlayingSessionItem> = sessionPanelPresentationItems(
        localItems = localItems,
        authoritativeItems = session.items,
        itemKey = { it.itemId },
    )

    fun upcomingItems(): List<NowPlayingSessionItem> = sessionPanelUpcomingItems(
        localItems = presentationItems(),
        currentIndex = activeIndex(),
    )

    fun absoluteIndexForUpcoming(upcomingIndex: Int): Int {
        val itemId = upcomingItems().getOrNull(upcomingIndex)?.itemId ?: return -1
        return localItems.indexOfFirst { it.itemId == itemId }
    }

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
    val currentIndex = sessionPanelActiveIndex(
        currentItemId = currentItemId,
        localItemIds = localItems.map { it.itemId },
    )
    val presentationItems = sessionPanelPresentationItems(
        localItems = localItems,
        authoritativeItems = session.items,
        itemKey = { it.itemId },
    )
    val activeItem = presentationItems.getOrNull(currentIndex)
    // Canonical History is already oldest-entrant-first. The transient fallback is retained
    // only until the first server projection arrives (including while initially offline).
    val historyRows = historyProjection?.entries?.map { it.toSessionHistoryPresentationRow() }
        ?: sessionPanelHistoryItems(session.historyItems).map { SessionHistoryPresentationRow(it) }
    val canonicalHistoryVisible = historyProjection != null
    // Earlier-in-queue items already sit in play order (oldest at the top,
    // most recently passed nearest Now Playing), so they need no reordering.
    // Authoritative lifecycle reconciliation can remove the active item while the
    // remembered presentation list is catching up. Treat that transient state as
    // no active item: nothing is earlier and every surviving row remains upcoming.
    val earlierItems = sessionPanelEarlierItems(presentationItems, currentIndex)
    val historyRowsByKey = historyRows.associateBy { row ->
        SessionRowSelectionKey(
            section = SessionSelectionSection.HISTORY,
            rowId = row.entryId ?: -row.item.itemId.toLong(),
            itemId = row.item.itemId,
        )
    }
    val earlierItemsByKey = earlierItems.associateBy { item ->
        SessionRowSelectionKey(
            section = SessionSelectionSection.EARLIER,
            rowId = item.itemId.toLong(),
            itemId = item.itemId,
        )
    }
    val selectableKeys = historyRowsByKey.keys + earlierItemsByKey.keys
    var selectionActive by remember { mutableStateOf(false) }
    var selectionSection by remember { mutableStateOf<SessionSelectionSection?>(null) }
    var selectedKeys by remember { mutableStateOf(emptySet<SessionRowSelectionKey>()) }

    fun clearSelection() {
        selectionActive = false
        selectionSection = null
        selectedKeys = emptySet()
    }

    fun enterSelection(key: SessionRowSelectionKey) {
        selectionActive = true
        selectionSection = key.section
        selectedKeys = setOf(key)
    }

    fun toggleSelection(key: SessionRowSelectionKey) {
        if (selectionSection != key.section) {
            selectionSection = key.section
            selectedKeys = setOf(key)
            return
        }
        val next = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        selectedKeys = next
        if (next.isEmpty()) selectionActive = false
    }

    LaunchedEffect(selectableKeys) {
        selectedKeys = selectedKeys.intersect(selectableKeys)
        if (selectedKeys.isEmpty()) clearSelection()
    }
    BackHandler(enabled = selectionActive) { clearSelection() }

    val selectedHistoryRows = selectedKeys.mapNotNull(historyRowsByKey::get)
    val selectedEarlierItems = selectedKeys.mapNotNull(earlierItemsByKey::get)
    val historyActions = historySelectionActions(selectedHistoryRows, historyManagementEnabled)
    val selectedHistoryItemIds = selectedHistoryRows.mapTo(linkedSetOf()) { it.item.itemId }
    val selectedHistoryTargets = selectedHistoryRows.mapNotNull { it.historyRemovalTarget() }
    val selectedEarlierIds = selectedEarlierItems.mapTo(linkedSetOf()) { it.itemId }
    val earlierArchivedByItemId = earlierItems.associate { item ->
        item.itemId to (item.isArchived || item.itemId in archivedHistoryItemIds)
    }
    val archiveIds = selectedSessionArchiveActionIds(selectedEarlierIds, earlierArchivedByItemId, archive = true)
    val unarchiveIds = selectedSessionArchiveActionIds(selectedEarlierIds, earlierArchivedByItemId, archive = false)
    val hasRowsBeforeActive = historyRows.isNotEmpty() || earlierItems.isNotEmpty()
    val upcomingItems = sessionPanelUpcomingItems(
        localItems = presentationItems,
        currentIndex = currentIndex,
    )
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
    LaunchedEffect(historyRows.isEmpty()) {
        if (historyRows.isEmpty()) {
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
        if (selectionActive) {
            if (selectionSection == SessionSelectionSection.HISTORY) {
                SessionSelectionBar(
                    selectedCount = selectedKeys.size,
                    canArchive = historyActions.canArchive,
                    canUnarchive = historyActions.canUnarchive,
                    onClearSelection = ::clearSelection,
                    onArchive = {
                        onBatchArchiveItems(selectedHistoryItemIds)
                        clearSelection()
                    },
                    onUnarchive = {
                        onBatchUnarchiveItems(selectedHistoryItemIds)
                        clearSelection()
                    },
                    historyMode = true,
                    canRemoveHistory = historyActions.canRemove &&
                        selectedHistoryTargets.size == selectedHistoryRows.size,
                    canMoveToBin = historyActions.canMoveToBin,
                    canRestore = historyActions.canRestore,
                    explanation = historyActions.explanation,
                    onRemoveHistory = {
                        onRemoveHistory(selectedHistoryTargets)
                        clearSelection()
                    },
                    onMoveToBin = {
                        onMoveHistoryToBin(selectedHistoryItemIds)
                        clearSelection()
                    },
                    onRestore = {
                        onRestoreHistory(selectedHistoryItemIds)
                        clearSelection()
                    },
                )
            } else {
                SessionSelectionBar(
                    selectedCount = selectedKeys.size,
                    canArchive = archiveIds.isNotEmpty(),
                    canUnarchive = unarchiveIds.isNotEmpty(),
                    onClearSelection = ::clearSelection,
                    onArchive = {
                        onBatchArchiveItems(archiveIds)
                        clearSelection()
                    },
                    onUnarchive = {
                        onBatchUnarchiveItems(unarchiveIds)
                        clearSelection()
                    },
                )
            }
        } else {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val top = coords.positionInParent().y
                            historyStickyBounds = SessionStickyHeaderBounds(
                                title = "History",
                                count = historyRows.size,
                                topPx = top,
                                headerHeightPx = historyHeaderHeightPx,
                                bottomPx = top + coords.size.height,
                            )
                        },
                ) {
                    SessionSectionHeader(
                        title = "History",
                        count = historyRows.size,
                        modifier = Modifier.onSizeChanged { size ->
                            historyHeaderHeightPx = size.height.toFloat()
                        },
                    )
                    if (historyRows.isEmpty()) {
                        Text(
                            text = historyEmptyCopy(
                                recordingSince = historyProjection?.recordingSince,
                                lastRemovalAt = historyProjection?.lastRemovalAt,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    } else {
                        historyRows.forEachIndexed { index, row ->
                            val selectionKey = SessionRowSelectionKey(
                                section = SessionSelectionSection.HISTORY,
                                rowId = row.entryId ?: -row.item.itemId.toLong(),
                                itemId = row.item.itemId,
                            )
                            SessionStaticItemRow(
                                item = row.item,
                                onOpenItem = onOpenItem,
                                onJumpToItem = if (canonicalHistoryVisible) null else onJumpToHistoryItem,
                                onArchiveItem = onArchiveSessionItem.takeIf { !row.isTrashed },
                                onUnarchiveItem = onUnarchiveSessionHistoryItem.takeIf { !row.isTrashed },
                                onBinItem = when {
                                    canonicalHistoryVisible && historyManagementEnabled && !row.isTrashed ->
                                        { itemId: Int -> onMoveHistoryToBin(setOf(itemId)) }
                                    !canonicalHistoryVisible -> onBinSessionHistoryItem
                                    else -> null
                                },
                                onRestoreItem = ({ itemId: Int -> onRestoreHistory(setOf(itemId)) })
                                    .takeIf { canonicalHistoryVisible && historyManagementEnabled && row.isTrashed },
                                onRemoveHistory = row.historyRemovalTarget()?.let { target ->
                                    ({ onRemoveHistory(listOf(target)) })
                                        .takeIf { canonicalHistoryVisible && historyManagementEnabled }
                                },
                                showArchivedIndicator = row.item.isArchived ||
                                    row.item.itemId in archivedHistoryItemIds,
                                stillInSession = row.stillInSession,
                                isBinned = row.isTrashed,
                                muted = canonicalHistoryVisible,
                                selection = SelectionState.Available(
                                    isActive = selectionActive,
                                    isSelected = selectionKey in selectedKeys,
                                    onToggle = { toggleSelection(selectionKey) },
                                    onEnter = { enterSelection(selectionKey) },
                                ),
                            )
                            if (index < historyRows.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                    historyBoundedCopy(
                        hasMore = historyProjection?.hasMore == true,
                        effectiveLimit = historyProjection?.effectiveLimit ?: 0,
                    )?.let { copy ->
                        Text(
                            text = copy,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    historyEditedCopy(historyProjection?.lastRemovalAt)?.let { copy ->
                        Text(
                            text = copy,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (historyAwaitingRefresh) {
                        Text(
                            text = "History is awaiting an authoritative refresh before another management action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
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
                            val selectionKey = SessionRowSelectionKey(
                                section = SessionSelectionSection.EARLIER,
                                rowId = item.itemId.toLong(),
                                itemId = item.itemId,
                            )
                            SessionStaticItemRow(
                                item = item,
                                onOpenItem = onOpenItem,
                                onJumpToItem = onJumpToQueueItem,
                                onArchiveItem = onArchiveSessionItem,
                                onUnarchiveItem = onUnarchiveSessionHistoryItem,
                                onBinItem = onBinSessionEarlierItem,
                                showArchivedIndicator = item.isArchived || item.itemId in archivedHistoryItemIds,
                                selection = SelectionState.Available(
                                    isActive = selectionActive,
                                    isSelected = selectionKey in selectedKeys,
                                    onToggle = { toggleSelection(selectionKey) },
                                    onEnter = { enterSelection(selectionKey) },
                                ),
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
                        SectionLabelChip(
                            label = NOW_PLAYING_SECTION_TITLE,
                            modifier = Modifier.semantics { heading() },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Now Playing, current: ${item.title?.ifBlank { item.url } ?: item.url}" }
                                .clickable { onOpenItem(item.itemId) }
                                .background(
                                    color = if (isV1) mColors.nowTint else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    shape = if (isV1) mShapes.card else RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = if (isV1) mShapes.card else RoundedCornerShape(8.dp),
                                )
                                .padding(start = 12.dp, end = 12.dp, top = densityTokens.rowPadV, bottom = densityTokens.rowPadV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = item.title?.ifBlank { null } ?: item.url,
                                    style = if (isV1) mTypography.title else MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isV1) mColors.fg else MaterialTheme.colorScheme.primary,
                                    maxLines = densityTokens.itemRowTitleMaxLines,
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
                    SectionLabelChip(
                        label = "Up Next · ${upcomingItems.size}",
                        modifier = Modifier.semantics { heading() },
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
                        val absoluteIndex = absoluteIndexForUpcoming(index)
                        val isDragging = draggingIndex == index
                        val itemVisualOffsetY = when {
                            isDragging -> dragOffsetY
                            draggingIndex >= 0 -> visualOffsetForItem(index, draggingIndex, currentTargetIndex)
                            else -> 0f
                        }
                        val sourceLabel = item.host
                            ?: item.sourceLabel?.takeIf { it.isNotBlank() }
                            ?: item.sourceType?.takeIf { it.isNotBlank() }
                        val rowMetadata = buildItemMetadata(sourceLabel)
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
                                metadata = rowMetadata,
                                status = null,
                                modifier = Modifier.semantics {
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move up") {
                                            onReorderItem(absoluteIndex, absoluteIndexForUpcoming(index - 1)); true
                                        })
                                        if (index < upcomingItems.lastIndex) add(CustomAccessibilityAction("Move down") {
                                            onReorderItem(absoluteIndex, absoluteIndexForUpcoming(index + 1)); true
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
                                        playContentDescription = "Jump to $rowTitle",
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
                // Reserve bottom space for the player panel so the last upcoming row can
                // scroll fully above it; collapses to 0 when no player is showing.
                if (snapBottomClearance > 0.dp) {
                    Spacer(modifier = Modifier.height(snapBottomClearance))
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
                        .graphicsLayer { translationY = stickyHeader.offsetYPx },
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
