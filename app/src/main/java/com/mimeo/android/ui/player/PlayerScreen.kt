package com.mimeo.android.ui.player

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import com.mimeo.android.ui.reader.ReaderBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private const val DEBUG_PLAYBACK = false
private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
private const val FALLBACK_CHUNK_MAX_CHARS = 900
private const val DONE_PERCENT_THRESHOLD = 98
private val CHEVRON_DOCK_HORIZONTAL_PADDING = 8.dp
private val CHEVRON_DOCK_VERTICAL_OFFSET = (-2).dp
private val CHEVRON_RESERVED_SPACE = 52.dp
private val CONTROL_CLUSTER_GAP = 4.dp
private val CONTROL_SLOT_SIZE = 48.dp
private val PLAYER_UPPER_LANE_HEIGHT = 28.dp
private val PLAYER_TRANSPORT_ROW_HEIGHT = 48.dp
private val NUB_CHEVRON_BOTTOM_MARGIN = 3.dp
private const val PLAYBACK_SPEED_MIN = 0.5f
private const val PLAYBACK_SPEED_MAX = 4.0f
private const val PLAYBACK_SPEED_STEP = 0.05f
private const val PLAYBACK_SPEED_STEPS = 69
private val PLAYBACK_SPEED_PILLS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private const val LOCUS_CONTINUATION_DEBUG_TAG = "MimeoLocusContinue"
private const val MANUAL_OPEN_DEBUG_TAG = "MimeoManualOpen"

internal enum class PlaybackOpenIntent {
    ManualOpen,
    AutoContinue,
    Replay,
}

internal fun resolveSeededPlaybackPosition(
    knownProgress: Int,
    hasChunks: Boolean,
    openIntent: PlaybackOpenIntent,
    positionForPercent: (Int) -> PlaybackPosition,
): PlaybackPosition {
    return when (openIntent) {
        PlaybackOpenIntent.AutoContinue,
        PlaybackOpenIntent.Replay -> PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
        PlaybackOpenIntent.ManualOpen -> {
            if (knownProgress > 0 && hasChunks) {
                positionForPercent(knownProgress)
            } else {
                PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
            }
        }
    }
}

private fun debugLog(message: String) {
    if (DEBUG_PLAYBACK) {
        println("[Mimeo][player] $message")
    }
}

private fun continuationLog(message: String) {
    Log.d(LOCUS_CONTINUATION_DEBUG_TAG, message)
}

private fun playableChunkLength(chunk: PlaybackChunk): Int {
    return chunk.text.length.coerceAtLeast(0)
}

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    initialItemId: Int,
    requestedItemId: Int? = null,
    startExpanded: Boolean = false,
    locusTapSignal: Int = 0,
    openRequestSignal: Int = 0,
    onOpenItem: (Int) -> Unit,
    onRequestBack: () -> Unit = {},
    onOpenDiagnostics: () -> Unit,
    stopPlaybackOnDispose: Boolean = false,
    compactControlsOnly: Boolean = false,
    showCompactControls: Boolean = true,
    controlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    lastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    chevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.RIGHT,
    onControlsModeChange: (PlayerControlsMode, PlayerControlsMode) -> Unit = { _, _ -> },
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
    var pendingOpenIntent by remember {
        mutableStateOf(
            if (vm.isItemCompletedForPlaybackStart(initialItemId)) {
                PlaybackOpenIntent.Replay
            } else {
                PlaybackOpenIntent.ManualOpen
            },
        )
    }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistMutationMessage by remember { mutableStateOf<String?>(null) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var hasRefreshProblem by rememberSaveable { mutableStateOf(false) }
    var preserveVisibleContentOnReload by remember { mutableStateOf(false) }
    var localDonePercentOverride by rememberSaveable(initialItemId) { mutableIntStateOf(-1) }
    var readerViewportSessionNonce by rememberSaveable { mutableIntStateOf(0) }
    val readerScrollState = rememberSaveable(currentItemId, readerViewportSessionNonce, saver = ScrollState.Saver) {
        ScrollState(0)
    }
    var activeChunkRange by remember { mutableStateOf<IntRange?>(null) }
    var readerScrollTriggerSignal by rememberSaveable { mutableIntStateOf(0) }
    var readerSelectionResetSignal by rememberSaveable { mutableIntStateOf(0) }
    var selectionClearArmed by rememberSaveable { mutableStateOf(false) }
    var lastHandledLocusTapSignal by rememberSaveable { mutableIntStateOf(locusTapSignal) }
    var lastHandledOpenRequestSignal by rememberSaveable { mutableIntStateOf(openRequestSignal) }
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
    val textToolbar = LocalTextToolbar.current
    val hasActiveSelection = textToolbar.status == TextToolbarStatus.Shown
    val chevronSide = remember(chevronSnapEdge) {
        when (chevronSnapEdge) {
            PlayerChevronSnapEdge.LEFT -> PlayerChevronSnapEdge.LEFT
            else -> PlayerChevronSnapEdge.RIGHT
        }
    }
    val storedLastNonNubMode = lastNonNubMode.takeIf { it != PlayerControlsMode.NUB } ?: PlayerControlsMode.FULL
    val chevronDescription = when (controlsMode) {
        PlayerControlsMode.FULL -> "Collapse player controls. Long press to hide player controls"
        PlayerControlsMode.MINIMAL -> "Expand player controls. Long press to hide player controls"
        PlayerControlsMode.NUB -> "Restore player controls"
    }
    val readerChromeHidden = !compactControlsOnly && isExpanded && readerModeEnabled
    LaunchedEffect(textToolbar) {
        snapshotFlow { textToolbar.status }.collect { status ->
            if (status == TextToolbarStatus.Shown) {
                selectionClearArmed = true
            }
        }
    }
    fun clearActiveSelection() {
        textToolbar.hide()
        readerSelectionResetSignal += 1
        selectionClearArmed = false
    }
    BackHandler(enabled = !compactControlsOnly && isExpanded) {
        if (selectionClearArmed || hasActiveSelection) {
            clearActiveSelection()
        } else {
            onRequestBack()
        }
    }

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
                continuationLog(
                    "onChunkDone item=${event.itemId} chunk=${event.chunkIndex} " +
                        "latestItem=$latestItemId latestChunk=${latestPosition.chunkIndex}",
                )
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
                val safeOffset = event.absoluteOffsetInChunk.coerceIn(0, playableChunkLength(currentChunks[safeIndex]))
                val safeRange = if (BuildConfig.DEBUG && settings.forceSentenceHighlightFallback) {
                    null
                } else {
                    event.activeRangeInChunk?.let { range ->
                        val start = range.first.coerceIn(0, playableChunkLength(currentChunks[safeIndex]))
                        val endExclusive = (range.last + 1).coerceIn(0, playableChunkLength(currentChunks[safeIndex]))
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
        val safeOffset = position.offsetInChunkChars.coerceIn(0, playableChunkLength(chunks[safeIndex]))
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
        val total = chunks.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
        val targetAbsolute = ((total.toLong() * boundedPercent) / 100L).toInt().coerceIn(0, total)
        var consumed = 0
        chunks.forEachIndexed { idx, chunk ->
            val chunkSpan = playableChunkLength(chunk).coerceAtLeast(1)
            val chunkEnd = consumed + chunkSpan
            if (targetAbsolute <= chunkEnd || idx == chunks.lastIndex) {
                val offset = (targetAbsolute - consumed).coerceIn(0, playableChunkLength(chunk))
                return PlaybackPosition(chunkIndex = idx, offsetInChunkChars = offset)
            }
            consumed = chunkEnd
        }
        return PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
    }

    fun setPlaybackPosition(chunkIndex: Int, offsetInChunkChars: Int) {
        if (chunks.isEmpty()) {
            vm.setPlaybackPosition(currentItemId, 0, 0)
            return
        }
        val safeIndex = normalizedChunkIndex(chunkIndex)
        val safeOffset = offsetInChunkChars.coerceIn(0, playableChunkLength(chunks[safeIndex]))
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
        val maxPlayableOffset = playableChunkLength(chunk)
        val safeOffset = offsetInChunkChars.coerceIn(0, maxPlayableOffset)
        val speakText = if (safeOffset > 0 && safeOffset < maxPlayableOffset) {
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
        pendingOpenIntent = if (vm.isItemCompletedForPlaybackStart(resolvedId)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        currentItemId = resolvedId
        resolvedInitial = true
    }

    LaunchedEffect(requestedItemId, resolvedInitial) {
        if (!resolvedInitial) return@LaunchedEffect
        val target = requestedItemId ?: return@LaunchedEffect
        if (target == currentItemId) return@LaunchedEffect
        continuationLog(
            "requestedItemEffect target=$target current=$currentItemId autoPlayAfterLoad=$autoPlayAfterLoad",
        )
        stopSpeaking(forceSync = true)
        pendingOpenIntent = if (vm.isItemCompletedForPlaybackStart(target)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        currentItemId = target
        autoPlayAfterLoad = false
    }

    LaunchedEffect(locusTapSignal) {
        if (locusTapSignal == lastHandledLocusTapSignal) return@LaunchedEffect
        lastHandledLocusTapSignal = locusTapSignal
        readerScrollTriggerSignal += 1
    }

    LaunchedEffect(openRequestSignal, resolvedInitial, requestedItemId) {
        if (!resolvedInitial) return@LaunchedEffect
        if (openRequestSignal == lastHandledOpenRequestSignal) return@LaunchedEffect
        lastHandledOpenRequestSignal = openRequestSignal
        val target = requestedItemId ?: currentItemId
        if (target != currentItemId) return@LaunchedEffect
        pendingOpenIntent = if (vm.isItemCompletedForPlaybackStart(target)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        autoPlayAfterLoad = false
        preserveVisibleContentOnReload = false
        reloadNonce += 1
        continuationLog(
            "openRequest sameItemReload target=$target reloadNonce=$reloadNonce autoPlayAfterLoad=$autoPlayAfterLoad",
        )
        Log.d(
            MANUAL_OPEN_DEBUG_TAG,
            "openRequest sameItemReload item=$target intent=$pendingOpenIntent reloadNonce=$reloadNonce",
        )
    }

    LaunchedEffect(compactControlsOnly) {
        if (compactControlsOnly && readerModeEnabled) {
            readerModeEnabled = false
        }
    }

    LaunchedEffect(currentItemId, resolvedInitial, reloadNonce) {
        if (!resolvedInitial) return@LaunchedEffect
        continuationLog(
            "loadItem start currentItemId=$currentItemId reloadNonce=$reloadNonce autoPlayAfterLoad=$autoPlayAfterLoad",
        )
        val preservingVisibleContent = preserveVisibleContentOnReload
        stopSpeaking(forceSync = false)
        vm.setNowPlayingCurrentItem(currentItemId)
        isLoading = !preservingVisibleContent
        uiMessage = null
        if (!preservingVisibleContent) {
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
                continuationLog(
                    "loadItem success item=$currentItemId chunks=${chunks.size} usingCache=$usingCachedText autoPlayAfterLoad=$autoPlayAfterLoad",
                )
                preserveVisibleContentOnReload = false

                val knownProgress = vm.knownProgressForItem(currentItemId)
                val seeded = resolveSeededPlaybackPosition(
                    knownProgress = knownProgress,
                    hasChunks = chunks.isNotEmpty(),
                    openIntent = pendingOpenIntent,
                    positionForPercent = ::positionForPercent,
                )
                val safe = normalizedPosition(seeded)
                readerViewportSessionNonce += 1
                vm.setPlaybackPosition(currentItemId, safe.chunkIndex, safe.offsetInChunkChars)
                if (!preservingVisibleContent) {
                    readerScrollTriggerSignal += 1
                }
                Log.d(
                    MANUAL_OPEN_DEBUG_TAG,
                    "loadSeed item=$currentItemId intent=$pendingOpenIntent knownProgress=$knownProgress " +
                        "seededChunk=${safe.chunkIndex} seededOffset=${safe.offsetInChunkChars}",
                )
                pendingOpenIntent = PlaybackOpenIntent.ManualOpen

                if (autoPlayAfterLoad && chunks.isNotEmpty()) {
                    continuationLog(
                        "loadItem autoplay item=$currentItemId chunk=${safe.chunkIndex} offset=${safe.offsetInChunkChars}",
                    )
                    autoPlayAfterLoad = false
                    isAutoPlaying = true
                    playChunk(safe.chunkIndex, safe.offsetInChunkChars)
                }
            }
            .onFailure { err ->
                if (err is CancellationException) {
                    return@onFailure
                }
                pendingOpenIntent = PlaybackOpenIntent.ManualOpen
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
        continuationLog(
            "doneEffect start eventItem=${event.itemId} eventChunk=${event.chunkIndex} " +
                "currentItem=$currentItemId isAutoPlaying=$isAutoPlaying chunks=${chunks.size} " +
                "currentChunk=${currentPosition.chunkIndex}",
        )
        if (!isAutoPlaying || chunks.isEmpty()) {
            continuationLog("doneEffect earlyExit autoPlaying=$isAutoPlaying chunks=${chunks.size}")
            return@LaunchedEffect
        }
        val safe = normalizedPosition(currentPosition)
        val transition = applyDoneTransition(
            event = event,
            currentItemId = currentItemId,
            currentPosition = safe,
            chunkCount = chunks.size,
            lastHandledUtteranceId = lastHandledDoneUtteranceId,
        )
        continuationLog(
            "doneEffect transition shouldHandle=${transition.shouldHandle} " +
                "playNextChunk=${transition.shouldPlayNextChunk} reachedEnd=${transition.reachedEnd} " +
                "nextChunk=${transition.nextPosition.chunkIndex}",
        )
        if (!transition.shouldHandle) {
            pendingDoneEvent = null
            return@LaunchedEffect
        }

        lastHandledDoneUtteranceId = transition.handledUtteranceId
        pendingDoneEvent = null

        if (transition.shouldPlayNextChunk) {
            val next = transition.nextPosition.chunkIndex
            debugLog("advance chunk ${safe.chunkIndex} -> $next")
            continuationLog("doneEffect nextChunk currentItem=$currentItemId nextChunk=$next")
            val finishedChunk = chunks[safe.chunkIndex]
            setPlaybackPosition(safe.chunkIndex, playableChunkLength(finishedChunk))
            setPlaybackPosition(next, 0)
            playChunk(next, 0)
        } else if (transition.reachedEnd) {
            debugLog("end of item chunk=${safe.chunkIndex}")
            continuationLog("doneEffect reachedEnd currentItem=$currentItemId playlistScoped=${vm.isCurrentSessionPlaylistScoped()}")
            isSpeaking = false
            isAutoPlaying = false
            actionScope.launch { syncProgress(force = true) }
            val playlistScoped = vm.isCurrentSessionPlaylistScoped()
            val shouldAutoAdvance = vm.shouldAutoAdvanceAfterCompletion()
            if (playlistScoped || shouldAutoAdvance) {
                val finishedItemId = currentItemId
                actionScope.launch {
                    val nextId = if (playlistScoped) {
                        vm.nextPlaylistScopedSessionItemId(finishedItemId)
                    } else {
                        vm.nextSessionItemId(finishedItemId)
                    }
                    continuationLog("doneEffect resolvedNextId currentItem=$finishedItemId nextId=$nextId")
                    if (nextId == null) {
                        uiMessage = "Completed"
                    } else {
                        stopSpeaking(forceSync = false)
                        continuationLog("doneEffect switching currentItem $finishedItemId -> $nextId")
                        pendingOpenIntent = PlaybackOpenIntent.AutoContinue
                        currentItemId = nextId
                        vm.setPlaybackPosition(nextId, 0, 0)
                        autoPlayAfterLoad = true
                        continuationLog("doneEffect navigating nextId=$nextId")
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
        val nextMode = nextPlayerControlsModeOnTap(controlsMode)
        val nextLastNonNub = if (nextMode == PlayerControlsMode.NUB) storedLastNonNubMode else nextMode
        onControlsModeChange(nextMode, nextLastNonNub)
    }
    val handleChevronLongPress = {
        if (controlsMode == PlayerControlsMode.NUB) {
            onControlsModeChange(storedLastNonNubMode, storedLastNonNubMode)
        } else {
            onControlsModeChange(PlayerControlsMode.NUB, controlsMode)
        }
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
    val toggleReaderMode = { readerModeEnabled = !readerModeEnabled }

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
                    val livePosition = normalizedPosition(vm.getPlaybackPosition(currentItemId))
                    val restartFromStart = livePosition.chunkIndex == chunks.lastIndex &&
                        livePosition.offsetInChunkChars >= playableChunkLength(chunks.last())
                    if (restartFromStart) {
                        setPlaybackPosition(0, 0)
                        nearEndForcedForItemId = -1
                        lastObservedPercent = 0
                    }
                    isAutoPlaying = true
                    val positionToPlay = if (restartFromStart) {
                        PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
                    } else {
                        livePosition
                    }
                    Log.d(
                        MANUAL_OPEN_DEBUG_TAG,
                        "playTap item=$currentItemId restart=$restartFromStart " +
                            "playChunk=${positionToPlay.chunkIndex} playOffset=${positionToPlay.offsetInChunkChars}",
                    )
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
                            playbackSpeed = settings.playbackSpeed,
                            overflowExpanded = overflowExpanded,
                            canMarkDone = textPayload != null,
                            isDone = showCompleted,
                            refreshState = refreshActionState,
                            showConnectivityIssue = queueOffline || hasRefreshProblem,
                            onRefresh = {
                                if (refreshActionState == RefreshActionVisualState.Refreshing) return@ExpandedPlayerTopBar
                                actionScope.launch {
                                    refreshActionState = RefreshActionVisualState.Refreshing
                                    val refreshResult = vm.refreshCurrentPlayerItem(currentItemId)
                                        .onSuccess {
                                            localDonePercentOverride = -1
                                            preserveVisibleContentOnReload = true
                                            reloadNonce += 1
                                            hasRefreshProblem = false
                                        }
                                        .onFailure { error ->
                                            if (error is CancellationException) return@onFailure
                                            val failureMessage = friendlyRefreshFailureMessage(error)
                                            val (actionLabel, actionKey) = refreshFailureAction(failureMessage)
                                            hasRefreshProblem = true
                                            onShowSnackbar(failureMessage, actionLabel, actionKey)
                                        }
                                    refreshActionState = if (refreshResult.isSuccess) {
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
                            onSpeedChange = { speed -> vm.savePlaybackSpeed(speed) },
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
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                } else {
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
                                .pointerInput(readerModeEnabled, hasActiveSelection, selectionClearArmed) {
                                    detectTapGestures {
                                        if (textToolbar.status == TextToolbarStatus.Shown || selectionClearArmed) {
                                            clearActiveSelection()
                                        } else {
                                            toggleReaderMode()
                                        }
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
                                readingFontOption = settings.readingFontOption,
                                readingLineHeightPercent = settings.readingLineHeightPercent,
                                readingMaxWidthDp = settings.readingMaxWidthDp,
                                paragraphSpacing = settings.readingParagraphSpacing,
                                selectionResetSignal = readerSelectionResetSignal,
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
                                    playbackSpeed = settings.playbackSpeed,
                                    overflowExpanded = overflowExpanded,
                                    canMarkDone = textPayload != null,
                                    isDone = showCompleted,
                                    refreshState = refreshActionState,
                                    showConnectivityIssue = queueOffline || hasRefreshProblem,
                                    onRefresh = {
                                        if (refreshActionState == RefreshActionVisualState.Refreshing) return@ExpandedPlayerTopBar
                                        actionScope.launch {
                                            refreshActionState = RefreshActionVisualState.Refreshing
                                            val refreshResult = vm.refreshCurrentPlayerItem(currentItemId)
                                                .onSuccess {
                                                    localDonePercentOverride = -1
                                                    preserveVisibleContentOnReload = true
                                                    reloadNonce += 1
                                                    hasRefreshProblem = false
                                                }
                                                .onFailure { error ->
                                                    if (error is CancellationException) return@onFailure
                                                    val failureMessage = friendlyRefreshFailureMessage(error)
                                                    val (actionLabel, actionKey) = refreshFailureAction(failureMessage)
                                                    hasRefreshProblem = true
                                                    onShowSnackbar(failureMessage, actionLabel, actionKey)
                                                }
                                            refreshActionState = if (refreshResult.isSuccess) {
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
                                    onSpeedChange = { speed -> vm.savePlaybackSpeed(speed) },
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

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExpandedPlayerTopBar(
    playbackSpeed: Float,
    overflowExpanded: Boolean,
    canMarkDone: Boolean,
    isDone: Boolean,
    refreshState: RefreshActionVisualState,
    showConnectivityIssue: Boolean,
    onRefresh: () -> Unit,
    onMarkDone: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOverflowExpandedChange: (Boolean) -> Unit,
    overflowMenuContent: @Composable () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.height(48.dp),
        windowInsets = WindowInsets(0, 0, 0, 0),
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
                    modifier = Modifier.size(22.dp),
                )
            }
            RefreshActionButton(
                state = refreshState,
                showConnectivityIssue = showConnectivityIssue,
                onClick = onRefresh,
                contentDescription = "Refresh item",
            )
            SpeedControlButton(
                speed = playbackSpeed,
                onSpeedChange = onSpeedChange,
            )
            LocusOverflowMenu(
                expanded = overflowExpanded,
                onExpandedChange = onOverflowExpandedChange,
                content = overflowMenuContent,
            )
        },
    )
}

@Composable
private fun SpeedControlButton(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draftSpeed by remember { mutableFloatStateOf(normalizePlaybackSpeed(speed)) }
    val shape = RoundedCornerShape(10.dp)
    val highlightColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val emphasize = pressed || expanded
    val backgroundColor by animateColorAsState(
        targetValue = if (emphasize) highlightColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 150),
        label = "speedTriggerBackground",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (emphasize) 0.12f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "speedTriggerGlowAlpha",
    )
    val triggerDisplaySpeed = if (expanded) draftSpeed else speed

    Box {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = if (emphasize) 6.dp else 0.dp,
                    shape = shape,
                    ambientColor = highlightColor.copy(alpha = glowAlpha),
                    spotColor = highlightColor.copy(alpha = glowAlpha),
                )
                .background(backgroundColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    if (expanded) {
                        expanded = false
                    } else {
                        val normalizedCurrent = normalizePlaybackSpeed(speed)
                        draftSpeed = normalizedCurrent
                        expanded = true
                    }
                }
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_speed_20),
                    contentDescription = "Playback speed",
                    tint = highlightColor,
                    modifier = Modifier.size(24.dp),
                )
                Row(
                    modifier = Modifier.requiredWidth(58.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f", normalizePlaybackSpeed(triggerDisplaySpeed)),
                        color = highlightColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = "×",
                        color = highlightColor.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.requiredWidth(248.dp),
        ) {
            SpeedControlPanel(
                speed = draftSpeed,
                onSpeedChange = { updated ->
                    val normalized = normalizePlaybackSpeed(updated)
                    draftSpeed = normalized
                    onSpeedChange(normalized)
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SpeedControlPanel(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val inactiveTrack = MaterialTheme.colorScheme.outlineVariant
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val thumbHovered by sliderInteractionSource.collectIsHoveredAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (thumbHovered) 1.12f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "speedThumbScale",
    )
    var sliderSpeed by remember { mutableFloatStateOf(normalizePlaybackSpeed(speed)) }

    LaunchedEffect(speed) {
        sliderSpeed = normalizePlaybackSpeed(speed)
    }

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PLAYBACK_SPEED_PILLS.forEach { preset ->
                SpeedPresetPill(
                    speed = preset,
                    selected = normalizePlaybackSpeed(sliderSpeed) == normalizePlaybackSpeed(preset),
                    onClick = {
                        val updated = normalizePlaybackSpeed(preset)
                        sliderSpeed = updated
                        onSpeedChange(updated)
                    },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
        Slider(
            value = sliderSpeed,
            onValueChange = { value ->
                val updated = normalizePlaybackSpeed(value)
                sliderSpeed = updated
                onSpeedChange(updated)
            },
            onValueChangeFinished = {},
            valueRange = PLAYBACK_SPEED_MIN..PLAYBACK_SPEED_MAX,
            steps = PLAYBACK_SPEED_STEPS,
            interactionSource = sliderInteractionSource,
            colors = SliderDefaults.colors(
                thumbColor = highlightColor,
                activeTrackColor = highlightColor,
                inactiveTrackColor = inactiveTrack,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(highlightColor.copy(alpha = 0.2f), CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer(
                                scaleX = thumbScale,
                                scaleY = thumbScale,
                            )
                            .background(highlightColor, CircleShape),
                    )
                }
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier
                        .height(4.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(999.dp),
                        ),
                    colors = SliderDefaults.colors(
                        activeTrackColor = highlightColor,
                        inactiveTrackColor = inactiveTrack,
                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedStepperButton(
                label = "-",
                onClick = {
                    val updated = normalizePlaybackSpeed(sliderSpeed - PLAYBACK_SPEED_STEP)
                    sliderSpeed = updated
                    onSpeedChange(updated)
                },
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f", sliderSpeed),
                    color = highlightColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 23.sp,
                )
                Text(
                    text = "×",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            SpeedStepperButton(
                label = "+",
                onClick = {
                    val updated = normalizePlaybackSpeed(sliderSpeed + PLAYBACK_SPEED_STEP)
                    sliderSpeed = updated
                    onSpeedChange(updated)
                },
            )
        }
    }
}

@Composable
private fun SpeedPresetPill(
    speed: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val highlightColor = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) highlightColor else MaterialTheme.colorScheme.outline
    val labelColor = if (selected) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .requiredWidth(40.dp)
            .height(28.dp)
            .border(1.dp, borderColor, shape)
            .background(
                color = if (selected) highlightColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatPlaybackSpeedPresetLabel(speed),
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SpeedStepperButton(
    label: String,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val highlightColor = MaterialTheme.colorScheme.primary
    val defaultBorder = MaterialTheme.colorScheme.outline
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val emphasize = hovered || pressed
    val borderColor by animateColorAsState(
        targetValue = if (emphasize) highlightColor else defaultBorder,
        animationSpec = tween(durationMillis = 150),
        label = "speedStepperBorder",
    )
    val labelColor by animateColorAsState(
        targetValue = if (emphasize) highlightColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 150),
        label = "speedStepperLabel",
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
    }
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
                modifier = Modifier.size(24.dp),
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
                            Alignment.BottomStart
                        } else {
                            Alignment.BottomEnd
                        },
                    )
                    .padding(horizontal = CHEVRON_DOCK_HORIZONTAL_PADDING)
                    .offset(y = CHEVRON_DOCK_VERTICAL_OFFSET),
            )
        }
    }
}

@Composable
private fun MinimalPlayerDock(
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
                            Alignment.BottomStart
                        } else {
                            Alignment.BottomEnd
                        },
                    )
                    .padding(horizontal = CHEVRON_DOCK_HORIZONTAL_PADDING)
                    .offset(y = CHEVRON_DOCK_VERTICAL_OFFSET),
            )
        }
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
            .height(PLAYER_TRANSPORT_ROW_HEIGHT),
    ) {
        PlayerProgressLine(
            progressPercent = progressPercent,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        )
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
                            Alignment.BottomStart
                        } else {
                            Alignment.BottomEnd
                        },
                    )
                    .padding(
                        start = CHEVRON_DOCK_HORIZONTAL_PADDING,
                        end = CHEVRON_DOCK_HORIZONTAL_PADDING,
                        bottom = NUB_CHEVRON_BOTTOM_MARGIN,
                    )
            )
        }
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
            .size(40.dp)
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.msr_chevron_right_24),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(scaleX = if (pointLeft) -1f else 1f),
            tint = MaterialTheme.colorScheme.onPrimary,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!minimal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLAYER_UPPER_LANE_HEIGHT),
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it.coerceIn(0f, 1f) },
                    onValueChangeFinished = {
                        onSeekToPercent((sliderValue * 100).toInt().coerceIn(0, 100))
                    },
                    enabled = canSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 18.dp),
                )
            }
        } else {
            PlayerProgressLine(
                progressPercent = progressPercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CHEVRON_RESERVED_SPACE, end = CHEVRON_RESERVED_SPACE)
                .height(PLAYER_TRANSPORT_ROW_HEIGHT)
                .padding(vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!minimal) {
                IconButton(onClick = onPreviousItem, modifier = Modifier.size(CONTROL_SLOT_SIZE)) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_previous_24),
                        contentDescription = "Previous item",
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(CONTROL_SLOT_SIZE))
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            IconButton(onClick = onPreviousSegment, enabled = canMoveBackward, modifier = Modifier.size(CONTROL_SLOT_SIZE)) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_rewind_24),
                    contentDescription = "Previous segment",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            IconButton(onClick = onPlayPause, enabled = canPlay, modifier = Modifier.size(CONTROL_SLOT_SIZE)) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.msr_pause_24 else R.drawable.msr_play_arrow_24,
                    ),
                    contentDescription = if (isPlaying) "Pause playback" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            IconButton(onClick = onNextSegment, enabled = canMoveForward, modifier = Modifier.size(CONTROL_SLOT_SIZE)) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_forward_24),
                    contentDescription = "Next segment",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            if (!minimal) {
                IconButton(onClick = onNextItem, modifier = Modifier.size(CONTROL_SLOT_SIZE)) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_next_24),
                        contentDescription = "Next item",
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(CONTROL_SLOT_SIZE))
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

private fun friendlyRefreshFailureMessage(error: Throwable): String {
    if (error is ApiException) {
        return when {
            error.statusCode == 401 -> "Check your API token"
            error.statusCode >= 500 -> "Server error. Try again."
            else -> error.message ?: "Refresh failed"
        }
    }
    if (isNetworkError(error)) {
        return "Couldn't reach server"
    }
    val message = error.message?.trim()
    if (message.isNullOrEmpty()) return "Refresh failed"
    if (message.contains("java.", ignoreCase = true) || message.length > 180) {
        return "Refresh failed"
    }
    return message
}

private fun refreshFailureAction(message: String): Pair<String, String> {
    return if (message.equals("Check your API token", ignoreCase = true)) {
        "Settings" to "open_settings"
    } else {
        "Diagnostics" to "open_diagnostics"
    }
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
    val normalized = normalizePlaybackSpeed(speed)
    val text = if ((normalized * 100).toInt() % 100 == 0) {
        normalized.toInt().toString()
    } else if ((normalized * 100).toInt() % 10 == 0) {
        String.format(Locale.US, "%.1f", normalized)
    } else {
        String.format(Locale.US, "%.2f", normalized)
    }
    return "${text}×"
}

private fun formatPlaybackSpeedPresetLabel(speed: Float): String {
    val normalized = normalizePlaybackSpeed(speed)
    return when {
        normalized % 1f == 0f -> normalized.toInt().toString()
        (normalized * 100).toInt() % 10 == 0 -> String.format(Locale.US, "%.1f", normalized)
        else -> String.format(Locale.US, "%.2f", normalized)
    }
}

private fun normalizePlaybackSpeed(value: Float): Float {
    val clamped = value.coerceIn(PLAYBACK_SPEED_MIN, PLAYBACK_SPEED_MAX)
    val stepsFromMin = ((clamped - PLAYBACK_SPEED_MIN) / PLAYBACK_SPEED_STEP).roundToInt()
    return (PLAYBACK_SPEED_MIN + (stepsFromMin * PLAYBACK_SPEED_STEP))
        .coerceIn(PLAYBACK_SPEED_MIN, PLAYBACK_SPEED_MAX)
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
