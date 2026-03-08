package com.mimeo.android.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.ui.theme.toFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private val READER_SCROLL_TOP_PADDING = 0.dp
private val READER_SCROLL_BOTTOM_PADDING = 0.dp
private const val MANUAL_SCROLL_SUPPRESS_MS = 1200L

@Composable
fun ReaderBody(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    currentChunkIndex: Int,
    currentChunkOffsetInChars: Int,
    activeRangeInChunk: IntRange?,
    scrollTriggerSignal: Int,
    autoScrollWhileListening: Boolean,
    readingFontSizeSp: Int,
    readingFontOption: ReaderFontOption,
    readingLineHeightPercent: Int,
    readingMaxWidthDp: Int,
    paragraphSpacing: ParagraphSpacingOption,
    selectionResetSignal: Int,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val paragraphSpacingDp = when (paragraphSpacing) {
        ParagraphSpacingOption.SMALL -> 12.dp
        ParagraphSpacingOption.MEDIUM -> 18.dp
        ParagraphSpacingOption.LARGE -> 24.dp
    }
    val safeChunkIndex = currentChunkIndex.coerceIn(0, (chunks.lastIndex).coerceAtLeast(0))
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val sentenceRangesByChunk = remember(chunks) {
        chunks.map { segmentSentences(it.text) }
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var activeTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var activeChunkTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var lastAnchorRange by remember { mutableStateOf<IntRange?>(null) }
    var lastAnchorWasFullyVisible by remember { mutableStateOf<Boolean?>(null) }
    var lastHandledScrollTrigger by remember { mutableIntStateOf(scrollTriggerSignal) }
    var lastObservedScrollValue by remember(scrollState) { mutableIntStateOf(scrollState.value) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var suppressTransitionUntilMs by remember { mutableStateOf(0L) }
    val highlightedSentenceRange = remember(chunks, sentenceRangesByChunk, safeChunkIndex, currentChunkOffsetInChars, activeRangeInChunk) {
        chunks.getOrNull(safeChunkIndex)?.let { chunk ->
            val offsetForSentence = activeRangeInChunk?.first ?: currentChunkOffsetInChars
            findSentenceRangeForOffset(
                text = chunk.text,
                offsetInText = offsetForSentence,
                sentenceRanges = sentenceRangesByChunk.getOrNull(safeChunkIndex).orEmpty(),
            ) ?: if (chunk.text.isNotEmpty()) SentenceRange(0, chunk.text.length) else null
        }
    }
    val readingTextStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            fontFamily = readingFontOption.toFontFamily(),
            fontSize = readingFontSizeSp.sp,
            lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
        ),
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topComfortPx = with(density) { READER_SCROLL_TOP_PADDING.roundToPx().toFloat() }
    val bottomComfortPx = with(density) { READER_SCROLL_BOTTOM_PADDING.roundToPx().toFloat() }
    val anchorRange = resolveReaderHighlightRange(
        textLength = chunks.getOrNull(safeChunkIndex)?.text?.length ?: 0,
        activeRange = activeRangeInChunk,
        sentenceRange = highlightedSentenceRange,
    )

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned { coordinates ->
                viewportTopInRootPx = coordinates.positionInRoot().y
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        key(selectionResetSignal) {
            SelectionContainer {
                if (chunks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = readingMaxWidthDp.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        chunks.forEachIndexed { index, chunk ->
                            val isHighlighted = index == safeChunkIndex
                            val effectiveHighlightRange = if (isHighlighted) {
                                resolveReaderHighlightRange(
                                    textLength = chunk.text.length,
                                    activeRange = activeRangeInChunk,
                                    sentenceRange = highlightedSentenceRange,
                                )
                            } else {
                                null
                            }
                            val chunkText = if (effectiveHighlightRange != null) {
                                buildAnnotatedString {
                                    append(chunk.text)
                                    addStyle(
                                        style = SpanStyle(
                                            background = highlightBg,
                                        ),
                                        start = effectiveHighlightRange.first,
                                        end = (effectiveHighlightRange.last + 1).coerceAtMost(chunk.text.length),
                                    )
                                }
                            } else {
                                buildAnnotatedString { append(chunk.text) }
                            }
                            Text(
                                text = chunkText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { base ->
                                        if (isHighlighted) {
                                            base.onGloballyPositioned { coordinates ->
                                                activeChunkTopInRootPx = coordinates.positionInRoot().y
                                            }
                                        } else {
                                            base
                                        }
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                style = readingTextStyle,
                                onTextLayout = { layout ->
                                    if (isHighlighted) {
                                        activeTextLayout = layout
                                    }
                                },
                            )
                            if (index < chunks.lastIndex) {
                                ParagraphSpacer(height = paragraphSpacingDp)
                            }
                        }
                    }
                } else {
                    Text(
                        text = fullText?.ifBlank { "No readable text available." } ?: "No readable text available.",
                        modifier = Modifier
                            .widthIn(max = readingMaxWidthDp.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        style = readingTextStyle,
                    )
                }
            }
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { scrollValue ->
                if (scrollValue != lastObservedScrollValue && !isProgrammaticScroll) {
                    suppressTransitionUntilMs = SystemClock.elapsedRealtime() + MANUAL_SCROLL_SUPPRESS_MS
                }
                lastObservedScrollValue = scrollValue
                val layout = activeTextLayout ?: return@collect
                val anchor = anchorRange ?: return@collect
                val chunkTopInRoot = activeChunkTopInRootPx ?: return@collect
                val viewportTopInRoot = viewportTopInRootPx ?: return@collect
                if (viewportSize.height <= 0) return@collect
                if (lastAnchorRange != anchor) return@collect

                val startBox = layout.getCursorRect(anchor.first)
                val endIndex = anchor.last.coerceAtLeast(anchor.first)
                val endBox = layout.getCursorRect(endIndex)
                val startTopInRoot = chunkTopInRoot + startBox.top
                val endBottomInRoot = chunkTopInRoot + endBox.bottom
                val visibleTopInRoot = viewportTopInRoot
                val visibleBottomInRoot = viewportTopInRoot + viewportSize.height.toFloat()
                val fullyVisible = startTopInRoot >= visibleTopInRoot &&
                    endBottomInRoot <= (visibleBottomInRoot - bottomComfortPx)
                lastAnchorWasFullyVisible = fullyVisible
            }
    }

    LaunchedEffect(
        scrollTriggerSignal,
        anchorRange,
        activeTextLayout,
        activeChunkTopInRootPx,
        viewportTopInRootPx,
        viewportSize,
        scrollState.maxValue,
        autoScrollWhileListening,
    ) {
        val layout = activeTextLayout ?: return@LaunchedEffect
        val anchor = anchorRange ?: return@LaunchedEffect
        val chunkTopInRoot = activeChunkTopInRootPx ?: return@LaunchedEffect
        val viewportTopInRoot = viewportTopInRootPx ?: return@LaunchedEffect
        if (viewportSize.height <= 0) return@LaunchedEffect

        val startBox = layout.getCursorRect(anchor.first)
        val endIndex = anchor.last.coerceAtLeast(anchor.first)
        val endBox = layout.getCursorRect(endIndex)
        val startTopInRoot = chunkTopInRoot + startBox.top
        val endBottomInRoot = chunkTopInRoot + endBox.bottom
        val visibleTopInRoot = viewportTopInRoot
        val visibleBottomInRoot = viewportTopInRoot + viewportSize.height.toFloat()
        val desiredBottomInRoot = visibleBottomInRoot - bottomComfortPx
        val fullyVisibleNow = startTopInRoot >= visibleTopInRoot && endBottomInRoot <= desiredBottomInRoot
        val desiredAnchorInRoot = visibleTopInRoot + topComfortPx
        val deltaToTopAnchor = startTopInRoot - desiredAnchorInRoot
        val nowMs = SystemClock.elapsedRealtime()

        val externalTrigger = scrollTriggerSignal != lastHandledScrollTrigger
        if (externalTrigger && scrollState.maxValue == 0 && abs(deltaToTopAnchor) > 1f) {
            return@LaunchedEffect
        }
        val anchorChanged = lastAnchorRange != anchor
        val hiddenByBottom = endBottomInRoot > desiredBottomInRoot
        val transitionCrossedBottom = anchorChanged &&
            lastAnchorWasFullyVisible == true &&
            hiddenByBottom
        val transitionTrigger = autoScrollWhileListening &&
            nowMs >= suppressTransitionUntilMs &&
            transitionCrossedBottom &&
            hiddenByBottom
        val shouldScroll = externalTrigger || transitionTrigger
        if (!shouldScroll) {
            if (anchorChanged) {
                lastAnchorWasFullyVisible = fullyVisibleNow
            }
            lastAnchorRange = anchor
            return@LaunchedEffect
        }

        suspend fun jumpAnchorToTop() {
            repeat(8) {
                val latestLayout = activeTextLayout ?: return
                val latestChunkTop = activeChunkTopInRootPx ?: return
                val latestViewportTop = viewportTopInRootPx ?: return
                val latestStartTopInRoot = latestChunkTop + latestLayout.getCursorRect(anchor.first).top
                val desiredAnchorInRoot = latestViewportTop + topComfortPx
                val delta = latestStartTopInRoot - desiredAnchorInRoot
                val target = (scrollState.value + delta).roundToInt().coerceIn(0, scrollState.maxValue)
                if (abs(target - scrollState.value) <= 1) return
                isProgrammaticScroll = true
                scrollState.scrollTo(target)
                withFrameNanos { }
                isProgrammaticScroll = false
                withFrameNanos { }
            }
        }

        jumpAnchorToTop()
        if (isProgrammaticScroll) {
            isProgrammaticScroll = false
        }
        if (externalTrigger) {
            lastHandledScrollTrigger = scrollTriggerSignal
            suppressTransitionUntilMs = 0L
        }
        lastAnchorRange = anchor
        lastAnchorWasFullyVisible = true
    }
}

@Composable
private fun ParagraphSpacer(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}
