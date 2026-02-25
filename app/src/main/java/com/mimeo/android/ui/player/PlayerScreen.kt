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
import com.mimeo.android.player.TtsController
import kotlinx.coroutines.launch
import kotlin.math.floor

private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val SEGMENT_MAX_CHARS = 700

@Composable
fun PlayerScreen(vm: AppViewModel, initialItemId: Int, onOpenItem: (Int) -> Unit) {
    var currentItemId by rememberSaveable { mutableIntStateOf(initialItemId) }
    var textPayload by remember { mutableStateOf<ItemTextResponse?>(null) }
    var segments by remember { mutableStateOf<List<String>>(emptyList()) }
    var uiMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isAutoPlaying by remember { mutableStateOf(false) }
    var segmentDoneTick by remember { mutableIntStateOf(0) }
    var autoPlayAfterLoad by remember { mutableStateOf(false) }
    var lastProgressSyncAtMs by remember { mutableLongStateOf(0L) }
    val segmentMap by vm.segmentIndexByItem.collectAsState()
    val currentSegmentIndex = segmentMap[currentItemId] ?: 0
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val ttsController = remember {
        TtsController(
            context = context,
            onSegmentDone = { segmentDoneTick += 1 },
            onError = {
                isSpeaking = false
                isAutoPlaying = false
                uiMessage = it
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsController.shutdown()
        }
    }

    fun normalizedSegmentIndex(index: Int): Int {
        if (segments.isEmpty()) return 0
        return index.coerceIn(0, segments.lastIndex)
    }

    fun setSegmentIndex(index: Int) {
        vm.setSegmentIndex(currentItemId, normalizedSegmentIndex(index))
    }

    suspend fun syncProgressForSegment(force: Boolean = false) {
        if (segments.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastProgressSyncAtMs) < PROGRESS_SYNC_DEBOUNCE_MS) return

        val percent = floor((normalizedSegmentIndex(currentSegmentIndex).toDouble() / segments.size.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
        vm.postProgress(currentItemId, percent)
        lastProgressSyncAtMs = now
    }

    fun playCurrentSegment() {
        if (segments.isEmpty()) return
        val index = normalizedSegmentIndex(currentSegmentIndex)
        ttsController.speakSegment(currentItemId, index, segments[index])
        isSpeaking = true
    }

    fun stopSpeaking() {
        ttsController.stop()
        isSpeaking = false
        isAutoPlaying = false
    }

    LaunchedEffect(currentItemId) {
        stopSpeaking()
        isLoading = true
        uiMessage = null
        textPayload = null
        segments = emptyList()
        val result = vm.fetchItemText(currentItemId)
        result.onSuccess { payload ->
            textPayload = payload
            val builtSegments = buildSegments(payload)
            segments = builtSegments
            val savedIndex = vm.getSegmentIndex(currentItemId)
            vm.setSegmentIndex(
                currentItemId,
                if (builtSegments.isEmpty()) 0 else savedIndex.coerceIn(0, builtSegments.lastIndex),
            )
            if (autoPlayAfterLoad && builtSegments.isNotEmpty()) {
                autoPlayAfterLoad = false
                isAutoPlaying = true
                playCurrentSegment()
            }
        }.onFailure { err ->
            uiMessage = if (err is ApiException && err.statusCode == 401) {
                "Unauthorized-check token"
            } else {
                err.message ?: "Failed to load text"
            }
        }
        isLoading = false
    }

    LaunchedEffect(currentSegmentIndex, currentItemId, segments.size) {
        if (segments.isNotEmpty()) {
            syncProgressForSegment(force = false)
        }
    }

    LaunchedEffect(segmentDoneTick) {
        if (segmentDoneTick == 0 || !isAutoPlaying || segments.isEmpty()) return@LaunchedEffect
        val current = normalizedSegmentIndex(vm.getSegmentIndex(currentItemId))
        if (current < segments.lastIndex) {
            setSegmentIndex(current + 1)
            playCurrentSegment()
        } else {
            isSpeaking = false
            isAutoPlaying = false
        }
    }

    val currentTitle = textPayload?.title?.ifBlank { null } ?: textPayload?.url.orEmpty()
    val currentSegmentText = if (segments.isNotEmpty()) {
        segments[normalizedSegmentIndex(currentSegmentIndex)].take(400)
    } else {
        textPayload?.text?.take(400).orEmpty()
    }
    val segmentLabel = if (segments.isEmpty()) {
        "Segment 0 / 0"
    } else {
        "Segment ${normalizedSegmentIndex(currentSegmentIndex) + 1} / ${segments.size}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Item $currentItemId")
        Text(text = segmentLabel)
        if (isLoading) {
            CircularProgressIndicator()
        }
        if (currentTitle.isNotBlank()) {
            Text(text = currentTitle)
        }
        if (currentSegmentText.isNotBlank()) {
            Text(text = currentSegmentText)
        }
        uiMessage?.let { Text(text = it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (segments.isNotEmpty() && normalizedSegmentIndex(currentSegmentIndex) > 0) {
                        setSegmentIndex(normalizedSegmentIndex(currentSegmentIndex) - 1)
                        if (isSpeaking || isAutoPlaying) {
                            isAutoPlaying = true
                            playCurrentSegment()
                        }
                    }
                },
                enabled = segments.size > 1,
            ) {
                Text("Prev Seg")
            }
            Button(
                onClick = {
                    if (segments.isNotEmpty() && normalizedSegmentIndex(currentSegmentIndex) < segments.lastIndex) {
                        setSegmentIndex(normalizedSegmentIndex(currentSegmentIndex) + 1)
                        if (isSpeaking || isAutoPlaying) {
                            isAutoPlaying = true
                            playCurrentSegment()
                        }
                    }
                },
                enabled = segments.size > 1,
            ) {
                Text("Next Seg")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (segments.isNotEmpty()) {
                        isAutoPlaying = true
                        playCurrentSegment()
                    }
                },
                enabled = segments.isNotEmpty(),
            ) {
                Text("Play")
            }
            Button(
                onClick = { stopSpeaking() },
                enabled = segments.isNotEmpty(),
            ) {
                Text("Pause/Stop")
            }
            Button(
                onClick = {
                    val nextId = vm.nextItemId(currentItemId)
                    if (nextId == null) {
                        uiMessage = "No next item"
                    } else {
                        stopSpeaking()
                        currentItemId = nextId
                        vm.setSegmentIndex(nextId, 0)
                        autoPlayAfterLoad = true
                        onOpenItem(nextId)
                    }
                },
                enabled = vm.queueItems.value.isNotEmpty(),
            ) {
                Text("Next Item")
            }
        }

        Button(
            onClick = {
                scope.launch {
                    vm.postProgress(currentItemId, 100)
                        .onSuccess { uiMessage = "Marked done" }
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

private fun buildSegments(payload: ItemTextResponse): List<String> {
    val fromApi = payload.paragraphs
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (fromApi.isNotEmpty()) {
        return fromApi.map(::normalizeWhitespace).flatMap { splitByLength(it, SEGMENT_MAX_CHARS) }
    }

    val blocks = payload.text
        .split(Regex("\\n\\s*\\n+"))
        .map { normalizeWhitespace(it) }
        .filter { it.isNotBlank() }
    return blocks.flatMap { splitByLength(it, SEGMENT_MAX_CHARS) }
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
            result.add(sb.toString().trim())
            sb.clear()
            sb.append(word)
        } else {
            sb.append(' ').append(word)
        }
    }
    if (sb.isNotEmpty()) {
        result.add(sb.toString().trim())
    }
    return result
}
