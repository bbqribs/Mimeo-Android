package com.mimeo.android.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel

private const val DONE_PERCENT_THRESHOLD = 98

@Composable
fun QueueScreen(vm: AppViewModel, onOpenPlayer: (Int) -> Unit) {
    val items by vm.queueItems.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val offline by vm.queueOffline.collectAsState()
    val pendingCount by vm.pendingProgressCount.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadQueue()
        vm.flushPendingProgress()
    }

    val resumeSummary = vm.nowPlayingSummaryText()
    val resumeItemId = vm.currentNowPlayingItemId()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.loadQueue() }) { Text("Refresh queue") }
            Button(onClick = { vm.flushPendingProgress() }) { Text("Sync") }
        }
        nowPlayingSession?.let { session ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { resumeItemId?.let { onOpenPlayer(it) } },
                    enabled = resumeItemId != null,
                ) {
                    Text("Resume Now Playing")
                }
                Text("Item ${session.currentIndex + 1}/${session.items.size}")
            }
            resumeSummary?.let { Text("Now Playing: $it") }
            if (resumeItemId != null && items.none { it.itemId == resumeItemId }) {
                Text("Current now-playing item is hidden from queue filters; Resume still works.")
            }
        }
        if (offline) {
            Text("Offline")
        }
        Text("Pending sync: $pendingCount")
        if (loading) {
            CircularProgressIndicator()
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val title = item.title?.ifBlank { null } ?: item.url
                val progress = item.lastReadPercent ?: 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.startNowPlayingSession(item.itemId)
                            onOpenPlayer(item.itemId)
                        }
                        .padding(8.dp),
                ) {
                    Text(text = title)
                    val doneMarker = if (progress >= DONE_PERCENT_THRESHOLD) " done" else ""
                    Text(text = "${item.host ?: ""} progress=$progress%$doneMarker")
                }
            }
        }
    }
}
