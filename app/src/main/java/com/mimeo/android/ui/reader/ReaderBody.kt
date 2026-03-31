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
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.ui.theme.toFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private val READER_SCROLL_TOP_PADDING = 0.dp
private val READER_SCROLL_BOTTOM_PADDING = 0.dp
private val READER_SEARCH_FOCUS_EXTRA_TOP_PADDING = 120.dp
private const val MANUAL_SCROLL_SUPPRESS_MS = 1200L
private const val URL_ANNOTATION_TAG = "reader-url"
private val READER_LINK_BLUE = Color(0xFF64B5F6)

@Composable
fun ReaderBody(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    currentChunkIndex: Int,
    currentChunkOffsetInChars: Int,
    activeRangeInChunk: IntRange?,
    searchHighlightRangesByChunk: Map<Int, List<IntRange>>,
    searchFocusChunkIndex: Int?,
    searchFocusRangeInChunk: IntRange?,
    searchFocusTriggerSignal: Int,
    scrollTriggerSignal: Int,
    autoScrollWhileListening: Boolean,
    readingFontSizeSp: Int,
    readingFontOption: ReaderFontOption,
    readingLineHeightPercent: Int,
    readingMaxWidthDp: Int,
    paragraphSpacing: ParagraphSpacingOption,
    selectionResetSignal: Int,
    scrollState: ScrollState,
    showEmptyPlaceholder: Boolean = true,
    onNonLinkTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val paragraphSpacingDp = when (paragraphSpacing) {
        ParagraphSpacingOption.SMALL -> 12.dp
        ParagraphSpacingOption.MEDIUM -> 18.dp
        ParagraphSpacingOption.LARGE -> 24.dp
    }
    val safeChunkIndex = currentChunkIndex.coerceIn(0, (chunks.lastIndex).coerceAtLeast(0))
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val searchHighlightBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    val searchActiveHighlightBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
    val useFullTextLayout = true
    val sentenceRangesByChunk = remember(chunks) {
        chunks.map { segmentSentences(it.text) }
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var activeTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var activeChunkTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var searchTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var searchChunkTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var lastHandledSearchTrigger by remember { mutableIntStateOf(searchFocusTriggerSignal) }
    var lastAnchorRange by remember { mutableStateOf<IntRange?>(null) }
    var lastAnchorWasFullyVisible by remember { mutableStateOf<Boolean?>(null) }
    var lastHandledScrollTrigger by remember { mutableIntStateOf(scrollTriggerSignal) }
    var lastObservedScrollValue by remember(scrollState) { mutableIntStateOf(scrollState.value) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var suppressTransitionUntilMs by remember { mutableStateOf(0L) }
    var manualScrollDetached by remember { mutableStateOf(false) }
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
    val effectiveFullText = remember(fullText, chunks) {
        fullText
            ?.takeIf { it.isNotBlank() }
            ?: chunks.joinToString(separator = "\n\n") { it.text }
    }
    val fullTextHighlightRange = remember(
        useFullTextLayout,
        effectiveFullText,
        chunks,
        safeChunkIndex,
        activeRangeInChunk,
        highlightedSentenceRange,
    ) {
        if (!useFullTextLayout || effectiveFullText.isBlank()) return@remember null
        val chunk = chunks.getOrNull(safeChunkIndex) ?: return@remember null
        val baseRange = resolveReaderHighlightRange(
            textLength = chunk.text.length,
            activeRange = activeRangeInChunk,
            sentenceRange = highlightedSentenceRange,
        ) ?: return@remember null
        mapChunkRangeToFullText(chunk, baseRange, effectiveFullText.length)
    }
    val fullTextSearchRanges = remember(useFullTextLayout, effectiveFullText, chunks, searchHighlightRangesByChunk) {
        if (!useFullTextLayout || effectiveFullText.isBlank()) return@remember emptyList()
        chunks.flatMapIndexed { index, chunk ->
            searchHighlightRangesByChunk[index].orEmpty().mapNotNull { range ->
                mapChunkRangeToFullText(chunk, range, effectiveFullText.length)
            }
        }
    }
    val fullTextFocusedSearchRange = remember(
        useFullTextLayout,
        effectiveFullText,
        chunks,
        searchFocusChunkIndex,
        searchFocusRangeInChunk,
    ) {
        if (!useFullTextLayout || effectiveFullText.isBlank()) return@remember null
        val focusChunk = searchFocusChunkIndex?.let { chunks.getOrNull(it) } ?: return@remember null
        val focusRange = searchFocusRangeInChunk ?: return@remember null
        mapChunkRangeToFullText(focusChunk, focusRange, effectiveFullText.length)
    }
    val readingTextStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            fontFamily = readingFontOption.toFontFamily(),
            fontSize = readingFontSizeSp.sp,
            lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topComfortPx = with(density) { READER_SCROLL_TOP_PADDING.roundToPx().toFloat() }
    val bottomComfortPx = with(density) { READER_SCROLL_BOTTOM_PADDING.roundToPx().toFloat() }
    val searchFocusExtraTopPx = with(density) { READER_SEARCH_FOCUS_EXTRA_TOP_PADDING.roundToPx().toFloat() }
    val anchorRange = if (useFullTextLayout) {
        fullTextHighlightRange
    } else {
        resolveReaderHighlightRange(
            textLength = chunks.getOrNull(safeChunkIndex)?.text?.length ?: 0,
            activeRange = activeRangeInChunk,
            sentenceRange = highlightedSentenceRange,
        )
    }

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
                if (useFullTextLayout && effectiveFullText.isNotBlank()) {
                    val fullTextAnnotated = buildAnnotatedString {
                        append(effectiveFullText)
                        extractReaderHttpLinks(effectiveFullText).forEach { link ->
                            val start = link.start.coerceIn(0, effectiveFullText.length)
                            val endExclusive = link.endExclusive.coerceIn(start, effectiveFullText.length)
                            if (start < endExclusive) {
                                addStringAnnotation(
                                    tag = URL_ANNOTATION_TAG,
                                    annotation = link.url,
                                    start = start,
                                    end = endExclusive,
                                )
                                addStyle(
                                    style = SpanStyle(
                                        color = READER_LINK_BLUE,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                    start = start,
                                    end = endExclusive,
                                )
                            }
                        }
                        fullTextSearchRanges.forEach { range ->
                            val start = range.first.coerceIn(0, effectiveFullText.length)
                            val endExclusive = (range.last + 1).coerceIn(start, effectiveFullText.length)
                            if (start < endExclusive) {
                                addStyle(
                                    style = SpanStyle(background = searchHighlightBg),
                                    start = start,
                                    end = endExclusive,
                                )
                            }
                        }
                        fullTextHighlightRange?.let { range ->
                            addStyle(
                                style = SpanStyle(background = highlightBg),
                                start = range.first.coerceIn(0, effectiveFullText.length),
                                end = (range.last + 1).coerceIn(range.first.coerceIn(0, effectiveFullText.length), effectiveFullText.length),
                            )
                        }
                        fullTextFocusedSearchRange?.let { range ->
                            addStyle(
                                style = SpanStyle(background = searchActiveHighlightBg),
                                start = range.first.coerceIn(0, effectiveFullText.length),
                                end = (range.last + 1).coerceIn(range.first.coerceIn(0, effectiveFullText.length), effectiveFullText.length),
                            )
                        }
                    }
                    ClickableText(
                        text = fullTextAnnotated,
                        modifier = Modifier
                            .widthIn(max = readingMaxWidthDp.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .onGloballyPositioned { coordinates ->
                                val top = coordinates.positionInRoot().y
                                activeChunkTopInRootPx = top
                                searchChunkTopInRootPx = top
                            },
                        style = readingTextStyle,
                        onTextLayout = { layout ->
                            activeTextLayout = layout
                            searchTextLayout = layout
                        },
                        onClick = { offset ->
                            val url = fullTextAnnotated.getStringAnnotations(
                                tag = URL_ANNOTATION_TAG,
                                start = offset,
                                end = offset,
                            ).firstOrNull()?.item
                            if (!url.isNullOrBlank()) {
                                runCatching { uriHandler.openUri(url) }
                            } else {
                                onNonLinkTap?.invoke()
                            }
                        },
                    )
                } else if (chunks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = readingMaxWidthDp.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        chunks.forEachIndexed { index, chunk ->
                            val isHighlighted = index == safeChunkIndex
                            val isSearchFocused = searchFocusChunkIndex == index
                            val passiveSearchRanges = searchHighlightRangesByChunk[index].orEmpty()
                            val effectiveHighlightRange = if (isHighlighted) {
                                resolveReaderHighlightRange(
                                    textLength = chunk.text.length,
                                    activeRange = activeRangeInChunk,
                                    sentenceRange = highlightedSentenceRange,
                                )
                            } else {
                                null
                            }
                            val effectiveSearchRange = if (isSearchFocused) {
                                resolveReaderHighlightRange(
                                    textLength = chunk.text.length,
                                    activeRange = searchFocusRangeInChunk,
                                    sentenceRange = null,
                                )
                            } else {
                                null
                            }
                            val chunkText = buildAnnotatedString {
                                append(chunk.text)
                                extractReaderHttpLinks(chunk.text).forEach { link ->
                                    val start = link.start.coerceIn(0, chunk.text.length)
                                    val endExclusive = link.endExclusive.coerceIn(start, chunk.text.length)
                                    if (start < endExclusive) {
                                        addStringAnnotation(
                                            tag = URL_ANNOTATION_TAG,
                                            annotation = link.url,
                                            start = start,
                                            end = endExclusive,
                                        )
                                        addStyle(
                                            style = SpanStyle(
                                                color = READER_LINK_BLUE,
                                                textDecoration = TextDecoration.Underline,
                                            ),
                                            start = start,
                                            end = endExclusive,
                                        )
                                    }
                                }
                                passiveSearchRanges.forEach { range ->
                                    val start = range.first.coerceIn(0, chunk.text.length)
                                    val endExclusive = (range.last + 1).coerceIn(start, chunk.text.length)
                                    if (start < endExclusive) {
                                        addStyle(
                                            style = SpanStyle(
                                                background = searchHighlightBg,
                                            ),
                                            start = start,
                                            end = endExclusive,
                                        )
                                    }
                                }
                                if (effectiveHighlightRange != null) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = highlightBg,
                                        ),
                                        start = effectiveHighlightRange.first,
                                        end = (effectiveHighlightRange.last + 1).coerceAtMost(chunk.text.length),
                                    )
                                }
                                if (effectiveSearchRange != null) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = searchActiveHighlightBg,
                                        ),
                                        start = effectiveSearchRange.first,
                                        end = (effectiveSearchRange.last + 1).coerceAtMost(chunk.text.length),
                                    )
                                }
                            }
                            ClickableText(
                                text = chunkText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { base ->
                                        if (isHighlighted || isSearchFocused) {
                                            base.onGloballyPositioned { coordinates ->
                                                if (isHighlighted) {
                                                    activeChunkTopInRootPx = coordinates.positionInRoot().y
                                                }
                                                if (isSearchFocused) {
                                                    searchChunkTopInRootPx = coordinates.positionInRoot().y
                                                }
                                            }
                                        } else {
                                            base
                                        }
                                    }
                                    .background(
                                        color = Color.Black,
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                style = readingTextStyle,
                                onTextLayout = { layout ->
                                    if (isHighlighted) {
                                        activeTextLayout = layout
                                    }
                                    if (isSearchFocused) {
                                        searchTextLayout = layout
                                    }
                                },
                                onClick = { offset ->
                                    val url = chunkText.getStringAnnotations(
                                        tag = URL_ANNOTATION_TAG,
                                        start = offset,
                                        end = offset,
                                    ).firstOrNull()?.item
                                    if (!url.isNullOrBlank()) {
                                        runCatching { uriHandler.openUri(url) }
                                    } else {
                                        onNonLinkTap?.invoke()
                                    }
                                },
                            )
                            if (index < chunks.lastIndex) {
                                ParagraphSpacer(height = paragraphSpacingDp)
                            }
                        }
                    }
                } else {
                    if (showEmptyPlaceholder) {
                        Text(
                            text = fullText?.ifBlank { "No readable text available." } ?: "No readable text available.",
                            modifier = Modifier
                                .widthIn(max = readingMaxWidthDp.dp)
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            style = readingTextStyle,
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .widthIn(max = readingMaxWidthDp.dp)
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { scrollValue ->
                if (scrollValue != lastObservedScrollValue && !isProgrammaticScroll) {
                    suppressTransitionUntilMs = SystemClock.elapsedRealtime() + MANUAL_SCROLL_SUPPRESS_MS
                    manualScrollDetached = true
                }
                lastObservedScrollValue = scrollValue
                val layout = activeTextLayout ?: return@collect
                val anchor = anchorRange ?: return@collect
                val chunkTopInRoot = activeChunkTopInRootPx ?: return@collect
                val viewportTopInRoot = viewportTopInRootPx ?: return@collect
                if (viewportSize.height <= 0) return@collect
                if (lastAnchorRange != anchor) return@collect

                val maxOffset = layout.layoutInput.text.length.coerceAtLeast(0)
                val startIndex = anchor.first.coerceIn(0, maxOffset)
                val endIndex = anchor.last
                    .coerceAtLeast(anchor.first)
                    .coerceIn(startIndex, maxOffset)
                val startBox = layout.getCursorRect(startIndex)
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
        searchFocusTriggerSignal,
        searchFocusChunkIndex,
        searchFocusRangeInChunk,
        searchTextLayout,
        searchChunkTopInRootPx,
        viewportTopInRootPx,
        viewportSize,
        scrollState.maxValue,
    ) {
        if (searchFocusTriggerSignal == lastHandledSearchTrigger) return@LaunchedEffect
        val range = searchFocusRangeInChunk ?: return@LaunchedEffect
        val layout = searchTextLayout ?: return@LaunchedEffect
        val chunkTopInRoot = searchChunkTopInRootPx ?: return@LaunchedEffect
        val viewportTopInRoot = viewportTopInRootPx ?: return@LaunchedEffect
        if (viewportSize.height <= 0) return@LaunchedEffect

        val maxOffset = layout.layoutInput.text.length.coerceAtLeast(0)
        val safeStart = range.first.coerceIn(0, maxOffset)
        val startBox = layout.getCursorRect(safeStart)
        val startTopInRoot = chunkTopInRoot + startBox.top
        val desiredAnchorInRoot = viewportTopInRoot + topComfortPx + searchFocusExtraTopPx
        val delta = startTopInRoot - desiredAnchorInRoot
        val target = (scrollState.value + delta).roundToInt().coerceIn(0, scrollState.maxValue)
        isProgrammaticScroll = true
        scrollState.scrollTo(target)
        isProgrammaticScroll = false
        lastHandledSearchTrigger = searchFocusTriggerSignal
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

        val maxOffset = layout.layoutInput.text.length.coerceAtLeast(0)
        val safeStart = anchor.first.coerceIn(0, maxOffset)
        val safeEnd = anchor.last
            .coerceAtLeast(anchor.first)
            .coerceIn(safeStart, maxOffset)
        val startBox = layout.getCursorRect(safeStart)
        val endBox = layout.getCursorRect(safeEnd)
        val startTopInRoot = chunkTopInRoot + startBox.top
        val endBottomInRoot = chunkTopInRoot + endBox.bottom
        val visibleTopInRoot = viewportTopInRoot
        val visibleBottomInRoot = viewportTopInRoot + viewportSize.height.toFloat()
        val desiredBottomInRoot = visibleBottomInRoot - bottomComfortPx
        val fullyVisibleNow = startTopInRoot >= visibleTopInRoot && endBottomInRoot <= desiredBottomInRoot
        val desiredTopAnchorInRoot = visibleTopInRoot + topComfortPx
        val deltaToTopAnchor = startTopInRoot - desiredTopAnchorInRoot
        val nowMs = SystemClock.elapsedRealtime()

        val externalTrigger = scrollTriggerSignal != lastHandledScrollTrigger
        val forceReattach = externalTrigger && scrollTriggerSignal > 0 && (scrollTriggerSignal % 2 != 0)
        val centerIfOffscreen = scrollTriggerSignal < 0
        val anchorChanged = lastAnchorRange != anchor
        if (manualScrollDetached && !externalTrigger && !forceReattach) {
            if (anchorChanged) {
                lastAnchorWasFullyVisible = fullyVisibleNow
            }
            lastAnchorRange = anchor
            return@LaunchedEffect
        }
        if (externalTrigger && scrollState.maxValue == 0 && abs(deltaToTopAnchor) > 1f) {
            return@LaunchedEffect
        }
        val hiddenByBottom = endBottomInRoot > desiredBottomInRoot
        val transitionCrossedBottom = anchorChanged &&
            lastAnchorWasFullyVisible == true &&
            hiddenByBottom
        val transitionTrigger = autoScrollWhileListening &&
            !manualScrollDetached &&
            nowMs >= suppressTransitionUntilMs &&
            transitionCrossedBottom &&
            hiddenByBottom
        val shouldScroll = transitionTrigger || (externalTrigger && !fullyVisibleNow) || forceReattach
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
                val latestMaxOffset = latestLayout.layoutInput.text.length.coerceAtLeast(0)
                val latestSafeStart = anchor.first.coerceIn(0, latestMaxOffset)
                val latestStartTopInRoot = latestChunkTop + latestLayout.getCursorRect(latestSafeStart).top
                val desiredAnchorInRoot = if (externalTrigger && centerIfOffscreen) {
                    latestViewportTop + (viewportSize.height.toFloat() / 2f)
                } else {
                    latestViewportTop + topComfortPx
                }
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
        if (forceReattach) {
            manualScrollDetached = false
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

private fun mapChunkRangeToFullText(
    chunk: PlaybackChunk,
    range: IntRange,
    fullTextLength: Int,
): IntRange? {
    if (fullTextLength <= 0) return null
    val chunkLength = chunk.text.length.coerceAtLeast(0)
    if (chunkLength <= 0) return null
    val safeStartInChunk = range.first.coerceIn(0, chunkLength)
    val safeEndInChunk = range.last
        .coerceAtLeast(safeStartInChunk)
        .coerceIn(safeStartInChunk, chunkLength)
    val start = (chunk.startChar + safeStartInChunk).coerceIn(0, fullTextLength)
    val endExclusive = (chunk.startChar + safeEndInChunk + 1).coerceIn(start, fullTextLength)
    return if (start < endExclusive) start until endExclusive else null
}
