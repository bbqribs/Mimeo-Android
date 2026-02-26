package com.mimeo.android.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaybackChunk

private const val AUTO_SCROLL_SUPPRESSION_MS = 5_000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    chunks: List<PlaybackChunk>,
    currentChunkIndex: Int,
    autoScrollWhileListening: Boolean,
    onSelectChunk: (Int) -> Unit,
    onPlayFromChunk: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chunks.isEmpty()) {
        Text("No chunked text available.", modifier = modifier)
        return
    }

    val listState = rememberLazyListState()
    var suppressAutoScrollUntilMs by remember { mutableLongStateOf(0L) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            suppressAutoScrollUntilMs = System.currentTimeMillis() + AUTO_SCROLL_SUPPRESSION_MS
        }
    }

    LaunchedEffect(currentChunkIndex, autoScrollWhileListening, chunks.size) {
        if (!autoScrollWhileListening || chunks.isEmpty()) return@LaunchedEffect
        if (System.currentTimeMillis() < suppressAutoScrollUntilMs) return@LaunchedEffect

        val safeIndex = currentChunkIndex.coerceIn(0, chunks.lastIndex)
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == safeIndex }
        if (isVisible) return@LaunchedEffect

        isProgrammaticScroll = true
        try {
            listState.animateScrollToItem(index = safeIndex)
        } finally {
            isProgrammaticScroll = false
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Long-press a chunk to start listening here.",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(chunks) { idx, chunk ->
                val isHighlighted = idx == currentChunkIndex
                val background = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                }
                val textColor = if (isHighlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background, shape = MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = { onSelectChunk(idx) },
                            onLongClick = { onPlayFromChunk(idx) },
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Chunk ${idx + 1} • chars ${chunk.startChar}-${chunk.endChar}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                    )
                    Text(
                        text = chunk.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
