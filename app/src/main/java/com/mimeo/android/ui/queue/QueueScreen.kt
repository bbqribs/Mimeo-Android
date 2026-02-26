package com.mimeo.android.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.ProgressSyncBadgeState

private const val DONE_PERCENT_THRESHOLD = 98

@Composable
fun QueueScreen(
    vm: AppViewModel,
    focusItemId: Int? = null,
    onOpenPlayer: (Int) -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    val items by vm.queueItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val offline by vm.queueOffline.collectAsState()
    val pendingCount by vm.pendingProgressCount.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val sessionIssueMessage by vm.sessionIssueMessage.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val syncBadgeState by vm.progressSyncBadgeState.collectAsState()
    var showClearSessionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.loadQueue()
        vm.flushPendingProgress()
    }

    LaunchedEffect(focusItemId) {
        pendingFocusId = focusItemId ?: -1
    }

    LaunchedEffect(items, pendingFocusId) {
        if (pendingFocusId <= 0) return@LaunchedEffect
        val index = items.indexOfFirst { it.itemId == pendingFocusId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            pendingFocusId = -1
        }
    }

    val resumeSummary = vm.nowPlayingSummaryText()
    val resumeItemId = vm.currentNowPlayingItemId()
    val selectedPlaylistName = settings.selectedPlaylistId?.let { id ->
        playlists.firstOrNull { it.id == id }?.name
    } ?: "Smart queue"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.loadQueue() }) { Text("Refresh queue") }
            Button(onClick = { vm.flushPendingProgress() }) { Text("Sync") }
            Button(onClick = onOpenPlaylists) { Text("Playlists") }
        }
        Box {
            Button(onClick = { playlistMenuExpanded = true }) {
                Text("Queue: $selectedPlaylistName")
            }
            DropdownMenu(
                expanded = playlistMenuExpanded,
                onDismissRequest = { playlistMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Smart queue") },
                    onClick = {
                        playlistMenuExpanded = false
                        vm.selectPlaylist(null)
                    },
                )
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(playlist.name) },
                        onClick = {
                            playlistMenuExpanded = false
                            vm.selectPlaylist(playlist.id)
                        },
                    )
                }
            }
        }
        sessionIssueMessage?.let {
            Text(it)
            Button(onClick = { showClearSessionDialog = true }) {
                Text("Clear session")
            }
        }
        nowPlayingSession?.let { session ->
            val current = session.currentItem ?: session.items.firstOrNull()
            val currentProgress = current?.itemId?.let { vm.knownProgressForItem(it) } ?: 0
            val title = current?.title?.ifBlank { null } ?: current?.url ?: "Session item"
            val host = current?.host ?: ""
            Text("Now Playing")
            Text("Item ${session.currentIndex + 1}/${session.items.size}  Progress $currentProgress%")
            Text("$title${if (host.isBlank()) "" else " - $host"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { resumeItemId?.let { onOpenPlayer(it) } },
                    enabled = resumeItemId != null,
                ) {
                    Text("Resume")
                }
                Button(onClick = { vm.restartNowPlayingSession() }) { Text("Restart Session") }
                Button(onClick = { showClearSessionDialog = true }) { Text("Clear") }
            }
            resumeSummary?.let { Text("Current: $it") }
            if (resumeItemId != null && items.none { it.itemId == resumeItemId }) {
                Text("Current now-playing item is hidden from queue filters; Resume still works.")
            }
        }
        if (offline) {
            Text("Offline")
        }
        val syncLabel = when (syncBadgeState) {
            ProgressSyncBadgeState.SYNCED -> "Synced"
            ProgressSyncBadgeState.QUEUED -> "Queued"
            ProgressSyncBadgeState.OFFLINE -> "Offline"
        }
        Text("Sync: $syncLabel")
        Text("Pending sync: $pendingCount")
        if (loading) {
            CircularProgressIndicator()
        }
        if (items.isEmpty() && settings.selectedPlaylistId != null && !loading) {
            Text("No items yet in this playlist.")
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                    val cachedMarker = if (cachedItemIds.contains(item.itemId)) {
                        " offline-ready"
                    } else {
                        " needs-network"
                    }
                    Text(text = "${item.host ?: ""} progress=$progress%$doneMarker$cachedMarker")
                }
            }
        }
    }

    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Clear session?") },
            text = { Text("This removes the persisted Now Playing snapshot.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearNowPlayingSession()
                        showClearSessionDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
