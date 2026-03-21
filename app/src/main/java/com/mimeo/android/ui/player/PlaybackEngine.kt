package com.mimeo.android.ui.player

import android.content.Context
import com.mimeo.android.BuildConfig
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.absoluteCharOffset
import com.mimeo.android.model.calculateCanonicalPercent
import com.mimeo.android.player.TITLE_INTRO_CHUNK_INDEX
import com.mimeo.android.player.TtsChunkDoneEvent
import com.mimeo.android.player.TtsChunkProgressEvent
import com.mimeo.android.player.TtsController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
private const val LOCUS_CONTINUATION_DEBUG_TAG = "MimeoLocusContinue"
private const val DONE_PERCENT_THRESHOLD = 98

data class PlaybackEngineState(
    val currentItemId: Int,
    val openIntent: PlaybackOpenIntent,
    val reloadNonce: Int = 0,
    val currentPosition: PlaybackPosition = PlaybackPosition(),
    val isSpeaking: Boolean = false,
    val isAutoPlaying: Boolean = false,
    val activeChunkRange: IntRange? = null,
    val autoPlayAfterLoad: Boolean = false,
    val hasStartedPlaybackForCurrentItem: Boolean = false,
    val lastOpenDiagnostics: PlaybackOpenDiagnosticsSnapshot? = null,
)

sealed interface PlaybackEngineEvent {
    data class NavigateToItem(val itemId: Int) : PlaybackEngineEvent
    data class UiMessage(val message: String) : PlaybackEngineEvent
}

data class PlaybackEngineSettings(
    val speakTitleBeforeArticle: Boolean,
    val skipDuplicateOpeningAfterTitleIntro: Boolean,
    val playCompletionCueAtArticleEnd: Boolean,
    val autoAdvanceOnCompletion: Boolean,
    val playbackSpeed: Float,
)

interface PlaybackEngineHost {
    fun knownProgressForItem(itemId: Int): Int
    fun knownFurthestForItem(itemId: Int): Int
    fun getPlaybackPosition(itemId: Int): PlaybackPosition
    fun setPlaybackPosition(itemId: Int, chunkIndex: Int, offsetInChunkChars: Int)
    suspend fun postProgress(itemId: Int, percent: Int): Result<*>
    fun shouldAutoAdvanceAfterCompletion(): Boolean
    fun isCurrentSessionPlaylistScoped(): Boolean
    fun currentPlaybackSettings(): PlaybackEngineSettings
    suspend fun nextSessionItemId(currentId: Int): Int?
    suspend fun nextPlaylistScopedSessionItemId(currentId: Int): Int?
}

class PlaybackEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val host: PlaybackEngineHost,
) {
    private val _state = MutableStateFlow(
        PlaybackEngineState(
            currentItemId = -1,
            openIntent = PlaybackOpenIntent.ManualOpen,
        ),
    )
    val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEngineEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PlaybackEngineEvent> = _events.asSharedFlow()

    private var textPayload: ItemTextResponse? = null
    private var chunks: List<PlaybackChunk> = emptyList()
    private var pendingBodyStartAfterTitleIntro: PlaybackPosition? = null
    private var lastHandledDoneUtteranceId: String? = null
    private var lastProgressSyncAtMs: Long = 0L
    private var lastSyncedPercent: Int = -1
    private var lastSyncedAbsoluteChars: Int = -1

    private val ttsController = TtsController(
        context = context.applicationContext,
        onChunkDone = ::handleChunkDoneEvent,
        onChunkProgress = ::handleChunkProgressEvent,
        onError = { message ->
            _events.tryEmit(PlaybackEngineEvent.UiMessage(message))
        },
    )

    fun openItem(itemId: Int, intent: PlaybackOpenIntent, autoPlayAfterLoad: Boolean) {
        val previous = _state.value
        stopInternal(forceSync = false)
        _state.value = reduceOpenItemState(
            previous = previous,
            itemId = itemId,
            intent = intent,
            autoPlayAfterLoad = autoPlayAfterLoad,
        )
        textPayload = null
        chunks = emptyList()
        pendingBodyStartAfterTitleIntro = null
        lastHandledDoneUtteranceId = null
        lastProgressSyncAtMs = 0L
        lastSyncedPercent = -1
        lastSyncedAbsoluteChars = -1
    }

    fun reloadCurrentItem(intent: PlaybackOpenIntent) {
        val current = _state.value
        if (current.currentItemId <= 0) return
        stopInternal(forceSync = false)
        _state.value = reduceReloadItemState(current, intent)
        pendingBodyStartAfterTitleIntro = null
        lastHandledDoneUtteranceId = null
        lastProgressSyncAtMs = 0L
        lastSyncedPercent = -1
        lastSyncedAbsoluteChars = -1
    }

    fun applyLoadedItem(
        payload: ItemTextResponse,
        loadedChunks: List<PlaybackChunk>,
        requestedItemId: Int?,
    ) {
        val current = _state.value
        if (payload.itemId != current.currentItemId) return
        textPayload = payload
        chunks = loadedChunks
        val knownProgress = host.knownProgressForItem(current.currentItemId)
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = knownProgress,
            hasChunks = loadedChunks.isNotEmpty(),
            openIntent = current.openIntent,
            positionForPercent = ::positionForPercent,
        )
        val safe = normalizedPosition(seeded)
        val startSource = resolveOpenStartSource(
            openIntent = current.openIntent,
            knownProgress = knownProgress,
            hasChunks = loadedChunks.isNotEmpty(),
        )
        host.setPlaybackPosition(current.currentItemId, safe.chunkIndex, safe.offsetInChunkChars)
        _state.value = current.copy(
            currentPosition = safe,
            openIntent = PlaybackOpenIntent.ManualOpen,
            lastOpenDiagnostics = PlaybackOpenDiagnosticsSnapshot(
                itemId = current.currentItemId,
                requestedItemId = requestedItemId,
                openIntent = current.openIntent,
                startSource = startSource,
                knownProgress = knownProgress,
                seededPosition = safe,
            ),
        )
    }

    fun maybeAutoPlayAfterLoad(settings: PlaybackEngineSettings) {
        val current = _state.value
        if (!current.autoPlayAfterLoad || chunks.isEmpty()) return
        _state.value = current.copy(autoPlayAfterLoad = false)
        startPlaybackAtPosition(current.currentPosition, allowTitleIntro = true, settings = settings)
    }

    fun play(settings: PlaybackEngineSettings) {
        val current = _state.value
        if (chunks.isEmpty() || current.currentItemId <= 0) return
        val livePosition = normalizedPosition(host.getPlaybackPosition(current.currentItemId))
        val restartFromStart = livePosition.chunkIndex == chunks.lastIndex &&
            livePosition.offsetInChunkChars >= playableChunkLength(chunks.last())
        if (restartFromStart) {
            setPlaybackPosition(0, 0)
            startPlaybackAtPosition(
                position = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
                allowTitleIntro = true,
                settings = settings,
            )
        } else {
            startPlaybackAtPosition(
                position = livePosition,
                allowTitleIntro = true,
                settings = settings,
            )
        }
    }

    fun applyPlaybackSpeed(settings: PlaybackEngineSettings) {
        ttsController.setSpeechRate(settings.playbackSpeed)
        val current = _state.value
        if ((current.isSpeaking || current.isAutoPlaying) && chunks.isNotEmpty()) {
            stopInternal(forceSync = false)
            _state.value = _state.value.copy(isAutoPlaying = true)
            playChunk(current.currentPosition.chunkIndex, current.currentPosition.offsetInChunkChars)
        }
    }

    fun pause(forceSync: Boolean) {
        stopInternal(forceSync = forceSync)
    }

    fun seekToChunkOffset(chunkIndex: Int, offsetInChunkChars: Int, keepPlaying: Boolean) {
        if (chunks.isEmpty()) return
        val safe = normalizedPosition(PlaybackPosition(chunkIndex = chunkIndex, offsetInChunkChars = offsetInChunkChars))
        setPlaybackPosition(safe.chunkIndex, safe.offsetInChunkChars)
        val current = _state.value
        if (keepPlaying && (current.isSpeaking || current.isAutoPlaying)) {
            _state.value = current.copy(isAutoPlaying = true)
            playChunk(safe.chunkIndex, safe.offsetInChunkChars)
        }
    }

    fun advanceToNextItem() {
        val current = _state.value
        if (current.currentItemId <= 0) return
        scope.launch {
            val nextId = host.nextSessionItemId(current.currentItemId)
            if (nextId == null) {
                _events.tryEmit(PlaybackEngineEvent.UiMessage("No next item"))
                return@launch
            }
            stopInternal(forceSync = true)
            host.setPlaybackPosition(nextId, 0, 0)
            openItem(nextId, PlaybackOpenIntent.AutoContinue, autoPlayAfterLoad = true)
            _events.tryEmit(PlaybackEngineEvent.NavigateToItem(nextId))
        }
    }

    fun shutdown() {
        stopInternal(forceSync = false)
        ttsController.shutdown()
    }

    private fun handleChunkProgressEvent(event: TtsChunkProgressEvent) {
        val current = _state.value
        if (event.itemId != current.currentItemId) return
        if (event.chunkIndex != current.currentPosition.chunkIndex) return
        val safe = normalizedPosition(
            PlaybackPosition(
                chunkIndex = event.chunkIndex,
                offsetInChunkChars = event.absoluteOffsetInChunk,
            ),
        )
        setPlaybackPosition(safe.chunkIndex, safe.offsetInChunkChars)
        _state.value = _state.value.copy(activeChunkRange = event.activeRangeInChunk)
        scope.launch { syncProgress(force = false) }
    }

    private fun handleChunkDoneEvent(event: TtsChunkDoneEvent) {
        scope.launch {
            val current = _state.value
            if (event.itemId != current.currentItemId) return@launch
            if (event.chunkIndex == TITLE_INTRO_CHUNK_INDEX) {
                val pendingStart = pendingBodyStartAfterTitleIntro ?: return@launch
                pendingBodyStartAfterTitleIntro = null
                playChunk(pendingStart.chunkIndex, pendingStart.offsetInChunkChars)
                return@launch
            }
            if (!current.isAutoPlaying || chunks.isEmpty()) return@launch
            val safe = normalizedPosition(current.currentPosition)
            val transition = applyDoneTransition(
                event = PlaybackDoneEvent(
                    utteranceId = event.utteranceId,
                    itemId = event.itemId,
                    chunkIndex = event.chunkIndex,
                ),
                currentItemId = current.currentItemId,
                currentPosition = safe,
                chunkCount = chunks.size,
                lastHandledUtteranceId = lastHandledDoneUtteranceId,
            )
            if (!transition.shouldHandle) return@launch
            lastHandledDoneUtteranceId = transition.handledUtteranceId

            if (transition.shouldPlayNextChunk) {
                val next = transition.nextPosition.chunkIndex
                setPlaybackPosition(next, 0)
                playChunk(next, 0)
            } else if (transition.reachedEnd) {
                val settings = host.currentPlaybackSettings()
                val playlistScoped = host.isCurrentSessionPlaylistScoped()
                val shouldAutoAdvance = settings.autoAdvanceOnCompletion
                if (settings.playCompletionCueAtArticleEnd) {
                    ttsController.playCompletionCue()
                }
                _state.value = _state.value.copy(
                    isSpeaking = false,
                    isAutoPlaying = false,
                )
                syncProgress(force = true)
                if (playlistScoped || shouldAutoAdvance) {
                    val nextId = if (playlistScoped) {
                        val scopedNextId = host.nextPlaylistScopedSessionItemId(current.currentItemId)
                        if (scopedNextId == null && shouldAutoAdvance) {
                            host.nextSessionItemId(current.currentItemId)
                        } else {
                            scopedNextId
                        }
                    } else {
                        host.nextSessionItemId(current.currentItemId)
                    }
                    if (nextId == null) {
                        _events.tryEmit(PlaybackEngineEvent.UiMessage("Completed"))
                    } else {
                        host.setPlaybackPosition(nextId, 0, 0)
                        openItem(nextId, PlaybackOpenIntent.AutoContinue, autoPlayAfterLoad = true)
                        _events.tryEmit(PlaybackEngineEvent.NavigateToItem(nextId))
                    }
                } else {
                    _events.tryEmit(PlaybackEngineEvent.UiMessage("Completed"))
                }
            }
        }
    }

    private fun startPlaybackAtPosition(
        position: PlaybackPosition,
        allowTitleIntro: Boolean,
        settings: PlaybackEngineSettings,
    ) {
        if (chunks.isEmpty()) return
        val current = _state.value
        val safe = normalizedPosition(position)
        val shouldSpeakTitleFirst = shouldUseTitleIntroOnPlaybackStart(
            allowTitleIntro = allowTitleIntro,
            hasStartedPlaybackForItem = current.hasStartedPlaybackForCurrentItem,
            speakTitleBeforeArticleEnabled = settings.speakTitleBeforeArticle,
            startPosition = safe,
            title = textPayload?.title,
            chunks = chunks,
        )
        if (shouldSpeakTitleFirst) {
            val title = textPayload?.title?.trim().orEmpty()
            val openingText = chunks.firstOrNull()?.text.orEmpty()
            val prefixSkipChars = if (settings.skipDuplicateOpeningAfterTitleIntro) {
                computeTitlePrefixSkipChars(title = title, openingText = openingText, minMatchedWords = 3)
            } else {
                0
            }
            pendingBodyStartAfterTitleIntro = applyTitlePrefixSkipToStartPosition(
                start = safe,
                chunks = chunks,
                skipCharsFromOpening = prefixSkipChars,
            )
            _state.value = _state.value.copy(
                isAutoPlaying = true,
                isSpeaking = true,
            )
            ttsController.speakTitleIntro(current.currentItemId, title)
        } else {
            pendingBodyStartAfterTitleIntro = null
            _state.value = _state.value.copy(isAutoPlaying = true)
            playChunk(safe.chunkIndex, safe.offsetInChunkChars)
        }
    }

    private fun playChunk(chunkIndex: Int, offsetInChunkChars: Int) {
        if (chunks.isEmpty()) return
        val current = _state.value
        val safeIndex = chunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[safeIndex]
        val maxPlayableOffset = playableChunkLength(chunk)
        val safeOffset = offsetInChunkChars.coerceIn(0, maxPlayableOffset)
        val speakText = if (safeOffset > 0 && safeOffset < maxPlayableOffset) {
            chunk.text.substring(safeOffset)
        } else {
            chunk.text
        }
        ttsController.speakChunk(
            itemId = current.currentItemId,
            chunkIndex = safeIndex,
            text = speakText,
            baseOffset = safeOffset,
        )
        _state.value = _state.value.copy(
            isSpeaking = true,
            hasStartedPlaybackForCurrentItem = true,
        )
    }

    private fun setPlaybackPosition(chunkIndex: Int, offsetInChunkChars: Int) {
        val current = _state.value
        if (current.currentItemId <= 0) return
        if (chunks.isEmpty()) {
            host.setPlaybackPosition(current.currentItemId, 0, 0)
            _state.value = current.copy(currentPosition = PlaybackPosition())
            return
        }
        val safe = normalizedPosition(
            PlaybackPosition(
                chunkIndex = chunkIndex,
                offsetInChunkChars = offsetInChunkChars,
            ),
        )
        host.setPlaybackPosition(current.currentItemId, safe.chunkIndex, safe.offsetInChunkChars)
        _state.value = _state.value.copy(currentPosition = safe)
    }

    private suspend fun syncProgress(force: Boolean) {
        if (chunks.isEmpty()) return
        val current = _state.value
        val safe = normalizedPosition(current.currentPosition)
        val now = System.currentTimeMillis()
        val totalChars = totalCharsForPercent()
        val absolute = absoluteCharOffset(totalChars, chunks, safe)
        val percent = calculateCanonicalPercent(totalChars, chunks, safe)
        val advancedPercent = percent > lastSyncedPercent
        val advancedChars = (absolute - lastSyncedAbsoluteChars) >= PROGRESS_CHAR_STEP
        val debounced = (now - lastProgressSyncAtMs) < PROGRESS_SYNC_DEBOUNCE_MS
        if (!force && debounced && !advancedPercent && !advancedChars) return
        if (!force && !advancedPercent && !advancedChars) return

        host.postProgress(current.currentItemId, percent)
            .onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (error is ApiException && error.statusCode == 401) return@onFailure
                _events.tryEmit(PlaybackEngineEvent.UiMessage(error.message ?: "Progress sync failed"))
            }
        lastProgressSyncAtMs = now
        lastSyncedPercent = percent
        lastSyncedAbsoluteChars = absolute
    }

    private fun stopInternal(forceSync: Boolean) {
        ttsController.stop()
        val current = _state.value
        _state.value = current.copy(
            isSpeaking = false,
            isAutoPlaying = false,
            activeChunkRange = null,
        )
        pendingBodyStartAfterTitleIntro = null
        if (forceSync) {
            scope.launch { syncProgress(force = true) }
        }
    }

    private fun normalizedPosition(position: PlaybackPosition): PlaybackPosition {
        if (chunks.isEmpty()) return PlaybackPosition()
        val safeIndex = position.chunkIndex.coerceIn(0, chunks.lastIndex)
        val safeOffset = position.offsetInChunkChars.coerceIn(0, playableChunkLength(chunks[safeIndex]))
        return PlaybackPosition(chunkIndex = safeIndex, offsetInChunkChars = safeOffset)
    }

    private fun playableChunkLength(chunk: PlaybackChunk): Int {
        return chunk.text.length.coerceAtLeast(0)
    }

    private fun totalCharsForPercent(): Int {
        val declared = textPayload?.totalChars ?: 0
        val chunkMax = chunks.maxOfOrNull { it.endChar } ?: 0
        if (declared > 0 && chunkMax > 0) return maxOf(declared, chunkMax)
        if (declared > 0) return declared
        if (chunkMax > 0) return chunkMax
        return textPayload?.text?.length ?: 0
    }

    private fun positionForPercent(percent: Int): PlaybackPosition {
        if (chunks.isEmpty()) return PlaybackPosition()
        val boundedPercent = percent.coerceIn(0, 100)
        if (boundedPercent <= 0) return PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
        val total = chunks.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
        val targetAbsolute = ((total.toLong() * boundedPercent) / 100L).toInt().coerceIn(0, total)
        var consumed = 0
        chunks.forEachIndexed { idx, chunk ->
            val chunkLen = chunk.length.coerceAtLeast(1)
            val next = consumed + chunkLen
            if (targetAbsolute <= next || idx == chunks.lastIndex) {
                val offset = (targetAbsolute - consumed).coerceIn(0, playableChunkLength(chunk))
                return PlaybackPosition(chunkIndex = idx, offsetInChunkChars = offset)
            }
            consumed = next
        }
        return PlaybackPosition(chunkIndex = chunks.lastIndex, offsetInChunkChars = 0)
    }
}

internal fun reduceOpenItemState(
    previous: PlaybackEngineState,
    itemId: Int,
    intent: PlaybackOpenIntent,
    autoPlayAfterLoad: Boolean,
): PlaybackEngineState {
    val nextReloadNonce = if (previous.currentItemId == itemId) previous.reloadNonce + 1 else previous.reloadNonce
    return previous.copy(
        currentItemId = itemId,
        openIntent = intent,
        reloadNonce = nextReloadNonce,
        currentPosition = PlaybackPosition(),
        isSpeaking = false,
        isAutoPlaying = false,
        activeChunkRange = null,
        autoPlayAfterLoad = autoPlayAfterLoad,
        hasStartedPlaybackForCurrentItem = false,
        lastOpenDiagnostics = null,
    )
}

internal fun reduceReloadItemState(
    current: PlaybackEngineState,
    intent: PlaybackOpenIntent,
): PlaybackEngineState {
    return current.copy(
        openIntent = intent,
        reloadNonce = current.reloadNonce + 1,
        currentPosition = PlaybackPosition(),
        isSpeaking = false,
        isAutoPlaying = false,
        activeChunkRange = null,
        autoPlayAfterLoad = false,
        hasStartedPlaybackForCurrentItem = false,
        lastOpenDiagnostics = null,
    )
}
