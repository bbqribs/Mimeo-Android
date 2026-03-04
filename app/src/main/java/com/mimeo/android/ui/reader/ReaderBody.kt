package com.mimeo.android.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaybackChunk

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderBody(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    currentChunkIndex: Int,
    currentChunkOffsetInChars: Int,
    activeRangeInChunk: IntRange?,
    readingFontSizeSp: Int,
    readingLineHeightPercent: Int,
    readingMaxWidthDp: Int,
    paragraphSpacing: ParagraphSpacingOption,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val paragraphSpacingDp = when (paragraphSpacing) {
        ParagraphSpacingOption.SMALL -> 12.dp
        ParagraphSpacingOption.MEDIUM -> 18.dp
        ParagraphSpacingOption.LARGE -> 24.dp
    }
    val safeChunkIndex = currentChunkIndex.coerceIn(0, (chunks.lastIndex).coerceAtLeast(0))
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val sentenceRangesByChunk = remember(chunks) {
        chunks.map { segmentSentences(it.text) }
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
    val readingTextStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            fontSize = readingFontSizeSp.sp,
            lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
        ),
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionContainer {
            if (chunks.isNotEmpty()) {
                val chunkRequesters = remember(chunks.size) {
                    List(chunks.size) { BringIntoViewRequester() }
                }
                LaunchedEffect(safeChunkIndex, highlightedSentenceRange, chunks.size) {
                    chunkRequesters.getOrNull(safeChunkIndex)?.bringIntoView()
                }
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
                                        color = highlightFg,
                                        fontWeight = FontWeight.SemiBold,
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
                                .bringIntoViewRequester(chunkRequesters[index])
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = readingTextStyle,
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

@Composable
private fun ParagraphSpacer(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}
