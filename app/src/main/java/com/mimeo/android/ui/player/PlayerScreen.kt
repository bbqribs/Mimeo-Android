package com.mimeo.android.ui.player

import android.os.Build
import android.os.SystemClock
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.AppViewModel
import com.mimeo.android.ArchiveActionSource
import com.mimeo.android.BuildConfig
import com.mimeo.android.R
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.LocusContentMode
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.ProblemReportCategory
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.absoluteCharOffset
import com.mimeo.android.model.calculateCanonicalPercent
import com.mimeo.android.model.positionFromAbsoluteOffset
import com.mimeo.android.player.TtsChunkDoneEvent
import com.mimeo.android.player.TtsChunkProgressEvent
import com.mimeo.android.player.SOURCE_CUE_CHUNK_INDEX
import com.mimeo.android.player.TITLE_INTRO_CHUNK_INDEX
import com.mimeo.android.player.TtsController
import com.mimeo.android.ui.common.locusCapturePresentation
import com.mimeo.android.ui.common.copyItemText
import com.mimeo.android.ui.common.openItemInBrowser
import com.mimeo.android.ui.common.shareItemText
import com.mimeo.android.ui.common.shareItemUrl
import com.mimeo.android.ui.common.shareSelectedText
import com.mimeo.android.ui.reader.ReaderTextToolbar
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.playlists.PlaylistPickerChoice
import com.mimeo.android.ui.playlists.PlaylistPickerDialog
import com.mimeo.android.ui.reader.ReaderBody
import com.mimeo.android.ui.reader.extractReaderPreservedLinks
import com.mimeo.android.ui.reader.segmentSentences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private const val DEBUG_PLAYBACK = false
private const val PROGRESS_SYNC_DEBOUNCE_MS = 2_000L
private const val PROGRESS_CHAR_STEP = 120
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
private const val ACTION_KEY_UNDO_ARCHIVE = "undo_archive"
private const val MANUAL_SCROLL_TAP_REATTACH_BLOCK_MS = 450L

private fun nextStandardScrollTriggerSignal(current: Int): Int {
    val base = kotlin.math.abs(current) + 1
    return if (base % 2 == 0) base else base + 1
}

private fun nextForceReattachScrollTriggerSignal(current: Int): Int {
    val base = kotlin.math.abs(current) + 1
    return if (base % 2 == 1) base else base + 1
}

internal data class LocusSearchMatch(
    val chunkIndex: Int,
    val rangeInChunk: IntRange,
)

internal data class LocusProblemReportContext(
    val itemId: Int?,
    val url: String?,
    val sourceType: String?,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val captureKind: String?,
    val articleTitle: String?,
    val articleText: String?,
)

internal fun resolveDefaultProblemReportCategory(
    itemId: Int?,
    url: String?,
): ProblemReportCategory {
    return when {
        (itemId ?: -1) > 0 -> ProblemReportCategory.CONTENT_PROBLEM
        !url.isNullOrBlank() -> ProblemReportCategory.SAVE_FAILURE
        else -> ProblemReportCategory.APP_PROBLEM
    }
}

internal fun resolveProblemReportFailureMessage(error: Throwable): String {
    if (error is ApiException) {
        return when (error.statusCode) {
            401, 403 -> "Sign in to submit problem reports."
            429 -> "Too many reports sent recently. Please try again later."
            else -> "Couldn't send problem report. Please try again."
        }
    }
    return if (isNetworkError(error)) {
        "Couldn't reach server. Check connection and try again."
    } else {
        "Couldn't send problem report. Please try again."
    }
}

internal fun collectLocusSearchMatches(
    chunks: List<PlaybackChunk>,
    query: String,
): List<LocusSearchMatch> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val loweredNeedle = needle.lowercase(Locale.ROOT)
    val matches = mutableListOf<LocusSearchMatch>()
    chunks.forEachIndexed { index, chunk ->
        val haystack = chunk.text
        if (haystack.isBlank()) return@forEachIndexed
        val loweredHaystack = haystack.lowercase(Locale.ROOT)
        var from = 0
        while (true) {
            val at = loweredHaystack.indexOf(loweredNeedle, startIndex = from)
            if (at < 0) break
            val endExclusive = (at + loweredNeedle.length).coerceAtMost(haystack.length)
            matches += LocusSearchMatch(
                chunkIndex = index,
                rangeInChunk = at until endExclusive,
            )
            from = (at + 1).coerceAtMost(loweredHaystack.length)
        }
    }
    return matches
}

enum class PlaybackOpenIntent {
    ManualOpen,
    AutoContinue,
    Replay,
}

data class PlaybackOpenDiagnosticsSnapshot(
    val itemId: Int,
    val requestedItemId: Int?,
    val openIntent: PlaybackOpenIntent,
    val startSource: String,
    val knownProgress: Int,
    val seededPosition: PlaybackPosition,
)

internal data class PlaybackObservabilityUiState(
    val currentItemId: Int,
    val requestedItemId: Int?,
    val openIntent: PlaybackOpenIntent?,
    val startSource: String?,
    val knownProgress: Int?,
    val seededChunk: Int?,
    val seededOffset: Int?,
    val handoffPending: Boolean,
    val handoffSettled: Boolean,
    val autoPath: Boolean,
)

internal fun resolveOpenStartSource(
    openIntent: PlaybackOpenIntent,
    knownProgress: Int,
    hasChunks: Boolean,
): String {
    return when (openIntent) {
        PlaybackOpenIntent.AutoContinue -> "autocontinue:start_of_item"
        PlaybackOpenIntent.Replay -> "replay:start_of_item"
        PlaybackOpenIntent.ManualOpen -> {
            if (knownProgress > 0 && hasChunks) {
                "manual:queue_progress_percent"
            } else {
                "manual:start_of_item"
            }
        }
    }
}

internal fun playbackObservabilityLines(state: PlaybackObservabilityUiState): List<String> {
    val openIntentLabel = state.openIntent?.name ?: "pending"
    val sourceLabel = state.startSource ?: "pending"
    val knownProgressLabel = state.knownProgress?.toString() ?: "pending"
    val seededChunkLabel = state.seededChunk?.toString() ?: "pending"
    val seededOffsetLabel = state.seededOffset?.toString() ?: "pending"
    return listOf(
        "item current=${state.currentItemId} requested=${state.requestedItemId ?: "none"}",
        "open_intent=$openIntentLabel auto_path=${state.autoPath}",
        "start_source=$sourceLabel known_progress=$knownProgressLabel",
        "seed chunk=$seededChunkLabel offset=$seededOffsetLabel",
        "handoff pending=${state.handoffPending} settled=${state.handoffSettled}",
    )
}

internal fun shouldShowReaderLoadingPlaceholder(
    waitingForRequestedItem: Boolean,
    hasStalePayloadForCurrentItem: Boolean,
    isLoading: Boolean,
    transitionSettled: Boolean,
): Boolean {
    return waitingForRequestedItem ||
        hasStalePayloadForCurrentItem ||
        isLoading ||
        !transitionSettled
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

internal fun shouldAcceptDoneEventChunk(eventChunkIndex: Int, currentChunkIndex: Int): Boolean {
    return eventChunkIndex == TITLE_INTRO_CHUNK_INDEX ||
        eventChunkIndex == SOURCE_CUE_CHUNK_INDEX ||
        eventChunkIndex == currentChunkIndex
}

internal fun shouldPlayEndOfArticleCompletionCue(enabled: Boolean): Boolean {
    return enabled
}

internal fun updateReaderScrollOffsets(
    offsets: Map<Int, Int>,
    itemId: Int,
    offset: Int,
): Map<Int, Int> {
    if (itemId <= 0) return offsets
    val safeOffset = offset.coerceAtLeast(0)
    val current = offsets[itemId]
    if (current == safeOffset) return offsets
    return offsets + (itemId to safeOffset)
}

internal fun resetReaderScrollOffset(
    offsets: Map<Int, Int>,
    itemId: Int,
): Map<Int, Int> {
    if (itemId <= 0) return offsets
    if (!offsets.containsKey(itemId)) return offsets
    return offsets - itemId
}

internal data class LocusTabTapAction(
    val returnToNowPlayingItem: Boolean,
    val triggerScrollToPlaybackImmediately: Boolean,
    val triggerScrollToPlaybackAfterReturn: Boolean,
)

internal fun resolveLocusTabTapAction(
    previewModeActive: Boolean,
    currentItemId: Int,
    returnToPlaybackPositionAfterPreview: Boolean,
): LocusTabTapAction {
    if (previewModeActive && currentItemId > 0) {
        return LocusTabTapAction(
            returnToNowPlayingItem = true,
            triggerScrollToPlaybackImmediately = false,
            triggerScrollToPlaybackAfterReturn = returnToPlaybackPositionAfterPreview,
        )
    }
    return LocusTabTapAction(
        returnToNowPlayingItem = false,
        triggerScrollToPlaybackImmediately = true,
        triggerScrollToPlaybackAfterReturn = false,
    )
}

internal fun resolveLocusPlaybackOwnerItemId(
    engineCurrentItemId: Int,
    sessionCurrentItemId: Int?,
    fallbackItemId: Int,
): Int {
    if (engineCurrentItemId > 0) return engineCurrentItemId
    val sessionId = sessionCurrentItemId ?: -1
    if (sessionId > 0) return sessionId
    return fallbackItemId
}

internal fun resolveLocusActionBarTitle(
    playbackActive: Boolean,
    playbackOwnerItemId: Int,
    playbackOwnerTitle: String,
    playbackOwnerUrl: String,
    previewModeActive: Boolean,
    previewTitle: String,
    fallbackItemId: Int,
): String {
    if (playbackActive && playbackOwnerItemId > 0) {
        return playbackOwnerTitle
            .ifBlank { playbackOwnerUrl }
            .ifBlank { "Item $playbackOwnerItemId" }
    }
    if (previewModeActive) {
        return previewTitle.ifBlank { "Item $fallbackItemId" }
    }
    return playbackOwnerTitle
        .ifBlank { previewTitle }
        .ifBlank { "Item $fallbackItemId" }
}

internal fun shouldPreservePlaybackOwnerForPreviewOpen(
    targetItemId: Int,
    currentItemId: Int,
    playbackActive: Boolean,
): Boolean {
    if (targetItemId <= 0 || currentItemId <= 0) return false
    if (targetItemId == currentItemId) return false
    return playbackActive
}

internal enum class RequestedItemTransitionMode {
    AlreadyCurrent,
    PreviewOnly,
    ReplaceCurrent,
}

internal fun resolveRequestedItemTransitionMode(
    targetItemId: Int,
    currentItemId: Int,
    playbackActive: Boolean,
    hasLockedPlaybackOwner: Boolean,
): RequestedItemTransitionMode {
    if (targetItemId <= 0 || targetItemId == currentItemId) {
        return RequestedItemTransitionMode.AlreadyCurrent
    }
    if (hasLockedPlaybackOwner) {
        return RequestedItemTransitionMode.PreviewOnly
    }
    return if (
        shouldPreservePlaybackOwnerForPreviewOpen(
            targetItemId = targetItemId,
            currentItemId = currentItemId,
            playbackActive = playbackActive,
        )
    ) {
        RequestedItemTransitionMode.PreviewOnly
    } else {
        RequestedItemTransitionMode.ReplaceCurrent
    }
}

internal fun shouldSpeakTitleBeforeBody(
    enabled: Boolean,
    title: String?,
    chunks: List<PlaybackChunk>,
): Boolean {
    if (!enabled) return false
    val cleanTitle = title?.trim().orEmpty()
    if (cleanTitle.isBlank()) return false
    return chunks.firstOrNull()?.text.orEmpty().isNotBlank()
}

internal fun buildSourceCueSpeechText(
    sourceLabel: String?,
    sourceType: String?,
    host: String?,
    url: String?,
): String? {
    val normalizedLabel = sourceLabel?.trim()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    val normalizedHost = host?.trim()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    val urlHost = runCatching { java.net.URI(url).host }.getOrNull()
        ?.trim()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotEmpty() }
    val candidates = listOfNotNull(normalizedLabel, normalizedHost, urlHost)
    val genericLabels = setOf("unknown source", "android selection", "app share", "shared-text.mimeo.local")
    val packageLikePattern = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+){2,}$")
    val chosen = candidates.firstOrNull { candidate ->
        val lowered = candidate.lowercase(Locale.US)
        lowered !in genericLabels &&
            candidate.length <= 64 &&
            !packageLikePattern.matches(lowered)
    } ?: return null
    val punctuated = chosen.trimEnd('.', '!', '?')
    if (punctuated.isBlank()) return null
    return "From $punctuated."
}

internal fun resolveSentenceJumpPosition(
    chunks: List<PlaybackChunk>,
    currentPosition: PlaybackPosition,
    direction: Int,
): PlaybackPosition? {
    if (chunks.isEmpty()) return null
    val safeDirection = direction.coerceIn(-1, 1)
    if (safeDirection == 0) return null
    val safeChunkIndex = currentPosition.chunkIndex.coerceIn(0, chunks.lastIndex)
    val safeOffset = currentPosition.offsetInChunkChars.coerceAtLeast(0)

    fun sentenceStartOffsets(chunkText: String): List<Int> {
        val ranges = segmentSentences(chunkText)
        if (ranges.isEmpty()) return if (chunkText.isBlank()) emptyList() else listOf(0)
        return ranges.map { it.start.coerceIn(0, chunkText.length) }
    }

    if (safeDirection > 0) {
        var chunkIndex = safeChunkIndex
        while (chunkIndex <= chunks.lastIndex) {
            val starts = sentenceStartOffsets(chunks[chunkIndex].text)
            val targetStart = if (chunkIndex == safeChunkIndex) {
                starts.firstOrNull { it > safeOffset }
            } else {
                starts.firstOrNull()
            }
            if (targetStart != null) {
                return PlaybackPosition(chunkIndex = chunkIndex, offsetInChunkChars = targetStart)
            }
            chunkIndex += 1
        }
        return null
    }

    var chunkIndex = safeChunkIndex
    while (chunkIndex >= 0) {
        val starts = sentenceStartOffsets(chunks[chunkIndex].text)
        val targetStart = if (chunkIndex == safeChunkIndex) {
            starts.lastOrNull { it < safeOffset }
        } else {
            starts.lastOrNull()
        }
        if (targetStart != null) {
            return PlaybackPosition(chunkIndex = chunkIndex, offsetInChunkChars = targetStart)
        }
        chunkIndex -= 1
    }
    return null
}

internal fun resolveParagraphJumpPosition(
    chunks: List<PlaybackChunk>,
    currentPosition: PlaybackPosition,
    direction: Int,
): PlaybackPosition? {
    if (chunks.isEmpty()) return null
    val safeDirection = direction.coerceIn(-1, 1)
    if (safeDirection == 0) return null
    val safeChunkIndex = currentPosition.chunkIndex.coerceIn(0, chunks.lastIndex)

    if (safeDirection > 0) {
        var idx = safeChunkIndex + 1
        while (idx <= chunks.lastIndex) {
            if (chunks[idx].text.isNotBlank()) {
                return PlaybackPosition(chunkIndex = idx, offsetInChunkChars = 0)
            }
            idx += 1
        }
        return null
    }

    var idx = safeChunkIndex - 1
    while (idx >= 0) {
        if (chunks[idx].text.isNotBlank()) {
            return PlaybackPosition(chunkIndex = idx, offsetInChunkChars = 0)
        }
        idx -= 1
    }
    return null
}

internal fun shouldSkipInitialReopen(
    resolvedItemId: Int,
    currentItemId: Int,
    engineCurrentItemId: Int,
    autoPlayAfterLoad: Boolean,
    isSpeaking: Boolean,
    isAutoPlaying: Boolean,
): Boolean {
    if (engineCurrentItemId <= 0) return false
    if (resolvedItemId <= 0 || currentItemId <= 0) return false
    if (resolvedItemId != currentItemId) return false
    if (engineCurrentItemId != resolvedItemId) return false
    // Only skip reopen when playback is already active/continuing.
    // If paused, we still need a reopen to prime the engine so Play works.
    return autoPlayAfterLoad || isSpeaking || isAutoPlaying
}

internal fun shouldPreserveActivePlaybackDuringLoad(
    autoPlayAfterLoad: Boolean,
    isSpeaking: Boolean,
    isAutoPlaying: Boolean,
): Boolean {
    return autoPlayAfterLoad || isSpeaking || isAutoPlaying
}

internal fun shouldSkipSurfaceHandoffReload(
    currentItemId: Int,
    payloadItemId: Int?,
    chunkCount: Int,
    autoPlayAfterLoad: Boolean,
    isSpeaking: Boolean,
    isAutoPlaying: Boolean,
): Boolean {
    if (currentItemId <= 0) return false
    if (payloadItemId != currentItemId) return false
    if (chunkCount <= 0) return false
    return autoPlayAfterLoad || isSpeaking || isAutoPlaying
}

internal fun shouldUseTitleIntroOnPlaybackStart(
    allowTitleIntro: Boolean,
    hasStartedPlaybackForItem: Boolean,
    speakTitleBeforeArticleEnabled: Boolean,
    startPosition: PlaybackPosition,
    title: String?,
    chunks: List<PlaybackChunk>,
): Boolean {
    if (!allowTitleIntro) return false
    if (hasStartedPlaybackForItem) return false
    if (startPosition.chunkIndex != 0 || startPosition.offsetInChunkChars != 0) return false
    return shouldSpeakTitleBeforeBody(
        enabled = speakTitleBeforeArticleEnabled,
        title = title,
        chunks = chunks,
    )
}

internal fun isTitleDuplicateOfOpeningText(title: String, openingText: String): Boolean {
    val normalizedTitle = normalizeIntroComparisonText(title)
    val normalizedOpening = normalizeIntroComparisonText(openingText)
    if (normalizedTitle.isBlank() || normalizedOpening.isBlank()) return false
    if (normalizedOpening.startsWith(normalizedTitle)) return true
    if (normalizedTitle.startsWith(normalizedOpening) && normalizedOpening.length >= 24) return true
    return false
}

internal fun computeTitlePrefixSkipChars(
    title: String?,
    openingText: String,
    minMatchedWords: Int = 3,
): Int {
    val cleanTitle = title?.trim().orEmpty()
    if (cleanTitle.isBlank()) return 0
    if (openingText.isBlank()) return 0

    val titleWords = tokenizeWordsWithEndOffsets(cleanTitle)
    val openingWords = tokenizeWordsWithEndOffsets(openingText)
    if (titleWords.isEmpty() || openingWords.isEmpty()) return 0

    var titleIndex = 0
    var openingIndex = 0
    var matched = 0
    var lastMatchedOpeningIndex = -1
    while (titleIndex < titleWords.size && openingIndex < openingWords.size) {
        val titleWord = titleWords[titleIndex].word
        val openingWord = openingWords[openingIndex].word
        if (titleWord == openingWord) {
            matched += 1
            lastMatchedOpeningIndex = openingIndex
            titleIndex += 1
            openingIndex += 1
            continue
        }
        if (isSkippableOpeningFillerWord(openingWord)) {
            openingIndex += 1
            continue
        }
        break
    }
    if (matched < minMatchedWords) return 0
    if (lastMatchedOpeningIndex < 0) return 0

    var skipTo = openingWords[lastMatchedOpeningIndex].endExclusive
    while (skipTo < openingText.length && !openingText[skipTo].isLetterOrDigit()) {
        skipTo += 1
    }
    return skipTo.coerceIn(0, openingText.length)
}

internal fun applyTitlePrefixSkipToStartPosition(
    start: PlaybackPosition,
    chunks: List<PlaybackChunk>,
    skipCharsFromOpening: Int,
): PlaybackPosition {
    if (skipCharsFromOpening <= 0) return start
    if (chunks.isEmpty()) return start
    if (start.chunkIndex != 0 || start.offsetInChunkChars != 0) return start

    var remaining = skipCharsFromOpening
    var chunkIndex = 0
    while (chunkIndex < chunks.size) {
        val len = playableChunkLength(chunks[chunkIndex])
        if (remaining < len) {
            return PlaybackPosition(chunkIndex = chunkIndex, offsetInChunkChars = remaining)
        }
        remaining -= len
        chunkIndex += 1
    }
    return PlaybackPosition(chunkIndex = chunks.lastIndex, offsetInChunkChars = 0)
}

private data class WordToken(
    val word: String,
    val endExclusive: Int,
)

private fun isSkippableOpeningFillerWord(word: String): Boolean {
    return word in setOf("a", "an", "the", "is", "are", "was", "were", "to", "of", "and")
}

private fun tokenizeWordsWithEndOffsets(input: String): List<WordToken> {
    val matches = Regex("[\\p{L}\\p{N}']+").findAll(input)
    return matches.map { match ->
        WordToken(
            word = match.value.lowercase(Locale.US),
            endExclusive = match.range.last + 1,
        )
    }.toList()
}

private fun normalizeIntroComparisonText(value: String): String {
    return value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
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

internal fun toggledLocusContentMode(current: LocusContentMode): LocusContentMode {
    return when (current) {
        LocusContentMode.FULL_TEXT -> LocusContentMode.FULL_TEXT_WITH_PLAYER
        LocusContentMode.FULL_TEXT_WITH_PLAYER -> LocusContentMode.PLAYBACK_FOCUSED
        LocusContentMode.PLAYBACK_FOCUSED -> LocusContentMode.FULL_TEXT
    }
}

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    initialItemId: Int,
    requestedItemId: Int? = null,
    locusTapSignal: Int = 0,
    openRequestSignal: Int = 0,
    onOpenItem: (Int) -> Unit,
    onOpenLocusForItem: (Int) -> Unit,
    onRequestBack: () -> Unit = {},
    onOpenDiagnostics: () -> Unit,
    compactControlsOnly: Boolean = false,
    showCompactControls: Boolean = true,
    controlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    lastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    chevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.RIGHT,
    onControlsModeChange: (PlayerControlsMode, PlayerControlsMode) -> Unit = { _, _ -> },
    onPlaybackActiveChange: (Boolean) -> Unit = {},
    onManualReadingActiveChange: (Boolean) -> Unit = {},
    onPlaybackProgressPercentChange: (Int) -> Unit = {},
    onReaderChromeVisibilityChange: (Boolean) -> Unit = {},
    onChevronSnapChange: (PlayerChevronSnapEdge) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engineState by vm.playbackEngineState.collectAsState()
    val currentItemId = if (engineState.currentItemId > 0) engineState.currentItemId else initialItemId
    val sharedContentState = vm.playerSurfaceContentState
    var resolvedInitial by rememberSaveable(initialItemId) { mutableStateOf(false) }
    val reloadNonce = engineState.reloadNonce
    var textPayload by sharedContentState.textPayload
    var viewerOverrideItemId by rememberSaveable { mutableIntStateOf(-1) }
    var viewerOverrideTitle by rememberSaveable { mutableStateOf("") }
    var viewerPayload by remember { mutableStateOf<ItemTextResponse?>(null) }
    var viewerPayloadItemId by rememberSaveable { mutableIntStateOf(-1) }
    var viewerChunks by remember { mutableStateOf<List<PlaybackChunk>>(emptyList()) }
    var usingCachedText by sharedContentState.usingCachedText
    var chunks by sharedContentState.chunks
    var uiMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by sharedContentState.isLoading
    val isSpeaking = engineState.isSpeaking
    val isAutoPlaying = engineState.isAutoPlaying
    val autoPlayAfterLoad = engineState.autoPlayAfterLoad
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var refreshActionState by remember { mutableStateOf(RefreshActionVisualState.Idle) }
    var hasRefreshProblem by rememberSaveable { mutableStateOf(false) }
    var preserveVisibleContentOnReload by sharedContentState.preserveVisibleContentOnReload
    var bodyRevealReady by sharedContentState.bodyRevealReady
    var localDonePercentOverride by rememberSaveable(initialItemId) { mutableIntStateOf(-1) }
    val activeChunkRange = engineState.activeChunkRange
    var readerScrollTriggerSignal by rememberSaveable { mutableIntStateOf(0) }
    var lastReaderManualScrollAtMs by remember { mutableLongStateOf(0L) }
    var readerSelectionResetSignal by rememberSaveable { mutableIntStateOf(0) }
    var selectionClearArmed by rememberSaveable { mutableStateOf(false) }
    var lastHandledLocusTapSignal by rememberSaveable { mutableIntStateOf(locusTapSignal) }
    var pendingLocusTabPlaybackScrollAfterReturn by rememberSaveable { mutableStateOf(false) }
    var lastHandledOpenRequestSignal by rememberSaveable { mutableIntStateOf(openRequestSignal) }
    val lastOpenDiagnostics = engineState.lastOpenDiagnostics
    var lastObservedPercent by remember { mutableIntStateOf(-1) }
    var nearEndForcedForItemId by remember { mutableIntStateOf(-1) }
    val queueOffline by vm.queueOffline.collectAsState()
    val syncBadgeState by vm.progressSyncBadgeState.collectAsState()
    val settings by vm.settings.collectAsState()
    val locusContentMode = settings.locusContentMode
    val readerModeEnabled = locusContentMode != LocusContentMode.PLAYBACK_FOCUSED
    val immersiveReaderMode = locusContentMode == LocusContentMode.FULL_TEXT
    val actionBarHiddenByMode = readerModeEnabled
    val playerDockHiddenByMode = immersiveReaderMode
    val queueItems by vm.queueItems.collectAsState()
    val archivedItems by vm.archivedItems.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val queueItemsById = remember(queueItems) { queueItems.associateBy { it.itemId } }
    val archivedItemIdSet = remember(archivedItems) { archivedItems.mapTo(hashSetOf()) { it.itemId } }
    val hasLockedPlaybackOwner =
        currentItemId > 0 &&
            (
                engineState.hasStartedPlaybackForCurrentItem ||
                    isSpeaking ||
                    isAutoPlaying
                )
    // Route item IDs can temporarily lag behind session ownership during auto-continue.
    // Preview mode should only be driven by an explicit viewer override, not raw route mismatch.
    val sessionCurrentItemId = nowPlayingSession?.currentItem?.itemId
    val playbackOwnerItemId = resolveLocusPlaybackOwnerItemId(
        engineCurrentItemId = engineState.currentItemId,
        sessionCurrentItemId = sessionCurrentItemId,
        fallbackItemId = currentItemId,
    )
    val previewItemId = viewerOverrideItemId.takeIf { it > 0 }
    val previewModeActive = previewItemId != null
    val readerScrollItemId = previewItemId ?: currentItemId
    // Locus scroll persistence rules:
    // - Persist per displayed item (including preview mode) so tab/surface returns feel stable.
    // - Reset for explicit same-item reopen requests, which should behave like a fresh open.
    // - Playback/search driven scroll events can still override via existing trigger signals.
    // - Locus-tab return from preview follows settings:
    //   default -> restore reader position, optional -> jump to playback pointer.
    var readerViewportSessionNonce by rememberSaveable { mutableIntStateOf(0) }
    var readerScrollOffsets by rememberSaveable { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var persistedReaderScrollOffsets by rememberSaveable { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var locusTopOverlayHeightPx by remember { mutableIntStateOf(0) }
    var locusBottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val persistedReaderOffset = nowPlayingSession
        ?.items
        ?.firstOrNull { it.itemId == readerScrollItemId }
        ?.readerScrollOffset
        ?.coerceAtLeast(0)
    val readerInitialOffset = readerScrollOffsets[readerScrollItemId]?.coerceAtLeast(0)
        ?: persistedReaderOffset
        ?: 0
    val readerScrollState = rememberSaveable(readerScrollItemId, readerViewportSessionNonce, saver = ScrollState.Saver) {
        ScrollState(readerInitialOffset)
    }
    val density = LocalDensity.current
    val waitingForRequestedItem =
        requestedItemId != null &&
            requestedItemId != currentItemId &&
            !previewModeActive &&
            !autoPlayAfterLoad &&
            !hasLockedPlaybackOwner
    val hasStalePayloadForCurrentItem =
        textPayload?.itemId?.let { it != currentItemId } == true
    val shouldHideForStalePayload =
        hasStalePayloadForCurrentItem &&
            !autoPlayAfterLoad &&
            !hasLockedPlaybackOwner
    val transitionSettled = !waitingForRequestedItem && !shouldHideForStalePayload && bodyRevealReady
    val bodyContentAlpha by animateFloatAsState(
        targetValue = if (transitionSettled) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "locusBodyAlpha",
    )
    val currentPosition = engineState.currentPosition
    val actionScope = rememberCoroutineScope()
    val view = LocalView.current
    val textToolbar = remember(view) {
        ReaderTextToolbar(view, context) { selectedText ->
            shareSelectedText(context, selectedText)
        }
    }
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
    val readerChromeHidden = !compactControlsOnly && immersiveReaderMode
    LaunchedEffect(textToolbar) {
        snapshotFlow { textToolbar.status }.collect { status ->
            if (status == TextToolbarStatus.Shown) {
                selectionClearArmed = true
            }
        }
    }
    LaunchedEffect(textToolbar) {
        while (true) {
            val speed = textToolbar.edgeScrollSpeed
            if (speed != 0f) readerScrollState.scrollBy(speed)
            delay(16L)
        }
    }
    DisposableEffect(textToolbar) {
        onDispose { textToolbar.dispose() }
    }
    fun clearActiveSelection() {
        textToolbar.hide()
        readerSelectionResetSignal += 1
        selectionClearArmed = false
    }
    BackHandler(enabled = !compactControlsOnly) {
        if (selectionClearArmed || hasActiveSelection) {
            clearActiveSelection()
        } else {
            onRequestBack()
        }
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

    fun stopSpeaking(forceSync: Boolean) {
        vm.playbackPause(forceSync = forceSync)
    }

    LaunchedEffect(initialItemId, requestedItemId) {
        if (resolvedInitial) return@LaunchedEffect
        val resolvedId = requestedItemId ?: vm.resolveInitialPlayerItemId(initialItemId)
        if (hasLockedPlaybackOwner && resolvedId != currentItemId) {
            continuationLog(
                "initialResolve previewOnly resolved=$resolvedId current=$currentItemId " +
                    "speaking=$isSpeaking auto=$isAutoPlaying",
            )
            resolvedInitial = true
            return@LaunchedEffect
        }
        val attachToActiveSession = shouldSkipInitialReopen(
            resolvedItemId = resolvedId,
            currentItemId = currentItemId,
            engineCurrentItemId = engineState.currentItemId,
            autoPlayAfterLoad = autoPlayAfterLoad,
            isSpeaking = isSpeaking,
            isAutoPlaying = isAutoPlaying,
        )
        if (attachToActiveSession) {
            continuationLog(
                "initialResolve skipReopen resolved=$resolvedId current=$currentItemId " +
                    "autoPlayAfterLoad=$autoPlayAfterLoad speaking=$isSpeaking auto=$isAutoPlaying",
            )
            resolvedInitial = true
            return@LaunchedEffect
        }
        val initialIntent = if (vm.isItemCompletedForPlaybackStart(resolvedId)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        continuationLog(
            "initialResolve initial=$initialItemId requested=$requestedItemId resolved=$resolvedId",
        )
        vm.playbackOpenItem(
            itemId = resolvedId,
            intent = initialIntent,
            autoPlayAfterLoad = false,
        )
        resolvedInitial = true
    }

    LaunchedEffect(requestedItemId, resolvedInitial) {
        if (!resolvedInitial) return@LaunchedEffect
        val target = requestedItemId ?: return@LaunchedEffect
        val playbackActiveNow = isSpeaking || isAutoPlaying || autoPlayAfterLoad
        val requestedItemTransitionMode = resolveRequestedItemTransitionMode(
            targetItemId = target,
            currentItemId = currentItemId,
            playbackActive = playbackActiveNow,
            hasLockedPlaybackOwner = hasLockedPlaybackOwner,
        )
        if (requestedItemTransitionMode == RequestedItemTransitionMode.AlreadyCurrent) {
            viewerOverrideItemId = -1
            viewerOverrideTitle = ""
            viewerPayload = null
            viewerPayloadItemId = -1
            viewerChunks = emptyList()
            if (pendingLocusTabPlaybackScrollAfterReturn) {
                pendingLocusTabPlaybackScrollAfterReturn = false
                readerScrollTriggerSignal = nextForceReattachScrollTriggerSignal(readerScrollTriggerSignal)
            }
            return@LaunchedEffect
        }
        if (requestedItemTransitionMode == RequestedItemTransitionMode.PreviewOnly) {
            continuationLog(
                "requestedItemEffect previewOnly target=$target current=$currentItemId speaking=$isSpeaking auto=$isAutoPlaying locked=$hasLockedPlaybackOwner",
            )
            // Keep the currently playing reader surface stable while preview content loads.
            preserveVisibleContentOnReload = true
            bodyRevealReady = true
            isLoading = false
            viewerOverrideItemId = target
            viewerOverrideTitle = queueItemsById[target]?.title.orEmpty()
            viewerPayload = null
            viewerPayloadItemId = -1
            viewerChunks = emptyList()
            val preferLocalPreviewLoad = queueOffline || vm.isItemCached(target)
            val previewLoadTag = if (preferLocalPreviewLoad) {
                "locus_preview_cache_first"
            } else {
                "locus_preview_network_first"
            }
            vm.fetchItemText(
                target,
                preferLocal = preferLocalPreviewLoad,
                loadPolicyTag = previewLoadTag,
            )
                .onSuccess { loaded ->
                    viewerPayload = loaded.payload
                    viewerPayloadItemId = target
                    viewerChunks = buildPlaybackChunks(loaded.payload)
                }
                .onFailure { err ->
                    if (err is CancellationException) return@onFailure
                    uiMessage = err.message ?: "Failed to load item"
                }
            return@LaunchedEffect
        }
        continuationLog(
            "requestedItemEffect target=$target current=$currentItemId autoPlayAfterLoad=$autoPlayAfterLoad",
        )
        viewerOverrideItemId = -1
        viewerOverrideTitle = ""
        viewerPayload = null
        viewerPayloadItemId = -1
        viewerChunks = emptyList()
        stopSpeaking(forceSync = true)
        // Clear current body immediately so the previously viewed article cannot flash
        // while the newly requested item is loading.
        preserveVisibleContentOnReload = false
        bodyRevealReady = false
        textPayload = null
        usingCachedText = false
        chunks = emptyList()
        isLoading = true
        val targetIntent = if (vm.isItemCompletedForPlaybackStart(target)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        vm.playbackOpenItem(
            itemId = target,
            intent = targetIntent,
            autoPlayAfterLoad = false,
        )
    }

    LaunchedEffect(waitingForRequestedItem) {
        if (!waitingForRequestedItem) return@LaunchedEffect
        if (autoPlayAfterLoad) {
            continuationLog(
                "loadItem wait preserveDuringAutoContinue currentItemId=$currentItemId requestedItemId=$requestedItemId",
            )
            return@LaunchedEffect
        }
        // During cross-item handoff, hide stale content immediately and wait for
        // the requested item to become current before any load/reveal work.
        preserveVisibleContentOnReload = false
        bodyRevealReady = false
        textPayload = null
        usingCachedText = false
        chunks = emptyList()
        isLoading = true
    }

    LaunchedEffect(locusTapSignal) {
        if (locusTapSignal == lastHandledLocusTapSignal) return@LaunchedEffect
        lastHandledLocusTapSignal = locusTapSignal
        pendingLocusTabPlaybackScrollAfterReturn = false
        val tapAction = resolveLocusTabTapAction(
            previewModeActive = previewModeActive,
            currentItemId = currentItemId,
            returnToPlaybackPositionAfterPreview = settings.locusTabReturnsToPlaybackPosition,
        )
        if (tapAction.returnToNowPlayingItem) {
            viewerOverrideItemId = -1
            viewerOverrideTitle = ""
            viewerPayload = null
            viewerPayloadItemId = -1
            viewerChunks = emptyList()
            val forceReturnToPlaybackPosition = isSpeaking || isAutoPlaying
            pendingLocusTabPlaybackScrollAfterReturn =
                tapAction.triggerScrollToPlaybackAfterReturn || forceReturnToPlaybackPosition
            onOpenItem(currentItemId)
            return@LaunchedEffect
        }
        if (tapAction.triggerScrollToPlaybackImmediately) {
            readerScrollTriggerSignal = nextForceReattachScrollTriggerSignal(readerScrollTriggerSignal)
        }
    }

    LaunchedEffect(openRequestSignal, resolvedInitial, requestedItemId) {
        if (!resolvedInitial) return@LaunchedEffect
        if (openRequestSignal == lastHandledOpenRequestSignal) return@LaunchedEffect
        // During auto-continue handoff we already have a queued open+autoplay for the next item.
        // A same-item reload here would clear autoPlayAfterLoad and suppress continuation playback.
        if (autoPlayAfterLoad) {
            lastHandledOpenRequestSignal = openRequestSignal
            return@LaunchedEffect
        }
        lastHandledOpenRequestSignal = openRequestSignal
        val target = requestedItemId ?: currentItemId
        if (target != currentItemId) return@LaunchedEffect
        val reloadIntent = if (vm.isItemCompletedForPlaybackStart(target)) {
            PlaybackOpenIntent.Replay
        } else {
            PlaybackOpenIntent.ManualOpen
        }
        vm.playbackReloadCurrentItem(reloadIntent)
        preserveVisibleContentOnReload = false
        bodyRevealReady = false
        readerScrollOffsets = resetReaderScrollOffset(readerScrollOffsets, target)
        isLoading = true
        continuationLog(
            "openRequest sameItemReload target=$target reloadNonce=$reloadNonce autoPlayAfterLoad=false",
        )
        Log.d(
            MANUAL_OPEN_DEBUG_TAG,
            "openRequest sameItemReload item=$target intent=$reloadIntent reloadNonce=$reloadNonce",
        )
    }

    LaunchedEffect(currentItemId, resolvedInitial, reloadNonce, waitingForRequestedItem, previewModeActive) {
        if (!resolvedInitial) return@LaunchedEffect
        val skipSurfaceHandoffReload = shouldSkipSurfaceHandoffReload(
            currentItemId = currentItemId,
            payloadItemId = textPayload?.itemId,
            chunkCount = chunks.size,
            autoPlayAfterLoad = autoPlayAfterLoad,
            isSpeaking = isSpeaking,
            isAutoPlaying = isAutoPlaying,
        )
        if (skipSurfaceHandoffReload) {
            continuationLog(
                "loadItem skip surfaceHandoff currentItemId=$currentItemId reloadNonce=$reloadNonce " +
                    "speaking=$isSpeaking auto=$isAutoPlaying autoPlayAfterLoad=$autoPlayAfterLoad",
            )
            isLoading = false
            bodyRevealReady = true
            preserveVisibleContentOnReload = false
            return@LaunchedEffect
        }
        val previewOnlyHandoffActive =
            hasLockedPlaybackOwner &&
                requestedItemId != null &&
                requestedItemId != currentItemId
        if (previewOnlyHandoffActive) {
            continuationLog(
                "loadItem skip previewOnlyHandoff currentItemId=$currentItemId requestedItemId=$requestedItemId reloadNonce=$reloadNonce",
            )
            isLoading = false
            bodyRevealReady = true
            preserveVisibleContentOnReload = true
            return@LaunchedEffect
        }
        if (previewModeActive) {
            continuationLog(
                "loadItem skip previewModeActive currentItemId=$currentItemId requestedItemId=$requestedItemId reloadNonce=$reloadNonce",
            )
            isLoading = false
            bodyRevealReady = true
            return@LaunchedEffect
        }
        if (waitingForRequestedItem) {
            continuationLog(
                "loadItem skip waitingForRequestedItem currentItemId=$currentItemId requestedItemId=$requestedItemId reloadNonce=$reloadNonce",
            )
            return@LaunchedEffect
        }
        continuationLog(
            "loadItem start currentItemId=$currentItemId reloadNonce=$reloadNonce autoPlayAfterLoad=$autoPlayAfterLoad",
        )
        // During auto-continue handoff we keep the existing reader/control surface visible
        // until the next item's payload is ready, avoiding a transient blank+spinner flash.
        val preservingVisibleContent = preserveVisibleContentOnReload || autoPlayAfterLoad
        val preserveActivePlayback = shouldPreserveActivePlaybackDuringLoad(
            autoPlayAfterLoad = autoPlayAfterLoad,
            isSpeaking = isSpeaking,
            isAutoPlaying = isAutoPlaying,
        )
        if (!preserveActivePlayback) {
            stopSpeaking(forceSync = false)
        } else {
            continuationLog(
                "loadItem keepSpeaking currentItemId=$currentItemId reloadNonce=$reloadNonce " +
                    "autoPlayAfterLoad=$autoPlayAfterLoad speaking=$isSpeaking auto=$isAutoPlaying",
            )
        }
        vm.setNowPlayingCurrentItem(currentItemId)
        isLoading = !preservingVisibleContent
        bodyRevealReady = preservingVisibleContent
        uiMessage = null
        if (!preservingVisibleContent) {
            textPayload = null
            usingCachedText = false
            chunks = emptyList()
        }
        lastObservedPercent = -1
        nearEndForcedForItemId = -1

        val currentItemCached = vm.isItemCached(currentItemId)
        val preferLocalTextLoad = autoPlayAfterLoad || queueOffline || currentItemCached
        val textLoadTag = when {
            autoPlayAfterLoad -> "locus_auto_continue_cache_first"
            queueOffline -> "locus_offline_cache_first"
            currentItemCached -> "locus_cached_open_cache_first"
            else -> "locus_manual_open_network_first"
        }
        vm.fetchItemText(
            currentItemId,
            preferLocal = preferLocalTextLoad,
            loadPolicyTag = textLoadTag,
        )
            .onSuccess { loaded ->
                val payload = loaded.payload
                textPayload = payload
                usingCachedText = loaded.usingCache
                chunks = buildPlaybackChunks(payload)
                continuationLog(
                    "loadItem success item=$currentItemId chunks=${chunks.size} usingCache=$usingCachedText autoPlayAfterLoad=$autoPlayAfterLoad",
                )
                preserveVisibleContentOnReload = false

                if (!preserveActivePlayback) {
                    vm.playbackApplyLoadedItem(
                        payload = payload,
                        chunks = chunks,
                        requestedItemId = requestedItemId,
                    )
                } else {
                    continuationLog(
                        "loadItem skipApplyLoadedItem currentItemId=$currentItemId reloadNonce=$reloadNonce activePlayback=true",
                    )
                }
                readerViewportSessionNonce += 1
                if (!preservingVisibleContent) {
                    readerScrollTriggerSignal = nextStandardScrollTriggerSignal(readerScrollTriggerSignal)
                }
                Log.d(
                    MANUAL_OPEN_DEBUG_TAG,
                    "loadSeed item=$currentItemId intent=${engineState.openIntent} knownProgress=${vm.knownProgressForItem(currentItemId)} " +
                        "seededChunk=${engineState.currentPosition.chunkIndex} seededOffset=${engineState.currentPosition.offsetInChunkChars}",
                )
                if (autoPlayAfterLoad && chunks.isNotEmpty()) {
                    continuationLog(
                        "loadItem autoplay item=$currentItemId chunk=${engineState.currentPosition.chunkIndex} offset=${engineState.currentPosition.offsetInChunkChars}",
                    )
                    vm.playbackMaybeAutoPlayAfterLoad()
                }
            }
            .onFailure { err ->
                if (err is CancellationException) {
                    return@onFailure
                }
                if (textPayload?.itemId != currentItemId) {
                    textPayload = null
                    usingCachedText = false
                    chunks = emptyList()
                }
                bodyRevealReady = true
                uiMessage = if (err is ApiException && err.statusCode == 401) {
                    "Unauthorized-check token"
                } else if (isNetworkError(err)) {
                    "Network error loading item text"
                } else {
                    err.message ?: "Failed to load text"
                }
                preserveVisibleContentOnReload = false
            }
        if (!preservingVisibleContent) {
            // Let ReaderBody apply the seeded scroll position before first reveal.
            delay(110)
            bodyRevealReady = true
        }
        isLoading = false
    }

    LaunchedEffect(readerScrollItemId, readerScrollState) {
        snapshotFlow { readerScrollState.value }
            .distinctUntilChanged()
            .collect { offset ->
                readerScrollOffsets = updateReaderScrollOffsets(
                    offsets = readerScrollOffsets,
                    itemId = readerScrollItemId,
                    offset = offset,
                )
                val previousPersisted = persistedReaderScrollOffsets[readerScrollItemId]
                if (previousPersisted == null || abs(previousPersisted - offset) >= 24) {
                    persistedReaderScrollOffsets = updateReaderScrollOffsets(
                        offsets = persistedReaderScrollOffsets,
                        itemId = readerScrollItemId,
                        offset = offset,
                    )
                    vm.setReaderScrollOffset(
                        itemId = readerScrollItemId,
                        offset = offset,
                    )
                }
            }
    }

    LaunchedEffect(Unit) {
        vm.playbackEngineEvents.collect { event ->
            when (event) {
                is PlaybackEngineEvent.NavigateToItem -> {
                    continuationLog("engineEvent navigate nextId=${event.itemId}")
                    onOpenItem(event.itemId)
                }
                is PlaybackEngineEvent.UiMessage -> {
                    uiMessage = event.message
                }
            }
        }
    }

    val safePosition = normalizedPosition(currentPosition)
    val totalChars = totalCharsForPercent()
    val currentPercent = calculateCanonicalPercent(totalChars, chunks, safePosition)
    val locusItemId = readerScrollItemId
    val locusActionItemId = locusItemId.takeIf { it > 0 } ?: currentItemId
    val previewPayloadForItem = viewerPayload.takeIf { viewerPayloadItemId == previewItemId }
    val displayPayload = if (previewModeActive) previewPayloadForItem else textPayload
    val displayChunks = when {
        previewModeActive && viewerPayloadItemId == previewItemId -> viewerChunks
        previewModeActive -> emptyList()
        else -> chunks
    }
    val displayReaderText = remember(displayPayload?.text, displayChunks) {
        if (displayChunks.isNotEmpty()) {
            displayChunks.joinToString(separator = "\n\n") { it.text }
        } else {
            displayPayload?.text.orEmpty()
        }
    }
    val preservedReaderLinks = remember(displayReaderText, displayPayload?.contentBlocks) {
        extractReaderPreservedLinks(
            text = displayReaderText,
            contentBlocks = displayPayload?.contentBlocks,
        )
    }
    var locusSearchActive by rememberSaveable(locusItemId) { mutableStateOf(false) }
    var locusSearchQuery by rememberSaveable(locusItemId) { mutableStateOf("") }
    var locusSearchMatchIndex by rememberSaveable(locusItemId) { mutableIntStateOf(-1) }
    var locusSearchScrollTriggerSignal by remember(locusItemId) { mutableIntStateOf(0) }
    val locusSearchMatches = remember(displayChunks, locusSearchQuery) {
        collectLocusSearchMatches(displayChunks, locusSearchQuery)
    }
    val activeLocusSearchMatch = locusSearchMatches.getOrNull(locusSearchMatchIndex)
    val locusSearchRangesByChunk = remember(locusSearchMatches) {
        locusSearchMatches
            .groupBy(keySelector = { it.chunkIndex }, valueTransform = { it.rangeInChunk })
            .mapValues { (_, ranges) -> ranges.sortedBy { it.first } }
    }
    val locusSearchSummary = when {
        locusSearchQuery.isBlank() -> null
        locusSearchMatches.isEmpty() -> "No matches"
        activeLocusSearchMatch == null -> "1 / ${locusSearchMatches.size}"
        else -> "${locusSearchMatchIndex + 1} / ${locusSearchMatches.size}"
    }
    fun focusLocusSearchMatch(index: Int) {
        if (index !in locusSearchMatches.indices) return
        locusSearchMatchIndex = index
        locusSearchScrollTriggerSignal += 1
    }
    fun moveLocusSearchMatch(step: Int) {
        if (locusSearchMatches.isEmpty()) return
        val from = if (locusSearchMatchIndex in locusSearchMatches.indices) locusSearchMatchIndex else 0
        val next = ((from + step) % locusSearchMatches.size + locusSearchMatches.size) % locusSearchMatches.size
        focusLocusSearchMatch(next)
    }
    LaunchedEffect(locusSearchQuery, locusSearchMatches, locusItemId) {
        if (locusSearchQuery.isBlank() || locusSearchMatches.isEmpty()) {
            locusSearchMatchIndex = -1
            return@LaunchedEffect
        }
        if (locusSearchMatchIndex !in locusSearchMatches.indices) {
            focusLocusSearchMatch(0)
        }
    }
    val capturePresentation = locusCapturePresentation(displayPayload)
    val queuedPreviewTitle = queueItemsById[locusItemId]?.title.orEmpty()
    val queuedNowPlayingTitle = queueItemsById[playbackOwnerItemId]?.title.orEmpty()
    val isLocusItemFavorited = queueItemsById[locusItemId]?.isFavorited == true
    val isLocusItemArchived = archivedItemIdSet.contains(locusActionItemId)
    val locusActionItem = queueItemsById[locusActionItemId]
    val currentTitle = when {
        previewModeActive -> {
            viewerOverrideTitle
                .ifBlank { queuedPreviewTitle }
                .ifBlank { capturePresentation.title }
                .ifBlank { displayPayload?.url.orEmpty() }
                .ifBlank { "Item $locusItemId" }
        }
        else -> capturePresentation.title.ifBlank { displayPayload?.url.orEmpty() }
    }
    val nowPlayingTitle = queuedNowPlayingTitle
        .ifBlank {
            textPayload
                ?.takeIf { it.itemId == playbackOwnerItemId }
                ?.title
                .orEmpty()
        }
        .ifBlank { nowPlayingSession?.currentItem?.title.orEmpty() }
        .ifBlank {
            textPayload
                ?.takeIf { it.itemId == playbackOwnerItemId }
                ?.sourceLabel
                .orEmpty()
        }
    val nowPlayingUrl = queueItemsById[playbackOwnerItemId]?.url.orEmpty()
        .ifBlank { nowPlayingSession?.currentItem?.url.orEmpty() }
    val locusActionBarTitle = resolveLocusActionBarTitle(
        playbackActive = isSpeaking || isAutoPlaying || autoPlayAfterLoad,
        playbackOwnerItemId = playbackOwnerItemId,
        playbackOwnerTitle = nowPlayingTitle,
        playbackOwnerUrl = nowPlayingUrl,
        previewModeActive = previewModeActive,
        previewTitle = currentTitle,
        fallbackItemId = locusItemId,
    )
    val currentSourceLabel = capturePresentation.sourceLabel
    val reportContext = remember(locusActionItemId, displayPayload, locusActionItem, capturePresentation) {
        LocusProblemReportContext(
            itemId = locusActionItemId.takeIf { it > 0 },
            url = displayPayload?.url?.takeIf { it.isNotBlank() }
                ?: locusActionItem?.url?.takeIf { it.isNotBlank() },
            sourceType = displayPayload?.sourceType,
            sourceLabel = displayPayload?.sourceLabel?.takeIf { it.isNotBlank() } ?: capturePresentation.sourceLabel,
            sourceUrl = displayPayload?.sourceUrl?.takeIf { it.isNotBlank() } ?: capturePresentation.sourceUrl,
            captureKind = displayPayload?.captureKind,
            articleTitle = displayPayload?.title?.takeIf { it.isNotBlank() } ?: currentTitle.takeIf { it.isNotBlank() },
            articleText = displayPayload?.text?.takeIf { it.isNotBlank() },
        )
    }
    val locusItemUrl = reportContext.url.orEmpty()
    val locusItemTitle = reportContext.articleTitle
    val locusHasUrl = locusItemUrl.isNotBlank()
    var showProblemReportDialog by remember { mutableStateOf(false) }
    var reportCategory by remember { mutableStateOf(ProblemReportCategory.CONTENT_PROBLEM) }
    var reportUserNote by remember { mutableStateOf("") }
    var reportUrlText by remember { mutableStateOf("") }
    var reportShowUrlField by remember { mutableStateOf(false) }
    var reportAttachFullText by remember { mutableStateOf(false) }
    var reportSubmitting by remember { mutableStateOf(false) }
    val chunkLabel = if (previewModeActive) {
        "Previewing item while playback continues"
    } else if (chunks.isEmpty()) {
        "Chunk 0 / 0"
    } else {
        "Chunk ${safePosition.chunkIndex + 1} / ${chunks.size}"
    }
    val syncBadgeText = when (syncBadgeState) {
        ProgressSyncBadgeState.SYNCED -> "Synced"
        ProgressSyncBadgeState.QUEUED -> "Queued"
        ProgressSyncBadgeState.OFFLINE -> "Offline"
    }
    val effectivePercent = if (localDonePercentOverride >= 0) localDonePercentOverride else currentPercent
    val showCompleted = effectivePercent >= DONE_PERCENT_THRESHOLD || nearEndForcedForItemId == currentItemId
    val undoDonePercent = effectivePercent.coerceIn(0, DONE_PERCENT_THRESHOLD - 1)
    var lastAppliedSpeed by remember { mutableStateOf(settings.playbackSpeed) }
    var lastAppliedVoiceName by remember { mutableStateOf(settings.ttsVoiceName) }

    LaunchedEffect(isSpeaking, isAutoPlaying) {
        onPlaybackActiveChange(isSpeaking || isAutoPlaying)
    }
    LaunchedEffect(readerModeEnabled, currentItemId, isSpeaking, isAutoPlaying, autoPlayAfterLoad) {
        val manualReadingActive =
            readerModeEnabled &&
                currentItemId > 0 &&
                !(isSpeaking || isAutoPlaying || autoPlayAfterLoad)
        onManualReadingActiveChange(manualReadingActive)
    }

    LaunchedEffect(currentPercent) {
        onPlaybackProgressPercentChange(currentPercent)
    }

    LaunchedEffect(readerChromeHidden) {
        onReaderChromeVisibilityChange(readerChromeHidden)
    }

    LaunchedEffect(settings.playbackSpeed, currentItemId, safePosition.chunkIndex, safePosition.offsetInChunkChars, chunks.size) {
        if (lastAppliedSpeed == settings.playbackSpeed) return@LaunchedEffect
        lastAppliedSpeed = settings.playbackSpeed
        vm.playbackApplyCurrentSettings()
    }

    LaunchedEffect(settings.ttsVoiceName) {
        if (lastAppliedVoiceName == settings.ttsVoiceName) return@LaunchedEffect
        lastAppliedVoiceName = settings.ttsVoiceName
        vm.playbackApplyCurrentSettings()
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
            isMember = vm.isItemInPlaylist(locusActionItemId, playlist.id),
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
    val toggleReaderMode = {
        val nextMode = toggledLocusContentMode(locusContentMode)
        vm.saveLocusContentMode(nextMode)
    }
    fun nextSessionItemIdForArchive(currentId: Int): Int? {
        val session = nowPlayingSession ?: return null
        val currentIndex = session.items.indexOfFirst { it.itemId == currentId }
        if (currentIndex < 0) return null
        val nextIndex = currentIndex + 1
        return session.items.getOrNull(nextIndex)?.itemId
    }
    fun playLocusItem() {
        if (locusItemId <= 0) return
        if (locusItemId != currentItemId) {
            val previewPayload = viewerPayload
            val previewChunks = viewerChunks
            if (previewPayload != null && viewerPayloadItemId == locusItemId) {
                textPayload = previewPayload
                chunks = previewChunks
                preserveVisibleContentOnReload = true
            }
            viewerOverrideItemId = -1
            viewerOverrideTitle = ""
            viewerPayload = null
            viewerPayloadItemId = -1
            viewerChunks = emptyList()
            vm.playbackOpenItem(
                itemId = locusItemId,
                intent = PlaybackOpenIntent.ManualOpen,
                autoPlayAfterLoad = true,
            )
            onOpenItem(locusItemId)
            onShowSnackbar("Playing item shown in Locus", null, null)
            return
        }
        if (!(isSpeaking || isAutoPlaying) && chunks.isNotEmpty()) {
            vm.playbackPlay()
        }
    }
    fun openProblemReport() {
        reportCategory = resolveDefaultProblemReportCategory(reportContext.itemId, reportContext.url)
        reportUserNote = ""
        reportUrlText = reportContext.url.orEmpty()
        reportShowUrlField = reportContext.url?.isNotBlank() == true
        reportAttachFullText = false
        reportSubmitting = false
        showProblemReportDialog = true
    }

    fun resolveLocusOpenTargetId(): Int {
        return listOf(
            playbackOwnerItemId,
            locusItemId,
            currentItemId,
            sessionCurrentItemId ?: -1,
            requestedItemId ?: -1,
            initialItemId,
        ).firstOrNull { it > 0 } ?: -1
    }

    val renderPlayerControlBar: @Composable () -> Unit = {
        val previousSentencePosition = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = safePosition,
            direction = -1,
        )
        val nextSentencePosition = resolveSentenceJumpPosition(
            chunks = chunks,
            currentPosition = safePosition,
            direction = 1,
        )
        val previousParagraphPosition = resolveParagraphJumpPosition(
            chunks = chunks,
            currentPosition = safePosition,
            direction = -1,
        )
        val nextParagraphPosition = resolveParagraphJumpPosition(
            chunks = chunks,
            currentPosition = safePosition,
            direction = 1,
        )
        fun seekToTargetAndReveal(target: PlaybackPosition) {
            setPlaybackPosition(target.chunkIndex, target.offsetInChunkChars)
            vm.playbackSeekToChunkOffset(
                chunkIndex = target.chunkIndex,
                offsetInChunkChars = target.offsetInChunkChars,
                keepPlaying = isSpeaking || isAutoPlaying,
            )
            readerScrollTriggerSignal = -(kotlin.math.abs(readerScrollTriggerSignal) + 1)
        }
        PlayerControlBar(
            progressPercent = currentPercent,
            minimal = controlsMode == PlayerControlsMode.MINIMAL,
            nowPlayingTitle = nowPlayingTitle,
            continuousMarquee = settings.continuousNowPlayingMarquee,
            onOpenLocusForItem = {
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastReaderManualScrollAtMs < MANUAL_SCROLL_TAP_REATTACH_BLOCK_MS) {
                    return@PlayerControlBar
                }
                readerScrollTriggerSignal = nextForceReattachScrollTriggerSignal(readerScrollTriggerSignal)
                val locusTargetId = resolveLocusOpenTargetId()
                if (locusTargetId > 0) onOpenLocusForItem(locusTargetId)
            },
            canSeek = chunks.isNotEmpty(),
            canMoveBackward = chunks.isNotEmpty(),
            canMoveForward = nextSentencePosition != null,
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
                vm.playbackSeekToChunkOffset(
                    chunkIndex = targetPosition.chunkIndex,
                    offsetInChunkChars = targetPosition.offsetInChunkChars,
                    keepPlaying = isSpeaking || isAutoPlaying,
                )
            },
            onPreviousSegment = {
                val fallbackStart = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
                val target = previousSentencePosition ?: fallbackStart
                seekToTargetAndReveal(target)
            },
            onPreviousSegmentLongPress = {
                val fallbackStart = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0)
                val target = previousParagraphPosition ?: fallbackStart
                seekToTargetAndReveal(target)
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
                    Log.d(
                        MANUAL_OPEN_DEBUG_TAG,
                        "playTap item=$currentItemId restart=$restartFromStart " +
                            "playChunk=${livePosition.chunkIndex} playOffset=${livePosition.offsetInChunkChars}",
                    )
                    readerScrollTriggerSignal = nextStandardScrollTriggerSignal(readerScrollTriggerSignal)
                    vm.playbackPlay()
                } else if (currentItemId > 0) {
                    vm.playbackOpenItem(
                        itemId = currentItemId,
                        intent = PlaybackOpenIntent.ManualOpen,
                        autoPlayAfterLoad = true,
                    )
                    uiMessage = "Loading audio..."
                }
            },
            onPlayButtonLongPress = { playLocusItem() },
            onNextSegment = {
                nextSentencePosition?.let { target ->
                    seekToTargetAndReveal(target)
                }
            },
            onNextSegmentLongPress = {
                nextParagraphPosition?.let { target ->
                    seekToTargetAndReveal(target)
                }
            },
            onPreviousItem = {
                actionScope.launch {
                    val prevId = vm.prevSessionItemId(currentItemId)
                    if (prevId == null) {
                        uiMessage = "No previous item"
                    } else {
                        stopSpeaking(forceSync = true)
                        vm.setPlaybackPosition(prevId, 0, 0)
                        vm.playbackOpenItem(
                            itemId = prevId,
                            intent = PlaybackOpenIntent.AutoContinue,
                            autoPlayAfterLoad = true,
                        )
                        onOpenItem(prevId)
                    }
                }
            },
            onNextItem = {
                vm.playbackAdvanceToNextItem()
            },
        )
    }
    val renderPlayerDock: @Composable () -> Unit = {
        val openLocusFromDock = {
            val locusTargetId = resolveLocusOpenTargetId()
            if (locusTargetId > 0) onOpenLocusForItem(locusTargetId)
        }
        when (controlsMode) {
            PlayerControlsMode.FULL -> FullPlayerDock(
                chevronSide = chevronSide,
                showChevron = showDockChevron,
                chevronContentDescription = chevronDescription,
                onChevronTap = handleChevronTap,
                onChevronLongPress = handleChevronLongPress,
                onChevronSnap = handleChevronSnap,
                onBackgroundTap = openLocusFromDock,
                content = renderPlayerControlBar,
            )

            PlayerControlsMode.MINIMAL -> MinimalPlayerDock(
                chevronSide = chevronSide,
                showChevron = showDockChevron,
                chevronContentDescription = chevronDescription,
                onChevronTap = handleChevronTap,
                onChevronLongPress = handleChevronLongPress,
                onChevronSnap = handleChevronSnap,
                onBackgroundTap = openLocusFromDock,
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
                onBackgroundTap = openLocusFromDock,
            )
        }
    }
    val observabilityUiState = PlaybackObservabilityUiState(
        currentItemId = currentItemId,
        requestedItemId = requestedItemId,
        openIntent = lastOpenDiagnostics?.openIntent,
        startSource = lastOpenDiagnostics?.startSource,
        knownProgress = lastOpenDiagnostics?.knownProgress,
        seededChunk = lastOpenDiagnostics?.seededPosition?.chunkIndex,
        seededOffset = lastOpenDiagnostics?.seededPosition?.offsetInChunkChars,
        handoffPending = waitingForRequestedItem || shouldHideForStalePayload,
        handoffSettled = transitionSettled,
        autoPath = isAutoPlaying,
    )
    val showReaderLoadingPlaceholder = shouldShowReaderLoadingPlaceholder(
        waitingForRequestedItem = waitingForRequestedItem,
        hasStalePayloadForCurrentItem = shouldHideForStalePayload,
        isLoading = isLoading,
        transitionSettled = transitionSettled,
    )

    if (showReaderLoadingPlaceholder && !compactControlsOnly) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(modifier),
            contentAlignment = Alignment.Center,
        ) {
            ReaderLoadingPlaceholder()
        }
    } else if (compactControlsOnly) {
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
                .background(Color.Black)
                .then(modifier),
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.Top,
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    ) {
                        val showLocusTopBar = transitionSettled
                        val showReaderDockOverlay = !playerDockHiddenByMode && transitionSettled
                        LaunchedEffect(showLocusTopBar) {
                            if (!showLocusTopBar) {
                                locusTopOverlayHeightPx = 0
                            }
                        }
                        LaunchedEffect(showReaderDockOverlay) {
                            if (!showReaderDockOverlay) {
                                locusBottomOverlayHeightPx = 0
                            }
                        }
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
                                }
                        ) {
                            val readerTopInsetDp = with(density) { locusTopOverlayHeightPx.toDp() }
                            CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                                ReaderBody(
                                    fullText = displayPayload?.text,
                                    chunks = displayChunks,
                                    preservedLinks = preservedReaderLinks,
                                    currentChunkIndex = if (previewModeActive) 0 else safePosition.chunkIndex,
                                    currentChunkOffsetInChars = if (previewModeActive) 0 else safePosition.offsetInChunkChars,
                                    activeRangeInChunk = if (previewModeActive) null else activeChunkRange,
                                    searchHighlightRangesByChunk = locusSearchRangesByChunk,
                                    searchFocusChunkIndex = activeLocusSearchMatch?.chunkIndex,
                                    searchFocusRangeInChunk = activeLocusSearchMatch?.rangeInChunk,
                                    searchFocusTriggerSignal = locusSearchScrollTriggerSignal,
                                    scrollTriggerSignal = readerScrollTriggerSignal,
                                    autoScrollWhileListening = !previewModeActive &&
                                        settings.autoScrollWhileListening &&
                                        (isSpeaking || isAutoPlaying),
                                    readingFontSizeSp = settings.readingFontSizeSp,
                                    readingFontOption = settings.readingFontOption,
                                    readingLineHeightPercent = settings.readingLineHeightPercent,
                                    readingMaxWidthDp = settings.readingMaxWidthDp,
                                    paragraphSpacing = settings.readingParagraphSpacing,
                                    selectionResetSignal = readerSelectionResetSignal,
                                    scrollState = readerScrollState,
                                    showEmptyPlaceholder = transitionSettled && !isLoading,
                                    topOverlayOcclusionPx = 0,
                                    bottomOverlayOcclusionPx = locusBottomOverlayHeightPx,
                                    onNonLinkTap = {
                                        if (textToolbar.status == TextToolbarStatus.Shown || selectionClearArmed) {
                                            clearActiveSelection()
                                        } else {
                                            toggleReaderMode()
                                        }
                                    },
                                    onManualScrollGesture = {
                                        lastReaderManualScrollAtMs = SystemClock.elapsedRealtime()
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = readerTopInsetDp)
                                        .graphicsLayer { alpha = bodyContentAlpha },
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLocusTopBar,
                                enter = slideInVertically(
                                    initialOffsetY = { -it / 2 },
                                    animationSpec = tween(durationMillis = 140, delayMillis = 40),
                                ) + fadeIn(animationSpec = tween(durationMillis = 120, delayMillis = 40)),
                                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(120)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        locusTopOverlayHeightPx = size.height
                                    },
                            ) {
                                ExpandedPlayerTopBar(
                                    title = currentTitle,
                                    titleDomain = capturePresentation.sourceLabel,
                                    titleSourceUrl = capturePresentation.sourceUrl,
                                    continuousMarquee = settings.continuousNowPlayingMarquee,
                                    playbackSpeed = settings.playbackSpeed,
                                    overflowExpanded = overflowExpanded,
                                    showTopBar = !actionBarHiddenByMode || locusSearchActive,
                                    canMarkDone = displayPayload != null,
                                    isDone = showCompleted,
                                    refreshState = refreshActionState,
                                    showConnectivityIssue = hasRefreshProblem,
                                    isFavorited = isLocusItemFavorited,
                                    isArchived = isLocusItemArchived,
                                    onRefresh = {
                                        if (refreshActionState == RefreshActionVisualState.Refreshing) return@ExpandedPlayerTopBar
                                        actionScope.launch {
                                            refreshActionState = RefreshActionVisualState.Refreshing
                                            val refreshResult = vm.refreshCurrentPlayerItem(locusActionItemId)
                                                .onSuccess {
                                                    localDonePercentOverride = -1
                                                    preserveVisibleContentOnReload = true
                                                    vm.playbackReloadCurrentItem(PlaybackOpenIntent.ManualOpen)
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
                                            vm.toggleCompletion(locusActionItemId, markDone = markDone, resumePercent = resumePercent)
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
                                    searchActive = locusSearchActive,
                                    searchQuery = locusSearchQuery,
                                    searchSummary = locusSearchSummary,
                                    onSearchToggle = { locusSearchActive = !locusSearchActive },
                                    onSearchQueryChange = { locusSearchQuery = it },
                                    onSearchNext = { moveLocusSearchMatch(1) },
                                    onSearchPrevious = { moveLocusSearchMatch(-1) },
                                    onToggleFavorite = {
                                        actionScope.launch {
                                                vm.setItemFavorited(locusItemId, favorited = !isLocusItemFavorited)
                                                .onSuccess {
                                                    val message = if (isLocusItemFavorited) "Removed from favourites" else "Added to favourites"
                                                    onShowSnackbar(message, null, null)
                                                }
                                                .onFailure { error ->
                                                    if (error is CancellationException) return@onFailure
                                                    onShowSnackbar("Couldn't update favourite", "Diagnostics", "open_diagnostics")
                                                }
                                        }
                                    },
                                    onArchive = {
                                        actionScope.launch {
                                            if (isLocusItemArchived) {
                                                vm.unarchiveItem(locusActionItemId)
                                                    .onSuccess {
                                                        onShowSnackbar("Unarchived", null, null)
                                                    }
                                                    .onFailure { error ->
                                                        if (error is CancellationException) return@onFailure
                                                        onShowSnackbar("Couldn't unarchive item", "Diagnostics", "open_diagnostics")
                                                    }
                                            } else {
                                                val nextItemId = nextSessionItemIdForArchive(locusActionItemId)
                                                vm.archiveItem(
                                                    locusActionItemId,
                                                    source = ArchiveActionSource.LOCUS,
                                                )
                                                    .onSuccess {
                                                        onShowSnackbar("Archived", "Undo", ACTION_KEY_UNDO_ARCHIVE)
                                                        val archivedActivePlaybackItem =
                                                            locusActionItemId == currentItemId &&
                                                                (isSpeaking || isAutoPlaying || autoPlayAfterLoad)
                                                        if (archivedActivePlaybackItem) {
                                                            // Keep playback continuity for the currently playing item.
                                                            Unit
                                                        } else if (locusActionItemId != currentItemId && currentItemId > 0) {
                                                            onOpenItem(currentItemId)
                                                        } else if (nextItemId != null) {
                                                            vm.startNowPlayingSession(startItemId = nextItemId)
                                                            vm.playbackOpenItem(
                                                                itemId = nextItemId,
                                                                intent = PlaybackOpenIntent.ManualOpen,
                                                                autoPlayAfterLoad = false,
                                                            )
                                                            onOpenItem(nextItemId)
                                                        } else {
                                                            onRequestBack()
                                                        }
                                                    }
                                                    .onFailure { error ->
                                                        if (error is CancellationException) return@onFailure
                                                        onShowSnackbar("Couldn't archive item", "Diagnostics", "open_diagnostics")
                                                    }
                                            }
                                        }
                                    },
                                    onOverflowExpandedChange = { expanded -> overflowExpanded = expanded },
                                    overflowMenuContent = {
                                        LocusOverflowMenuItems(
                                            onPlayCurrentItem = {
                                                overflowExpanded = false
                                                playLocusItem()
                                            },
                                            onPlayNext = {
                                                overflowExpanded = false
                                                vm.playNext(locusActionItemId)
                                            },
                                            onPlayLast = {
                                                overflowExpanded = false
                                                vm.playLast(locusActionItemId)
                                            },
                                            onReportProblem = {
                                                overflowExpanded = false
                                                openProblemReport()
                                            },
                                            onMoveToBin = {
                                                overflowExpanded = false
                                                actionScope.launch {
                                                    val nextItemId = nextSessionItemIdForArchive(locusActionItemId)
                                                    vm.moveItemToBin(
                                                        locusActionItemId,
                                                        refreshQueue = false,
                                                        source = ArchiveActionSource.LOCUS,
                                                    )
                                                        .onSuccess {
                                                            val offlineDeferred = vm.queueOffline.value
                                                            if (offlineDeferred) {
                                                                onShowSnackbar("Moved to Bin offline; will sync", null, null)
                                                            } else {
                                                                onShowSnackbar("Moved to Bin (14 days)", "Undo", ACTION_KEY_UNDO_ARCHIVE)
                                                            }
                                                            if (locusActionItemId != currentItemId && currentItemId > 0) {
                                                                onOpenItem(currentItemId)
                                                            } else if (nextItemId != null) {
                                                                vm.startNowPlayingSession(startItemId = nextItemId)
                                                                vm.playbackOpenItem(
                                                                    itemId = nextItemId,
                                                                    intent = PlaybackOpenIntent.ManualOpen,
                                                                    autoPlayAfterLoad = false,
                                                                )
                                                                onOpenItem(nextItemId)
                                                            } else {
                                                                onRequestBack()
                                                            }
                                                        }
                                                        .onFailure { error ->
                                                            if (error is CancellationException) return@onFailure
                                                            onShowSnackbar("Couldn't move item to Bin", "Diagnostics", "open_diagnostics")
                                                        }
                                                }
                                            },
                                            onOpenPlaylists = {
                                                overflowExpanded = false
                                                vm.refreshPlaylists()
                                                showPlaylistPicker = true
                                            },
                                            canReportProblem = settings.apiToken.isNotBlank(),
                                            hasUrl = locusHasUrl,
                                            onOpenInBrowser = {
                                                overflowExpanded = false
                                                openItemInBrowser(context, locusItemUrl)
                                            },
                                            onShareUrl = {
                                                overflowExpanded = false
                                                shareItemUrl(context, locusItemUrl, locusItemTitle)
                                            },
                                            hasArticleText = displayReaderText.isNotBlank(),
                                            onCopyArticleText = {
                                                overflowExpanded = false
                                                copyItemText(context, displayReaderText)
                                                onShowSnackbar("Article text copied", null, null)
                                            },
                                            onShareArticleText = {
                                                overflowExpanded = false
                                                shareItemText(context, displayReaderText, locusItemTitle, currentSourceLabel, locusItemUrl.takeIf { locusHasUrl })
                                            },
                                        )
                                    },
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showReaderDockOverlay,
                                enter = slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(durationMillis = 140, delayMillis = 40),
                                ) + fadeIn(animationSpec = tween(durationMillis = 120, delayMillis = 40)),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        locusBottomOverlayHeightPx = size.height
                                    },
                            ) {
                                renderPlayerDock()
                            }
                        }
                    }
                if (BuildConfig.DEBUG && settings.showPlaybackDiagnostics) {
                    PlaybackObservabilityStrip(
                        lines = playbackObservabilityLines(observabilityUiState),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showProblemReportDialog) {
        LocusProblemReportDialog(
            category = reportCategory,
            onCategoryChange = { reportCategory = it },
            userNote = reportUserNote,
            onUserNoteChange = { next ->
                if (next.length <= 500) {
                    reportUserNote = next
                }
            },
            showUrlField = reportShowUrlField,
            urlValue = reportUrlText,
            onUrlChange = { reportUrlText = it },
            onUrlClear = { reportUrlText = "" },
            attachFullText = reportAttachFullText,
            onAttachFullTextChange = { reportAttachFullText = it },
            submitting = reportSubmitting,
            onDismiss = {
                if (!reportSubmitting) {
                    showProblemReportDialog = false
                }
            },
            onSubmit = {
                val trimmedNote = reportUserNote.trim()
                if (trimmedNote.isBlank()) {
                    onShowSnackbar("Please add a note before submitting.", null, null)
                    return@LocusProblemReportDialog
                }
                reportSubmitting = true
                actionScope.launch {
                    val submitResult = vm.submitProblemReport(
                        category = reportCategory,
                        userNote = trimmedNote,
                        itemId = reportContext.itemId,
                        url = if (reportShowUrlField) reportUrlText else null,
                        sourceType = reportContext.sourceType,
                        sourceLabel = reportContext.sourceLabel,
                        sourceUrl = reportContext.sourceUrl,
                        captureKind = reportContext.captureKind,
                        articleTitle = reportContext.articleTitle,
                        articleText = reportContext.articleText,
                        includeFullTextAttachment = reportAttachFullText,
                    )
                    reportSubmitting = false
                    submitResult
                        .onSuccess { reportId ->
                            showProblemReportDialog = false
                            onShowSnackbar("Problem report sent. Reference: $reportId", null, null)
                        }
                        .onFailure { error ->
                            val message = resolveProblemReportFailureMessage(error)
                            if (message.startsWith("Sign in", ignoreCase = true)) {
                                onShowSnackbar(message, "Settings", "open_settings")
                            } else {
                                onShowSnackbar(message, null, null)
                            }
                        }
                }
            },
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            itemTitle = if (currentTitle.isNotBlank()) currentTitle else "Item $locusItemId",
            playlistChoices = playlistChoices,
            isLoading = false,
            onDismiss = { showPlaylistPicker = false },
            onTogglePlaylist = { choice ->
                actionScope.launch {
                    vm.togglePlaylistMembership(locusActionItemId, choice.playlistId)
                        .onSuccess { result ->
                            val verb = if (result.added) "Added to" else "Removed from"
                            showPlaylistPicker = false
                            onShowSnackbar("$verb ${choice.playlistName}", null, null)
                        }
                        .onFailure { error ->
                            showPlaylistPicker = false
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
private fun ReaderLoadingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun PlaybackObservabilityStrip(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Playback diagnostics (debug)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun ExpandedPlayerTopBar(
    title: String,
    titleDomain: String?,
    titleSourceUrl: String?,
    continuousMarquee: Boolean,
    playbackSpeed: Float,
    overflowExpanded: Boolean,
    showTopBar: Boolean = true,
    canMarkDone: Boolean,
    isDone: Boolean,
    refreshState: RefreshActionVisualState,
    showConnectivityIssue: Boolean,
    isFavorited: Boolean,
    isArchived: Boolean,
    onRefresh: () -> Unit,
    onMarkDone: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    searchSummary: String?,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onArchive: () -> Unit,
    onOverflowExpandedChange: (Boolean) -> Unit,
    overflowMenuContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var titleExpanded by remember(title) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Locus title strip — always visible when ExpandedPlayerTopBar is shown
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title.ifBlank { "" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .clickable { titleExpanded = !titleExpanded }
                    .let {
                        if (!titleExpanded) {
                            it.basicMarquee(
                                iterations = if (continuousMarquee) Int.MAX_VALUE else 1,
                                initialDelayMillis = 3_000,
                                delayMillis = 5_000,
                            )
                        } else {
                            it
                        }
                    },
                maxLines = if (titleExpanded) 3 else 1,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            if (!titleDomain.isNullOrBlank()) {
                val domainModifier = if (!titleSourceUrl.isNullOrBlank()) {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clickable { openItemInBrowser(context, titleSourceUrl) }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                }
                Text(
                    text = titleDomain,
                    modifier = domainModifier,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            )
        }
        AnimatedVisibility(visible = showTopBar) {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = {},
                actions = {
                    ActionHintTooltip(label = if (isDone) "Mark as not done" else "Mark as done") {
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
                    }
                    ActionHintTooltip(label = "Refresh") {
                        RefreshActionButton(
                            state = refreshState,
                            showConnectivityIssue = showConnectivityIssue,
                            onClick = onRefresh,
                            contentDescription = "Refresh item",
                        )
                    }
                    ActionHintTooltip(label = if (searchActive) "Hide search" else "Search in item") {
                        IconButton(onClick = onSearchToggle) {
                            Icon(
                                painter = painterResource(id = R.drawable.msr_search_24),
                                contentDescription = if (searchActive) "Hide search" else "Search in item",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    ActionHintTooltip(label = "Speed") {
                        SpeedControlButton(
                            speed = playbackSpeed,
                            onSpeedChange = onSpeedChange,
                        )
                    }
                    ActionHintTooltip(label = if (isFavorited) "Unfavourite" else "Favourite") {
                        IconToggleButton(
                            checked = isFavorited,
                            onCheckedChange = { onToggleFavorite() },
                        ) {
                            Text(
                                text = if (isFavorited) "\u2665" else "\u2661",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isFavorited) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    ActionHintTooltip(label = if (isArchived) "Unarchive" else "Archive") {
                        IconButton(onClick = onArchive) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_archive_box_24),
                                contentDescription = if (isArchived) "Unarchive item" else "Archive item",
                                tint = if (isArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    ActionHintTooltip(label = "More actions") {
                        LocusOverflowMenu(
                            expanded = overflowExpanded,
                            onExpandedChange = onOverflowExpandedChange,
                            content = overflowMenuContent,
                        )
                    }
                },
            )
        }
        AnimatedVisibility(visible = searchActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isBlank()) {
                                    Text(
                                        text = "Search this item",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }
                    IconButton(
                        onClick = onSearchPrevious,
                        enabled = searchQuery.isNotBlank(),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_chevron_right_24),
                            contentDescription = "Previous match",
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = -90f },
                        )
                    }
                    IconButton(
                        onClick = onSearchNext,
                        enabled = searchQuery.isNotBlank(),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.msr_chevron_right_24),
                            contentDescription = "Next match",
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = 90f },
                        )
                    }
                    Text(
                        text = searchSummary ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocusProblemReportDialog(
    category: ProblemReportCategory,
    onCategoryChange: (ProblemReportCategory) -> Unit,
    userNote: String,
    onUserNoteChange: (String) -> Unit,
    showUrlField: Boolean,
    urlValue: String,
    onUrlChange: (String) -> Unit,
    onUrlClear: () -> Unit,
    attachFullText: Boolean,
    onAttachFullTextChange: (Boolean) -> Unit,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = "Report problem",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .clickable(enabled = !submitting) { categoryMenuExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category.label, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.msr_chevron_right_24),
                                contentDescription = "Open category menu",
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer(rotationZ = 90f),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        ProblemReportCategory.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    categoryMenuExpanded = false
                                    onCategoryChange(option)
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = userNote,
                    onValueChange = onUserNoteChange,
                    label = { Text("What happened?") },
                    placeholder = { Text("Describe the issue") },
                    minLines = 3,
                    maxLines = 8,
                    enabled = !submitting,
                    supportingText = { Text("${userNote.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))

                val attachBlock: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top,
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                            Checkbox(
                                checked = attachFullText,
                                onCheckedChange = onAttachFullTextChange,
                                enabled = !submitting,
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    .padding(top = 2.dp)
                                    .size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier
                                .align(Alignment.Top)
                                .padding(top = 2.dp),
                        ) {
                            Text("Attach article title and text")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.padding(top = 0.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "!",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            lineHeight = 9.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Content may contain sensitive data.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (showUrlField) {
                    OutlinedTextField(
                        value = urlValue,
                        onValueChange = onUrlChange,
                        label = { Text("URL (optional)") },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            attachBlock()
                        }
                        Text(
                            text = "Clear URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(enabled = !submitting) { onUrlClear() },
                        )
                    }
                } else {
                    attachBlock()
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                onClick = onSubmit,
                modifier = Modifier.offset(y = (-6).dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !submitting,
                modifier = Modifier.offset(y = (-6).dp),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LocusOverflowMenuItems(
    onPlayCurrentItem: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onReportProblem: () -> Unit,
    onMoveToBin: () -> Unit,
    onOpenPlaylists: () -> Unit,
    canReportProblem: Boolean,
    hasUrl: Boolean,
    onOpenInBrowser: () -> Unit,
    onShareUrl: () -> Unit,
    hasArticleText: Boolean,
    onCopyArticleText: () -> Unit,
    onShareArticleText: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Play this item") },
        onClick = onPlayCurrentItem,
    )
    DropdownMenuItem(
        text = { Text("Play Next") },
        onClick = onPlayNext,
    )
    DropdownMenuItem(
        text = { Text("Play Last") },
        onClick = onPlayLast,
    )
    DropdownMenuItem(
        text = { Text("Playlists...") },
        onClick = onOpenPlaylists,
    )
    if (hasUrl) {
        DropdownMenuItem(
            text = { Text("Open in browser") },
            onClick = onOpenInBrowser,
        )
        DropdownMenuItem(
            text = { Text("Share URL") },
            onClick = onShareUrl,
        )
    }
    if (hasArticleText) {
        DropdownMenuItem(
            text = { Text("Copy article text") },
            onClick = onCopyArticleText,
        )
        DropdownMenuItem(
            text = { Text("Share article text") },
            onClick = onShareArticleText,
        )
    }
    if (canReportProblem) {
        DropdownMenuItem(
            text = { Text("Report problem") },
            onClick = onReportProblem,
        )
    }
    DropdownMenuItem(
        text = { Text("Move to Bin (14 days)") },
        onClick = onMoveToBin,
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
    onBackgroundTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onBackgroundTap,
            ),
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
    onBackgroundTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onBackgroundTap,
            ),
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
    onBackgroundTap: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PLAYER_TRANSPORT_ROW_HEIGHT)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onBackgroundTap,
            ),
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
@OptIn(ExperimentalFoundationApi::class)
private fun PlayerControlBar(
    progressPercent: Int,
    minimal: Boolean,
    nowPlayingTitle: String,
    continuousMarquee: Boolean,
    onOpenLocusForItem: () -> Unit,
    canSeek: Boolean,
    canMoveBackward: Boolean,
    canMoveForward: Boolean,
    canPlay: Boolean,
    isPlaying: Boolean,
    onSeekToPercent: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onPreviousSegmentLongPress: () -> Unit,
    onPlayPause: () -> Unit,
    onPlayButtonLongPress: () -> Unit,
    onNextSegment: () -> Unit,
    onNextSegmentLongPress: () -> Unit,
    onPreviousItem: () -> Unit,
    onNextItem: () -> Unit,
) {
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val controlPanelInteractionSource = remember { MutableInteractionSource() }
    val isDraggingSlider by sliderInteractionSource.collectIsDraggedAsState()
    var sliderValue by remember { mutableFloatStateOf(progressPercent.coerceIn(0, 100) / 100f) }
    LaunchedEffect(progressPercent, isDraggingSlider) {
        if (!isDraggingSlider) {
            sliderValue = progressPercent.coerceIn(0, 100) / 100f
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = controlPanelInteractionSource,
                indication = null,
                onClick = onOpenLocusForItem,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (nowPlayingTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clickable(onClick = onOpenLocusForItem),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "❯ ",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1f).sp,
                    ),
                )
                Text(
                    text = nowPlayingTitle,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = if (continuousMarquee) Int.MAX_VALUE else 1,
                            initialDelayMillis = 3_000,
                            delayMillis = 5_000,
                        ),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1f).sp,
                    ),
                )
            }
        }
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
                        val targetPercent = (sliderValue * 100).toInt().coerceIn(0, 100)
                        sliderValue = targetPercent / 100f
                        onSeekToPercent(targetPercent)
                    },
                    enabled = canSeek,
                    interactionSource = sliderInteractionSource,
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
            Box(
                modifier = Modifier
                    .size(CONTROL_SLOT_SIZE)
                    .combinedClickable(
                        enabled = canMoveBackward,
                        onClick = onPreviousSegment,
                        onLongClick = onPreviousSegmentLongPress,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_rewind_24),
                    contentDescription = "Previous sentence (long press: previous paragraph)",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            Box(
                modifier = Modifier
                    .size(CONTROL_SLOT_SIZE)
                    .combinedClickable(
                        enabled = canPlay,
                        onClick = onPlayPause,
                        onLongClick = onPlayButtonLongPress,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.msr_pause_24 else R.drawable.msr_play_arrow_24,
                    ),
                    contentDescription = if (isPlaying) "Pause playback" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(CONTROL_CLUSTER_GAP))
            Box(
                modifier = Modifier
                    .size(CONTROL_SLOT_SIZE)
                    .combinedClickable(
                        enabled = canMoveForward,
                        onClick = onNextSegment,
                        onLongClick = onNextSegmentLongPress,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_fast_forward_24),
                    contentDescription = "Next sentence (long press: next paragraph)",
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
