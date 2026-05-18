package com.mimeo.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

sealed interface ItemActionMenuEntry {
    data class Action(
        val label: String,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : ItemActionMenuEntry

    data object Divider : ItemActionMenuEntry
}

sealed interface SelectionState {
    /** Selection is not supported on this row. Tap opens; no long-press affordance. */
    data object None : SelectionState

    /**
     * Selection is supported. [isActive] tracks whether the screen is currently in
     * selection mode. When inactive, long-press calls [onEnter] to begin selection.
     * When active, tap calls [onToggle] to toggle this row's selection.
     */
    data class Available(
        val isActive: Boolean,
        val isSelected: Boolean,
        val onToggle: () -> Unit,
        val onEnter: () -> Unit,
    ) : SelectionState
}

@Composable
fun ItemRow(
    title: String,
    metadata: String?,
    status: String?,
    onOpen: () -> Unit,
    selection: SelectionState = SelectionState.None,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleMaxLines: Int? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onPlayNow: (() -> Unit)? = null,
    menuEntries: List<ItemActionMenuEntry> = emptyList(),
) {
    val available = selection as? SelectionState.Available
    val isActive = available?.isActive == true
    val isSelected = available?.isSelected == true
    LibraryItemRow(
        title = title,
        metadata = metadata,
        modifier = modifier,
        isSelected = isSelected,
        containerColor = containerColor,
        titleColor = titleColor,
        titleMaxLines = titleMaxLines,
        onClick = if (isActive && available != null) available.onToggle else onOpen,
        onLongClick = if (available != null && !isActive) available.onEnter else null,
        leadingContent = if (isActive) {
            { SelectionAffordance(isSelected = isSelected) }
        } else {
            leadingContent
        },
        progressStateLine = itemStatusPillLine(status),
        trailingContent = when {
            isActive -> null
            trailingContent != null -> trailingContent
            onPlayNow != null || menuEntries.isNotEmpty() -> ({
                ItemActionMenu(
                    title = title,
                    onPlayNow = onPlayNow,
                    entries = menuEntries,
                )
            })
            else -> null
        },
    )
}

@Composable
fun ItemActionMenu(
    title: String,
    onPlayNow: (() -> Unit)?,
    entries: List<ItemActionMenuEntry>,
) {
    ItemRowTrailingActions(title = title, onPlayNow = onPlayNow) {
        entries.forEach { entry ->
            when (entry) {
                is ItemActionMenuEntry.Action -> DropdownMenuItem(
                    enabled = entry.enabled,
                    text = { Text(entry.label) },
                    onClick = entry.onClick,
                )
                ItemActionMenuEntry.Divider -> HorizontalDivider()
            }
        }
    }
}

/**
 * Shared trailing-actions slot: visible Play button + MoreVert overflow button/menu.
 *
 * [onPlayNow] — if null the play button is omitted (e.g. item still loading).
 * [menuContent] — surface-specific DropdownMenuItem blocks go here.
 */
@Composable
fun ItemRowTrailingActions(
    title: String,
    onPlayNow: (() -> Unit)?,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    var menuExpanded by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ItemRowActionSize) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (onPlayNow != null) {
                IconButton(
                    onClick = onPlayNow,
                    modifier = Modifier.size(ItemRowActionSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play $title",
                        tint = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(ItemRowActionSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions for $title",
                        tint = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    content = { menuContent() },
                )
            }
        }
    }
}

@Composable
fun ItemRowPlayRemoveActions(
    title: String,
    onPlayNow: () -> Unit,
    onRemove: () -> Unit,
    playContentDescription: String = "Play $title",
    removeContentDescription: String = "Remove $title",
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ItemRowActionSize) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(
                onClick = onPlayNow,
                modifier = Modifier.size(ItemRowActionSize),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = playContentDescription,
                    tint = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(ItemRowActionSize),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeContentDescription,
                    tint = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val ItemRowActionSize = 36.dp

/**
 * Returns the standard status-pill [progressStateLine] lambda for [LibraryItemRow],
 * or null when the status should not be shown (null or "ready").
 */
fun itemStatusPillLine(status: String?): (@Composable () -> Unit)? {
    val statusForLine = status?.takeIf { it != "ready" } ?: return null
    return {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ListStatusPill(status = statusForLine)
        }
    }
}
