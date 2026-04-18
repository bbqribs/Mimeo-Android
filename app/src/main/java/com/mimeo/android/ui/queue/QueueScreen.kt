package com.mimeo.android.ui.queue

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.mimeo.android.repository.NowPlayingSession
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mimeo.android.AppViewModel
import com.mimeo.android.ArchiveActionSource
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.isPendingProcessingFailureMessage
import com.mimeo.android.isTerminalPendingProcessingStatus
import com.mimeo.android.isNoActiveContentError
import com.mimeo.android.resolveSessionSourcePlaylistId
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.AutoDownloadDiagnostics
import com.mimeo.android.model.AutoDownloadWorkerState
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.extractFirstHttpUrl
import com.mimeo.android.share.isRetryablePendingSaveResult
import com.mimeo.android.ui.common.openItemInBrowser
import com.mimeo.android.ui.common.queueCapturePresentation
import com.mimeo.android.ui.common.shareItemUrl
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

private const val DONE_PERCENT_THRESHOLD = 98
private const val ACTION_KEY_OPEN_SETTINGS = "open_settings"
private const val ACTION_KEY_UNDO_ARCHIVE = "undo_archive"
private const val LOCUS_CONTINUATION_DEBUG_TAG = "MimeoLocusContinue"

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

internal fun shouldAutoScrollToTopForNewItems(
    previousDisplayedItemIds: List<Int>,
    currentDisplayedItemIds: List<Int>,
    pendingFocusId: Int,
    hasSearchQuery: Boolean,
    isDefaultFilterAndSort: Boolean,
): Boolean {
    if (pendingFocusId > 0) return false
    if (hasSearchQuery) return false
    if (!isDefaultFilterAndSort) return false
    if (previousDisplayedItemIds.isEmpty()) return false
    if (currentDisplayedItemIds.isEmpty()) return false
    val previousSet = previousDisplayedItemIds.toHashSet()
    // Only scroll to top if the FIRST item in the sorted list is genuinely new
    // (a new save appeared at the head). Appended pages from pagination add items
    // at the tail and must not trigger a bounce to top.
    return currentDisplayedItemIds.first() !in previousSet
}

internal data class UpNextRestorePosition(
    val index: Int,
    val offset: Int,
)

internal data class SessionSeedSourcePresentation(
    val seededFromLabel: String,
    val currentSourceLabel: String,
)

internal fun resolveUpNextRestorePosition(
    currentDisplayedItemIds: List<Int>,
    savedIndex: Int,
    savedOffset: Int,
    savedAnchorItemId: Int?,
): UpNextRestorePosition? {
    if (currentDisplayedItemIds.isEmpty()) return null
    val resolvedIndex = when {
        savedAnchorItemId != null -> {
            val anchorIndex = currentDisplayedItemIds.indexOf(savedAnchorItemId)
            if (anchorIndex < 0) return null
            anchorIndex
        }
        savedIndex <= 0 -> 0
        else -> savedIndex.coerceAtMost(currentDisplayedItemIds.lastIndex)
    }
    val resolvedOffset = if (resolvedIndex == savedIndex) {
        savedOffset.coerceAtLeast(0)
    } else {
        0
    }
    return UpNextRestorePosition(index = resolvedIndex, offset = resolvedOffset)
}

internal fun resolveSessionSeedSourcePresentation(
    sessionSourcePlaylistId: Int?,
    selectedPlaylistId: Int?,
    playlists: List<PlaylistSummary>,
): SessionSeedSourcePresentation {
    val seededFrom = if (sessionSourcePlaylistId == null) {
        "Unknown source"
    } else {
        resolveQueueSourceLabel(sessionSourcePlaylistId, playlists)
    }
    val currentSource = resolveQueueSourceLabel(
        selectedPlaylistId = resolveSessionSourcePlaylistId(selectedPlaylistId),
        playlists = playlists,
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
): String {
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

private enum class QueueFilterChip(val label: String, val enabled: Boolean = true) {
    ALL("All"),
    FAVORITES("Favourites"),
    ARCHIVE("Archive"),
    BIN("Bin"),
    UNREAD("Unread"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
    PENDING("Pending"),
}

private enum class QueueSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    PROGRESS_HIGH("Progress"),
    PROGRESS_LOW("Least progress"),
    TITLE_AZ("Title A-Z"),
}

internal fun shouldStartNewSessionOnQueueOpen(
    tappedItemId: Int,
    sessionCurrentItemId: Int,
    playbackActive: Boolean,
): Boolean {
    if (tappedItemId <= 0) return false
    if (sessionCurrentItemId <= 0) return true
    if (tappedItemId == sessionCurrentItemId) return true
    val preserveExistingOwner = playbackActive
    // Preserve playback owner only while playback is actively running. If playback is
    // paused/inactive, tapping a row should retarget ownership to that opened item.
    return !preserveExistingOwner
}

internal data class QueueRowStateMarkers(
    val playingItemId: Int?,
    val readyResumeItemId: Int?,
)

internal fun resolveQueueRowStateMarkers(
    engineCurrentItemId: Int,
    sessionCurrentItemId: Int?,
    isSpeaking: Boolean,
    isAutoPlaying: Boolean,
    autoPlayAfterLoad: Boolean,
): QueueRowStateMarkers {
    val ownerItemId = when {
        engineCurrentItemId > 0 -> engineCurrentItemId
        (sessionCurrentItemId ?: -1) > 0 -> sessionCurrentItemId
        else -> null
    }
    val playbackActive = isSpeaking || isAutoPlaying || autoPlayAfterLoad
    val playingItemId = if (playbackActive) ownerItemId else null
    val readyResumeItemId = if (!playbackActive) ownerItemId else null
    return QueueRowStateMarkers(
        playingItemId = playingItemId,
        readyResumeItemId = readyResumeItemId,
    )
}

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
    onOpenPlayer: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val items by vm.queueItems.collectAsState()
    val archivedItems by vm.archivedItems.collectAsState()
    val binItems by vm.binItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val loadingMore by vm.queueLoadingMore.collectAsState()
    val queueHasMorePages by vm.queueHasMorePages.collectAsState()
    val queueReloadGeneration by vm.queueReloadGeneration.collectAsState()
    val offline by vm.queueOffline.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val noActiveContentItemIds by vm.noActiveContentItemIds.collectAsState()
    val autoDownloadDiagnostics by vm.autoDownloadDiagnostics.collectAsState()
    val pendingManualSaves by vm.pendingManualSaves.collectAsState()
    val pendingManualRetryInProgress by vm.pendingManualRetryInProgress.collectAsState()
    val pendingShareFocusItemId by vm.pendingQueueFocusItemId.collectAsState()
    val lastQueueFetchDebug by vm.lastQueueFetchDebug.collectAsState()
    val queueScrollState by vm.queueScrollState.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val playbackState by vm.playbackEngineState.collectAsState()
    val actionScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val listState = rememberLazyListState()
    val pullRefreshMaxPx = with(density) { 96.dp.toPx() }
    val pullRefreshThresholdPx = pullRefreshMaxPx
    var pullRefreshDistancePx by remember { mutableFloatStateOf(0f) }
    var pendingFocusId by remember { mutableIntStateOf(-1) }
    var previousProjectedPendingIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var previousDisplayedItemIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var suppressAutoScrollToTopOnce by remember { mutableStateOf(false) }
    var initialUpNextRestoreHandled by remember { mutableStateOf(false) }
    var collapsingArchivedItemIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var rowMenuItemId by remember { mutableIntStateOf(-1) }
    var playlistPickerItem by remember { mutableStateOf<PlaybackQueueItem?>(null) }
    var topActionsMenuExpanded by remember { mutableStateOf(false) }
    var showReseedConfirmation by remember { mutableStateOf(false) }
    var showPendingSavesHub by remember { mutableStateOf(false) }
    var pendingHubStatusMessage by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(QueueFilterChip.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(QueueSortOption.NEWEST) }
    var showQueueFetchDebug by rememberSaveable { mutableStateOf(false) }
    var hasRefreshProblem by rememberSaveable { mutableStateOf(false) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var simulatedPendingOutcome by remember { mutableStateOf<PendingOutcomeSimulation?>(null) }
    var lastHandledUpNextTapSignal by rememberSaveable { mutableIntStateOf(upNextTabTapSignal) }
    var nextUpNextTapScrollTargetTop by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(focusItemId, pendingShareFocusItemId) {
        pendingFocusId = pendingShareFocusItemId ?: (focusItemId ?: -1)
    }

    val selectedPlaylistName = settings.selectedPlaylistId?.let { id ->
        playlists.firstOrNull { it.id == id }?.name
    } ?: "Smart queue"
    val queueFeedLabel = buildString {
        append("Queue feed")
        append(" · source: ")
        append(selectedPlaylistName)
        append(" · sort: ")
        append(selectedSort.label)
        if (selectedFilter != QueueFilterChip.ALL) {
            append(" · filter: ")
            append(selectedFilter.label)
        }
    }
    val canReseedFromCurrentSource = !loading
    val sessionSeedPresentation = nowPlayingSession?.let { session ->
        resolveSessionSeedSourcePresentation(
            sessionSourcePlaylistId = session.sourcePlaylistId,
            selectedPlaylistId = settings.selectedPlaylistId,
            playlists = playlists,
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
    val playlistChoices = playlistPickerItem?.let { target ->
        playlists.map { playlist ->
            PlaylistPickerChoice(
                playlistId = playlist.id,
                playlistName = playlist.name,
                isMember = vm.isItemInPlaylist(target.itemId, playlist.id),
            )
        }
    }.orEmpty()
    LaunchedEffect(selectedFilter) {
        when (selectedFilter) {
            QueueFilterChip.ARCHIVE -> vm.loadArchivedItems()
            QueueFilterChip.BIN -> vm.loadBinItems()
            else -> Unit
        }
    }

    val searchNeedle = remember(searchQuery) { searchQuery.trim().lowercase() }
    val normalizedSearchNeedle = remember(searchNeedle) { normalizeSearchText(searchNeedle) }
    val favoritesSourceItems = remember(items, archivedItems) {
        (items + archivedItems).distinctBy { it.itemId }
    }
    val activeItems = remember(selectedFilter, items, favoritesSourceItems, archivedItems, binItems) {
        when (selectedFilter) {
            QueueFilterChip.PENDING -> emptyList()
            QueueFilterChip.FAVORITES -> favoritesSourceItems
            QueueFilterChip.ARCHIVE -> archivedItems
            QueueFilterChip.BIN -> binItems
            else -> items
        }
    }
    val archivedItemIds = remember(archivedItems) { archivedItems.mapTo(hashSetOf()) { it.itemId } }
    val filteredItems = remember(activeItems, selectedFilter, searchNeedle, normalizedSearchNeedle) {
        activeItems.filter { item ->
            val matchesSearch = if (searchNeedle.isBlank()) {
                true
            } else {
                listOf(
                    item.title.orEmpty(),
                    item.host.orEmpty(),
                    item.url,
                ).any { candidate ->
                    val lowered = candidate.lowercase()
                    lowered.contains(searchNeedle) || normalizeSearchText(lowered).contains(normalizedSearchNeedle)
                }
            }
            val matchesFilter = when (selectedFilter) {
                QueueFilterChip.ALL -> true
                QueueFilterChip.PENDING -> false
                QueueFilterChip.FAVORITES -> item.isFavorited
                QueueFilterChip.UNREAD -> item.furthestPercent <= 0
                QueueFilterChip.IN_PROGRESS -> item.furthestPercent in 1 until DONE_PERCENT_THRESHOLD
                QueueFilterChip.DONE -> item.furthestPercent >= DONE_PERCENT_THRESHOLD
                QueueFilterChip.ARCHIVE -> true
                QueueFilterChip.BIN -> true
            }
            matchesSearch && matchesFilter
        }
    }
    val displayedItems = remember(filteredItems, selectedSort) {
        when (selectedSort) {
            QueueSortOption.NEWEST -> filteredItems.sortedByDescending { it.createdAt ?: "" }
            QueueSortOption.OLDEST -> filteredItems.sortedBy { it.createdAt ?: "" }
            QueueSortOption.PROGRESS_HIGH -> filteredItems.sortedByDescending { it.furthestPercent }
            QueueSortOption.PROGRESS_LOW -> filteredItems.sortedBy { it.furthestPercent }
            QueueSortOption.TITLE_AZ -> filteredItems.sortedBy { (it.title ?: it.url).lowercase() }
        }
    }
    val rowStateMarkers = remember(
        playbackState.currentItemId,
        nowPlayingSession?.currentItem?.itemId,
        playbackState.isSpeaking,
        playbackState.isAutoPlaying,
        playbackState.autoPlayAfterLoad,
    ) {
        resolveQueueRowStateMarkers(
            engineCurrentItemId = playbackState.currentItemId,
            sessionCurrentItemId = nowPlayingSession?.currentItem?.itemId,
            isSpeaking = playbackState.isSpeaking,
            isAutoPlaying = playbackState.isAutoPlaying,
            autoPlayAfterLoad = playbackState.autoPlayAfterLoad,
        )
    }
    val activePlayingItemId = rowStateMarkers.playingItemId
    val readyResumeItemId = rowStateMarkers.readyResumeItemId
    val visibleProjectedPendingItems = remember(selectedFilter, pendingManualSaves, searchQuery) {
        when (selectedFilter) {
            QueueFilterChip.PENDING -> pendingManualSaves.filter { pending ->
                pendingMatchesSearch(pending, searchQuery)
            }
            else -> emptyList()
        }
    }
    val hasVisibleQueueContent = displayedItems.isNotEmpty() || visibleProjectedPendingItems.isNotEmpty()
    val pullRefreshProgress = (pullRefreshDistancePx / pullRefreshThresholdPx).coerceIn(0f, 1f)
    val emptyStateMessage = when {
        selectedFilter == QueueFilterChip.PENDING -> null
        loading -> null
        !hasVisibleQueueContent && offline && settings.selectedPlaylistId != null ->
            "Offline. Can't refresh \"$selectedPlaylistName\" right now."
        !hasVisibleQueueContent && offline ->
            "Offline. Can't refresh Smart Queue right now."
        !hasVisibleQueueContent && settings.selectedPlaylistId != null -> "No items yet in \"$selectedPlaylistName\"."
        !hasVisibleQueueContent -> "No items in Smart queue yet. Share a link to add one."
        displayedItems.isEmpty() && searchQuery.isNotBlank() ->
            "No results for \"$searchQuery\" in $selectedPlaylistName."
        displayedItems.isEmpty() && selectedFilter == QueueFilterChip.ARCHIVE ->
            "Archive is empty."
        displayedItems.isEmpty() && selectedFilter == QueueFilterChip.BIN ->
            "Bin is empty. Items stay in Bin for 14 days unless purged earlier."
        displayedItems.isEmpty() && selectedFilter != QueueFilterChip.ALL && selectedFilter != QueueFilterChip.PENDING ->
            "No items match the ${selectedFilter.label.lowercase()} filter."
        displayedItems.isEmpty() -> "No items match the current search/filter."
        else -> null
    }
    suspend fun refreshQueueContent() {
        if (refreshActionState == RefreshActionVisualState.Refreshing) return
        refreshActionState = RefreshActionVisualState.Refreshing
        val result = when (selectedFilter) {
            QueueFilterChip.ARCHIVE -> vm.loadArchivedItems()
            QueueFilterChip.BIN -> vm.loadBinItems()
            QueueFilterChip.PENDING -> vm.loadQueueOnce(forceAutoDownloadAllVisibleUncached = true)
            else -> vm.loadQueueOnce(forceAutoDownloadAllVisibleUncached = true)
        }
        hasRefreshProblem = result.isFailure
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
    val pullToRefreshConnection = remember(
        listState,
        refreshActionState,
        pullRefreshMaxPx,
        pullRefreshThresholdPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (refreshActionState == RefreshActionVisualState.Refreshing) return Offset.Zero
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!isAtTop) return Offset.Zero

                val nextDistance = (pullRefreshDistancePx + available.y).coerceIn(0f, pullRefreshMaxPx)
                val consumedY = nextDistance - pullRefreshDistancePx
                if (consumedY == 0f) return Offset.Zero
                pullRefreshDistancePx = nextDistance
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (
                    refreshActionState != RefreshActionVisualState.Refreshing &&
                    pullRefreshDistancePx >= pullRefreshThresholdPx
                ) {
                    actionScope.launch { refreshQueueContent() }
                }
                pullRefreshDistancePx = 0f
                return Velocity.Zero
            }
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

    LaunchedEffect(displayedItems, pendingFocusId) {
        if (pendingFocusId <= 0) return@LaunchedEffect
        val index = displayedItems.indexOfFirst { it.itemId == pendingFocusId }
        if (index >= 0) {
            val focusedItemId = pendingFocusId
            listState.animateScrollToItem(index)
            vm.consumePendingQueueFocusItemId(focusedItemId)
            pendingFocusId = -1
        }
    }

    LaunchedEffect(
        displayedItems,
        visibleProjectedPendingItems,
        pendingFocusId,
        selectedFilter,
        selectedSort,
        searchQuery,
        queueScrollState,
    ) {
        if (initialUpNextRestoreHandled) return@LaunchedEffect
        val currentIds = displayedItems.map { it.itemId }
        if (currentIds.isEmpty()) return@LaunchedEffect
        if (pendingFocusId > 0) return@LaunchedEffect
        val isNormalUpNextView = selectedFilter == QueueFilterChip.ALL &&
            selectedSort == QueueSortOption.NEWEST &&
            searchQuery.isBlank()
        if (!isNormalUpNextView) {
            previousDisplayedItemIds = currentIds
            previousProjectedPendingIds = visibleProjectedPendingItems.map { it.id }
            initialUpNextRestoreHandled = true
            return@LaunchedEffect
        }
        val restore = resolveUpNextRestorePosition(
            currentDisplayedItemIds = currentIds,
            savedIndex = queueScrollState.index,
            savedOffset = queueScrollState.offset,
            savedAnchorItemId = queueScrollState.anchorItemId,
        )
        previousDisplayedItemIds = currentIds
        previousProjectedPendingIds = visibleProjectedPendingItems.map { it.id }
        initialUpNextRestoreHandled = true
        if (restore == null) {
            vm.clearQueueScrollState()
            return@LaunchedEffect
        }
        if (restore.index == 0 && restore.offset == 0) return@LaunchedEffect
        suppressAutoScrollToTopOnce = true
        listState.scrollToItem(restore.index, restore.offset)
    }

    LaunchedEffect(listState, displayedItems, selectedFilter, selectedSort, searchQuery) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val isNormalUpNextView = selectedFilter == QueueFilterChip.ALL &&
                    selectedSort == QueueSortOption.NEWEST &&
                    searchQuery.isBlank()
                if (!isNormalUpNextView || displayedItems.isEmpty()) return@collect
                val safeIndex = index.coerceIn(0, displayedItems.lastIndex)
                vm.setQueueScrollState(
                    index = safeIndex,
                    offset = offset,
                    anchorItemId = displayedItems.getOrNull(safeIndex)?.itemId,
                )
            }
    }

    LaunchedEffect(visibleProjectedPendingItems) {
        val currentIds = visibleProjectedPendingItems.map { it.id }
        if (!initialUpNextRestoreHandled) {
            previousProjectedPendingIds = currentIds
            return@LaunchedEffect
        }
        val previousIds = previousProjectedPendingIds.toHashSet()
        val hasNewProjectedPending = currentIds.any { it !in previousIds }
        if (hasNewProjectedPending && currentIds.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        previousProjectedPendingIds = currentIds
    }

    LaunchedEffect(displayedItems, pendingFocusId, searchQuery, selectedFilter, selectedSort) {
        val currentIds = displayedItems.map { it.itemId }
        if (!initialUpNextRestoreHandled) {
            previousDisplayedItemIds = currentIds
            return@LaunchedEffect
        }
        if (suppressAutoScrollToTopOnce) {
            suppressAutoScrollToTopOnce = false
            previousDisplayedItemIds = currentIds
            return@LaunchedEffect
        }
        val shouldScroll = shouldAutoScrollToTopForNewItems(
            previousDisplayedItemIds = previousDisplayedItemIds,
            currentDisplayedItemIds = currentIds,
            pendingFocusId = pendingFocusId,
            hasSearchQuery = searchQuery.isNotBlank(),
            isDefaultFilterAndSort = selectedFilter == QueueFilterChip.ALL && selectedSort == QueueSortOption.NEWEST,
        )
        if (shouldScroll) {
            listState.animateScrollToItem(0)
        }
        previousDisplayedItemIds = currentIds
    }

    LaunchedEffect(upNextTabTapSignal, displayedItems, activePlayingItemId) {
        if (upNextTabTapSignal == lastHandledUpNextTapSignal) return@LaunchedEffect
        lastHandledUpNextTapSignal = upNextTabTapSignal
        if (displayedItems.isEmpty()) return@LaunchedEffect
        if (nextUpNextTapScrollTargetTop) {
            listState.animateScrollToItem(0)
            nextUpNextTapScrollTargetTop = false
            return@LaunchedEffect
        }
        val activeIndex = activePlayingItemId?.let { id ->
            displayedItems.indexOfFirst { item -> item.itemId == id }.takeIf { it >= 0 }
        }
        if (activeIndex != null) {
            listState.animateScrollToItem(activeIndex)
            nextUpNextTapScrollTargetTop = true
        } else {
            listState.animateScrollToItem(0)
            nextUpNextTapScrollTargetTop = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Source: $selectedPlaylistName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ActionHintTooltip(label = "Archive") {
                            IconButton(onClick = { selectedFilter = QueueFilterChip.ARCHIVE }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_archive_box_24),
                                    contentDescription = "Show Archive",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        ActionHintTooltip(label = if (searchExpanded) "Close search" else "Search") {
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.msr_search_24),
                                    contentDescription = if (searchExpanded) "Close search" else "Search queue",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        ActionHintTooltip(label = "Refresh") {
                            RefreshActionButton(
                                state = refreshActionState,
                                showConnectivityIssue = offline || hasRefreshProblem,
                                onClick = {
                                    actionScope.launch { refreshQueueContent() }
                                },
                                contentDescription = "Refresh queue and sync progress",
                                pullProgress = pullRefreshProgress,
                            )
                        }
                        ActionHintTooltip(label = "Save") {
                            IconButton(
                                enabled = !manualSaveInProgress,
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
                        ActionHintTooltip(label = "Switch queue") {
                            Box {
                                IconButton(onClick = { playlistMenuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.msr_list_layers_24),
                                        contentDescription = "Switch queue",
                                        modifier = Modifier.size(24.dp),
                                    )
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
                        }
                        ActionHintTooltip(label = "Sort") {
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.msr_sort_layers_24),
                                        contentDescription = "Sort queue: ${selectedSort.label}",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                ) {
                                    QueueSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedSort = option
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        ActionHintTooltip(label = "Queue actions") {
                            Box {
                                IconButton(onClick = { topActionsMenuExpanded = true }) {
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
                                    if (BuildConfig.DEBUG) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (showQueueFetchDebug) {
                                                    "Hide debug fetch"
                                                } else {
                                                    "Show debug fetch"
                                                },
                                            )
                                        },
                                        onClick = {
                                            showQueueFetchDebug = !showQueueFetchDebug
                                            topActionsMenuExpanded = false
                                        },
                                    )
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
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
        if (BuildConfig.DEBUG && showQueueFetchDebug && lastQueueFetchDebug.statusCode != null) {
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
                    Text("Debug queue fetch")
                    Text(
                        text = "playlistId=${lastQueueFetchDebug.selectedPlaylistId ?: "smart"} status=${lastQueueFetchDebug.statusCode} responseCount=${lastQueueFetchDebug.responseItemCount} responseContains409=${lastQueueFetchDebug.responseContains409}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "appliedCount=${lastQueueFetchDebug.appliedItemCount} appliedContains409=${lastQueueFetchDebug.appliedContains409} bytes=${lastQueueFetchDebug.responseBytes} hash=${lastQueueFetchDebug.responseHash}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "at=${lastQueueFetchDebug.lastFetchAt}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lastQueueFetchDebug.requestUrl,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "renderedIds=${displayedItems.take(3).joinToString { it.itemId.toString() }}${if (displayedItems.size > 6) " … " else ""}${displayedItems.takeLast(3).joinToString { it.itemId.toString() }}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (searchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Search Up Next") },
                )
                TextButton(
                    onClick = {
                        searchQuery = ""
                        searchExpanded = false
                    },
                ) {
                    Text("Close")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueFilterChip.entries.forEach { chip ->
                FilterChip(
                    selected = selectedFilter == chip,
                    onClick = { selectedFilter = chip },
                    enabled = chip.enabled,
                    label = { Text(chip.label) },
                )
            }
        }
        nowPlayingSession?.let { session ->
            NowPlayingSessionPanel(
                session = session,
                seededFromLabel = sessionSeedPresentation?.seededFromLabel ?: "Unknown source",
                currentSourceLabel = sessionSeedPresentation?.currentSourceLabel ?: selectedPlaylistName,
                onOpenItem = { itemId -> onOpenPlayer(itemId) },
                onMoveItemUp = { index ->
                    vm.reorderNowPlayingSessionItem(
                        fromIndex = index,
                        toIndex = index - 1,
                    )
                },
                onMoveItemDown = { index ->
                    vm.reorderNowPlayingSessionItem(
                        fromIndex = index,
                        toIndex = index + 1,
                    )
                },
                onRemoveItem = { itemId -> vm.removeItemFromSession(itemId) },
                onClearSession = { vm.clearNowPlayingSession() },
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
            )
        }
        Text(
            text = queueFeedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f, fill = true)
                .nestedScroll(pullToRefreshConnection),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (pullRefreshDistancePx > 0f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pullRefreshDistancePx.toDp() }),
                )
            }
            emptyStateMessage?.let { message ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LaunchedEffect(listState, queueHasMorePages, queueReloadGeneration) {
                if (BuildConfig.DEBUG) Log.d("MimeoQueueFetch", "scroll-trigger LaunchedEffect started hasMore=$queueHasMorePages")
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleIndex to totalItems
                }
                    .distinctUntilChanged()
                    .collect { (lastVisible, total) ->
                        if (BuildConfig.DEBUG) Log.d("MimeoQueueFetch", "scroll pos: last=$lastVisible total=$total hasMore=$queueHasMorePages threshold=${total - 5}")
                        if (queueHasMorePages && total > 0 && lastVisible >= total - 5) {
                            vm.loadMoreQueueItems()
                        }
                    }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(
                    items = visibleProjectedPendingItems,
                    key = { _, item -> "pending-${item.id}" },
                ) { index, item ->
                    Column(modifier = Modifier) {
                        PendingProjectedQueueItemCard(
                            item = item,
                            retryInProgress = pendingManualRetryInProgress,
                            onRetry = { retryPendingItem(item) },
                            onDismiss = { vm.removePendingManualSave(item.id) },
                            onTap = {
                                if (isPendingFailureState(item.lastFailureMessage)) {
                                    onShowSnackbar(item.lastFailureMessage, null, null)
                                }
                            },
                        )
                        if (index < visibleProjectedPendingItems.lastIndex || displayedItems.isNotEmpty()) {
                            ThinQueueDivider()
                        }
                    }
                }
                itemsIndexed(
                    items = displayedItems,
                    key = { _, item -> item.itemId },
                ) { index, item ->
                    Column(modifier = Modifier) {
                        AnimatedVisibility(
                            visible = item.itemId !in collapsingArchivedItemIds,
                            exit = shrinkVertically(animationSpec = tween(durationMillis = 220)) +
                                fadeOut(animationSpec = tween(durationMillis = 180)),
                        ) {
                            QueueItemCard(
                                item = item,
                                isBinView = selectedFilter == QueueFilterChip.BIN,
                                isArchiveView = selectedFilter == QueueFilterChip.ARCHIVE,
                                isArchivedItem = archivedItemIds.contains(item.itemId),
                                cached = cachedItemIds.contains(item.itemId),
                                noActiveContent = noActiveContentItemIds.contains(item.itemId),
                                failedProcessing = hasFailedPendingProjectionStatus(item),
                                isActivePlayingItem = item.itemId == activePlayingItemId,
                                isReadyResumeItem = item.itemId == readyResumeItemId,
                                showQueueCaptureMetadata = settings.showQueueCaptureMetadata,
                                onOpenPlayer = {
                                    Log.d(
                                        LOCUS_CONTINUATION_DEBUG_TAG,
                                        "queue.openPlayer tapped=${item.itemId} sort=${selectedSort.name} " +
                                            "playlist=${settings.selectedPlaylistId ?: "smart"} " +
                                            "displayedHead=${displayedItems.take(8).joinToString { it.itemId.toString() }} " +
                                            "sessionBefore=${vm.currentNowPlayingItemId()}",
                                    )
                                    vm.warmItemTextForPlayer(item.itemId)
                                    val itemAlreadyInSession = nowPlayingSession?.items?.any { it.itemId == item.itemId } == true
                                    if (!itemAlreadyInSession &&
                                        shouldStartNewSessionOnQueueOpen(
                                            tappedItemId = item.itemId,
                                            sessionCurrentItemId = nowPlayingSession?.currentItem?.itemId ?: -1,
                                            playbackActive = playbackState.isSpeaking || playbackState.isAutoPlaying,
                                        )
                                    ) {
                                        vm.startNowPlayingSession(
                                            startItemId = item.itemId,
                                            orderedQueueItems = displayedItems,
                                        )
                                    }
                                    onOpenPlayer(item.itemId)
                                },
                                onDownload = {
                                    actionScope.launch {
                                        if (cachedItemIds.contains(item.itemId)) {
                                            onShowSnackbar("Already available offline", null, null)
                                            return@launch
                                        }
                                        val result = vm.downloadItemForOffline(item.itemId)
                                        if (result.isSuccess) {
                                            onShowSnackbar("Offline cache ready", null, null)
                                        } else if (result.exceptionOrNull()?.let(::isNoActiveContentError) == true) {
                                            onShowSnackbar("Unavailable offline for this item", null, null)
                                        } else {
                                            onShowSnackbar("Couldn't cache offline right now", null, null)
                                        }
                                    }
                                },
                                onOpenPlaylistPicker = {
                                    vm.refreshPlaylists()
                                    playlistPickerItem = item
                                },
                                onArchive = {
                                    actionScope.launch {
                                        if (item.itemId in collapsingArchivedItemIds) return@launch
                                        collapsingArchivedItemIds = collapsingArchivedItemIds + item.itemId
                                        delay(220)
                                        suppressAutoScrollToTopOnce = true
                                        vm.archiveItem(
                                            item.itemId,
                                            refreshQueue = false,
                                            source = ArchiveActionSource.UP_NEXT,
                                        )
                                            .onSuccess {
                                                collapsingArchivedItemIds = collapsingArchivedItemIds - item.itemId
                                                onShowSnackbar("Archived", "Undo", ACTION_KEY_UNDO_ARCHIVE)
                                            }
                                            .onFailure {
                                                collapsingArchivedItemIds = collapsingArchivedItemIds - item.itemId
                                                suppressAutoScrollToTopOnce = false
                                                onShowSnackbar("Couldn't archive item", "Diagnostics", "open_diagnostics")
                                            }
                                        }
                                },
                                onMoveToBin = {
                                    actionScope.launch {
                                        if (item.itemId in collapsingArchivedItemIds) return@launch
                                        collapsingArchivedItemIds = collapsingArchivedItemIds + item.itemId
                                        delay(220)
                                        suppressAutoScrollToTopOnce = true
                                        vm.moveItemToBin(
                                            item.itemId,
                                            refreshQueue = false,
                                            source = ArchiveActionSource.UP_NEXT,
                                        )
                                            .onSuccess {
                                                collapsingArchivedItemIds = collapsingArchivedItemIds - item.itemId
                                                onShowSnackbar("Moved to Bin (14 days)", "Undo", ACTION_KEY_UNDO_ARCHIVE)
                                            }
                                            .onFailure {
                                                collapsingArchivedItemIds = collapsingArchivedItemIds - item.itemId
                                                suppressAutoScrollToTopOnce = false
                                                onShowSnackbar("Couldn't move item to Bin", "Diagnostics", "open_diagnostics")
                                            }
                                    }
                                },
                                onToggleFavorite = {
                                    actionScope.launch {
                                        vm.setItemFavorited(item.itemId, favorited = !item.isFavorited)
                                            .onSuccess {
                                                val message = if (item.isFavorited) "Removed from favourites" else "Added to favourites"
                                                onShowSnackbar(message, null, null)
                                            }
                                            .onFailure {
                                                onShowSnackbar("Couldn't update favourite", "Diagnostics", "open_diagnostics")
                                            }
                                    }
                                },
                                onRestoreFromBin = {
                                    actionScope.launch {
                                        vm.restoreItemFromBin(item.itemId)
                                            .onSuccess {
                                                onShowSnackbar("Restored from Bin", null, null)
                                            }
                                            .onFailure {
                                                onShowSnackbar("Couldn't restore from Bin", "Diagnostics", "open_diagnostics")
                                            }
                                    }
                                },
                                onUnarchive = {
                                    actionScope.launch {
                                        vm.unarchiveItem(item.itemId)
                                            .onSuccess {
                                                onShowSnackbar("Unarchived", null, null)
                                            }
                                            .onFailure {
                                                onShowSnackbar("Couldn't unarchive item", "Diagnostics", "open_diagnostics")
                                            }
                                    }
                                },
                                onPurgeFromBin = {
                                    actionScope.launch {
                                        vm.purgeItemFromBin(item.itemId)
                                            .onSuccess {
                                                onShowSnackbar("Permanently deleted", null, null)
                                            }
                                            .onFailure {
                                                onShowSnackbar("Couldn't purge item", "Diagnostics", "open_diagnostics")
                                            }
                                    }
                                },
                                onPlayNext = {
                                    vm.playNext(item.itemId)
                                },
                                onPlayLast = {
                                    vm.playLast(item.itemId)
                                },
                                onShareUrl = {
                                    shareItemUrl(context, item.url, item.title)
                                },
                                onOpenInBrowser = {
                                    openItemInBrowser(context, item.url)
                                },
                                isMenuExpanded = rowMenuItemId == item.itemId,
                                onDismissMenu = { rowMenuItemId = -1 },
                                onExpandMenu = { rowMenuItemId = item.itemId },
                            )
                        }
                        if (index < displayedItems.lastIndex) {
                            ThinQueueDivider()
                        }
                    }
                }
                if (loadingMore) {
                    item(key = "queue-load-more-footer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

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
                            playlistPickerItem = null
                            onShowSnackbar("$verb ${choice.playlistName}", null, null)
                        }
                        .onFailure { error ->
                            playlistPickerItem = null
                            onShowSnackbar(
                                friendlyPlaylistError(error),
                                "Diagnostics",
                                "open_diagnostics",
                            )
                        }
                }
            },
        )
    }

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

@Composable
private fun ThinQueueDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    )
}

private fun normalizeSearchText(value: String): String {
    return value.filter { it.isLetterOrDigit() }
}

private fun pendingMatchesSearch(item: PendingManualSaveItem, query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim().lowercase()
    val normalizedNeedle = normalizeSearchText(needle)
    val title = item.titleInput.orEmpty()
    val body = item.bodyInput.orEmpty()
    val url = item.urlInput
    return listOf(title, body, url).any { candidate ->
        val lowered = candidate.lowercase()
        lowered.contains(needle) || normalizeSearchText(lowered).contains(normalizedNeedle)
    }
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
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingProjectedQueueItemCard(
    item: PendingManualSaveItem,
    retryInProgress: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
) {
    var actionsMenuExpanded by remember { mutableStateOf(false) }
    val hostLabel = resolvePendingHost(item.urlInput)
    val subLabel = hostLabel
    val primaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
    val failedProcessing = isPendingFailureState(item.lastFailureMessage)
    val resolvedAwaitingCache = item.resolvedItemId != null && !failedProcessing
    val statusText = when {
        failedProcessing -> "Processing failed"
        resolvedAwaitingCache -> "Caching offline..."
        else -> "Pending save..."
    }
    val statusTint = if (failedProcessing) {
        MaterialTheme.colorScheme.error
    } else {
        secondaryTextColor
    }
    val titleLine = when {
        !item.titleInput.isNullOrBlank() -> item.titleInput
        item.urlInput.isNotBlank() -> item.urlInput
        item.type == PendingManualSaveType.TEXT -> "Pasted text"
        else -> "(pending save)"
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = failedProcessing, onClick = onTap),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = titleLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        enabled = !retryInProgress,
                        onClick = { actionsMenuExpanded = true },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Pending item actions",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = actionsMenuExpanded,
                        onDismissRequest = { actionsMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            onClick = {
                                onRetry()
                                actionsMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Dismiss") },
                            onClick = {
                                onDismiss()
                                actionsMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = secondaryTextColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(modifier = Modifier.size(6.dp))
                Icon(
                    painter = painterResource(
                        id = if (failedProcessing) R.drawable.msr_error_circle_24 else R.drawable.msr_sync_problem_24,
                    ),
                    contentDescription = if (failedProcessing) {
                        "Pending save failed"
                    } else {
                        "Saved and waiting for offline cache"
                    },
                    tint = statusTint,
                    modifier = Modifier
                        .size(16.dp),
                )
            }
        }
    }
}

private fun resolvePendingHost(urlInput: String): String {
    val extracted = extractFirstHttpUrl(urlInput)?.trim().orEmpty()
    if (extracted.isEmpty()) return "Pending"
    return runCatching {
        URI(extracted).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "Pending"
}

private fun hasFailedPendingProjectionStatus(queueItem: PlaybackQueueItem): Boolean {
    return isTerminalPendingProcessingStatus(queueItem.status)
}

private fun isPendingFailureState(message: String): Boolean {
    return isPendingProcessingFailureMessage(message)
}

internal fun queueCaptureStrategyLabel(strategyUsed: String?): String? {
    val normalized = strategyUsed
        ?.trim()
        ?.lowercase()
        ?.replace('_', ' ')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val humanized = normalized.split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
    return "Capture: $humanized"
}

internal fun queueSourceMetadataLine(
    source: String,
    captureStrategyLabel: String?,
    showQueueCaptureMetadata: Boolean,
): String {
    return if (showQueueCaptureMetadata && !captureStrategyLabel.isNullOrBlank()) {
        "$source  •  $captureStrategyLabel"
    } else {
        source
    }
}

internal fun queueProgressIconRes(
    progress: Int,
    isDone: Boolean,
    noActiveContent: Boolean,
    failedProcessing: Boolean,
): Int {
    return when {
        noActiveContent || failedProcessing -> R.drawable.msr_error_circle_24
        isDone -> R.drawable.ic_book_closed_24
        progress <= 0 -> R.drawable.ic_book_closed_plain_24
        else -> R.drawable.ic_book_open_24
    }
}

internal fun queueProgressIconDescription(
    progress: Int,
    isDone: Boolean,
    noActiveContent: Boolean,
    failedProcessing: Boolean,
): String {
    return when {
        noActiveContent -> "Not available offline"
        failedProcessing -> "Processing failed"
        isDone -> "Done"
        progress <= 0 -> "Unread"
        else -> "In progress"
    }
}

internal fun queueOfflineStateLabel(
    progress: Int,
    cached: Boolean,
    noActiveContent: Boolean,
    failedProcessing: Boolean,
): String {
    return when {
        failedProcessing -> "Processing failed"
        noActiveContent -> "Unavailable offline"
        else -> "$progress%"
    }
}

internal fun queueDownloadMenuLabel(
    noActiveContent: Boolean,
    failedProcessing: Boolean,
): String {
    return when {
        noActiveContent || failedProcessing -> "Retry offline cache"
        else -> "Download for offline"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItemCard(
    item: PlaybackQueueItem,
    isBinView: Boolean,
    isArchiveView: Boolean,
    isArchivedItem: Boolean,
    cached: Boolean,
    noActiveContent: Boolean,
    failedProcessing: Boolean,
    isActivePlayingItem: Boolean,
    isReadyResumeItem: Boolean,
    showQueueCaptureMetadata: Boolean,
    onOpenPlayer: () -> Unit,
    onDownload: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onArchive: () -> Unit,
    onMoveToBin: () -> Unit,
    onRestoreFromBin: () -> Unit,
    onUnarchive: () -> Unit,
    onPurgeFromBin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onShareUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onExpandMenu: () -> Unit,
) {
    val capturePresentation = queueCapturePresentation(item)
    val title = capturePresentation.title
    val source = capturePresentation.sourceLabel ?: "Unknown source"
    val hasUrl = item.url.isNotBlank()
    val progress = item.progressPercent
    val isDone = item.furthestPercent >= DONE_PERCENT_THRESHOLD
    val captureStrategyLabel = queueCaptureStrategyLabel(item.strategyUsed)
    val sourceLine = queueSourceMetadataLine(
        source = source,
        captureStrategyLabel = captureStrategyLabel,
        showQueueCaptureMetadata = showQueueCaptureMetadata,
    )
    val progressIconRes = queueProgressIconRes(
        progress = progress,
        isDone = isDone,
        noActiveContent = noActiveContent && !cached,
        failedProcessing = failedProcessing,
    )
    val progressIconDescription = queueProgressIconDescription(
        progress = progress,
        isDone = isDone,
        noActiveContent = noActiveContent && !cached,
        failedProcessing = failedProcessing,
    )
    val primaryTextColor = if (cached) {
        MaterialTheme.colorScheme.onSurface
    } else if (noActiveContent || failedProcessing) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    }
    val secondaryTextColor = if (cached) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (noActiveContent || failedProcessing) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
    }
    val activeIndicatorColor = MaterialTheme.colorScheme.primary

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActivePlayingItem) {
                    Modifier.border(
                        width = 1.dp,
                        color = activeIndicatorColor.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                enabled = !isBinView,
                onClick = onOpenPlayer,
                onLongClick = onExpandMenu,
            ),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onExpandMenu,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_more_vert_24),
                            contentDescription = "Item actions",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMenu,
                    ) {
                        if (isBinView) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                onClick = {
                                    onDismissMenu()
                                    onRestoreFromBin()
                                },
                            )
                            if (hasUrl) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    onClick = {
                                        onDismissMenu()
                                        onOpenInBrowser()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share URL") },
                                    onClick = {
                                        onDismissMenu()
                                        onShareUrl()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Purge permanently") },
                                onClick = {
                                    onDismissMenu()
                                    onPurgeFromBin()
                                },
                            )
                        } else if (isArchiveView) {
                            DropdownMenuItem(
                                text = { Text(if (item.isFavorited) "Unfavourite" else "Favourite") },
                                onClick = {
                                    onDismissMenu()
                                    onToggleFavorite()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Unarchive") },
                                onClick = {
                                    onDismissMenu()
                                    onUnarchive()
                                },
                            )
                            if (hasUrl) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    onClick = {
                                        onDismissMenu()
                                        onOpenInBrowser()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share URL") },
                                    onClick = {
                                        onDismissMenu()
                                        onShareUrl()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Move to Bin (14 days)") },
                                onClick = {
                                    onDismissMenu()
                                    onMoveToBin()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    onDismissMenu()
                                    onPlayNext()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Play Last") },
                                onClick = {
                                    onDismissMenu()
                                    onPlayLast()
                                },
                            )
                            if (!cached) {
                                DropdownMenuItem(
                                    text = {
                                        Text(queueDownloadMenuLabel(noActiveContent, failedProcessing))
                                    },
                                    onClick = {
                                        onDismissMenu()
                                        onDownload()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Playlists...") },
                                onClick = {
                                    onDismissMenu()
                                    onOpenPlaylistPicker()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (item.isFavorited) "Unfavourite" else "Favourite") },
                                onClick = {
                                    onDismissMenu()
                                    onToggleFavorite()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = {
                                    onDismissMenu()
                                    onArchive()
                                },
                            )
                            if (hasUrl) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    onClick = {
                                        onDismissMenu()
                                        onOpenInBrowser()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share URL") },
                                    onClick = {
                                        onDismissMenu()
                                        onShareUrl()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Move to Bin (14 days)") },
                                onClick = {
                                    onDismissMenu()
                                    onMoveToBin()
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = sourceLine,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = if ((noActiveContent || failedProcessing) && !cached) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                    } else {
                        secondaryTextColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActivePlayingItem) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_play_arrow_24),
                            contentDescription = "Currently playing",
                            tint = activeIndicatorColor,
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (isReadyResumeItem) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_play_arrow_24),
                            contentDescription = "Ready to resume",
                            tint = activeIndicatorColor.copy(alpha = 0.66f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = queueOfflineStateLabel(progress, cached, noActiveContent, failedProcessing),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                    )
                    if (isArchivedItem) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_archive_box_24),
                            contentDescription = "Archived",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Icon(
                        painter = painterResource(id = progressIconRes),
                        contentDescription = progressIconDescription,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp),
                    )
                    if (item.isFavorited) {
                        Text(
                            text = "\u2665",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
    onMoveItemUp: (Int) -> Unit,
    onMoveItemDown: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearSession: () -> Unit,
    reseedEnabled: Boolean,
    onReseed: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val showCurrentSource = seededFromLabel != currentSourceLabel

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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
            TextButton(
                enabled = reseedEnabled,
                onClick = onReseed,
            ) {
                Text(
                    text = "Re-seed",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(
                onClick = onClearSession,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear session queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse session queue" else "Expand session queue",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 192.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                session.items.forEachIndexed { index, item ->
                    val isCurrent = index == session.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(item.itemId) }
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50),
                                ),
                        )
                        Text(
                            text = item.title?.ifBlank { null } ?: item.host ?: item.url,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMoveItemUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up in session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        IconButton(
                            onClick = { onMoveItemDown(index) },
                            enabled = index < session.items.lastIndex,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down in session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (!isCurrent) {
                            IconButton(
                                onClick = { onRemoveItem(item.itemId) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from session",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    if (index < session.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
