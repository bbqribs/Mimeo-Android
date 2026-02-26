package com.mimeo.android.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.absoluteCharOffset
import com.mimeo.android.model.calculateCanonicalPercent
import com.mimeo.android.player.TtsChunkDoneEvent
import com.mimeo.android.player.TtsChunkProgressEvent
import com.mimeo.android.player.TtsController
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
fun PlayerScreen(vm: AppViewModel, initialItemId: Int, onOpenItem: (Int) -> Unit) {
    var currentItemId by rememberSaveable { mutableIntStateOf(initialItemId) }
    var resolvedInitial by rememberSaveable { mutableStateOf(false) }
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
    var lastProgressSyncAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedPercent by remember { mutableIntStateOf(-1) }
    var lastSyncedAbsoluteChars by remember { mutableIntStateOf(-1) }
    var lastObservedPercent by remember { mutableIntStateOf(-1) }
    var nearEndForcedForItemId by remember { mutableIntStateOf(-1) }
    val playbackPositionByItem by vm.playbackPositionByItem.collectAsState()
    val currentPosition = playbackPositionByItem[currentItemId] ?: PlaybackPosition()
    val actionScope = rememberCoroutineScope()
    val context = LocalContext.current

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

    LaunchedEffect(currentItemId, resolvedInitial) {
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
                val safe = normalizedPosition(saved)
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
        }
    }

    val safePosition = normalizedPosition(currentPosition)
    val totalChars = totalCharsForPercent()
    val currentPercent = calculateCanonicalPercent(totalChars, chunks, safePosition)
    val currentTitle = textPayload?.title?.ifBlank { null } ?: textPayload?.url.orEmpty()
    val currentChunkText = if (chunks.isNotEmpty()) {
        chunks[safePosition.chunkIndex].text.take(400)
    } else {
        textPayload?.text?.take(400).orEmpty()
    }
    val chunkLabel = if (chunks.isEmpty()) {
        "Chunk 0 / 0"
    } else {
        "Chunk ${safePosition.chunkIndex + 1} / ${chunks.size}"
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Item $currentItemId")
        Text(text = "Progress: $currentPercent%")
        if (showCompleted) {
            Text(text = "Completed")
        }
        Text(text = chunkLabel)
        if (isLoading) CircularProgressIndicator()
        if (currentTitle.isNotBlank()) Text(text = currentTitle)
        if (currentChunkText.isNotBlank()) Text(text = currentChunkText)
        if (usingCachedText) Text(text = "Using cached text")
        uiMessage?.let { Text(text = it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
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
            ) {
                Text("Prev Seg")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
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
            ) {
                Text("Next Seg")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
                onClick = {
                    if (chunks.isNotEmpty()) {
                        isAutoPlaying = true
                        playChunk(safePosition.chunkIndex, safePosition.offsetInChunkChars)
                    }
                },
                enabled = chunks.isNotEmpty(),
            ) {
                Text("Play")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
                onClick = { stopSpeaking(forceSync = true) },
                enabled = chunks.isNotEmpty(),
            ) {
                Text("Pause")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
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
            ) {
                Text("Prev Item")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
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
            ) {
                Text("Next Item")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
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
                                if (error is CancellationException) {
                                    return@onFailure
                                }
                                uiMessage = error.message ?: "Progress update failed"
                            }
                    }
                },
                enabled = textPayload != null,
            ) {
                Text("Done")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
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
