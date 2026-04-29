package com.mimeo.android.ui.queue

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.isTerminalPendingProcessingStatus
import com.mimeo.android.resolveSmartPlaylistIdFromSessionSourceId
import com.mimeo.android.resolveSessionSourcePlaylistId
import com.mimeo.android.smartPlaylistSessionSourceLabel
import com.mimeo.android.model.AutoDownloadDiagnostics
import com.mimeo.android.model.AutoDownloadWorkerState
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.extractFirstHttpUrl
import com.mimeo.android.share.isRetryablePendingSaveResult
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private const val ACTION_KEY_OPEN_SETTINGS = "open_settings"

internal fun autoDownloadWorkerStateLabel(state: AutoDownloadWorkerState): String {
    return when (state) {
        AutoDownloadWorkerState.IDLE -> "idle"
        AutoDownloadWorkerState.QUEUED -> "queued"
        AutoDownloadWorkerState.RUNNING -> "in progress"
        AutoDownloadWorkerState.RETRY_PENDING -> "retry pending"
        AutoDownloadWorkerState.SUCCEEDED -> "succeeded"
        AutoDownloadWorkerState.COMPLETED_WITH_FAILURES -> "completed with failures"
        AutoDownloadWorkerState.SKIPPED_ALREADY_CACHED -> "skipped (already cached)"
        AutoDownloadWorkerState.SKIPPED_DISABLED -> "skipped (disabled)"
        AutoDownloadWorkerState.SKIPPED_NO_TOKEN -> "skipped (no token)"
    }
}

internal fun autoDownloadStatusLines(status: AutoDownloadDiagnostics): List<String> {
    val enabledLabel = if (status.autoDownloadEnabled) "On" else "Off"
    val queueCount = status.queueItemCount.coerceAtLeast(0)
    val readyCount = status.offlineReadyCount.coerceIn(0, maxOf(1, queueCount))
    val knownNoActive = status.knownNoActiveCount.coerceAtLeast(0)
    val workerLabel = autoDownloadWorkerStateLabel(status.workerState)
    val queuedOfCandidate = "${status.queuedCount.coerceAtLeast(0)}/${status.candidateCount.coerceAtLeast(0)}"
    val scheduleModeLabel = if (status.includeAllVisibleUncached) "refresh-all" else "newly-surfaced"
    val runSummary = "attempted=${status.attemptedCount.coerceAtLeast(0)} success=${status.successCount.coerceAtLeast(0)} retryable=${status.retryableFailureCount.coerceAtLeast(0)} terminal=${status.terminalFailureCount.coerceAtLeast(0)}"
    return listOf(
        "Auto-download: $enabledLabel  |  Offline-ready: $readyCount/$queueCount  |  Known unavailable: $knownNoActive",
        "Worker: $workerLabel  |  Last schedule: queued $queuedOfCandidate ($scheduleModeLabel)",
        "Skipped: cached=${status.skippedCachedCount.coerceAtLeast(0)} no-active=${status.skippedNoActiveCount.coerceAtLeast(0)}  |  Last run: $runSummary",
    )
}

internal data class SessionSeedSourcePresentation(
    val seededFromLabel: String,
    val currentSourceLabel: String,
)

internal fun resolveSessionSeedSourcePresentation(
    sessionSourcePlaylistId: Int?,
    selectedPlaylistId: Int?,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary> = emptyList(),
): SessionSeedSourcePresentation {
    val seededFrom = if (sessionSourcePlaylistId == null) {
        "Unknown source"
    } else {
        resolveQueueSourceLabel(sessionSourcePlaylistId, playlists, smartPlaylists)
    }
    val currentSource = resolveQueueSourceLabel(
        selectedPlaylistId = resolveSessionSourcePlaylistId(selectedPlaylistId),
        playlists = playlists,
        smartPlaylists = smartPlaylists,
    )
    return SessionSeedSourcePresentation(
        seededFromLabel = seededFrom,
        currentSourceLabel = currentSource,
    )
}

internal fun shouldConfirmReseedFromCurrentSource(
    session: NowPlayingSession?,
    sourceItems: List<PlaybackQueueItem>,
    selectedPlaylistId: Int?,
): Boolean {
    val activeSession = session ?: return false
    val sessionItemIds = activeSession.items.map { it.itemId }
    val sourceItemIds = sourceItems.map { it.itemId }
    val currentSourceId = resolveSessionSourcePlaylistId(selectedPlaylistId)
    return activeSession.sourcePlaylistId != currentSourceId || sessionItemIds != sourceItemIds
}

private fun resolveQueueSourceLabel(
    selectedPlaylistId: Int,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary> = emptyList(),
): String {
    resolveSmartPlaylistIdFromSessionSourceId(selectedPlaylistId)?.let { smartPlaylistId ->
        val smartName = smartPlaylists.firstOrNull { it.id == smartPlaylistId }?.name
        return if (smartName.isNullOrBlank()) {
            "Smart view ($smartPlaylistId)"
        } else {
            smartPlaylistSessionSourceLabel(smartName)
        }
    }
    if (selectedPlaylistId < 0) return "Smart queue"
    return playlists.firstOrNull { it.id == selectedPlaylistId }?.name
        ?: "Playlist ($selectedPlaylistId)"
}

internal enum class ManualSaveMode {
    URL,
    TEXT,
}

internal data class ManualSavePrefill(
    val urlInput: String = "",
    val bodyInput: String = "",
)

private data class ManualSavePayload(
    val type: PendingManualSaveType,
    val urlInput: String,
    val titleInput: String?,
    val bodyInput: String?,
    val destinationPlaylistId: Int?,
)

internal enum class PendingOutcomeSimulation {
    CACHED,
    NO_ACTIVE_CONTENT,
    FAILED_PROCESSING,
}

internal data class PendingOutcomeSimulationPresentation(
    val title: String,
    val detail: String,
    val iconRes: Int,
    val isError: Boolean,
)

internal fun pendingOutcomeSimulationMessage(outcome: PendingOutcomeSimulation): String {
    return when (outcome) {
        PendingOutcomeSimulation.CACHED -> "Saved. Available offline."
        PendingOutcomeSimulation.NO_ACTIVE_CONTENT -> "Saved, but unavailable offline for this item."
        PendingOutcomeSimulation.FAILED_PROCESSING -> "Saved, but offline processing failed."
    }
}

internal fun pendingOutcomeSimulationPresentation(outcome: PendingOutcomeSimulation): PendingOutcomeSimulationPresentation {
    return when (outcome) {
        PendingOutcomeSimulation.CACHED -> PendingOutcomeSimulationPresentation(
            title = "Offline ready",
            detail = "Item is saved and ready for offline reading.",
            iconRes = R.drawable.ic_book_closed_24,
            isError = false,
        )
        PendingOutcomeSimulation.NO_ACTIVE_CONTENT -> PendingOutcomeSimulationPresentation(
            title = "Unavailable offline",
            detail = "No active readable content was found for this item.",
            iconRes = R.drawable.msr_error_circle_24,
            isError = true,
        )
        PendingOutcomeSimulation.FAILED_PROCESSING -> PendingOutcomeSimulationPresentation(
            title = "Offline processing failed",
            detail = "Offline caching failed. Retry from the item menu.",
            iconRes = R.drawable.msr_error_circle_24,
            isError = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    focusItemId: Int? = null,
    upNextTabTapSignal: Int = 0,
    snapToActiveSignal: Int = 0,
    snapBottomClearance: Dp = 0.dp,
    renderSnapPillLocally: Boolean = true,
    onSnapPillVisibilityChange: (Boolean) -> Unit = {},
    onOpenPlayer: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val items by vm.queueItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val smartPlaylists by vm.smartPlaylists.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val offline by vm.queueOffline.collectAsState()
    val autoDownloadDiagnostics by vm.autoDownloadDiagnostics.collectAsState()
    val pendingManualSaves by vm.pendingManualSaves.collectAsState()
    val pendingManualRetryInProgress by vm.pendingManualRetryInProgress.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val actionScope = rememberCoroutineScope()

    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var showReseedConfirmation by remember { mutableStateOf(false) }
    var showClearUpcomingConfirmation by remember { mutableStateOf(false) }
    var showClearAllSessionConfirmation by remember { mutableStateOf(false) }
    var showSaveQueueAsPlaylistDialog by remember { mutableStateOf(false) }
    var saveQueuePlaylistNameInput by rememberSaveable { mutableStateOf("") }
    var saveQueueNameError by remember { mutableStateOf<String?>(null) }
    var saveQueueInProgress by remember { mutableStateOf(false) }
    val saveQueueNameFocusRequester = remember { FocusRequester() }
    var showPendingSavesHub by remember { mutableStateOf(false) }
    var pendingHubStatusMessage by remember { mutableStateOf<String?>(null) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var simulatedPendingOutcome by remember { mutableStateOf<PendingOutcomeSimulation?>(null) }
    var showSaveEntryDialog by remember { mutableStateOf(false) }
    var manualSaveMode by rememberSaveable { mutableStateOf(ManualSaveMode.URL) }
    var manualUrlInput by rememberSaveable { mutableStateOf("") }
    var manualTitleInput by rememberSaveable { mutableStateOf("") }
    var manualBodyInput by rememberSaveable { mutableStateOf("") }
    var manualUrlError by remember { mutableStateOf<String?>(null) }
    var manualBodyError by remember { mutableStateOf<String?>(null) }
    var manualSubmitError by remember { mutableStateOf<String?>(null) }
    var manualSaveInProgress by remember { mutableStateOf(false) }
    var manualSaveJob by remember { mutableStateOf<Job?>(null) }
    var manualActivePayload by remember { mutableStateOf<ManualSavePayload?>(null) }
    var manualSaveAttemptVersion by remember { mutableIntStateOf(0) }
    val manualUrlFocusRequester = remember { FocusRequester() }
    val manualBodyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.loadQueueIfNotRecent()
        vm.flushPendingProgress()
    }

    val selectedPlaylistName = settings.selectedPlaylistId?.let { id ->
        playlists.firstOrNull { it.id == id }?.name
    } ?: "Smart queue"
    val canReseedFromCurrentSource = !loading
    val hasQueueContent = nowPlayingSession?.currentItem != null
    val sessionSeedPresentation = nowPlayingSession?.let { session ->
        resolveSessionSeedSourcePresentation(
            sessionSourcePlaylistId = session.sourcePlaylistId,
            selectedPlaylistId = settings.selectedPlaylistId,
            playlists = playlists,
            smartPlaylists = smartPlaylists,
        )
    }

    suspend fun executeReseedFromCurrentSource() {
        if (!canReseedFromCurrentSource) {
            onShowSnackbar("Queue is still loading. Wait, then re-seed.", null, null)
            return
        }
        vm.reseedNowPlayingSessionFromCurrentSource()
            .onSuccess { result ->
                if (result.rebuiltItemCount > 0) {
                    onShowSnackbar("Re-seeded Up Next from ${result.sourceLabel}.", null, null)
                } else {
                    onShowSnackbar("Cleared Up Next because ${result.sourceLabel} is empty.", null, null)
                }
            }
            .onFailure {
                onShowSnackbar("Couldn't re-seed Up Next from current source.", "Diagnostics", "open_diagnostics")
            }
    }

    suspend fun refreshQueueContent() {
        if (refreshActionState == RefreshActionVisualState.Refreshing) return
        refreshActionState = RefreshActionVisualState.Refreshing
        val result = vm.loadQueueOnce(forceAutoDownloadAllVisibleUncached = true)
        refreshActionState = if (result.isSuccess) {
            RefreshActionVisualState.Success
        } else {
            RefreshActionVisualState.Failure
        }
        delay(700)
        if (
            refreshActionState == RefreshActionVisualState.Success ||
            refreshActionState == RefreshActionVisualState.Failure
        ) {
            refreshActionState = RefreshActionVisualState.Idle
        }
    }

    val retryPendingItem: (PendingManualSaveItem) -> Unit = { item ->
        actionScope.launch {
            if (offline) {
                pendingHubStatusMessage = "Still offline. Pending saves kept."
                onShowSnackbar("Still offline. Pending saves kept.", null, null)
                return@launch
            }
            val retryResult = vm.retryPendingManualSave(item.id) ?: return@launch
            if (retryResult is ShareSaveResult.Saved) {
                pendingHubStatusMessage = null
                vm.loadQueue(autoRetryPendingSaves = false)
            } else {
                pendingHubStatusMessage = retryResult.notificationText
            }
            if (shouldSurfacePendingRetrySnackbar(retryResult)) {
                val actionLabel = if (retryResult.opensSettings) "Open Settings" else null
                val actionKey = if (retryResult.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                onShowSnackbar(retryResult.notificationText, actionLabel, actionKey)
            }
        }
    }
    val retryAllPendingItems: () -> Unit = {
        actionScope.launch {
            if (offline) {
                pendingHubStatusMessage = "Still offline. Pending saves kept."
                onShowSnackbar("Still offline. Pending saves kept.", null, null)
                return@launch
            }
            val retrySummary = vm.retryAllPendingManualSaves()
            if (retrySummary.successCount > 0) {
                pendingHubStatusMessage = null
                vm.loadQueue(autoRetryPendingSaves = false)
                onShowSnackbar("Retried ${retrySummary.successCount} pending saves", null, null)
            } else if (retrySummary.firstFailureResult != null) {
                pendingHubStatusMessage = retrySummary.firstFailureResult.notificationText
                val actionLabel = if (retrySummary.firstFailureResult.opensSettings) "Open Settings" else null
                val actionKey = if (retrySummary.firstFailureResult.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                onShowSnackbar(retrySummary.firstFailureResult.notificationText, actionLabel, actionKey)
            } else if (pendingManualSaves.isNotEmpty()) {
                pendingHubStatusMessage = "No pending saves retried. Check API token/connection."
                onShowSnackbar("No pending saves retried. Check API token/connection.", null, null)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 1. Header card: seed-source labels + Refresh + Save + overflow
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (sessionSeedPresentation == null) {
                    Text(
                        text = "Source: $selectedPlaylistName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ActionHintTooltip(label = "Refresh") {
                            RefreshActionButton(
                                state = refreshActionState,
                                showConnectivityIssue = offline,
                                onClick = { actionScope.launch { refreshQueueContent() } },
                                contentDescription = "Refresh queue and sync progress",
                                pullProgress = 0f,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        ActionHintTooltip(label = "Save") {
                            IconButton(
                                enabled = !manualSaveInProgress,
                                modifier = Modifier.size(36.dp),
                                onClick = {
                                    val prefill = buildManualSavePrefill(readClipboardText(context))
                                    manualSaveMode = ManualSaveMode.URL
                                    manualUrlInput = prefill.urlInput
                                    manualTitleInput = ""
                                    manualBodyInput = prefill.bodyInput
                                    manualUrlError = null
                                    manualBodyError = null
                                    manualSubmitError = null
                                    showSaveEntryDialog = true
                                },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.msr_add_24),
                                    contentDescription = "Save URL",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        ActionHintTooltip(label = "Queue actions") {
                            Box {
                                IconButton(
                                    onClick = { topActionsMenuExpanded = true },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.msr_more_vert_24),
                                        contentDescription = "Queue actions",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = topActionsMenuExpanded,
                                    onDismissRequest = { topActionsMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (pendingManualSaves.isEmpty()) {
                                                    "Pending saves"
                                                } else {
                                                    "Pending saves (${pendingManualSaves.size})"
                                                },
                                            )
                                        },
                                        onClick = {
                                            showPendingSavesHub = true
                                            topActionsMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                buildString {
                                                    append("Re-seed from ")
                                                    append(selectedPlaylistName)
                                                },
                                            )
                                        },
                                        enabled = canReseedFromCurrentSource,
                                        onClick = {
                                            topActionsMenuExpanded = false
                                            if (shouldConfirmReseedFromCurrentSource(
                                                    session = nowPlayingSession,
                                                    sourceItems = items,
                                                    selectedPlaylistId = settings.selectedPlaylistId,
                                                )
                                            ) {
                                                showReseedConfirmation = true
                                            } else {
                                                actionScope.launch { executeReseedFromCurrentSource() }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear all session") },
                                        enabled = nowPlayingSession != null,
                                        onClick = {
                                            topActionsMenuExpanded = false
                                            showClearAllSessionConfirmation = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save queue as playlist…") },
                                        enabled = hasQueueContent && !saveQueueInProgress,
                                        onClick = {
                                            topActionsMenuExpanded = false
                                            saveQueuePlaylistNameInput = ""
                                            saveQueueNameError = null
                                            showSaveQueueAsPlaylistDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Auto-download diagnostics card (optional)
        if (settings.showAutoDownloadDiagnostics) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Autodownload status", style = MaterialTheme.typography.labelMedium)
                    autoDownloadStatusLines(autoDownloadDiagnostics).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 3. Pending outcome simulator (debug only)
        if (BuildConfig.DEBUG && settings.showPendingOutcomeSimulator) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Pending outcome simulator", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "Developer-only: simulate pending resolution outcomes without backend state changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(
                        visible = simulatedPendingOutcome != null,
                        enter = expandVertically(animationSpec = tween(durationMillis = 140)) +
                            fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 160)) +
                            fadeOut(animationSpec = tween(durationMillis = 140)),
                    ) {
                        simulatedPendingOutcome?.let { outcome ->
                            val presentation = pendingOutcomeSimulationPresentation(outcome)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = presentation.iconRes),
                                    contentDescription = presentation.title,
                                    tint = if (presentation.isError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = presentation.title,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        text = presentation.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { simulatedPendingOutcome = null }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        TextButton(
                            onClick = {
                                simulatedPendingOutcome = PendingOutcomeSimulation.CACHED
                                val message = pendingOutcomeSimulationMessage(PendingOutcomeSimulation.CACHED)
                                onShowSnackbar(message, null, null)
                            },
                        ) {
                            Text(
                                text = "Simulate cached",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = {
                                simulatedPendingOutcome = PendingOutcomeSimulation.NO_ACTIVE_CONTENT
                                val message = pendingOutcomeSimulationMessage(PendingOutcomeSimulation.NO_ACTIVE_CONTENT)
                                onShowSnackbar(message, null, null)
                            },
                        ) {
                            Text(
                                text = "Simulate unavailable",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = {
                                simulatedPendingOutcome = PendingOutcomeSimulation.FAILED_PROCESSING
                                val message = pendingOutcomeSimulationMessage(PendingOutcomeSimulation.FAILED_PROCESSING)
                                onShowSnackbar(message, null, null)
                            },
                        ) {
                            Text(
                                text = "Simulate failed",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 4. NowPlayingSessionPanel with weight(1f), or empty-state card
        val session = nowPlayingSession
        if (session != null) {
            NowPlayingSessionPanel(
                modifier = Modifier.weight(1f),
                session = session,
                seededFromLabel = sessionSeedPresentation?.seededFromLabel ?: "Unknown source",
                currentSourceLabel = sessionSeedPresentation?.currentSourceLabel ?: selectedPlaylistName,
                onOpenItem = { itemId -> onOpenPlayer(itemId) },
                onReorderItem = { from, to ->
                    vm.reorderNowPlayingSessionItem(fromIndex = from, toIndex = to)
                },
                onRemoveItem = { itemId -> vm.removeItemFromSession(itemId) },
                onClearUpcoming = { showClearUpcomingConfirmation = true },
                reseedEnabled = canReseedFromCurrentSource,
                onReseed = {
                    if (shouldConfirmReseedFromCurrentSource(
                            session = nowPlayingSession,
                            sourceItems = items,
                            selectedPlaylistId = settings.selectedPlaylistId,
                        )
                    ) {
                        showReseedConfirmation = true
                    } else {
                        actionScope.launch { executeReseedFromCurrentSource() }
                    }
                },
                snapBottomClearance = snapBottomClearance,
                snapToActiveSignal = snapToActiveSignal,
                renderSnapPillLocally = renderSnapPillLocally,
                onSnapPillVisibilityChange = onSnapPillVisibilityChange,
            )
        } else {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No active session. Open an item to start one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Re-seed confirmation dialog
    if (showReseedConfirmation) {
        AlertDialog(
            onDismissRequest = { showReseedConfirmation = false },
            title = { Text("Re-seed Up Next?") },
            text = {
                Text(
                    "This replaces the current local Up Next session with items from \"$selectedPlaylistName\".",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReseedConfirmation = false
                        actionScope.launch { executeReseedFromCurrentSource() }
                    },
                ) {
                    Text("Re-seed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReseedConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearUpcomingConfirmation) {
        val upcomingCount = nowPlayingSession?.let { session ->
            val safeIndex = session.currentIndex.coerceIn(0, (session.items.size - 1).coerceAtLeast(0))
            (session.items.size - safeIndex - 1).coerceAtLeast(0)
        } ?: 0
        AlertDialog(
            onDismissRequest = { showClearUpcomingConfirmation = false },
            title = { Text("Clear upcoming?") },
            text = {
                Text(
                    "This removes $upcomingCount upcoming item${if (upcomingCount == 1) "" else "s"} after Now Playing. The active item and earlier session items stay in place.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = upcomingCount > 0,
                    onClick = {
                        showClearUpcomingConfirmation = false
                        vm.clearUpcomingNowPlayingItems()
                    },
                ) {
                    Text("Clear upcoming")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearUpcomingConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearAllSessionConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllSessionConfirmation = false },
            title = { Text("Clear all session?") },
            text = {
                Text("This clears the whole local Now Playing session, including the active item and all upcoming items.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllSessionConfirmation = false
                        vm.clearNowPlayingSession()
                    },
                ) {
                    Text("Clear all session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllSessionConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Save queue as playlist dialog
    if (showSaveQueueAsPlaylistDialog) {
        suspend fun executeSaveQueueAsPlaylist() {
            val trimmedName = saveQueuePlaylistNameInput.trim()
            if (trimmedName.isEmpty()) {
                saveQueueNameError = "Enter a playlist name"
                return
            }
            val session = nowPlayingSession ?: run {
                showSaveQueueAsPlaylistDialog = false
                return
            }
            val activeIdx = session.currentIndex.coerceIn(0, (session.items.size - 1).coerceAtLeast(0))
            val itemIds = session.items.drop(activeIdx).map { it.itemId }
            if (itemIds.isEmpty()) {
                saveQueueNameError = "No items to save"
                return
            }
            saveQueueInProgress = true
            try {
                val result = vm.saveQueueAsPlaylist(trimmedName, itemIds)
                showSaveQueueAsPlaylistDialog = false
                saveQueuePlaylistNameInput = ""
                saveQueueNameError = null
                result
                    .onSuccess { playlistName ->
                        onShowSnackbar(
                            "Saved ${itemIds.size} item${if (itemIds.size == 1) "" else "s"} to “$playlistName”",
                            null,
                            null,
                        )
                    }
                    .onFailure { error ->
                        onShowSnackbar("Couldn't save playlist: ${error.message}", null, null)
                    }
            } finally {
                saveQueueInProgress = false
            }
        }

        LaunchedEffect(showSaveQueueAsPlaylistDialog) {
            if (showSaveQueueAsPlaylistDialog) saveQueueNameFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = {
                if (!saveQueueInProgress) {
                    showSaveQueueAsPlaylistDialog = false
                    saveQueueNameError = null
                }
            },
            title = { Text("Save queue as playlist…") },
            text = {
                OutlinedTextField(
                    value = saveQueuePlaylistNameInput,
                    onValueChange = {
                        saveQueuePlaylistNameInput = it
                        saveQueueNameError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(saveQueueNameFocusRequester),
                    singleLine = true,
                    enabled = !saveQueueInProgress,
                    label = { Text("Playlist name") },
                    isError = saveQueueNameError != null,
                    supportingText = { saveQueueNameError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (saveQueuePlaylistNameInput.isNotBlank() && !saveQueueInProgress) {
                                actionScope.launch { executeSaveQueueAsPlaylist() }
                            }
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = saveQueuePlaylistNameInput.isNotBlank() && !saveQueueInProgress,
                    onClick = { actionScope.launch { executeSaveQueueAsPlaylist() } },
                ) {
                    Text(if (saveQueueInProgress) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saveQueueInProgress,
                    onClick = {
                        showSaveQueueAsPlaylistDialog = false
                        saveQueueNameError = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Manual save dialog
    if (showSaveEntryDialog) {
        val canSubmit = canSubmitManualSave(
            mode = manualSaveMode,
            urlInput = manualUrlInput,
            bodyInput = manualBodyInput,
            inProgress = manualSaveInProgress,
        )
        suspend fun submitManualEntry() {
            manualSubmitError = null
            manualSaveJob = currentCoroutineContext()[Job]
            val extractedUrl = resolveManualSaveUrl(manualUrlInput)
            if (manualSaveMode == ManualSaveMode.URL && extractedUrl == null) {
                manualUrlError = "Enter a valid http(s) URL"
                manualSaveJob = null
                return
            }
            manualUrlError = null

            val normalizedBody = if (manualSaveMode == ManualSaveMode.TEXT) {
                normalizeManualTextBody(manualBodyInput).also {
                    if (it == null) {
                        manualBodyError = "Paste text is required"
                    }
                }
            } else {
                null
            }
            if (manualSaveMode == ManualSaveMode.TEXT && normalizedBody == null) {
                manualSaveJob = null
                return
            }
            manualBodyError = null
            val payload = if (manualSaveMode == ManualSaveMode.TEXT) {
                ManualSavePayload(
                    type = PendingManualSaveType.TEXT,
                    urlInput = resolveManualTextSaveUrl(
                        urlInput = manualUrlInput,
                        titleInput = manualTitleInput,
                        bodyInput = normalizedBody.orEmpty(),
                    ),
                    titleInput = manualTitleInput.trim().takeIf { it.isNotEmpty() },
                    bodyInput = normalizedBody,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                )
            } else {
                ManualSavePayload(
                    type = PendingManualSaveType.URL,
                    urlInput = extractedUrl.orEmpty(),
                    titleInput = null,
                    bodyInput = null,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                )
            }
            manualActivePayload = payload
            vm.enqueueAcceptedPendingSave(
                source = PendingSaveSource.MANUAL,
                type = payload.type,
                urlInput = payload.urlInput,
                titleInput = payload.titleInput,
                bodyInput = payload.bodyInput,
                destinationPlaylistId = payload.destinationPlaylistId,
            )
            fun queueCurrentManualSaveAndClose(
                statusMessage: String,
                pendingResult: ShareSaveResult = ShareSaveResult.NetworkError,
            ) {
                vm.queueFailedManualSave(
                    type = payload.type,
                    urlInput = payload.urlInput,
                    titleInput = payload.titleInput,
                    bodyInput = payload.bodyInput,
                    result = pendingResult,
                    destinationPlaylistId = payload.destinationPlaylistId,
                )
                showSaveEntryDialog = false
                manualUrlInput = ""
                manualTitleInput = ""
                manualBodyInput = ""
                manualUrlError = null
                manualBodyError = null
                manualSubmitError = null
                onShowSnackbar(statusMessage, null, null)
            }
            if (offline) {
                queueCurrentManualSaveAndClose(ShareSaveResult.PendingQueued.notificationText)
                manualSaveJob = null
                return
            }
            val attemptVersion = manualSaveAttemptVersion + 1
            manualSaveAttemptVersion = attemptVersion
            manualSaveInProgress = true
            try {
                val result = if (manualSaveMode == ManualSaveMode.TEXT) {
                    vm.saveManualTextFromUpNext(
                        urlInput = payload.urlInput,
                        titleInput = payload.titleInput,
                        bodyInput = payload.bodyInput.orEmpty(),
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                } else {
                    vm.saveUrlFromUpNext(
                        rawInput = payload.urlInput,
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                }
                if (manualSaveAttemptVersion != attemptVersion) {
                    return
                }
                if (isManualSaveSuccess(result)) {
                    val actionLabel = if (result.opensSettings) "Open Settings" else null
                    val actionKey = if (result.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                    onShowSnackbar(result.notificationText, actionLabel, actionKey)
                    if (result is ShareSaveResult.Saved && result.itemId != null) {
                        vm.markAcceptedPendingSaveResolved(
                            source = PendingSaveSource.MANUAL,
                            type = payload.type,
                            urlInput = payload.urlInput,
                            titleInput = payload.titleInput,
                            bodyInput = payload.bodyInput,
                            destinationPlaylistId = payload.destinationPlaylistId,
                            resolvedItemId = result.itemId,
                        )
                    }
                    showSaveEntryDialog = false
                    manualUrlInput = ""
                    manualTitleInput = ""
                    manualBodyInput = ""
                    manualSubmitError = null
                } else if (isRetryablePendingSaveResult(result)) {
                    queueCurrentManualSaveAndClose(
                        statusMessage = ShareSaveResult.PendingQueued.notificationText,
                        pendingResult = result,
                    )
                } else {
                    val actionLabel = if (result.opensSettings) "Open Settings" else null
                    val actionKey = if (result.opensSettings) ACTION_KEY_OPEN_SETTINGS else null
                    onShowSnackbar(result.notificationText, actionLabel, actionKey)
                    vm.queueFailedManualSave(
                        type = payload.type,
                        urlInput = payload.urlInput,
                        titleInput = payload.titleInput,
                        bodyInput = payload.bodyInput,
                        result = result,
                        destinationPlaylistId = payload.destinationPlaylistId,
                    )
                    manualSubmitError = result.notificationText
                }
            } catch (_: CancellationException) {
                return
            } finally {
                if (manualSaveAttemptVersion == attemptVersion) {
                    manualSaveInProgress = false
                    manualSaveJob = null
                    manualActivePayload = null
                }
            }
        }

        LaunchedEffect(showSaveEntryDialog) {
            if (showSaveEntryDialog) {
                manualUrlFocusRequester.requestFocus()
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!manualSaveInProgress) {
                    showSaveEntryDialog = false
                    manualUrlError = null
                    manualBodyError = null
                    manualSubmitError = null
                }
            },
            title = { Text("Save Item") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = manualSaveMode == ManualSaveMode.URL,
                            enabled = !manualSaveInProgress,
                            onClick = {
                                manualSaveMode = ManualSaveMode.URL
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            label = { Text("Save URL") },
                        )
                        FilterChip(
                            selected = manualSaveMode == ManualSaveMode.TEXT,
                            enabled = !manualSaveInProgress,
                            onClick = {
                                manualSaveMode = ManualSaveMode.TEXT
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            label = { Text("Paste Text") },
                        )
                    }
                    OutlinedTextField(
                        value = manualUrlInput,
                        onValueChange = {
                            manualUrlInput = it
                            manualUrlError = null
                            manualSubmitError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(manualUrlFocusRequester),
                        singleLine = true,
                        enabled = !manualSaveInProgress,
                        label = {
                            Text(
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    "Article URL (optional)"
                                } else {
                                    "Article URL"
                                },
                            )
                        },
                        placeholder = {
                            Text(
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    "https://example.com/article (optional)"
                                } else {
                                    "https://example.com/article"
                                },
                            )
                        },
                        isError = manualUrlError != null,
                        supportingText = {
                            manualUrlError?.let { Text(it) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = if (manualSaveMode == ManualSaveMode.URL) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                if (manualSaveMode == ManualSaveMode.TEXT) {
                                    manualBodyFocusRequester.requestFocus()
                                }
                            },
                            onDone = {
                                if (canSubmit) {
                                    actionScope.launch { submitManualEntry() }
                                }
                            },
                        ),
                    )
                    manualSubmitError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (manualSaveMode == ManualSaveMode.TEXT) {
                        OutlinedTextField(
                            value = manualTitleInput,
                            onValueChange = { manualTitleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !manualSaveInProgress,
                            label = { Text("Title (optional)") },
                        )
                        OutlinedTextField(
                            value = manualBodyInput,
                            onValueChange = {
                                manualBodyInput = it
                                manualBodyError = null
                                manualSubmitError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 320.dp)
                                .focusRequester(manualBodyFocusRequester),
                            minLines = 8,
                            maxLines = 16,
                            enabled = !manualSaveInProgress,
                            label = { Text("Body text") },
                            placeholder = { Text("Paste article text here") },
                            isError = manualBodyError != null,
                            supportingText = {
                                manualBodyError?.let { Text(it) }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canSubmit) {
                                        actionScope.launch { submitManualEntry() }
                                    }
                                },
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        actionScope.launch { submitManualEntry() }
                    },
                ) {
                    Text(if (manualSaveInProgress) "Saving..." else "Save")
                }
            },
            dismissButton = {
                if (manualSaveInProgress && offline) {
                    TextButton(
                        onClick = {
                            val payload = manualActivePayload ?: return@TextButton
                            vm.queueFailedManualSave(
                                type = payload.type,
                                urlInput = payload.urlInput,
                                titleInput = payload.titleInput,
                                bodyInput = payload.bodyInput,
                                result = ShareSaveResult.NetworkError,
                                destinationPlaylistId = payload.destinationPlaylistId,
                            )
                            manualSaveAttemptVersion += 1
                            manualSaveJob?.cancel()
                            manualSaveInProgress = false
                            showSaveEntryDialog = false
                            manualUrlInput = ""
                            manualTitleInput = ""
                            manualBodyInput = ""
                            manualActivePayload = null
                            manualUrlError = null
                            manualBodyError = null
                            manualSubmitError = null
                            onShowSnackbar(ShareSaveResult.PendingQueued.notificationText, null, null)
                        },
                    ) {
                        Text("Queue now")
                    }
                } else {
                    TextButton(
                        enabled = !manualSaveInProgress,
                        onClick = {
                            showSaveEntryDialog = false
                            manualUrlError = null
                            manualBodyError = null
                            manualSubmitError = null
                        },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    // Pending saves hub dialog
    if (showPendingSavesHub) {
        AlertDialog(
            onDismissRequest = {
                showPendingSavesHub = false
                pendingHubStatusMessage = null
            },
            title = { Text("Pending saves") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingHubStatusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (pendingManualSaves.isEmpty()) {
                        Text("No pending saves.")
                    } else {
                        PendingManualRetryCard(
                            pendingItems = pendingManualSaves,
                            playlistNameById = playlists.associate { it.id to it.name },
                            retryInProgress = pendingManualRetryInProgress,
                            onRetry = retryPendingItem,
                            onRetryAll = retryAllPendingItems,
                            onDismiss = { item -> vm.removePendingManualSave(item.id) },
                            onClearAll = { vm.clearPendingManualSaves() },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPendingSavesHub = false
                    pendingHubStatusMessage = null
                }) {
                    Text("Close")
                }
            },
        )
    }
}

internal fun projectPendingItemsForDestination(
    pendingItems: List<PendingManualSaveItem>,
    selectedPlaylistId: Int?,
    queueItems: List<PlaybackQueueItem>,
    cachedItemIds: Set<Int>,
    noActiveContentItemIds: Set<Int>,
): List<PendingManualSaveItem> {
    return pendingItems.filter { pending ->
        if (pending.destinationPlaylistId != selectedPlaylistId) {
            return@filter false
        }
        val matchedQueueItem = queueItems.firstOrNull { item ->
            pending.resolvedItemId?.let { resolvedItemId ->
                item.itemId == resolvedItemId
            } ?: (normalizePendingComparisonUrl(pending.urlInput) == normalizePendingComparisonUrl(item.url))
        } ?: return@filter true
        if (pending.resolvedItemId == null) return@filter true
        if (hasFailedPendingProjectionStatus(matchedQueueItem)) return@filter true
        val matchedItemId = matchedQueueItem.itemId
        if (noActiveContentItemIds.contains(matchedItemId)) return@filter false
        !cachedItemIds.contains(matchedItemId)
    }
}

private fun normalizePendingComparisonUrl(raw: String?): String? {
    val extracted = extractFirstHttpUrl(raw)?.trim()?.lowercase() ?: return null
    return extracted.removeSuffix("/")
}

private fun hasFailedPendingProjectionStatus(queueItem: PlaybackQueueItem): Boolean {
    return isTerminalPendingProcessingStatus(queueItem.status)
}

internal fun resolveManualSaveUrl(input: String): String? {
    return extractFirstHttpUrl(input.trim())
}

internal fun normalizeManualTextBody(input: String): String? {
    return input.trim().takeIf { it.isNotEmpty() }
}

internal fun canSubmitManualSave(
    mode: ManualSaveMode,
    urlInput: String,
    bodyInput: String,
    inProgress: Boolean,
): Boolean {
    if (inProgress) return false
    return when (mode) {
        ManualSaveMode.URL -> resolveManualSaveUrl(urlInput) != null
        ManualSaveMode.TEXT -> bodyInput.isNotBlank()
    }
}

internal fun isManualSaveSuccess(result: ShareSaveResult): Boolean {
    return result is ShareSaveResult.Saved
}

internal fun shouldSurfacePendingRetrySnackbar(result: ShareSaveResult): Boolean {
    return when (result) {
        is ShareSaveResult.Saved,
        ShareSaveResult.MissingToken,
        ShareSaveResult.Unauthorized,
        ShareSaveResult.NoValidUrl,
        -> true
        else -> false
    }
}

internal fun buildManualSavePrefill(clipboardText: String?): ManualSavePrefill {
    val raw = clipboardText?.trim().orEmpty()
    if (raw.isEmpty()) {
        return ManualSavePrefill()
    }
    val url = resolveManualSaveUrl(raw)
    return if (url != null) {
        ManualSavePrefill(urlInput = url)
    } else {
        ManualSavePrefill(bodyInput = raw)
    }
}

internal fun resolveManualTextSaveUrl(
    urlInput: String,
    titleInput: String,
    bodyInput: String,
): String {
    return resolveManualSaveUrl(urlInput) ?: buildManualTextFallbackUrl(titleInput, bodyInput)
}

private fun buildManualTextFallbackUrl(titleInput: String, bodyInput: String): String {
    val seedSource = titleInput.trim().ifBlank {
        bodyInput.trim().lineSequence().firstOrNull().orEmpty()
    }
    val slug = seedSource
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
        .ifBlank { "manual-text" }
    val suffix = UUID.randomUUID().toString().substring(0, 8)
    return "https://manual.mimeo.local/$slug-$suffix"
}

private fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val item = manager.primaryClip?.getItemAt(0) ?: return null
    return item.coerceToText(context)?.toString()
}

internal fun formatPendingDestinationLabel(
    destinationPlaylistId: Int?,
    playlistNameById: Map<Int, String>,
): String {
    if (destinationPlaylistId == null) {
        return "Destination: Smart Queue"
    }
    val playlistName = playlistNameById[destinationPlaylistId]
    return if (playlistName.isNullOrBlank()) {
        "Destination: Playlist #$destinationPlaylistId"
    } else {
        "Destination: $playlistName"
    }
}

internal fun classifyPendingFailureReason(message: String): String {
    val lower = message.lowercase()
    return when {
        lower.contains("saving") -> "Saving..."
        lower.contains("unauthorized") || lower.contains("forbidden") || lower.contains("token") ->
            "Auth required"
        lower.contains("timeout") || lower.contains("timed out") ->
            "Request timed out"
        lower.contains("network") || lower.contains("offline") ||
            lower.contains("couldn't reach server") || lower.contains("could not reach server") ->
            "Backend unreachable"
        lower.contains("blocked") || lower.contains("paywall") ->
            "Blocked by source"
        lower.contains("unsupported") ->
            "Source unsupported"
        else -> "Save failed"
    }
}

@Composable
private fun PendingManualRetryCard(
    pendingItems: List<PendingManualSaveItem>,
    playlistNameById: Map<Int, String>,
    retryInProgress: Boolean,
    onRetry: (PendingManualSaveItem) -> Unit,
    onRetryAll: () -> Unit,
    onDismiss: (PendingManualSaveItem) -> Unit,
    onClearAll: () -> Unit,
) {
    var menuExpandedItemId by remember { mutableStateOf<Long?>(null) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pending saves (${pendingItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !retryInProgress, onClick = onRetryAll) {
                        Text("Retry all")
                    }
                    TextButton(enabled = !retryInProgress, onClick = onClearAll) {
                        Text("Clear all")
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingItems.forEachIndexed { index, item ->
                    val sourceLabel = if (item.source == PendingSaveSource.SHARE) {
                        "Source: Shared"
                    } else {
                        "Source: Manual"
                    }
                    val destinationLabel = formatPendingDestinationLabel(
                        destinationPlaylistId = item.destinationPlaylistId,
                        playlistNameById = playlistNameById,
                    )
                    val titleLine = when {
                        !item.titleInput.isNullOrBlank() -> item.titleInput
                        item.urlInput.isNotBlank() -> item.urlInput
                        item.type == PendingManualSaveType.TEXT -> "Pasted text"
                        else -> "(no title)"
                    }
                    val bodyPreview = item.bodyInput?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = titleLine,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                IconButton(
                                    enabled = !retryInProgress,
                                    onClick = { menuExpandedItemId = item.id },
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.msr_more_vert_24),
                                        contentDescription = "Pending item actions",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpandedItemId == item.id,
                                    onDismissRequest = { menuExpandedItemId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Retry") },
                                        onClick = {
                                            onRetry(item)
                                            menuExpandedItemId = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Dismiss") },
                                        onClick = {
                                            onDismiss(item)
                                            menuExpandedItemId = null
                                        },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = destinationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = item.urlInput.ifBlank { "(no URL provided)" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        bodyPreview?.let { preview ->
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "Pending: ${classifyPendingFailureReason(item.lastFailureMessage)} • retries: ${item.retryCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index < pendingItems.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 1.dp, max = 1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionHintTooltip(
    label: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun NowPlayingSessionPanel(
    session: NowPlayingSession,
    seededFromLabel: String,
    currentSourceLabel: String,
    onOpenItem: (Int) -> Unit,
    onReorderItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    reseedEnabled: Boolean,
    onReseed: () -> Unit,
    modifier: Modifier = Modifier,
    snapBottomClearance: Dp = 0.dp,
    snapToActiveSignal: Int = 0,
    renderSnapPillLocally: Boolean = true,
    onSnapPillVisibilityChange: (Boolean) -> Unit = {},
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val showCurrentSource = seededFromLabel != currentSourceLabel

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
    val upcomingStartIndex = (currentIndex + 1).coerceIn(0, localItems.size)
    val upcomingItems = localItems.drop(upcomingStartIndex)
    val density = LocalDensity.current
    val minVisibleActiveHeightPx = with(density) { 24.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Session queue · ${session.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Seeded from: $seededFromLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showCurrentSource) {
                    Text(
                        text = "Current source: $currentSourceLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailingActions?.invoke(this)
            TextButton(
                enabled = reseedEnabled,
                onClick = onReseed,
            ) {
                Text(
                    text = "Re-seed",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        LaunchedEffect(currentItemId) {
            if (currentItemId == null) return@LaunchedEffect
            val offset = activeTopOffset ?: 0f
            listScrollState.animateScrollTo(offset.toInt())
        }
        LaunchedEffect(snapToActiveSignal) {
            if (snapToActiveSignal > 0) {
                listScrollState.animateScrollTo(0)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { listViewportHeight = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .passiveVerticalScrollIndicator(
                        scrollState = listScrollState,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                    )
                    .verticalScroll(listScrollState),
            ) {
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
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenItem(item.itemId) }
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Spacer(Modifier.size(24.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title?.ifBlank { null } ?: item.url,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (sourceLabel != null) {
                                    Text(
                                        text = sourceLabel,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Upcoming · ${upcomingItems.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = itemVisualOffsetY }
                                .onGloballyPositioned { coords ->
                                    itemTopOffsets[item.itemId] = coords.positionInParent().y
                                    itemHeights[item.itemId] = coords.size.height.toFloat()
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenItem(item.itemId) }
                                    .background(
                                        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Black,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                                    .semantics {
                                        customActions = buildList {
                                            if (index > 0) add(CustomAccessibilityAction("Move up") {
                                                onReorderItem(absoluteIndex, absoluteIndex - 1); true
                                            })
                                            if (index < upcomingItems.lastIndex) add(CustomAccessibilityAction("Move down") {
                                                onReorderItem(absoluteIndex, absoluteIndex + 1); true
                                            })
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .pointerInput(item.itemId, index) {
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
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = item.title?.ifBlank { null } ?: item.url,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (sourceLabel != null) {
                                        Text(
                                            text = sourceLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveItem(item.itemId) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from session",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            if (index < upcomingItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }
            val activeHeight = activeMeasuredHeight
            val activeVisibleHeight = (activeHeight - listScrollState.value.toFloat()).coerceAtLeast(0f)
            val showSnapToActive = activeItem != null &&
                listViewportHeight > 0 &&
                activeHeight > 0f &&
                activeVisibleHeight < minVisibleActiveHeightPx
            LaunchedEffect(showSnapToActive) {
                onSnapPillVisibilityChange(showSnapToActive)
            }
            if (renderSnapPillLocally && showSnapToActive) {
                JumpToNowPlayingPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = snapBottomClearance),
                    onClick = {
                        snapScope.launch { listScrollState.animateScrollTo(0) }
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
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Jump to Now Playing",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}
