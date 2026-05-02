package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.BlueskyBrowseItem
import com.mimeo.android.model.BlueskyBrowsePinResponse
import com.mimeo.android.model.BlueskySourceInfo
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BlueskyBrowseScreen(
    vm: AppViewModel,
    onNavigateBack: () -> Unit,
    onOpenItem: (Int) -> Unit,
) {
    val items by vm.blueskyBrowseItems.collectAsState()
    val sources by vm.blueskyBrowseSources.collectAsState()
    val pins by vm.blueskyBrowsePins.collectAsState()
    val loading by vm.blueskyBrowseLoading.collectAsState()
    val loadingMore by vm.blueskyBrowseLoadingMore.collectAsState()
    val error by vm.blueskyBrowseError.collectAsState()
    val sourceFilter by vm.blueskyBrowseSourceFilter.collectAsState()
    val query by vm.blueskyBrowseQuery.collectAsState()
    val nextCursor by vm.blueskyBrowseNextCursor.collectAsState()
    val pinsAvailable by vm.blueskyBrowsePinsAvailable.collectAsState()

    var queryDraft by remember(query) { mutableStateOf(query) }

    LaunchedEffect(Unit) {
        vm.loadBlueskyBrowse(refresh = true)
    }

    val pinnedSourceIds = remember(pins) { pins.map { it.sourceId }.toSet() }
    val sortedSources = remember(sources, pins) {
        val pinOrder = pins.associate { it.sourceId to it.position }
        val pinned = sources.filter { it.id in pinnedSourceIds }.sortedBy { pinOrder[it.id] ?: Int.MAX_VALUE }
        val unpinned = sources.filter { it.id !in pinnedSourceIds }
        pinned + unpinned
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - 4
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { near ->
                if (near && nextCursor != null && !loadingMore && !loading) {
                    vm.loadMoreBlueskyBrowse()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Back")
            }
            Text(
                text = "Bluesky Browse",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = queryDraft,
            onValueChange = { queryDraft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 4.dp),
            placeholder = { Text("Search title or URL...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.setBlueskyBrowseQuery(queryDraft.trim())
                vm.loadBlueskyBrowse(refresh = true)
            }),
        )

        if (sortedSources.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = sourceFilter == null,
                        onClick = {
                            vm.setBlueskyBrowseSourceFilter(null)
                            vm.loadBlueskyBrowse(refresh = true)
                        },
                        label = { Text("All") },
                    )
                }
                items(sortedSources, key = { it.id }) { source ->
                    val isPinned = source.id in pinnedSourceIds
                    SourceChip(
                        source = source,
                        isPinned = isPinned,
                        isSelected = sourceFilter == source.id,
                        pinsAvailable = pinsAvailable,
                        onSelect = {
                            val next = if (sourceFilter == source.id) null else source.id
                            vm.setBlueskyBrowseSourceFilter(next)
                            vm.loadBlueskyBrowse(refresh = true)
                        },
                        onTogglePin = {
                            if (isPinned) vm.removeBlueskyBrowsePin(source.id)
                            else vm.addBlueskyBrowsePin(source.id)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            !error.isNullOrBlank() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.loadBlueskyBrowse(refresh = true) }) {
                    Text("Retry")
                }
            }
            items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("No harvested items found.")
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    BrowseItemRow(item = item, onOpen = { onOpenItem(item.id) })
                }
                if (loadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (nextCursor != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { vm.loadMoreBlueskyBrowse() }) {
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    source: BlueskySourceInfo,
    isPinned: Boolean,
    isSelected: Boolean,
    pinsAvailable: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelect,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(source.resolvedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingIcon = if (pinsAvailable) {
            {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else null,
    )
}

@Composable
private fun BrowseItemRow(
    item: BlueskyBrowseItem,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = item.title ?: item.url,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
            add(item.bluesky.sourceLabel)
            if (!item.bluesky.authorHandle.isNullOrBlank()) add("@${item.bluesky.authorHandle}")
            val timestamp = item.bluesky.postIndexedAt ?: item.createdAt
            if (!timestamp.isNullOrBlank()) add(formatBrowseTimestamp(timestamp))
            if (!item.status.isNullOrBlank() && item.status != "ready") add(item.status.orEmpty())
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatBrowseTimestamp(iso: String): String {
    return runCatching {
        val normalized = iso.replace("Z", "+00:00")
        val dt = java.time.OffsetDateTime.parse(normalized)
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val hours = java.time.Duration.between(dt, now).toHours()
        when {
            hours < 1 -> "just now"
            hours < 24 -> "${hours}h ago"
            hours < 24 * 7 -> "${hours / 24}d ago"
            else -> {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d")
                dt.format(fmt)
            }
        }
    }.getOrDefault(iso.take(10))
}
