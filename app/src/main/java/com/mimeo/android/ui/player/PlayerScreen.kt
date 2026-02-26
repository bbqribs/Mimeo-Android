package com.mimeo.android.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.mimeo.android.player.TtsController
import kotlinx.coroutines.launch

private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
private const val FALLBACK_CHUNK_MAX_CHARS = 900

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
    var chunkDoneTick by remember { mutableIntStateOf(0) }
    var autoPlayAfterLoad by remember { mutableStateOf(false) }
    var lastProgressSyncAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedPercent by remember { mutableIntStateOf(-1) }
    var lastSyncedAbsoluteChars by remember { mutableIntStateOf(-1) }
    val playbackPositionByItem by vm.playbackPositionByItem.collectAsState()
    val currentPosition = playbackPositionByItem[currentItemId] ?: PlaybackPosition()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val ttsController = remember {
        TtsController(
            context = context,
            onChunkDone = { chunkDoneTick += 1 },
            onChunkProgress = { spokenOffset ->
                if (chunks.isNotEmpty()) {
                    val safeIndex = currentPosition.chunkIndex.coerceIn(0, chunks.lastIndex)
                    val safeOffset = spokenOffset.coerceIn(0, chunks[safeIndex].length)
                    val nextOffset = safeOffset.coerceAtLeast(currentPosition.offsetInChunkChars)
                    val advancedEnough = nextOffset - currentPosition.offsetInChunkChars >= 20
                    if (safeIndex != currentPosition.chunkIndex || advancedEnough) {
                        vm.setPlaybackPosition(
                            itemId = currentItemId,
                            chunkIndex = safeIndex,
                            offsetInChunkChars = nextOffset,
                        )
                    }
                }
            },
            onError = {
                isSpeaking = false
                isAutoPlaying = false
                uiMessage = it
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

    suspend fun syncProgress(force: Boolean = false) {
        if (chunks.isEmpty()) return
        val now = System.currentTimeMillis()
        val safePosition = normalizedPosition(currentPosition)
        val totalChars = totalCharsForPercent()
        val absolute = absoluteCharOffset(totalChars, chunks, safePosition)
        val percent = calculateCanonicalPercent(totalChars, chunks, safePosition)
        val advancedPercent = percent > lastSyncedPercent
        val advancedChars = (absolute - lastSyncedAbsoluteChars) >= PROGRESS_CHAR_STEP
        val debounced = (now - lastProgressSyncAtMs) < PROGRESS_SYNC_DEBOUNCE_MS

        if (!force && debounced && !advancedPercent && !advancedChars) return
        if (!force && !advancedPercent && !advancedChars) return

        vm.postProgress(currentItemId, percent)
            .onSuccess {
                if (it.queued) uiMessage = "Offline: progress queued"
            }
            .onFailure {
                uiMessage = it.message ?: "Progress sync failed"
            }

        lastProgressSyncAtMs = now
        lastSyncedPercent = percent
        lastSyncedAbsoluteChars = absolute
    }

    fun setPlaybackPosition(chunkIndex: Int, offsetInChunkChars: Int) {
        if (chunks.isEmpty()) {
            vm.setPlaybackPosition(currentItemId, 0, 0)
            return
        }
        val safeIndex = normalizedChunkIndex(chunkIndex)
        val safeOffset = offsetInChunkChars.coerceIn(0, chunks[safeIndex].length)
        vm.setPlaybackPosition(currentItemId, safeIndex, safeOffset)
    }

    fun playCurrentChunk() {
        if (chunks.isEmpty()) return
        val safe = normalizedPosition(currentPosition)
        val chunk = chunks[safe.chunkIndex]
        val startAt = safe.offsetInChunkChars.coerceIn(0, chunk.length)
        val speakText = if (startAt > 0 && startAt < chunk.text.length) {
            chunk.text.substring(startAt)
        } else {
            chunk.text
        }
        ttsController.speakChunk(
            itemId = currentItemId,
            chunkIndex = safe.chunkIndex,
            text = speakText,
            baseOffset = startAt,
        )
        isSpeaking = true
    }

    fun stopSpeaking(forceSync: Boolean) {
        ttsController.stop()
        isSpeaking = false
        isAutoPlaying = false
        if (forceSync) {
            scope.launch { syncProgress(force = true) }
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
        lastSyncedPercent = -1
        lastSyncedAbsoluteChars = -1
        lastProgressSyncAtMs = 0L

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
                    playCurrentChunk()
                }
            }
            .onFailure { err ->
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

    LaunchedEffect(chunkDoneTick) {
        if (chunkDoneTick == 0 || !isAutoPlaying || chunks.isEmpty()) return@LaunchedEffect
        val safe = normalizedPosition(currentPosition)
        val finished = chunks[safe.chunkIndex]
        setPlaybackPosition(safe.chunkIndex, finished.length)

        if (safe.chunkIndex < chunks.lastIndex) {
            setPlaybackPosition(safe.chunkIndex + 1, 0)
            playCurrentChunk()
        } else {
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Item $currentItemId")
        Text(text = "Progress: $currentPercent%")
        Text(text = chunkLabel)
        if (isLoading) CircularProgressIndicator()
        if (currentTitle.isNotBlank()) Text(text = currentTitle)
        if (currentChunkText.isNotBlank()) Text(text = currentChunkText)
        if (usingCachedText) Text(text = "Using cached text")
        uiMessage?.let { Text(text = it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (chunks.isNotEmpty() && safePosition.chunkIndex > 0) {
                        setPlaybackPosition(safePosition.chunkIndex - 1, 0)
                        if (isSpeaking || isAutoPlaying) {
                            isAutoPlaying = true
                            playCurrentChunk()
                        }
                    }
                },
                enabled = chunks.size > 1,
            ) {
                Text("Prev Seg")
            }
            Button(
                onClick = {
                    if (chunks.isNotEmpty() && safePosition.chunkIndex < chunks.lastIndex) {
                        setPlaybackPosition(safePosition.chunkIndex + 1, 0)
                        if (isSpeaking || isAutoPlaying) {
                            isAutoPlaying = true
                            playCurrentChunk()
                        }
                    }
                },
                enabled = chunks.size > 1,
            ) {
                Text("Next Seg")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (chunks.isNotEmpty()) {
                        isAutoPlaying = true
                        playCurrentChunk()
                    }
                },
                enabled = chunks.isNotEmpty(),
            ) {
                Text("Play")
            }
            Button(
                onClick = { stopSpeaking(forceSync = true) },
                enabled = chunks.isNotEmpty(),
            ) {
                Text("Pause/Stop")
            }
            Button(
                onClick = {
                    scope.launch {
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
                onClick = {
                    scope.launch {
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
        }

        Button(
            onClick = {
                scope.launch {
                    vm.postProgress(currentItemId, 100)
                        .onSuccess {
                            uiMessage = if (it.queued) "Done queued for sync" else "Marked done"
                            if (chunks.isNotEmpty()) {
                                val last = chunks.last()
                                vm.setPlaybackPosition(currentItemId, chunks.lastIndex, last.length)
                            }
                        }
                        .onFailure { uiMessage = it.message ?: "Progress update failed" }
                }
            },
            enabled = textPayload != null,
        ) {
            Text("Done")
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
