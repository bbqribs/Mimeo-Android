package com.mimeo.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mimeo.android.model.PlaybackQueueItem

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
    onSortChange: (LibrarySortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (Int) -> Unit,
) {
    var pendingExpanded by rememberSaveable { mutableStateOf(false) }

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
                                LibraryItemRow(item = item, onOpen = { onOpenItem(item.itemId) })
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }

                    // Ready / main items
                    items(items = readyItems, key = { it.itemId }) { item ->
                        LibraryItemRow(item = item, onOpen = { onOpenItem(item.itemId) })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }
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
private fun LibraryItemRow(
    item: PlaybackQueueItem,
    onOpen: () -> Unit,
) {
    val title = item.title?.takeIf { it.isNotBlank() } ?: item.url
    val source = item.host?.takeIf { it.isNotBlank() } ?: item.url
    val progress = item.progressPercent.coerceIn(0, 100)
    val progressLabel = if (progress > 0) " · $progress%" else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
