package com.mimeo.android.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.absoluteCharOffset
import com.mimeo.android.model.calculateCanonicalPercent
import com.mimeo.android.model.positionFromAbsoluteOffset
import com.mimeo.android.player.TtsChunkDoneEvent
import com.mimeo.android.player.TtsChunkProgressEvent
import com.mimeo.android.player.TtsController
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import com.mimeo.android.ui.reader.ReaderBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DEBUG_PLAYBACK = false
private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
private const val FALLBACK_CHUNK_MAX_CHARS = 900
private const val DONE_PERCENT_THRESHOLD = 98
private val READER_CHROME_TOP_OFFSET = 56.dp
private val PLAYBACK_SPEED_OPTIONS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

private fun debugLog(message: String) {
    if (DEBUG_PLAYBACK) {
        println("[Mimeo][player] $message")
    }
}

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    initialItemId: Int,
    requestedItemId: Int? = null,
    startExpanded: Boolean = false,
    locusTapSignal: Int = 0,
    onOpenItem: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
    stopPlaybackOnDispose: Boolean = false,
    compactControlsOnly: Boolean = false,
    showCompactControls: Boolean = true,
    controlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    chevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.RIGHT,
    onControlsModeChange: (PlayerControlsMode) -> Unit = {},
    onPlaybackActiveChange: (Boolean) -> Unit = {},
    onPlaybackProgressPercentChange: (Int) -> Unit = {},
    onReaderChromeVisibilityChange: (Boolean) -> Unit = {},
    onChevronSnapChange: (PlayerChevronSnapEdge) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var currentItemId by rememberSaveable { mutableIntStateOf(initialItemId) }
    var resolvedInitial by rememberSaveable { mutableStateOf(false) }
    var reloadNonce by rememberSaveable { mutableIntStateOf(0) }
    var textPayload by remember { mutableStateOf<ItemTextResponse?>(null) }
    var usingCachedText by remember { mutableStateOf(false) }
    var chunks by remember { mutableStateOf<List<PlaybackChunk>>(emptyList()) }
    var uiMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isAutoPlaying by remember { mutableStateOf(false) }
    var pendingDoneEvent by remember { mutableStateOf<PlaybackDoneEvent?>(null) }
    var lastHandledDoneUtteranceId by remember { mutableStateOf<String?>(null) }
    var autoPlayAfterLoad by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var preserveVisibleContentOnReload by remember { mutableStateOf(false) }
    var localDonePercentOverride by rememberSaveable(initialItemId) { mutableIntStateOf(-1) }
    val readerScrollState = rememberSaveable(currentItemId, saver = ScrollState.Saver) { ScrollState(0) }
    var activeChunkRange by remember { mutableStateOf<IntRange?>(null) }
    var readerScrollTriggerSignal by rememberSaveable { mutableIntStateOf(0) }
    var lastHandledLocusTapSignal by rememberSaveable { mutableIntStateOf(locusTapSignal) }
    var lastProgressSyncAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedPercent by remember { mutableIntStateOf(-1) }
    var lastSyncedAbsoluteChars by remember { mutableIntStateOf(-1) }
    var lastObservedPercent by remember { mutableIntStateOf(-1) }
    var nearEndForcedForItemId by remember { mutableIntStateOf(-1) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var readerModeEnabled by rememberSaveable { mutableStateOf(false) }
    val playbackPositionByItem by vm.playbackPositionByItem.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val syncBadgeState by vm.progressSyncBadgeState.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val settings by vm.settings.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val currentPosition = playbackPositionByItem[currentItemId] ?: PlaybackPosition()
    val actionScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val isPhysicalDevice = remember { isLikelyPhysicalDevice() }
    val baseUrlHint = vm.baseUrlHintForDevice(isPhysicalDevice)
    val chevronSide = remember(chevronSnapEdge) {
        when (chevronSnapEdge) {
            PlayerChevronSnapEdge.LEFT -> PlayerChevronSnapEdge.LEFT
            else -> PlayerChevronSnapEdge.RIGHT
        }
    }
    val chevronDescription = when (controlsMode) {
        PlayerControlsMode.FULL -> "Collapse player controls"
        PlayerControlsMode.MINIMAL -> "Expand player controls. Long press to hide player controls"
        PlayerControlsMode.NUB -> "Show player controls"
    }
    val readerChromeHidden = !compactControlsOnly && isExpanded && readerModeEnabled
    val readerChromeScrollCompensationPx = with(density) { READER_CHROME_TOP_OFFSET.roundToPx() }

    val latestChunks by rememberUpdatedState(chunks)
    val latestItemId by rememberUpdatedState(currentItemId)
    val latestPosition by rememberUpdatedState(currentPosition)

    val ttsController = remember {
        TtsController(
            context = context,
            onChunkDone = { event: TtsChunkDoneEvent ->
                val currentChunks = latestChunks
                if (currentChunks.isEmpty()) return@TtsController
                if (event.itemId != latestItemId) return@TtsController
                if (event.chunkIndex != latestPosition.chunkIndex) {
                    debugLog("ignore stale onDone utterance=${event.utteranceId} eventChunk=${event.chunkIndex} currentChunk=${latestPosition.chunkIndex}")
                    return@TtsController
                }
                activeChunkRange = null
                pendingDoneEvent = PlaybackDoneEvent(
                    utteranceId = event.utteranceId,
                    itemId = event.itemId,
                    chunkIndex = event.chunkIndex,
                )
            },
            onChunkProgress = { event: TtsChunkProgressEvent ->
                val currentChunks = latestChunks
                if (currentChunks.isEmpty()) return@TtsController
                if (event.itemId != latestItemId) return@TtsController
                if (event.chunkIndex != latestPosition.chunkIndex) return@TtsController
                val safeIndex = event.chunkIndex.coerceIn(0, currentChunks.lastIndex)
                val safeOffset = event.absoluteOffsetInChunk.coerceIn(0, currentChunks[safeIndex].length)
                val safeRange = if (BuildConfig.DEBUG && settings.forceSentenceHighlightFallback) {
                    null
                } else {
                    event.activeRangeInChunk?.let { range ->
                        val start = range.first.coerceIn(0, currentChunks[safeIndex].length)
                        val endExclusive = (range.last + 1).coerceIn(0, currentChunks[safeIndex].length)
                        if (endExclusive > start) start until endExclusive else null
                    }
                }
                if (safeRange != activeChunkRange) {
                    activeChunkRange = safeRange
                }
                val currentOffset = latestPosition.offsetInChunkChars.coerceAtLeast(0)
                if (safeOffset > currentOffset) {
                    vm.setPlaybackPosition(
                        itemId = event.itemId,
                        chunkIndex = safeIndex,
                        offsetInChunkChars = safeOffset,
                    )
                }
            },
            onError = {
                isSpeaking = false
                isAutoPlaying = false
                uiMessage = "TTS unavailable. Retry playback."
            },
        )
    }

    fun normalizedChunkIndex(index: Int): Int {
        if (chunks.isEmpty()) return 0
        return index.coerceIn(0, chunks.lastIndex)
    }

    fun normalizedPosition(position: PlaybackPosition): PlaybackPosition {
        if (chunks.isEmpty()) return PlaybackPosition()
        val safeIndex = normalizedChunkIndex(position.chunkIndex)
        val safeOffset = position.offsetInChunkChars.coerceIn(0, chunks[safeIndex].length)
        return PlaybackPosition(chunkIndex = safeIndex, offsetInChunkChars = safeOffset)
    }

    fun totalCharsForPercent(): Int {
        val declared = textPayload?.totalChars ?: 0
        val chunkMax = chunks.maxOfOrNull { it.endChar } ?: 0
        if (declared > 0 && chunkMax > 0) return maxOf(declared, chunkMax)
        if (declared > 0) return declared
        if (chunkMax > 0) return chunkMax
        return textPayload?.text?.length ?: 0
    }

    fun positionForPercent(percent: Int): PlaybackPosition {
        if (chunks.isEmpty()) return PlaybackPosition()
        val boundedPercent = percent.coerceIn(0, 100)
        if (boundedPercent <= 0) return PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
        val total = totalCharsForPercent().coerceAtLeast(1)
        val targetAbsolute = ((total.toLong() * boundedPercent) / 100L).toInt().coerceIn(0, total)
        val idx = chunks.indexOfFirst { targetAbsolute <= it.endChar }.let { if (it >= 0) it else chunks.lastIndex }
        val chunk = chunks[idx]
        val offset = (targetAbsolute - chunk.startChar).coerceIn(0, chunk.length)
        return PlaybackPosition(chunkIndex = idx, offsetInChunkChars = offset)
    }

    fun setPlaybackPosition(chunkIndex: Int, offsetInChunkChars: Int) {
        if (chunks.isEmpty()) {
            vm.setPlaybackPosition(currentItemId, 0, 0)
            return
        }
        val safeIndex = normalizedChunkIndex(chunkIndex)
        val safeOffset = offsetInChunkChars.coerceIn(0, chunks[safeIndex].length)
        debugLog("setPosition item=$currentItemId chunk=$safeIndex offset=$safeOffset")
        vm.setPlaybackPosition(currentItemId, safeIndex, safeOffset)
    }

    fun setPlaybackPositionFromAbsoluteOffset(absoluteOffset: Int) {
        val mapped = positionFromAbsoluteOffset(
            totalChars = totalCharsForPercent(),
            chunks = chunks,
            absoluteOffset = absoluteOffset,
        )
        setPlaybackPosition(mapped.chunkIndex, mapped.offsetInChunkChars)
    }

    fun playChunk(chunkIndex: Int, offsetInChunkChars: Int) {
        if (chunks.isEmpty()) return
        val safeIndex = normalizedChunkIndex(chunkIndex)
        val chunk = chunks[safeIndex]
        val safeOffset = offsetInChunkChars.coerceIn(0, chunk.length)
        val speakText = if (safeOffset > 0 && safeOffset < chunk.text.length) {
            chunk.text.substring(safeOffset)
        } else {
            chunk.text
        }
        debugLog("play item=$currentItemId chunk=$safeIndex offset=$safeOffset")
        ttsController.speakChunk(
            itemId = currentItemId,
            chunkIndex = safeIndex,
            text = speakText,
            baseOffset = safeOffset,
        )
        isSpeaking = true
    }

    suspend fun syncProgress(force: Boolean = false) {
        if (chunks.isEmpty()) return
        val safePosition = normalizedPosition(currentPosition)
        val now = System.currentTimeMillis()
        val totalChars = totalCharsForPercent()
        val absolute = absoluteCharOffset(totalChars, chunks, safePosition)
        val percent = calculateCanonicalPercent(totalChars, chunks, safePosition)
        val advancedPercent = percent > lastSyncedPercent
        val advancedChars = (absolute - lastSyncedAbsoluteChars) >= PROGRESS_CHAR_STEP
        val debounced = (now - lastProgressSyncAtMs) < PROGRESS_SYNC_DEBOUNCE_MS

        if (!force && debounced && !advancedPercent && !advancedChars) return
        if (!force && !advancedPercent && !advancedChars) return

        debugLog("progress item=$currentItemId chunk=${safePosition.chunkIndex} offset=${safePosition.offsetInChunkChars} percent=$percent")
        vm.postProgress(currentItemId, percent)
            .onSuccess {
                if (it.queued) {
                    uiMessage = "Offline: progress queued"
                }
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    return@onFailure
                }
                uiMessage = error.message ?: "Progress sync failed"
            }

        lastProgressSyncAtMs = now
        lastSyncedPercent = percent
        lastSyncedAbsoluteChars = absolute
    }

    fun stopSpeaking(forceSync: Boolean) {
        ttsController.stop()
        isSpeaking = false
        isAutoPlaying = false
        activeChunkRange = null
        if (forceSync) {
            actionScope.launch { syncProgress(force = true) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (stopPlaybackOnDispose) {
                ttsController.shutdown()
            }
        }
    }

    LaunchedEffect(initialItemId) {
        if (resolvedInitial) return@LaunchedEffect
        val resolvedId = vm.resolveInitialPlayerItemId(initialItemId)
        currentItemId = resolvedId
        resolvedInitial = true
    }

    LaunchedEffect(requestedItemId, resolvedInitial) {
        if (!resolvedInitial) return@LaunchedEffect
        val target = requestedItemId ?: return@LaunchedEffect
        if (target == currentItemId) return@LaunchedEffect
        stopSpeaking(forceSync = true)
        currentItemId = target
        autoPlayAfterLoad = false
    }

    LaunchedEffect(locusTapSignal) {
        if (locusTapSignal == lastHandledLocusTapSignal) return@LaunchedEffect
        lastHandledLocusTapSignal = locusTapSignal
        readerScrollTriggerSignal += 1
    }

    LaunchedEffect(compactControlsOnly) {
        if (compactControlsOnly && readerModeEnabled) {
            readerModeEnabled = false
        }
    }

    LaunchedEffect(currentItemId, resolvedInitial, reloadNonce) {
        if (!resolvedInitial) return@LaunchedEffect
        stopSpeaking(forceSync = false)
        vm.setNowPlayingCurrentItem(currentItemId)
        isLoading = true
        uiMessage = null
        if (!preserveVisibleContentOnReload) {
            textPayload = null
            usingCachedText = false
            chunks = emptyList()
        }
        pendingDoneEvent = null
        activeChunkRange = null
        lastHandledDoneUtteranceId = null
        lastSyncedPercent = -1
        lastSyncedAbsoluteChars = -1
        lastProgressSyncAtMs = 0L
        lastObservedPercent = -1
        nearEndForcedForItemId = -1

        vm.fetchItemText(currentItemId)
            .onSuccess { loaded ->
                val payload = loaded.payload
                textPayload = payload
                usingCachedText = loaded.usingCache
                chunks = buildChunks(payload)
                preserveVisibleContentOnReload = false
                readerScrollTriggerSignal += 1

                val saved = vm.getPlaybackPosition(currentItemId)
                val knownProgress = vm.knownProgressForItem(currentItemId)
                val seeded = if (
                    saved.chunkIndex == 0 &&
                    saved.offsetInChunkChars == 0 &&
                    knownProgress > 0 &&
                    chunks.isNotEmpty()
                ) {
                    positionForPercent(knownProgress)
                } else {
                    saved
                }
                val safe = normalizedPosition(seeded)
                vm.setPlaybackPosition(currentItemId, safe.chunkIndex, safe.offsetInChunkChars)

                if (autoPlayAfterLoad && chunks.isNotEmpty()) {
                    autoPlayAfterLoad = false
                    isAutoPlaying = true
                    playChunk(safe.chunkIndex, safe.offsetInChunkChars)
                }
            }
            .onFailure { err ->
                if (err is CancellationException) {
                    return@onFailure
                }
                uiMessage = if (err is ApiException && err.statusCode == 401) {
                    "Unauthorized-check token"
                } else if (isNetworkError(err)) {
                    "Network error loading item text"
                } else {
                    err.message ?: "Failed to load text"
                }
                preserveVisibleContentOnReload = false
            }
        isLoading = false
    }

    LaunchedEffect(currentItemId, currentPosition.chunkIndex, currentPosition.offsetInChunkChars, chunks.size) {
        if (chunks.isNotEmpty()) {
            syncProgress(force = false)
        }
    }

    LaunchedEffect(pendingDoneEvent, isAutoPlaying, chunks.size) {
        val event = pendingDoneEvent ?: return@LaunchedEffect
        if (!isAutoPlaying || chunks.isEmpty()) return@LaunchedEffect
        val safe = normalizedPosition(currentPosition)
        val transition = applyDoneTransition(
            event = event,
            currentItemId = currentItemId,
            currentPosition = safe,
            chunkCount = chunks.size,
            lastHandledUtteranceId = lastHandledDoneUtteranceId,
        )
        if (!transition.shouldHandle) {
            pendingDoneEvent = null
            return@LaunchedEffect
        }

        lastHandledDoneUtteranceId = transition.handledUtteranceId
        pendingDoneEvent = null
        val finishedChunk = chunks[safe.chunkIndex]
        setPlaybackPosition(safe.chunkIndex, finishedChunk.length)

        if (transition.shouldPlayNextChunk) {
            val next = transition.nextPosition.chunkIndex
            debugLog("advance chunk ${safe.chunkIndex} -> $next")
            setPlaybackPosition(next, 0)
            playChunk(next, 0)
        } else if (transition.reachedEnd) {
            debugLog("end of item chunk=${safe.chunkIndex}")
            isSpeaking = false
            isAutoPlaying = false
            syncProgress(force = true)
            val shouldAutoAdvance = vm.shouldAutoAdvanceAfterCompletion()
            if (shouldAutoAdvance) {
                actionScope.launch {
                    val nextId = vm.nextSessionItemId(currentItemId)
                    if (nextId == null) {
                        uiMessage = "Completed"
                    } else {
                        stopSpeaking(forceSync = true)
                        currentItemId = nextId
                        vm.setPlaybackPosition(nextId, 0, 0)
                        autoPlayAfterLoad = true
                        onOpenItem(nextId)
                    }
                }
            } else {
                uiMessage = "Completed"
            }
        }
    }

    val safePosition = normalizedPosition(currentPosition)
    val totalChars = totalCharsForPercent()
    val currentPercent = calculateCanonicalPercent(totalChars, chunks, safePosition)
    val currentTitle = textPayload?.title?.ifBlank { null } ?: textPayload?.url.orEmpty()
    val chunkLabel = if (chunks.isEmpty()) {
        "Chunk 0 / 0"
    } else {
        "Chunk ${safePosition.chunkIndex + 1} / ${chunks.size}"
    }
    val syncBadgeText = when (syncBadgeState) {
        ProgressSyncBadgeState.SYNCED -> "Synced"
        ProgressSyncBadgeState.QUEUED -> "Queued"
        ProgressSyncBadgeState.OFFLINE -> "Offline"
    }
    val offlineAvailability = if (cachedItemIds.contains(currentItemId) || usingCachedText || vm.isItemCached(currentItemId)) {
        "Available offline"
    } else {
        "Needs network"
    }
    val effectivePercent = if (localDonePercentOverride >= 0) localDonePercentOverride else currentPercent
    val showCompleted = effectivePercent >= DONE_PERCENT_THRESHOLD || nearEndForcedForItemId == currentItemId
    val undoDonePercent = effectivePercent.coerceIn(0, DONE_PERCENT_THRESHOLD - 1)
    var lastAppliedSpeed by remember { mutableStateOf(settings.playbackSpeed) }

    LaunchedEffect(isSpeaking, isAutoPlaying) {
        onPlaybackActiveChange(isSpeaking || isAutoPlaying)
    }

    LaunchedEffect(currentPercent) {
        onPlaybackProgressPercentChange(currentPercent)
    }

    LaunchedEffect(readerChromeHidden) {
        onReaderChromeVisibilityChange(readerChromeHidden)
    }

    LaunchedEffect(settings.playbackSpeed, currentItemId, safePosition.chunkIndex, safePosition.offsetInChunkChars, chunks.size) {
        ttsController.setSpeechRate(settings.playbackSpeed)
        if (lastAppliedSpeed == settings.playbackSpeed) return@LaunchedEffect
        lastAppliedSpeed = settings.playbackSpeed
        if ((isSpeaking || isAutoPlaying) && chunks.isNotEmpty()) {
            stopSpeaking(forceSync = false)
            isAutoPlaying = true
            playChunk(safePosition.chunkIndex, safePosition.offsetInChunkChars)
            uiMessage = "Speed ${formatPlaybackSpeed(settings.playbackSpeed)}"
        }
    }

    LaunchedEffect(currentItemId, currentPercent) {
        val crossedNearEnd = shouldForceNearEndCommit(
            previousPercent = lastObservedPercent,
            currentPercent = currentPercent,
            thresholdPercent = DONE_PERCENT_THRESHOLD,
        )
        lastObservedPercent = currentPercent
        if (!crossedNearEnd) return@LaunchedEffect
        if (nearEndForcedForItemId == currentItemId) return@LaunchedEffect

        nearEndForcedForItemId = currentItemId
        debugLog("CROSSED_NEAR_END threshold=$DONE_PERCENT_THRESHOLD item=$currentItemId percent=$currentPercent forcing=100")
        vm.postProgress(currentItemId, 100)
            .onSuccess {
                debugLog("near-end forced progress post ok item=$currentItemId queued=${it.queued}")
                uiMessage = if (it.queued) "Near-end reached: done queued for sync" else "Completed"
                vm.flushPendingProgress()
            }
            .onFailure { error ->
                if (error is CancellationException) return@onFailure
                debugLog("near-end forced progress post failed item=$currentItemId err=${error.message}")
                uiMessage = error.message ?: "Near-end completion sync failed"
            }
    }
    val isRecoverableNetworkError = isNetworkErrorMessage(uiMessage.orEmpty())
    val showRecoveryActions = uiMessage != null && (isRecoverableNetworkError || textPayload == null)
    val showDiagnosticsHint = showRecoveryActions && baseUrlHint != null
    val showLocalPlayerBanner = uiMessage != null && !showRecoveryActions && !showDiagnosticsHint
    var overflowExpanded by remember { mutableStateOf(false) }
    val playlistChoices = playlists.map { playlist ->
        PlaylistPickerChoice(
            playlistId = playlist.id,
            playlistName = playlist.name,
            isMember = vm.isItemInPlaylist(currentItemId, playlist.id),
        )
    }
    val showDockChevron = true
    val handleChevronTap = {
        onControlsModeChange(nextPlayerControlsModeOnTap(controlsMode))
    }
    val handleChevronLongPress = {
        onControlsModeChange(nextPlayerControlsModeOnLongPress(controlsMode))
    }
    val handleChevronSnap: (Float) -> Unit = { delta ->
        if (abs(delta) >= 32f) {
            val nextEdge = if (delta < 0f) {
                PlayerChevronSnapEdge.LEFT
            } else {
                PlayerChevronSnapEdge.RIGHT
            }
            if (nextEdge != chevronSide) {
                onChevronSnapChange(nextEdge)
            }
        }
    }
    val toggleReaderMode = {
        val nextReaderMode = !readerModeEnabled
        readerModeEnabled = nextReaderMode
        actionScope.launch {
            val delta = if (nextReaderMode) {
                readerChromeScrollCompensationPx
            } else {
                -readerChromeScrollCompensationPx
            }
            val target = (readerScrollState.value + delta).coerceIn(0, readerScrollState.maxValue)
            readerScrollState.scrollTo(target)
        }
    }

    val renderPlayerControlBar: @Composable () -> Unit = {
        PlayerControlBar(
            progressPercent = currentPercent,
            minimal = controlsMode == PlayerControlsMode.MINIMAL,
            canSeek = chunks.isNotEmpty(),
            canMoveBackward = chunks.size > 1 && safePosition.chunkIndex > 0,
            canMoveForward = chunks.size > 1 && safePosition.chunkIndex < chunks.lastIndex,
            canPlay = chunks.isNotEmpty(),
            isPlaying = isSpeaking || isAutoPlaying,
            onSeekToPercent = { targetPercent ->
                if (chunks.isEmpty()) return@PlayerControlBar
                localDonePercentOverride = targetPercent
                if (targetPercent < DONE_PERCENT_THRESHOLD) {
                    nearEndForcedForItemId = -1
                    lastObservedPercent = targetPercent
                }
                val targetPosition = positionForPercent(targetPercent)
                setPlaybackPosition(targetPosition.chunkIndex, targetPosition.offsetInChunkChars)
                if (isSpeaking || isAutoPlaying) {
                    isAutoPlaying = true
                    playChunk(targetPosition.chunkIndex, targetPosition.offsetInChunkChars)
                }
            },
            onPreviousSegment = {
                if (chunks.isNotEmpty() && safePosition.chunkIndex > 0) {
                    val target = safePosition.chunkIndex - 1
                    setPlaybackPosition(target, 0)
                    if (isSpeaking || isAutoPlaying) {
                        isAutoPlaying = true
                        playChunk(target, 0)
                    }
                }
            },
            onPlayPause = {
                if (isSpeaking || isAutoPlaying) {
                    stopSpeaking(forceSync = true)
                } else if (chunks.isNotEmpty()) {
                    val restartFromStart = safePosition.chunkIndex == chunks.lastIndex &&
                        safePosition.offsetInChunkChars >= chunks.last().length
                    if (restartFromStart) {
                        setPlaybackPosition(0, 0)
                        nearEndForcedForItemId = -1
                        lastObservedPercent = 0
                    }
                    isAutoPlaying = true
                    val positionToPlay = if (restartFromStart) {
                        PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
                    } else {
                        safePosition
                    }
                    readerScrollTriggerSignal += 1
                    playChunk(positionToPlay.chunkIndex, positionToPlay.offsetInChunkChars)
                }
            },
            onNextSegment = {
                if (chunks.isNotEmpty() && safePosition.chunkIndex < chunks.lastIndex) {
                    val target = safePosition.chunkIndex + 1
                    setPlaybackPosition(target, 0)
                    if (isSpeaking || isAutoPlaying) {
                        isAutoPlaying = true
                        playChunk(target, 0)
                    }
                }
            },
            onPreviousItem = {
                actionScope.launch {
                    val prevId = vm.prevSessionItemId(currentItemId)
                    if (prevId == null) {
                        uiMessage = "No previous item"
                    } else {
                        stopSpeaking(forceSync = true)
                        currentItemId = prevId
                        vm.setPlaybackPosition(prevId, 0, 0)
                        autoPlayAfterLoad = true
                        onOpenItem(prevId)
                    }
                }
            },
            onNextItem = {
                actionScope.launch {
                    val nextId = vm.nextSessionItemId(currentItemId)
                    if (nextId == null) {
                        uiMessage = "No next item"
                    } else {
                        stopSpeaking(forceSync = true)
                        currentItemId = nextId
                        vm.setPlaybackPosition(nextId, 0, 0)
                        autoPlayAfterLoad = true
                        onOpenItem(nextId)
                    }
                }
            },
        )
    }
    val renderPlayerDock: @Composable () -> Unit = {
        when (controlsMode) {
            PlayerControlsMode.FULL -> FullPlayerDock(
                chevronSide = chevronSide,
                showChevron = showDockChevron,
                chevronContentDescription = chevronDescription,
                onChevronTap = handleChevronTap,
                onChevronLongPress = handleChevronLongPress,
                onChevronSnap = handleChevronSnap,
                content = renderPlayerControlBar,
            )

            PlayerControlsMode.MINIMAL -> MinimalPlayerDock(
                progressPercent = currentPercent,
                chevronSide = chevronSide,
                showChevron = showDockChevron,
                chevronContentDescription = chevronDescription,
                onChevronTap = handleChevronTap,
                onChevronLongPress = handleChevronLongPress,
                onChevronSnap = handleChevronSnap,
                content = renderPlayerControlBar,
            )

            PlayerControlsMode.NUB -> NubPlayerDock(
                progressPercent = currentPercent,
                chevronSide = chevronSide,
                showChevron = showDockChevron,
                chevronContentDescription = chevronDescription,
                onChevronTap = handleChevronTap,
                onChevronLongPress = handleChevronLongPress,
                onChevronSnap = handleChevronSnap,
            )
        }
    }

    if (compactControlsOnly) {
        if (showCompactControls) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier),
            ) {
                renderPlayerDock()
            }
        } else {
            Box(modifier = modifier)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(modifier),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!isExpanded) {
                    AnimatedVisibility(
                        visible = !readerChromeHidden,
                        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = tween(150)),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(120)),
                    ) {
                        ExpandedPlayerTopBar(
                            speedLabel = formatPlaybackSpeed(settings.playbackSpeed),
                            overflowExpanded = overflowExpanded,
                            canMarkDone = textPayload != null,
                            isDone = showCompleted,
                            onRefresh = {
                                if (isRefreshing) return@ExpandedPlayerTopBar
                                actionScope.launch {
                                    isRefreshing = true
                                    vm.refreshCurrentPlayerItem(currentItemId)
                                        .onSuccess {
                                            localDonePercentOverride = -1
                                            preserveVisibleContentOnReload = true
                                            reloadNonce += 1
                                        }
                                        .onFailure { error ->
                                            if (error is CancellationException) return@onFailure
                                            uiMessage = error.message ?: "Refresh failed"
                                            onShowSnackbar(uiMessage.orEmpty(), "Diagnostics", "open_diagnostics")
                                        }
                                    isRefreshing = false
                                }
                            },
                            onMarkDone = {
                                actionScope.launch {
                                    val markDone = !showCompleted
                                    val targetPercent = if (markDone) 100 else 97
                                    val resumePercent = if (markDone) currentPercent else undoDonePercent
                                    vm.toggleCompletion(currentItemId, markDone = markDone, resumePercent = resumePercent)
                                        .onSuccess {
                                            localDonePercentOverride = targetPercent
                                            val toggleMessage = when {
                                                showCompleted -> "Marked not done"
                                                else -> "Marked done"
                                            }
                                            onShowSnackbar(toggleMessage, null, null)
                                            if (showCompleted && chunks.isNotEmpty()) {
                                                nearEndForcedForItemId = -1
                                                lastObservedPercent = targetPercent
                                            }
                                        }
                                        .onFailure { error ->
                                            if (error is CancellationException) return@onFailure
                                            uiMessage = error.message ?: "Completion update failed"
                                            onShowSnackbar(uiMessage.orEmpty(), "Diagnostics", "open_diagnostics")
                                        }
                                }
                            },
                            onSpeed = { showSpeedDialog = true },
                            onOverflowExpandedChange = { expanded -> overflowExpanded = expanded },
                            overflowMenuContent = {
                                LocusOverflowMenuItems(
                                    onOpenPlaylists = {
                                        overflowExpanded = false
                                        vm.refreshPlaylists()
                                        showPlaylistPicker = true
                                    },
                                    isExpanded = isExpanded,
                                    onToggleExpanded = {
                                        overflowExpanded = false
                                        isExpanded = !isExpanded
                                    },
                                )
                            },
                        )
                    }
                    LocusPeekCard(
                        title = if (currentTitle.isNotBlank()) currentTitle else "Item $currentItemId",
                        statusLine = "Sync $syncBadgeText  -  $chunkLabel  -  $offlineAvailability",
                        overflowExpanded = overflowExpanded,
                        overflowMenuContent = {
                            Spacer(modifier = Modifier)
                        },
                    )
                    playlistMutationMessage?.let { message ->
                        StatusBanner(
                            stateLabel = if (message.contains("Unauthorized", ignoreCase = true)) "Auth" else "Offline",
                            summary = message,
                            detail = null,
                            onRetry = { playlistMutationMessage = null },
                            onDiagnostics = onOpenDiagnostics,
                        )
                    }
                    if (showLocalPlayerBanner) {
                        StatusBanner(
                            stateLabel = "Status",
                            summary = uiMessage ?: "Player status",
                            detail = null,
                            onRetry = {
                                uiMessage = null
                                if (textPayload == null) {
                                    reloadNonce += 1
                                } else if (chunks.isNotEmpty()) {
                                    isAutoPlaying = true
                                    playChunk(safePosition.chunkIndex, safePosition.offsetInChunkChars)
                                }
                            },
                            onDiagnostics = null,
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                } else {
                    playlistMutationMessage?.let { message ->
                        StatusBanner(
                            stateLabel = if (message.contains("Unauthorized", ignoreCase = true)) "Auth" else "Offline",
                            summary = message,
                            detail = null,
                            onRetry = { playlistMutationMessage = null },
                            onDiagnostics = onOpenDiagnostics,
                        )
                    }
                    if (showLocalPlayerBanner) {
                        StatusBanner(
                            stateLabel = "Status",
                            summary = uiMessage ?: "Player status",
                            detail = null,
                            onRetry = {
                                uiMessage = null
                                if (textPayload == null) {
                                    reloadNonce += 1
                                } else if (chunks.isNotEmpty()) {
                                    isAutoPlaying = true
                                    playChunk(safePosition.chunkIndex, safePosition.offsetInChunkChars)
                                }
                            },
                            onDiagnostics = null,
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(readerModeEnabled) {
                                    detectTapGestures {
                                        toggleReaderMode()
                                    }
                                },
                        ) {
                            ReaderBody(
                                fullText = textPayload?.text,
                                chunks = chunks,
                                currentChunkIndex = safePosition.chunkIndex,
                                currentChunkOffsetInChars = safePosition.offsetInChunkChars,
                                activeRangeInChunk = activeChunkRange,
                                scrollTriggerSignal = readerScrollTriggerSignal,
                                autoScrollWhileListening = settings.autoScrollWhileListening,
                                readingFontSizeSp = settings.readingFontSizeSp,
                                readingLineHeightPercent = settings.readingLineHeightPercent,
                                readingMaxWidthDp = settings.readingMaxWidthDp,
                                paragraphSpacing = settings.readingParagraphSpacing,
                                scrollState = readerScrollState,
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !readerChromeHidden,
                                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = tween(150)),
                                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(120)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                            ) {
                                ExpandedPlayerTopBar(
                                    speedLabel = formatPlaybackSpeed(settings.playbackSpeed),
                                    overflowExpanded = overflowExpanded,
                                    canMarkDone = textPayload != null,
                                    isDone = showCompleted,
                                    onRefresh = {
                                        if (isRefreshing) return@ExpandedPlayerTopBar
                                        actionScope.launch {
                                            isRefreshing = true
                                            vm.refreshCurrentPlayerItem(currentItemId)
                                                .onSuccess {
                                                    localDonePercentOverride = -1
                                                    preserveVisibleContentOnReload = true
                                                    reloadNonce += 1
                                                }
                                                .onFailure { error ->
                                                    if (error is CancellationException) return@onFailure
                                                    uiMessage = error.message ?: "Refresh failed"
                                                    onShowSnackbar(uiMessage.orEmpty(), "Diagnostics", "open_diagnostics")
                                                }
                                            isRefreshing = false
                                        }
                                    },
                                    onMarkDone = {
                                        actionScope.launch {
                                            val markDone = !showCompleted
                                            val targetPercent = if (markDone) 100 else 97
                                            val resumePercent = if (markDone) currentPercent else undoDonePercent
                                            vm.toggleCompletion(currentItemId, markDone = markDone, resumePercent = resumePercent)
                                                .onSuccess {
                                                    localDonePercentOverride = targetPercent
                                                    val toggleMessage = when {
                                                        showCompleted -> "Marked not done"
                                                        else -> "Marked done"
                                                    }
                                                    onShowSnackbar(toggleMessage, null, null)
                                                    if (showCompleted && chunks.isNotEmpty()) {
                                                        nearEndForcedForItemId = -1
                                                        lastObservedPercent = targetPercent
                                                    }
                                                }
                                                .onFailure { error ->
                                                    if (error is CancellationException) return@onFailure
                                                    uiMessage = error.message ?: "Completion update failed"
                                                    onShowSnackbar(uiMessage.orEmpty(), "Diagnostics", "open_diagnostics")
                                                }
                                        }
                                    },
                                    onSpeed = { showSpeedDialog = true },
                                    onOverflowExpandedChange = { expanded -> overflowExpanded = expanded },
                                    overflowMenuContent = {
                                        LocusOverflowMenuItems(
                                            onOpenPlaylists = {
                                                overflowExpanded = false
                                                vm.refreshPlaylists()
                                                showPlaylistPicker = true
                                            },
                                            isExpanded = isExpanded,
                                            onToggleExpanded = {
                                                overflowExpanded = false
                                                isExpanded = !isExpanded
                                            },
                                        )
                                    },
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !readerChromeHidden,
                                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(150)),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                            ) {
                                renderPlayerDock()
                            }
                        }
                    }
                }
                if (!isExpanded) {
                    AnimatedVisibility(
                        visible = !readerChromeHidden,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(150)),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120)),
                    ) {
                        renderPlayerDock()
                    }
                }
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            itemTitle = if (currentTitle.isNotBlank()) currentTitle else "Item $currentItemId",
            playlistChoices = playlistChoices,
            isLoading = false,
            onDismiss = { showPlaylistPicker = false },
            onTogglePlaylist = { choice ->
                actionScope.launch {
                    vm.togglePlaylistMembership(currentItemId, choice.playlistId)
                        .onSuccess { result ->
                            val verb = if (result.added) "Added to" else "Removed from"
                            showPlaylistPicker = false
                            onShowSnackbar("$verb ${choice.playlistName}", null, null)
                            playlistMutationMessage = null
                        }
                        .onFailure { error ->
                            showPlaylistPicker = false
                            playlistMutationMessage = friendlyPlaylistError(error)
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

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                        TextButton(
                            onClick = {
                                showSpeedDialog = false
                                vm.savePlaybackSpeed(speed)
                            },
                        ) {
                            Text(
                                text = if (speed == settings.playbackSpeed) {
                                    "${formatPlaybackSpeed(speed)} (current)"
                                } else {
                                    formatPlaybackSpeed(speed)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExpandedPlayerTopBar(
    speedLabel: String,
    overflowExpanded: Boolean,
    canMarkDone: Boolean,
    isDone: Boolean,
    onRefresh: () -> Unit,
    onMarkDone: () -> Unit,
    onSpeed: () -> Unit,
    onOverflowExpandedChange: (Boolean) -> Unit,
    overflowMenuContent: @Composable () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.heightIn(min = 48.dp),
        title = {},
        actions = {
            IconToggleButton(
                checked = isDone,
                enabled = canMarkDone,
                onCheckedChange = { checked ->
                    if (checked != isDone) {
                        onMarkDone()
                    }
                },
            ) {
                Icon(
                    painter = painterResource(id = if (isDone) R.drawable.ic_book_closed_24 else R.drawable.ic_book_open_24),
                    contentDescription = if (isDone) "Mark as not done" else "Mark as done",
                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_refresh_24),
                    contentDescription = "Refresh item",
                )
            }
            TextButton(onClick = onSpeed) {
                Text(speedLabel)
            }
            LocusOverflowMenu(
                expanded = overflowExpanded,
                onExpandedChange = onOverflowExpandedChange,
                content = overflowMenuContent,
            )
        },
    )
}

@Composable
private fun LocusPeekCard(
    title: String,
    statusLine: String,
    overflowExpanded: Boolean,
    overflowMenuContent: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLine,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocusOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painter = painterResource(id = R.drawable.msr_more_vert_24),
                contentDescription = "More actions",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            content()
        }
    }
}

@Composable
private fun LocusOverflowMenuItems(
    onOpenPlaylists: () -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Playlists...") },
        onClick = onOpenPlaylists,
    )
    DropdownMenuItem(
        text = { Text(if (isExpanded) "Collapse player" else "Expand player") },
        onClick = onToggleExpanded,
    )
}

private fun nextPlayerControlsModeOnTap(current: PlayerControlsMode): PlayerControlsMode {
    return when (current) {
        PlayerControlsMode.FULL -> PlayerControlsMode.MINIMAL
        PlayerControlsMode.MINIMAL -> PlayerControlsMode.FULL
        PlayerControlsMode.NUB -> PlayerControlsMode.MINIMAL
    }
}

private fun nextPlayerControlsModeOnLongPress(current: PlayerControlsMode): PlayerControlsMode {
    return when (current) {
        PlayerControlsMode.FULL -> PlayerControlsMode.MINIMAL
        PlayerControlsMode.MINIMAL -> PlayerControlsMode.NUB
        PlayerControlsMode.NUB -> PlayerControlsMode.MINIMAL
    }
}

@Composable
private fun FullPlayerDock(
    chevronSide: PlayerChevronSnapEdge,
    showChevron: Boolean,
    chevronContentDescription: String,
    onChevronTap: () -> Unit,
    onChevronLongPress: () -> Unit,
    onChevronSnap: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        content()
        if (showChevron) {
            PlayerChromeChevron(
                contentDescription = chevronContentDescription,
                pointLeft = chevronSide == PlayerChevronSnapEdge.LEFT,
                onTap = onChevronTap,
                onLongPress = onChevronLongPress,
                onSnap = onChevronSnap,
                modifier = Modifier
                    .align(
                        if (chevronSide == PlayerChevronSnapEdge.LEFT) {
                            Alignment.TopStart
                        } else {
                            Alignment.TopEnd
                        },
                    )
                    .padding(horizontal = 18.dp)
                    .offset(y = (-30).dp),
            )
        }
    }
}

@Composable
private fun MinimalPlayerDock(
    progressPercent: Int,
    chevronSide: PlayerChevronSnapEdge,
    showChevron: Boolean,
    chevronContentDescription: String,
    onChevronTap: () -> Unit,
    onChevronLongPress: () -> Unit,
    onChevronSnap: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        ) {
            content()
            if (showChevron) {
                PlayerChromeChevron(
                    contentDescription = chevronContentDescription,
                    pointLeft = chevronSide == PlayerChevronSnapEdge.LEFT,
                    onTap = onChevronTap,
                    onLongPress = onChevronLongPress,
                    onSnap = onChevronSnap,
                    modifier = Modifier
                        .align(
                            if (chevronSide == PlayerChevronSnapEdge.LEFT) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            },
                        )
                        .padding(horizontal = 10.dp),
                )
            }
        }
        PlayerProgressLine(progressPercent = progressPercent)
    }
}

@Composable
private fun NubPlayerDock(
    progressPercent: Int,
    chevronSide: PlayerChevronSnapEdge,
    showChevron: Boolean,
    chevronContentDescription: String,
    onChevronTap: () -> Unit,
    onChevronLongPress: () -> Unit,
    onChevronSnap: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
    ) {
        PlayerProgressLine(
            progressPercent = progressPercent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
        PlayerChromeChevron(
            contentDescription = chevronContentDescription,
            pointLeft = chevronSide == PlayerChevronSnapEdge.LEFT,
            onTap = onChevronTap,
            onLongPress = onChevronLongPress,
            onSnap = onChevronSnap,
            modifier = Modifier
                .align(
                    if (chevronSide == PlayerChevronSnapEdge.LEFT) {
                        Alignment.BottomStart
                    } else {
                        Alignment.BottomEnd
                    },
                )
                .padding(horizontal = 10.dp)
                .offset(y = (-8).dp),
        )
    }
}

@Composable
private fun PlayerProgressLine(
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    val clamped = progressPercent.coerceIn(0, 100)
    Box(
        modifier = modifier
            .height(3.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped / 100f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlayerChromeChevron(
    contentDescription: String,
    pointLeft: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSnap: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragAccumulation by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .size(44.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulation += dragAmount
                    },
                    onDragEnd = {
                        onSnap(dragAccumulation)
                        dragAccumulation = 0f
                    },
                    onDragCancel = {
                        dragAccumulation = 0f
                    },
                )
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = MaterialTheme.shapes.large,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.msr_chevron_right_24),
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer(scaleX = if (pointLeft) -1f else 1f),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlayerControlBar(
    progressPercent: Int,
    minimal: Boolean,
    canSeek: Boolean,
    canMoveBackward: Boolean,
    canMoveForward: Boolean,
    canPlay: Boolean,
    isPlaying: Boolean,
    onSeekToPercent: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onPlayPause: () -> Unit,
    onNextSegment: () -> Unit,
    onPreviousItem: () -> Unit,
    onNextItem: () -> Unit,
) {
    var sliderValue by remember(progressPercent) { mutableStateOf(progressPercent.coerceIn(0, 100) / 100f) }
    val minimalSideInset = if (minimal) 56.dp else 0.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!minimal) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it.coerceIn(0f, 1f) },
                onValueChangeFinished = {
                    onSeekToPercent((sliderValue * 100).toInt().coerceIn(0, 100))
                },
                enabled = canSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = minimalSideInset, end = minimalSideInset)
                .padding(vertical = 0.dp),
            horizontalArrangement = if (minimal) Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally) else Arrangement.SpaceEvenly,
        ) {
            if (!minimal) {
                IconButton(onClick = onPreviousItem) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_previous_24),
                        contentDescription = "Previous item",
                    )
                }
            }
            IconButton(onClick = onPreviousSegment, enabled = canMoveBackward) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_rewind_24),
                    contentDescription = "Previous segment",
                )
            }
            IconButton(onClick = onPlayPause, enabled = canPlay) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.msr_pause_24 else R.drawable.msr_play_arrow_24,
                    ),
                    contentDescription = if (isPlaying) "Pause playback" else "Play",
                )
            }
            IconButton(onClick = onNextSegment, enabled = canMoveForward) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_forward_24),
                    contentDescription = "Next segment",
                )
            }
            if (!minimal) {
                IconButton(onClick = onNextItem) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_next_24),
                        contentDescription = "Next item",
                    )
                }
            }
        }
    }
}

private fun buildChunks(payload: ItemTextResponse): List<PlaybackChunk> {
    val apiChunks = payload.chunks.orEmpty()
    if (apiChunks.isNotEmpty()) {
        return apiChunks
            .sortedBy { it.index }
            .map { chunk ->
                val cleanText = normalizeWhitespace(chunk.text)
                val safeStart = chunk.startChar.coerceAtLeast(0)
                val safeEnd = maxOf(safeStart, chunk.endChar)
                PlaybackChunk(
                    index = chunk.index,
                    startChar = safeStart,
                    endChar = safeEnd,
                    text = if (cleanText.isBlank()) chunk.text.trim() else cleanText,
                )
            }
            .filter { it.text.isNotBlank() && it.length > 0 }
    }

    val seeds = payload.paragraphs
        ?.map(::normalizeWhitespace)
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .ifEmpty {
            payload.text
                .split(Regex("\\n\\s*\\n+"))
                .map(::normalizeWhitespace)
                .filter { it.isNotBlank() }
        }

    val chunks = mutableListOf<PlaybackChunk>()
    var cursor = 0
    var index = 0
    for (seed in seeds) {
        for (part in splitByLength(seed, FALLBACK_CHUNK_MAX_CHARS)) {
            val start = cursor
            val end = start + part.length
            chunks += PlaybackChunk(
                index = index,
                startChar = start,
                endChar = end,
                text = part,
            )
            cursor = end + 1
            index += 1
        }
    }
    if (chunks.isNotEmpty()) return chunks

    val fallback = normalizeWhitespace(payload.text)
    if (fallback.isBlank()) return emptyList()
    return listOf(
        PlaybackChunk(
            index = 0,
            startChar = 0,
            endChar = fallback.length,
            text = fallback,
        ),
    )
}

private fun normalizeWhitespace(value: String): String {
    return value.replace(Regex("\\s+"), " ").trim()
}

private fun splitByLength(value: String, maxChars: Int): List<String> {
    if (value.length <= maxChars) return listOf(value)
    val result = mutableListOf<String>()
    val words = value.split(" ")
    val sb = StringBuilder()
    for (word in words) {
        if (sb.isEmpty()) {
            sb.append(word)
            continue
        }
        if (sb.length + 1 + word.length > maxChars) {
            result += sb.toString().trim()
            sb.clear()
            sb.append(word)
        } else {
            sb.append(' ').append(word)
        }
    }
    if (sb.isNotEmpty()) {
        result += sb.toString().trim()
    }
    return result
}

private fun isNetworkError(error: Throwable): Boolean {
    return error is java.io.IOException
}

private fun isNetworkErrorMessage(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("network") ||
        lower.contains("timeout") ||
        lower.contains("failed to connect") ||
        lower.contains("connection refused")
}

private fun formatPlaybackSpeed(speed: Float): String {
    val text = if ((speed * 100).toInt() % 100 == 0) {
        speed.toInt().toString()
    } else if ((speed * 100).toInt() % 10 == 0) {
        String.format("%.1f", speed)
    } else {
        String.format("%.2f", speed)
    }
    return "${text}x"
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
