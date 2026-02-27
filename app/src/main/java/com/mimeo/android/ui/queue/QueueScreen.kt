package com.mimeo.android.ui.queue

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import kotlinx.coroutines.launch

private const val DONE_PERCENT_THRESHOLD = 98

@Composable
fun QueueScreen(
    vm: AppViewModel,
    focusItemId: Int? = null,
    onOpenPlayer: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
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
    val statusMessage by vm.statusMessage.collectAsState()
    val context = LocalContext.current
    val actionScope = rememberCoroutineScope()

    var showClearSessionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }

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

    val selectedPlaylistName = settings.selectedPlaylistId?.let { id ->
        playlists.firstOrNull { it.id == id }?.name
    } ?: "Smart queue"
    val baseUrlHint = vm.baseUrlHintForDevice(isLikelyPhysicalDevice())
    val baseAddress = settings.baseUrl.trim().removePrefix("http://").removePrefix("https://")
    val statusLooksError = statusMessage?.let { message ->
        val lower = message.lowercase()
        lower.contains("failed") ||
            lower.contains("error") ||
            lower.contains("unauthorized") ||
            lower.contains("forbidden") ||
            lower.contains("timeout")
    } ?: false
    val bannerStateLabel = when {
        offline -> "Offline"
        baseUrlHint != null -> "LAN mismatch"
        else -> "Online"
    }
    val bannerSummary = when {
        offline -> "Cannot reach server at $baseAddress"
        baseUrlHint != null -> baseUrlHint
        !statusMessage.isNullOrBlank() -> statusMessage.orEmpty()
        else -> "Connected"
    }
    val showBanner = offline || baseUrlHint != null || statusLooksError
    val syncLabel = when (syncBadgeState) {
        ProgressSyncBadgeState.SYNCED -> "Synced"
        ProgressSyncBadgeState.QUEUED -> "Queued"
        ProgressSyncBadgeState.OFFLINE -> "Offline"
    }
    val resumeSummary = vm.nowPlayingSummaryText()
    val resumeItemId = vm.currentNowPlayingItemId()
    val playlistChoices = playlistPickerItem?.let { target ->
        playlists.map { playlist ->
            PlaylistPickerChoice(
                playlistId = playlist.id,
                playlistName = playlist.name,
                isMember = vm.isItemInPlaylist(target.itemId, playlist.id),
            )
        }
    }.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        playlistMutationMessage?.let { message ->
            StatusBanner(
                stateLabel = if (message.contains("Unauthorized", ignoreCase = true)) "Auth" else "Offline",
                summary = message,
                detail = null,
                onRetry = { playlistMutationMessage = null },
                onDiagnostics = onOpenDiagnostics,
            )
        }

        if (showBanner) {
            StatusBanner(
                stateLabel = bannerStateLabel,
                summary = bannerSummary,
                detail = if (statusLooksError) statusMessage else baseUrlHint,
                onRetry = { vm.loadQueue() },
                onDiagnostics = onOpenDiagnostics,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { vm.loadQueue() }) { Text("Refresh") }
            TextButton(onClick = { vm.flushPendingProgress() }) { Text("Sync") }
            Box {
                AssistChip(
                    onClick = { playlistMenuExpanded = true },
                    label = { Text("Queue: $selectedPlaylistName") },
                )
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
            Text(
                modifier = Modifier.weight(1f),
                text = "Sync: $syncLabel  Pending: $pendingCount",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        nowPlayingSession?.let { session ->
            val current = session.currentItem ?: session.items.firstOrNull()
            val title = current?.title?.ifBlank { null } ?: current?.url ?: "Session item"
            val progress = current?.itemId?.let { vm.knownProgressForItem(it) } ?: 0
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Now Playing ${session.currentIndex + 1}/${session.items.size} - $progress%")
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { resumeItemId?.let { onOpenPlayer(it) } },
                            enabled = resumeItemId != null,
                        ) { Text("Resume") }
                        TextButton(onClick = { vm.restartNowPlayingSession() }) { Text("Restart") }
                        TextButton(onClick = { showClearSessionDialog = true }) { Text("Clear") }
                    }
                    if (resumeSummary != null && resumeItemId != null && items.none { it.itemId == resumeItemId }) {
                        Text(
                            text = "Current item hidden by queue filters; Resume still works.",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        sessionIssueMessage?.let {
            Text(
                text = it,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (loading) {
            CircularProgressIndicator()
        }
        if (items.isEmpty() && settings.selectedPlaylistId != null && !loading) {
            Text("No items yet in this playlist.")
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items) { item ->
                QueueItemCard(
                    item = item,
                    cached = cachedItemIds.contains(item.itemId),
                    onOpenPlayer = {
                        vm.startNowPlayingSession(item.itemId)
                        onOpenPlayer(item.itemId)
                    },
                    onOpenPlaylistPicker = {
                        vm.refreshPlaylists()
                        playlistPickerItem = item
                    },
                    isMenuExpanded = rowMenuItemId == item.itemId,
                    onDismissMenu = { rowMenuItemId = -1 },
                    onExpandMenu = { rowMenuItemId = item.itemId },
                )
            }
        }
    }

    playlistPickerItem?.let { target ->
        PlaylistPickerDialog(
            itemTitle = target.title?.ifBlank { null } ?: target.url,
            playlistChoices = playlistChoices,
            isLoading = false,
            onDismiss = { playlistPickerItem = null },
            onTogglePlaylist = { choice ->
                actionScope.launch {
                    vm.togglePlaylistMembership(target.itemId, choice.playlistId)
                        .onSuccess { result ->
                            val verb = if (result.added) "Added to" else "Removed from"
                            Toast.makeText(context, "$verb ${choice.playlistName}", Toast.LENGTH_SHORT).show()
                            playlistMutationMessage = null
                        }
                        .onFailure { error ->
                            playlistMutationMessage = friendlyPlaylistError(error)
                        }
                }
            },
        )
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

@Composable
private fun QueueItemCard(
    item: PlaybackQueueItem,
    cached: Boolean,
    onOpenPlayer: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onExpandMenu: () -> Unit,
) {
    val title = item.title?.ifBlank { null } ?: item.url
    val progress = item.lastReadPercent ?: 0
    val doneMarker = if (progress >= DONE_PERCENT_THRESHOLD) "done" else "active"
    val cacheMarker = if (cached) "offline-ready" else "needs-network"

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = onExpandMenu) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Item actions",
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist…") },
                            onClick = {
                                onDismissMenu()
                                onOpenPlaylistPicker()
                            },
                        )
                    }
                }
            }
            Text(
                text = "${item.host ?: "-"}  $progress%  $doneMarker  $cacheMarker",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun friendlyPlaylistError(error: Throwable): String {
    return when (error) {
        is ApiException -> {
            when (error.statusCode) {
                401, 403 -> "Unauthorized. Check token, then open Diagnostics."
                else -> "Could not update playlist. Open Diagnostics and retry."
            }
        }
        else -> "Couldn't update playlist. Check connection, then open Diagnostics."
    }
}

private fun isLikelyPhysicalDevice(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val brand = Build.BRAND.lowercase()
    return !(fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        brand.startsWith("generic"))
}
