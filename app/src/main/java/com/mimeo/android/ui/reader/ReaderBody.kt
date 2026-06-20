package com.mimeo.android.ui.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.coerceParagraphSpacing
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.model.ReaderTextAlignOption
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import com.mimeo.android.ui.theme.toFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private val READER_SCROLL_TOP_PADDING = 8.dp
private val READER_SCROLL_BOTTOM_PADDING = 0.dp
private val READER_SEARCH_FOCUS_EXTRA_TOP_PADDING = 120.dp
private const val MANUAL_SCROLL_SUPPRESS_MS = 1200L
private const val URL_ANNOTATION_TAG = "reader-url"
private val READER_LINK_BLUE = Color(0xFF64B5F6)
private const val READER_SCROLL_DEBUG_TAG = "MimeoReaderScroll"

@Composable
fun ReaderBody(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    preservedLinks: List<ReaderLinkRange> = emptyList(),
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
    readingTextAlign: ReaderTextAlignOption,
    paragraphSpacing: Float,
    selectionResetSignal: Int,
    scrollState: ScrollState,
    topOverlayOcclusionPx: Int = 0,
    bottomOverlayOcclusionPx: Int = 0,
    showEmptyPlaceholder: Boolean = true,
    onNonLinkTap: (() -> Unit)? = null,
    onManualScrollGesture: (() -> Unit)? = null,
    onLinkLongPress: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val safeChunkIndex = currentChunkIndex.coerceIn(0, (chunks.lastIndex).coerceAtLeast(0))
    val highlightBg = if (isV1) mColors.accent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val searchHighlightBg = if (isV1) mColors.accentDim else MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    val searchActiveHighlightBg = if (isV1) mColors.accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
    val sentenceRangesByChunk = remember(chunks) {
        chunks.map { segmentSentences(it.text) }
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var activeTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var activeChunkTopInRootPx by remember { mutableStateOf<Float?>(null) }
    var searchTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastHandledSearchTrigger by remember { mutableIntStateOf(searchFocusTriggerSignal) }
    var lastAnchorRange by remember { mutableStateOf<IntRange?>(null) }
    var lastAnchorWasFullyVisible by remember { mutableStateOf<Boolean?>(null) }
    var lastHandledScrollTrigger by remember { mutableIntStateOf(scrollTriggerSignal) }
    var lastObservedScrollValue by remember(scrollState) { mutableIntStateOf(scrollState.value) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var suppressTransitionUntilMs by remember { mutableStateOf(0L) }
    var manualScrollDetached by remember { mutableStateOf(false) }
    var followSuppressedByManualScroll by remember { mutableStateOf(false) }
    fun armManualScrollSuppression(reason: String) {
        if (!autoScrollWhileListening) return
        suppressTransitionUntilMs = SystemClock.elapsedRealtime() + MANUAL_SCROLL_SUPPRESS_MS
        manualScrollDetached = true
        followSuppressedByManualScroll = true
        onManualScrollGesture?.invoke()
        if (BuildConfig.DEBUG) {
            Log.d(
                READER_SCROLL_DEBUG_TAG,
                "manual_detach reason=$reason scroll=${scrollState.value} suppressUntil=$suppressTransitionUntilMs",
            )
        }
    }
    val manualScrollNestedConnection = remember(autoScrollWhileListening) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    shouldArmManualSuppressionFromDrag(
                        autoScrollWhileListening = autoScrollWhileListening,
                        source = source,
                        availableY = available.y,
                    )
                ) {
                    armManualScrollSuppression("nested_drag")
                }
                return Offset.Zero
            }
        }
    }
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
        if (chunks.isNotEmpty()) {
            chunks.joinToString(separator = READER_CHUNK_SEPARATOR) { it.text }
        } else {
            fullText.orEmpty()
        }
    }
    val fullTextChunkStartOffsets = remember(chunks, effectiveFullText) {
        if (effectiveFullText.isBlank() || chunks.isEmpty()) {
            emptyList()
        } else {
            buildChunkStartOffsetsForJoinedText(chunks, READER_CHUNK_SEPARATOR.length)
        }
    }
    // Each inter-chunk separator is a zero-width character that forms its own
    // paragraph; a ParagraphStyle gives that paragraph a height equal to the
    // chosen paragraph-spacing gap. This makes spacing freely tunable (not
    // quantized to whole blank lines) without changing any chunk offsets.
    val separatorParagraphRanges = remember(chunks, fullTextChunkStartOffsets) {
        buildChunkSeparatorParagraphRanges(chunks, fullTextChunkStartOffsets, READER_CHUNK_SEPARATOR.length)
    }
    val separatorGapLineHeight = readerParagraphGapLineHeight(
        spacingMultiplier = paragraphSpacing,
        bodyLineHeightSp = readingFontSizeSp * (readingLineHeightPercent / 100f),
    ).sp
    val fullTextHighlightRange = remember(
        effectiveFullText,
        chunks,
        safeChunkIndex,
        activeRangeInChunk,
        highlightedSentenceRange,
    ) {
        if (effectiveFullText.isBlank()) return@remember null
        val chunk = chunks.getOrNull(safeChunkIndex) ?: return@remember null
        val baseRange = resolveReaderHighlightRange(
            textLength = chunk.text.length,
            activeRange = activeRangeInChunk,
            sentenceRange = highlightedSentenceRange,
        ) ?: return@remember null
        mapChunkRangeToFullText(
            chunkIndex = safeChunkIndex,
            chunks = chunks,
            chunkStartOffsets = fullTextChunkStartOffsets,
            range = baseRange,
            fullTextLength = effectiveFullText.length,
        )
    }
    val fullTextFollowRange = remember(
        effectiveFullText,
        chunks,
        safeChunkIndex,
        currentChunkOffsetInChars,
    ) {
        if (effectiveFullText.isBlank()) return@remember null
        val chunk = chunks.getOrNull(safeChunkIndex) ?: return@remember null
        val chunkLength = chunk.text.length
        if (chunkLength <= 0) return@remember null
        val clampedOffset = currentChunkOffsetInChars.coerceIn(0, chunkLength - 1)
        mapChunkRangeToFullText(
            chunkIndex = safeChunkIndex,
            chunks = chunks,
            chunkStartOffsets = fullTextChunkStartOffsets,
            range = clampedOffset..clampedOffset,
            fullTextLength = effectiveFullText.length,
        )
    }
    val fullTextSearchRanges = remember(effectiveFullText, chunks, searchHighlightRangesByChunk) {
        if (effectiveFullText.isBlank()) return@remember emptyList()
        chunks.flatMapIndexed { index, chunk ->
            searchHighlightRangesByChunk[index].orEmpty().mapNotNull { range ->
                mapChunkRangeToFullText(
                    chunkIndex = index,
                    chunks = chunks,
                    chunkStartOffsets = fullTextChunkStartOffsets,
                    range = range,
                    fullTextLength = effectiveFullText.length,
                )
            }
        }
    }
    val fullTextFocusedSearchRange = remember(
        effectiveFullText,
        chunks,
        searchFocusChunkIndex,
        searchFocusRangeInChunk,
    ) {
        if (effectiveFullText.isBlank()) return@remember null
        val focusChunkIndex = searchFocusChunkIndex ?: return@remember null
        chunks.getOrNull(focusChunkIndex) ?: return@remember null
        val focusRange = searchFocusRangeInChunk ?: return@remember null
        mapChunkRangeToFullText(
            chunkIndex = focusChunkIndex,
            chunks = chunks,
            chunkStartOffsets = fullTextChunkStartOffsets,
            range = focusRange,
            fullTextLength = effectiveFullText.length,
        )
    }
    val searchFocusRangeForScroll = fullTextFocusedSearchRange
    val fullTextLinks = remember(effectiveFullText, preservedLinks) {
        if (preservedLinks.isNotEmpty()) {
            preservedLinks
                .mapNotNull { link ->
                    val safeStart = link.start.coerceIn(0, effectiveFullText.length)
                    val safeEnd = link.endExclusive.coerceIn(safeStart, effectiveFullText.length)
                    if (safeStart < safeEnd) {
                        ReaderLinkRange(
                            start = safeStart,
                            endExclusive = safeEnd,
                            url = link.url,
                        )
                    } else {
                        null
                    }
                }
                .distinctBy { Triple(it.start, it.endExclusive, it.url) }
        } else {
            extractReaderHttpLinks(effectiveFullText)
        }
    }
    val fullTextBaseAnnotated = remember(
        effectiveFullText,
        fullTextLinks,
        fullTextSearchRanges,
        searchHighlightBg,
        separatorParagraphRanges,
        separatorGapLineHeight,
    ) {
        if (effectiveFullText.isBlank()) {
            AnnotatedString("")
        } else {
            buildReaderBaseAnnotatedText(
                text = effectiveFullText,
                links = fullTextLinks,
                passiveSearchRanges = fullTextSearchRanges,
                passiveSearchHighlightBg = searchHighlightBg,
                separatorParagraphRanges = separatorParagraphRanges,
                separatorGapLineHeight = separatorGapLineHeight,
            )
        }
    }
    val fullTextAnnotated = remember(
        fullTextBaseAnnotated,
        effectiveFullText,
        fullTextHighlightRange,
        fullTextFocusedSearchRange,
        highlightBg,
        searchActiveHighlightBg,
    ) {
        if (effectiveFullText.isBlank()) {
            AnnotatedString("")
        } else {
            withReaderHighlightOverlays(
                base = fullTextBaseAnnotated,
                textLength = effectiveFullText.length,
                highlightRange = fullTextHighlightRange,
                focusedSearchRange = fullTextFocusedSearchRange,
                highlightBg = highlightBg,
                focusedSearchHighlightBg = searchActiveHighlightBg,
            )
        }
    }
    val readingTextStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            fontFamily = readingFontOption.toFontFamily(),
            fontSize = readingFontSizeSp.sp,
            lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
            color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
            textAlign = when (readingTextAlign) {
                ReaderTextAlignOption.JUSTIFIED -> TextAlign.Justify
                ReaderTextAlignOption.LEFT -> TextAlign.Start
                ReaderTextAlignOption.RIGHT -> TextAlign.Right
            },
        ),
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topComfortPx = with(density) { READER_SCROLL_TOP_PADDING.roundToPx().toFloat() }
    val bottomComfortPx = with(density) { READER_SCROLL_BOTTOM_PADDING.roundToPx().toFloat() }
    val searchFocusExtraTopPx = with(density) { READER_SEARCH_FOCUS_EXTRA_TOP_PADDING.roundToPx().toFloat() }
    val topOverlayPx = topOverlayOcclusionPx.coerceAtLeast(0).toFloat()
    val bottomOverlayPx = bottomOverlayOcclusionPx.coerceAtLeast(0).toFloat()
    // Trailing scroll headroom (== the dock height) added as bottom padding on the text so
    // the bottom-most lines can be scrolled clear of the player dock, mirroring the bottom
    // content padding on list screens. The dock always covers this region during playback so
    // it shows no blank space. It is applied as bottom padding on the existing text node (not
    // a wrapping layout) so the follow/anchor coordinate measurements are byte-for-byte the
    // baseline; the follow/search targets use the full scrollState.maxValue (which now
    // includes this room) so autoplay can keep the highlight above the dock at the end of the
    // document — clamping it away breaks end-of-text follow.
    val bottomContentSpacerPx = bottomOverlayOcclusionPx.coerceAtLeast(0)
    val bottomContentSpacerDp = with(density) { bottomContentSpacerPx.toDp() }
    val anchorRange = fullTextHighlightRange
    val followRange = fullTextFollowRange
    val scrollAnchorRange = followRange ?: anchorRange
    val scrollbarColor = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val latestOnNonLinkTap by rememberUpdatedState(onNonLinkTap)
    val latestOnLinkLongPress by rememberUpdatedState(onLinkLongPress)

    Box(
        modifier = modifier
            .passiveVerticalScrollIndicator(scrollState = scrollState, color = scrollbarColor)
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned { coordinates ->
                viewportTopInRootPx = coordinates.positionInRoot().y
            }
            .nestedScroll(manualScrollNestedConnection)
            .verticalScroll(scrollState),
        contentAlignment = Alignment.TopCenter,
    ) {
        key(selectionResetSignal) {
            SelectionContainer {
                if (effectiveFullText.isNotBlank()) {
                    ClickableText(
                        text = fullTextAnnotated,
                        modifier = Modifier
                            // Trailing headroom so the last lines clear the player dock. Bottom
                            // padding only — it grows the scroll range without shifting the text
                            // top, so anchor/viewport coordinates match the baseline exactly.
                            .padding(bottom = bottomContentSpacerDp)
                            .widthIn(max = readingMaxWidthDp.dp)
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                activeChunkTopInRootPx = coordinates.positionInRoot().y
                            }
                            // Purely observing long-press detector: reports the
                            // link (if any) under a long-press so the selection
                            // toolbar can offer link-address actions. It never
                            // consumes pointer events, so ClickableText's
                            // tap-to-open and the SelectionContainer's
                            // long-press text selection are both unaffected.
                            .pointerInput(fullTextAnnotated) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val longPress = awaitLongPressOrCancellation(down.id)
                                    if (longPress != null) {
                                        val layout = activeTextLayout
                                        if (layout != null) {
                                            val offset = layout.getOffsetForPosition(longPress.position)
                                            latestOnLinkLongPress?.invoke(
                                                resolveSelectionLinkUrl(
                                                    selectionStart = offset,
                                                    selectionEndExclusive = offset + 1,
                                                    links = fullTextLinks,
                                                ),
                                            )
                                        }
                                    }
                                }
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
                                val opened = try {
                                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(viewIntent)
                                    true
                                } catch (_: ActivityNotFoundException) {
                                    false
                                } catch (_: SecurityException) {
                                    false
                                } catch (_: IllegalArgumentException) {
                                    false
                                }
                                if (!opened) {
                                    latestOnNonLinkTap?.invoke()
                                }
                            } else {
                                latestOnNonLinkTap?.invoke()
                            }
                        },
                    )
                } else {
                    if (showEmptyPlaceholder) {
                        Text(
                            text = fullText?.ifBlank { "No readable text available." } ?: "No readable text available.",
                            modifier = Modifier
                                .widthIn(max = readingMaxWidthDp.dp)
                                .fillMaxWidth(),
                            style = readingTextStyle,
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .widthIn(max = readingMaxWidthDp.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(scrollState, topOverlayOcclusionPx, bottomOverlayOcclusionPx) {
        snapshotFlow { scrollState.value }
            .collect { scrollValue ->
                val manualScrollChange = scrollValue != lastObservedScrollValue && !isProgrammaticScroll
                lastObservedScrollValue = scrollValue
                val layout = activeTextLayout ?: return@collect
                val anchor = scrollAnchorRange ?: return@collect
                val chunkTopInRoot = activeChunkTopInRootPx ?: return@collect
                val viewportTopInRoot = viewportTopInRootPx ?: return@collect
                if (viewportSize.height <= 0) return@collect
                if (lastAnchorRange != anchor && !manualScrollChange) return@collect

                val metrics = resolveReaderAnchorMetrics(layout, anchor) ?: return@collect
                val startTopInRoot = chunkTopInRoot + metrics.startTop
                val endBottomInRoot = chunkTopInRoot + metrics.endBottom
                val (visibleTopInRoot, visibleBottomInRoot) = computeReaderVisibleBounds(
                    viewportTopInRoot = viewportTopInRoot,
                    viewportHeightPx = viewportSize.height,
                    topOcclusionPx = topOverlayPx,
                    bottomOcclusionPx = bottomOverlayPx,
                )
                val fullyVisible = startTopInRoot >= visibleTopInRoot &&
                    endBottomInRoot <= (visibleBottomInRoot - bottomComfortPx)
                if (
                    manualScrollChange &&
                    autoScrollWhileListening
                ) {
                    armManualScrollSuppression("scroll_delta")
                }
                lastAnchorWasFullyVisible = fullyVisible
            }
    }

    LaunchedEffect(
        searchFocusTriggerSignal,
        searchFocusChunkIndex,
        searchFocusRangeForScroll,
        searchTextLayout,
        viewportSize,
        scrollState.maxValue,
    ) {
        if (searchFocusTriggerSignal == lastHandledSearchTrigger) return@LaunchedEffect
        val range = searchFocusRangeForScroll ?: return@LaunchedEffect
        val layout = searchTextLayout ?: return@LaunchedEffect
        if (viewportSize.height <= 0) return@LaunchedEffect

        val maxOffset = layout.layoutInput.text.length.coerceAtLeast(0)
        val safeStart = range.first.coerceIn(0, maxOffset)
        val startBox = layout.getCursorRect(safeStart)
        val target = computeReaderSearchFocusScrollTarget(
            scrollMaxValue = scrollState.maxValue,
            startBoxTop = startBox.top,
            topComfortPx = topComfortPx,
            searchFocusExtraTopPx = searchFocusExtraTopPx,
        )
        isProgrammaticScroll = true
        scrollState.scrollTo(target)
        isProgrammaticScroll = false
        suppressTransitionUntilMs = SystemClock.elapsedRealtime() + MANUAL_SCROLL_SUPPRESS_MS
        lastHandledSearchTrigger = searchFocusTriggerSignal
    }

    LaunchedEffect(
        scrollTriggerSignal,
        scrollAnchorRange,
        activeTextLayout,
        activeChunkTopInRootPx,
        viewportTopInRootPx,
        viewportSize,
        topOverlayOcclusionPx,
        bottomOverlayOcclusionPx,
        scrollState.maxValue,
        autoScrollWhileListening,
    ) {
        val layout = activeTextLayout ?: return@LaunchedEffect
        val anchor = scrollAnchorRange ?: return@LaunchedEffect
        val chunkTopInRoot = activeChunkTopInRootPx ?: return@LaunchedEffect
        val viewportTopInRoot = viewportTopInRootPx ?: return@LaunchedEffect
        if (viewportSize.height <= 0) return@LaunchedEffect

        val metrics = resolveReaderAnchorMetrics(layout, anchor) ?: return@LaunchedEffect
        val startTopInRoot = chunkTopInRoot + metrics.startTop
        val endBottomInRoot = chunkTopInRoot + metrics.endBottom
        val (visibleTopInRoot, visibleBottomInRoot) = computeReaderVisibleBounds(
            viewportTopInRoot = viewportTopInRoot,
            viewportHeightPx = viewportSize.height,
            topOcclusionPx = topOverlayPx,
            bottomOcclusionPx = bottomOverlayPx,
        )
        val desiredBottomInRoot = visibleBottomInRoot - bottomComfortPx
        val fullyVisibleNow = startTopInRoot >= visibleTopInRoot && endBottomInRoot <= desiredBottomInRoot
        val desiredTopAnchorInRoot = visibleTopInRoot + topComfortPx
        val deltaToTopAnchor = startTopInRoot - desiredTopAnchorInRoot
        val nowMs = SystemClock.elapsedRealtime()

        val triggerKind = classifyReaderScrollTrigger(scrollTriggerSignal, lastHandledScrollTrigger)
        val externalTrigger = triggerKind != ReaderScrollTriggerKind.NONE
        val forceReattach = triggerKind == ReaderScrollTriggerKind.FORCE_REATTACH
        val anchorChanged = lastAnchorRange != anchor
        if (followSuppressedByManualScroll && !forceReattach) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    READER_SCROLL_DEBUG_TAG,
                    "skip_follow suppressed=true trigger=$triggerKind anchorChanged=$anchorChanged",
                )
            }
            if (externalTrigger) {
                lastHandledScrollTrigger = scrollTriggerSignal
            }
            if (anchorChanged) {
                lastAnchorWasFullyVisible = fullyVisibleNow
            }
            lastAnchorRange = anchor
            return@LaunchedEffect
        }
        if (forceReattach) {
            followSuppressedByManualScroll = false
            if (BuildConfig.DEBUG) {
                Log.d(READER_SCROLL_DEBUG_TAG, "force_reattach trigger=$triggerKind")
            }
        }
        val canAutoReattachNow = forceReattach || nowMs >= suppressTransitionUntilMs
        if (canAutoReattachNow) {
            if (
                shouldAutoReattachAfterManualScroll(
                    manualScrollDetached = manualScrollDetached,
                    anchorFullyVisible = fullyVisibleNow,
                    triggerKind = triggerKind,
                )
            ) {
                manualScrollDetached = false
            }
        }
        if (
            shouldClearFollowSuppression(
                followSuppressedByManualScroll = followSuppressedByManualScroll,
                manualScrollDetached = manualScrollDetached,
                triggerKind = triggerKind,
                nowMs = nowMs,
                suppressUntilMs = suppressTransitionUntilMs,
            )
        ) {
            followSuppressedByManualScroll = false
        }
        if (
            shouldSuppressStandardTriggerDuringCooldown(
                triggerKind = triggerKind,
                nowMs = nowMs,
                suppressUntilMs = suppressTransitionUntilMs,
            )
        ) {
            lastHandledScrollTrigger = scrollTriggerSignal
            if (anchorChanged) {
                lastAnchorWasFullyVisible = fullyVisibleNow
            }
            lastAnchorRange = anchor
            return@LaunchedEffect
        }
        if (shouldKeepDetachedAfterTrigger(manualScrollDetached, triggerKind)) {
            if (externalTrigger) {
                // Consume non-reattach triggers while detached so playback updates cannot
                // keep the effect in a perpetual "external trigger" state.
                lastHandledScrollTrigger = scrollTriggerSignal
            }
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
        val followEnabled = autoScrollWhileListening && !followSuppressedByManualScroll
        val standardFollowTrigger = shouldAutoScrollForStandardPlayback(
            triggerKind = triggerKind,
            autoScrollWhileListening = followEnabled,
            manualScrollDetached = manualScrollDetached,
            hiddenByBottom = hiddenByBottom,
            nowMs = nowMs,
            suppressUntilMs = suppressTransitionUntilMs,
        )
        val boundaryFollowTrigger = shouldAutoScrollForPlaybackBoundary(
            autoScrollWhileListening = followEnabled,
            manualScrollDetached = manualScrollDetached,
            anchorChanged = anchorChanged,
            hiddenByBottom = hiddenByBottom,
            nowMs = nowMs,
            suppressUntilMs = suppressTransitionUntilMs,
        )
        val centerIfOffscreenTrigger = shouldCenterForTrigger(
            triggerKind = triggerKind,
            anchorOffscreen = !fullyVisibleNow,
        )
        val useCenteredAnchor = shouldUseCenteredJumpAnchor(
            centerIfOffscreenTrigger = centerIfOffscreenTrigger,
            standardFollowTrigger = standardFollowTrigger,
            boundaryFollowTrigger = boundaryFollowTrigger,
            forceReattach = forceReattach,
        )
        val shouldScroll = boundaryFollowTrigger || standardFollowTrigger || centerIfOffscreenTrigger || forceReattach
        if (!shouldScroll) {
            if (externalTrigger) {
                lastHandledScrollTrigger = scrollTriggerSignal
            }
            if (anchorChanged) {
                lastAnchorWasFullyVisible = fullyVisibleNow
            }
            lastAnchorRange = anchor
            return@LaunchedEffect
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                READER_SCROLL_DEBUG_TAG,
                "jump trigger=$triggerKind standard=$standardFollowTrigger boundary=$boundaryFollowTrigger center=$centerIfOffscreenTrigger force=$forceReattach detached=$manualScrollDetached suppressed=$followSuppressedByManualScroll",
            )
        }

        suspend fun jumpAnchorToTop() {
            repeat(8) {
                val latestLayout = activeTextLayout ?: return
                val latestChunkTop = activeChunkTopInRootPx ?: return
                val latestViewportTop = viewportTopInRootPx ?: return
                val latestMetrics = resolveReaderAnchorMetrics(latestLayout, anchor) ?: return
                val latestStartTopInRoot = latestChunkTop + latestMetrics.startTop
                val (latestVisibleTop, latestVisibleBottom) = computeReaderVisibleBounds(
                    viewportTopInRoot = latestViewportTop,
                    viewportHeightPx = viewportSize.height,
                    topOcclusionPx = topOverlayPx,
                    bottomOcclusionPx = bottomOverlayPx,
                )
                val latestVisibleHeight = (latestVisibleBottom - latestVisibleTop).coerceAtLeast(1f)
                val desiredAnchorInRoot = if (useCenteredAnchor) {
                    latestVisibleTop + (latestVisibleHeight / 2f)
                } else {
                    latestVisibleTop + topComfortPx
                }
                val delta = latestStartTopInRoot - desiredAnchorInRoot
                val target = computeReaderFollowTarget(
                    currentScroll = scrollState.value,
                    deltaToAnchorPx = delta,
                    scrollMaxValue = scrollState.maxValue,
                )
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
            manualScrollDetached = nextManualDetachState(
                currentDetached = manualScrollDetached,
                triggerKind = triggerKind,
            )
            suppressTransitionUntilMs = 0L
        }
        lastAnchorRange = anchor
        lastAnchorWasFullyVisible = true
    }
}

internal fun mapChunkRangeToFullText(
    chunkIndex: Int,
    chunks: List<PlaybackChunk>,
    chunkStartOffsets: List<Int>,
    range: IntRange,
    fullTextLength: Int,
): IntRange? {
    if (fullTextLength <= 0) return null
    if (chunkIndex !in chunks.indices) return null
    if (chunkIndex !in chunkStartOffsets.indices) return null
    val chunk = chunks[chunkIndex]
    val chunkLength = chunk.text.length.coerceAtLeast(0)
    if (chunkLength <= 0) return null
    val safeStartInChunk = range.first.coerceIn(0, chunkLength)
    val safeEndInChunk = range.last
        .coerceAtLeast(safeStartInChunk)
        .coerceIn(safeStartInChunk, chunkLength)
    val chunkStart = chunkStartOffsets[chunkIndex].coerceIn(0, fullTextLength)
    val start = (chunkStart + safeStartInChunk).coerceIn(0, fullTextLength)
    val endExclusive = (chunkStart + safeEndInChunk + 1).coerceIn(start, fullTextLength)
    return if (start < endExclusive) start until endExclusive else null
}

/**
 * Zero-width separator placed between chunks in the joined full-text reader
 * layout. Each separator forms its own paragraph (see
 * [buildChunkSeparatorParagraphRanges]) whose height is set by a [ParagraphStyle]
 * to the chosen paragraph-spacing gap. Keeping it a single character means chunk
 * character offsets stay in sync via [buildChunkStartOffsetsForJoinedText], and
 * the rendered gap is freely tunable rather than quantized to whole blank lines.
 */
internal const val READER_CHUNK_SEPARATOR: String = "\u200B"

/**
 * Plain blank-line separator substituted for [READER_CHUNK_SEPARATOR] in any
 * reader text leaving the renderer \u2014 copy, share, export. The zero-width
 * separator is a layout-only character; copied or shared text must not carry
 * invisible characters, so it is swapped for the prior stable "\n\n" separator.
 */
internal fun readerTextWithPlainSeparators(text: String): String =
    text.replace(READER_CHUNK_SEPARATOR, "\n\n")

/**
 * Height, in sp, of the inter-chunk separator paragraph. [spacingMultiplier] is
 * the selected paragraph-spacing preset, expressed as a multiple of the body
 * line height so the gap scales with the reader's font-size and line-spacing
 * settings. The multiplier is clamped to the valid preset range.
 */
internal fun readerParagraphGapLineHeight(
    spacingMultiplier: Float,
    bodyLineHeightSp: Float,
): Float = bodyLineHeightSp.coerceAtLeast(0f) * coerceParagraphSpacing(spacingMultiplier)

/**
 * Ranges (one per inter-chunk separator) covering the zero-width separator
 * character in the joined full-text layout. Each separator is its own
 * paragraph; applying a [ParagraphStyle] with the desired gap line height to
 * these ranges renders the inter-paragraph spacing without altering any chunk
 * character offsets.
 */
internal fun buildChunkSeparatorParagraphRanges(
    chunks: List<PlaybackChunk>,
    chunkStartOffsets: List<Int>,
    separatorLength: Int,
): List<IntRange> {
    if (separatorLength < 1 || chunks.size < 2 || chunkStartOffsets.size != chunks.size) {
        return emptyList()
    }
    return buildList {
        chunks.forEachIndexed { index, chunk ->
            if (index < chunks.lastIndex) {
                val separatorStart = chunkStartOffsets[index] + chunk.text.length
                add(separatorStart..(separatorStart + separatorLength - 1))
            }
        }
    }
}

internal fun buildChunkStartOffsetsForJoinedText(
    chunks: List<PlaybackChunk>,
    separatorLength: Int = 2,
): List<Int> {
    if (chunks.isEmpty()) return emptyList()
    var cursor = 0
    return buildList(chunks.size) {
        chunks.forEachIndexed { index, chunk ->
            add(cursor)
            cursor += chunk.text.length
            if (index < chunks.lastIndex) {
                cursor += separatorLength.coerceAtLeast(0)
            }
        }
    }
}

internal fun computeReaderSearchFocusScrollTarget(
    scrollMaxValue: Int,
    startBoxTop: Float,
    topComfortPx: Float,
    searchFocusExtraTopPx: Float,
): Int {
    val rawTarget = startBoxTop - (topComfortPx + searchFocusExtraTopPx)
    return rawTarget.roundToInt().coerceIn(0, scrollMaxValue)
}

internal fun buildReaderBaseAnnotatedText(
    text: String,
    links: List<ReaderLinkRange>,
    passiveSearchRanges: List<IntRange>,
    passiveSearchHighlightBg: Color,
    separatorParagraphRanges: List<IntRange> = emptyList(),
    separatorGapLineHeight: TextUnit = TextUnit.Unspecified,
): AnnotatedString = buildAnnotatedString {
    append(text)
    // Make each zero-width chunk separator its own paragraph with a fixed line
    // height: that paragraph's height is the rendered inter-paragraph gap.
    if (separatorGapLineHeight != TextUnit.Unspecified) {
        separatorParagraphRanges.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val endExclusive = (range.last + 1).coerceIn(start, text.length)
            if (start < endExclusive) {
                addStyle(
                    style = ParagraphStyle(
                        lineHeight = separatorGapLineHeight,
                        // The separator is a single-line paragraph. Without an
                        // explicit LineHeightStyle, Compose does not apply
                        // lineHeight to a paragraph's first (here, only) line,
                        // so every spacing option rendered an identical gap.
                        // Trim.None keeps the full lineHeight as the gap height.
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None,
                        ),
                    ),
                    start = start,
                    end = endExclusive,
                )
                // Shrink the separator glyph to ~nothing. A line cannot render
                // shorter than its font's intrinsic height, so at the body font
                // size every gap below ~one line clamped to the same minimum.
                // A 1sp glyph lets lineHeight control the gap across the full
                // preset range, including sub-line spacings.
                addStyle(
                    style = SpanStyle(fontSize = 1.sp),
                    start = start,
                    end = endExclusive,
                )
            }
        }
    }
    links.forEach { link ->
        val start = link.start.coerceIn(0, text.length)
        val endExclusive = link.endExclusive.coerceIn(start, text.length)
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
        addBackgroundStyleIfValidRange(
            range = range,
            textLength = text.length,
            background = passiveSearchHighlightBg,
        )
    }
}

internal fun withReaderHighlightOverlays(
    base: AnnotatedString,
    textLength: Int,
    highlightRange: IntRange?,
    focusedSearchRange: IntRange?,
    highlightBg: Color,
    focusedSearchHighlightBg: Color,
): AnnotatedString {
    if (base.text.isBlank() || textLength <= 0) return base
    val builder = AnnotatedString.Builder(base)
    builder.addBackgroundStyleIfValidRange(
        range = highlightRange,
        textLength = textLength,
        background = highlightBg,
    )
    builder.addBackgroundStyleIfValidRange(
        range = focusedSearchRange,
        textLength = textLength,
        background = focusedSearchHighlightBg,
    )
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.addBackgroundStyleIfValidRange(
    range: IntRange?,
    textLength: Int,
    background: Color,
) {
    val normalized = normalizeInclusiveRange(range = range, textLength = textLength) ?: return
    addStyle(
        style = SpanStyle(background = background),
        start = normalized.first,
        end = normalized.last + 1,
    )
}

private fun normalizeInclusiveRange(range: IntRange?, textLength: Int): IntRange? {
    if (range == null || textLength <= 0) return null
    val start = range.first.coerceIn(0, textLength)
    val endExclusive = (range.last + 1).coerceIn(start, textLength)
    return if (start < endExclusive) start until endExclusive else null
}

internal fun shouldArmManualSuppressionFromDrag(
    autoScrollWhileListening: Boolean,
    source: NestedScrollSource,
    availableY: Float,
): Boolean {
    if (!autoScrollWhileListening) return false
    if (source != NestedScrollSource.Drag) return false
    return abs(availableY) > 0.5f
}

internal fun shouldSuppressStandardTriggerDuringCooldown(
    triggerKind: ReaderScrollTriggerKind,
    nowMs: Long,
    suppressUntilMs: Long,
): Boolean {
    return triggerKind == ReaderScrollTriggerKind.STANDARD && nowMs < suppressUntilMs
}

/**
 * Resolves the absolute scroll offset the follow should animate to so the active anchor
 * lands at the desired position. [deltaToAnchorPx] is how far the anchor must move (positive
 * = scroll down). The result clamps to `[0, scrollMaxValue]` using the FULL scroll range —
 * including the trailing dock-clearing spacer — so end-of-document follow can lift the last
 * anchor above the dock. Reducing [scrollMaxValue] here (e.g. by the spacer height) silently
 * breaks autoplay follow once the highlight reaches the bottom of the visible text.
 */
internal fun computeReaderFollowTarget(
    currentScroll: Int,
    deltaToAnchorPx: Float,
    scrollMaxValue: Int,
): Int = (currentScroll + deltaToAnchorPx).roundToInt().coerceIn(0, scrollMaxValue.coerceAtLeast(0))

internal fun computeReaderVisibleBounds(
    viewportTopInRoot: Float,
    viewportHeightPx: Int,
    topOcclusionPx: Float,
    bottomOcclusionPx: Float,
): Pair<Float, Float> {
    val visibleTop = viewportTopInRoot + topOcclusionPx.coerceAtLeast(0f)
    val rawVisibleBottom = viewportTopInRoot + viewportHeightPx.toFloat() - bottomOcclusionPx.coerceAtLeast(0f)
    val visibleBottom = if (rawVisibleBottom <= visibleTop) visibleTop + 1f else rawVisibleBottom
    return visibleTop to visibleBottom
}

internal data class ReaderAnchorMetrics(
    val startTop: Float,
    val endBottom: Float,
)

internal fun resolveReaderAnchorMetrics(
    layout: TextLayoutResult,
    anchorRange: IntRange,
): ReaderAnchorMetrics? {
    val text = layout.layoutInput.text.text
    if (text.isEmpty()) return null
    val maxIndex = text.lastIndex
    val safeStart = anchorRange.first.coerceIn(0, maxIndex)
    val safeEnd = anchorRange.last
        .coerceAtLeast(anchorRange.first)
        .coerceIn(safeStart, maxIndex)
    val glyphStart = findReaderVisibleGlyphOffset(
        text = text,
        start = safeStart,
        end = safeEnd,
        preferEnd = false,
    )
    val glyphEnd = findReaderVisibleGlyphOffset(
        text = text,
        start = safeStart,
        end = safeEnd,
        preferEnd = true,
    )
    val startRect = resolveReaderGlyphRect(
        layout = layout,
        preferredOffset = glyphStart,
        fallbackOffset = safeStart,
    )
    val endRect = resolveReaderGlyphRect(
        layout = layout,
        preferredOffset = glyphEnd,
        fallbackOffset = safeEnd,
    )
    return ReaderAnchorMetrics(
        startTop = startRect.top,
        endBottom = endRect.bottom,
    )
}

internal fun findReaderVisibleGlyphOffset(
    text: String,
    start: Int,
    end: Int,
    preferEnd: Boolean,
): Int {
    if (text.isEmpty()) return 0
    val maxIndex = text.lastIndex
    val safeStart = start.coerceIn(0, maxIndex)
    val safeEnd = end.coerceAtLeast(safeStart).coerceIn(safeStart, maxIndex)
    if (preferEnd) {
        for (index in safeEnd downTo safeStart) {
            if (!text[index].isWhitespace()) return index
        }
        return safeEnd
    }
    for (index in safeStart..safeEnd) {
        if (!text[index].isWhitespace()) return index
    }
    return safeStart
}

private fun resolveReaderGlyphRect(
    layout: TextLayoutResult,
    preferredOffset: Int,
    fallbackOffset: Int,
): Rect {
    val textLength = layout.layoutInput.text.length
    if (textLength <= 0) return Rect.Zero
    val maxIndex = textLength - 1
    val safePreferred = preferredOffset.coerceIn(0, maxIndex)
    val safeFallback = fallbackOffset.coerceIn(0, maxIndex)
    val glyphRect = layout.getBoundingBox(safePreferred)
    if (glyphRect.height > 0f) return glyphRect
    return layout.getCursorRect(safeFallback.coerceIn(0, textLength))
}
