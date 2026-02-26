package com.mimeo.android.ui.player

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.absoluteCharOffset
import com.mimeo.android.model.calculateCanonicalPercent
import com.mimeo.android.model.positionFromAbsoluteOffset
import com.mimeo.android.player.TtsChunkDoneEvent
import com.mimeo.android.player.TtsChunkProgressEvent
import com.mimeo.android.player.TtsController
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.reader.ReaderScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val DEBUG_PLAYBACK = false
private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
private const val FALLBACK_CHUNK_MAX_CHARS = 900
private const val BUTTON_MIN_HEIGHT_DP = 48
private const val DONE_PERCENT_THRESHOLD = 98

private fun debugLog(message: String) {
    if (DEBUG_PLAYBACK) {
        println("[Mimeo][player] $message")
    }
}

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    initialItemId: Int,
    onOpenItem: (Int) -> Unit,
    onBackToQueue: (Int?) -> Unit,
    onOpenDiagnostics: () -> Unit,
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
    var showClearSessionDialog by remember { mutableStateOf(false) }
    var lastProgressSyncAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedPercent by remember { mutableIntStateOf(-1) }
    var lastSyncedAbsoluteChars by remember { mutableIntStateOf(-1) }
    var lastObservedPercent by remember { mutableIntStateOf(-1) }
    var nearEndForcedForItemId by remember { mutableIntStateOf(-1) }
    val playbackPositionByItem by vm.playbackPositionByItem.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val syncBadgeState by vm.progressSyncBadgeState.collectAsState()
    val cachedItemIds by vm.cachedItemIds.collectAsState()
    val settings by vm.settings.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val currentPosition = playbackPositionByItem[currentItemId] ?: PlaybackPosition()
    val actionScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isPhysicalDevice = remember { isLikelyPhysicalDevice() }
    val baseUrlHint = vm.baseUrlHintForDevice(isPhysicalDevice)

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
        if (forceSync) {
            actionScope.launch { syncProgress(force = true) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsController.shutdown()
        }
    }

    LaunchedEffect(initialItemId) {
        if (resolvedInitial) return@LaunchedEffect
        val resolvedId = vm.resolveInitialPlayerItemId(initialItemId)
        currentItemId = resolvedId
        resolvedInitial = true
    }

    LaunchedEffect(currentItemId, resolvedInitial, reloadNonce) {
        if (!resolvedInitial) return@LaunchedEffect
        stopSpeaking(forceSync = false)
        vm.setNowPlayingCurrentItem(currentItemId)
        isLoading = true
        uiMessage = null
        textPayload = null
        usingCachedText = false
        chunks = emptyList()
        pendingDoneEvent = null
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
    val isDoneLocal = currentPercent >= DONE_PERCENT_THRESHOLD
    val knownProgress = vm.knownProgressForItem(currentItemId)
    val showCompleted = isDoneLocal || nearEndForcedForItemId == currentItemId || knownProgress >= DONE_PERCENT_THRESHOLD

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
    val sessionItemCount = nowPlayingSession?.items?.size ?: 0
    val sessionIndex = nowPlayingSession?.let { session ->
        val found = session.items.indexOfFirst { it.itemId == currentItemId }
        val resolved = if (found >= 0) found else session.currentIndex
        resolved.coerceIn(0, (session.items.size - 1).coerceAtLeast(0))
    } ?: 0
    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LabeledIconControl(
                        onClick = {
                            if (chunks.isNotEmpty() && safePosition.chunkIndex > 0) {
                                val target = safePosition.chunkIndex - 1
                                setPlaybackPosition(target, 0)
                                if (isSpeaking || isAutoPlaying) {
                                    isAutoPlaying = true
                                    playChunk(target, 0)
                                }
                            }
                        },
                        enabled = chunks.size > 1,
                        icon = Icons.Filled.ArrowBack,
                        contentDescription = "Previous segment",
                    )
                    LabeledIconControl(
                        onClick = {
                            if (isSpeaking || isAutoPlaying) {
                                stopSpeaking(forceSync = true)
                            } else if (chunks.isNotEmpty()) {
                                val restartFromStart = showCompleted ||
                                    (safePosition.chunkIndex == chunks.lastIndex &&
                                        safePosition.offsetInChunkChars >= chunks.last().length)
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
                                playChunk(positionToPlay.chunkIndex, positionToPlay.offsetInChunkChars)
                            }
                        },
                        enabled = chunks.isNotEmpty(),
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = if (isSpeaking || isAutoPlaying) "Pause playback" else "Play",
                        iconWhenEnabledAlt = if (isSpeaking || isAutoPlaying) Icons.Filled.CheckCircle else null,
                    )
                    LabeledIconControl(
                        onClick = {
                            if (chunks.isNotEmpty() && safePosition.chunkIndex < chunks.lastIndex) {
                                val target = safePosition.chunkIndex + 1
                                setPlaybackPosition(target, 0)
                                if (isSpeaking || isAutoPlaying) {
                                    isAutoPlaying = true
                                    playChunk(target, 0)
                                }
                            }
                        },
                        enabled = chunks.size > 1,
                        icon = Icons.Filled.ArrowForward,
                        contentDescription = "Next segment",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LabeledIconControl(
                        onClick = {
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
                        enabled = true,
                        icon = Icons.Filled.ArrowBack,
                        contentDescription = "Previous item",
                    )
                    LabeledIconControl(
                        onClick = {
                            actionScope.launch {
                                vm.postProgress(currentItemId, 100)
                                    .onSuccess {
                                        uiMessage = if (it.queued) "Done queued for sync" else "Marked done"
                                        if (chunks.isNotEmpty()) {
                                            val last = chunks.last()
                                            vm.setPlaybackPosition(currentItemId, chunks.lastIndex, last.length)
                                        }
                                    }
                                    .onFailure { error ->
                                        if (error is CancellationException) return@onFailure
                                        uiMessage = error.message ?: "Progress update failed"
                                }
                            }
                        },
                        enabled = textPayload != null,
                        icon = Icons.Filled.CheckCircle,
                        contentDescription = "Mark done",
                    )
                    LabeledIconControl(
                        onClick = {
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
                        enabled = true,
                        icon = Icons.Filled.ArrowForward,
                        contentDescription = "Next item",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = if (currentTitle.isNotBlank()) currentTitle else "Item $currentItemId",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Back to queue") },
                            onClick = {
                                overflowExpanded = false
                                onBackToQueue(currentItemId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restart session") },
                            onClick = {
                                overflowExpanded = false
                                actionScope.launch {
                                    vm.restartNowPlayingSession()
                                    vm.currentNowPlayingItemId()?.let { resumedId ->
                                        currentItemId = resumedId
                                        onOpenItem(resumedId)
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear session") },
                            onClick = {
                                overflowExpanded = false
                                showClearSessionDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Diagnostics") },
                            onClick = {
                                overflowExpanded = false
                                onOpenDiagnostics()
                            },
                        )
                    }
                }
            }
            if (sessionItemCount > 0) {
                Text(
                    text = "Session ${sessionIndex + 1}/$sessionItemCount",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Progress $currentPercent%  -  Sync $syncBadgeText  -  $chunkLabel  -  $offlineAvailability${if (showCompleted) "  -  Done" else ""}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLoading) CircularProgressIndicator()
            if (uiMessage != null || showDiagnosticsHint) {
                StatusBanner(
                    stateLabel = if (queueOffline) "Offline" else "Status",
                    summary = uiMessage ?: "Network guidance",
                    detail = if (showDiagnosticsHint) "${uiMessage.orEmpty()}\n${baseUrlHint.orEmpty()}" else uiMessage,
                    onRetry = {
                        uiMessage = null
                        if (textPayload == null) {
                            reloadNonce += 1
                        } else if (chunks.isNotEmpty()) {
                            isAutoPlaying = true
                            playChunk(safePosition.chunkIndex, safePosition.offsetInChunkChars)
                        }
                    },
                    onDiagnostics = if (showRecoveryActions || showDiagnosticsHint) onOpenDiagnostics else null,
                )
            }
            if (chunks.isNotEmpty()) {
                ReaderScreen(
                    chunks = chunks,
                    currentChunkIndex = safePosition.chunkIndex,
                    autoScrollWhileListening = settings.autoScrollWhileListening,
                    onSelectChunk = { index ->
                        val safeIndex = index.coerceIn(0, chunks.lastIndex)
                        setPlaybackPositionFromAbsoluteOffset(chunks[safeIndex].startChar)
                        uiMessage = "Selected chunk ${safeIndex + 1}"
                    },
                    onPlayFromChunk = { index ->
                        val safeIndex = index.coerceIn(0, chunks.lastIndex)
                        setPlaybackPositionFromAbsoluteOffset(chunks[safeIndex].startChar)
                        isAutoPlaying = true
                        playChunk(safeIndex, 0)
                        uiMessage = "Starting from chunk ${safeIndex + 1}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Clear session?") },
            text = { Text("This removes the persisted Now Playing snapshot and returns to queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSessionDialog = false
                        actionScope.launch {
                            stopSpeaking(forceSync = true)
                            vm.clearNowPlayingSessionNow()
                            onBackToQueue(null)
                        }
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
private fun LabeledIconControl(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    iconWhenEnabledAlt: ImageVector? = null,
) {
    Column(
        modifier = Modifier.heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(iconWhenEnabledAlt ?: icon, contentDescription = contentDescription)
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
