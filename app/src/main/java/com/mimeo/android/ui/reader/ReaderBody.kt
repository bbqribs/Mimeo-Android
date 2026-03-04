package com.mimeo.android.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaybackChunk

@Composable
fun ReaderBody(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    currentChunkIndex: Int,
    readingFontSizeSp: Int,
    readingLineHeightPercent: Int,
    readingMaxWidthDp: Int,
    paragraphSpacing: ParagraphSpacingOption,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val paragraphSeparator = when (paragraphSpacing) {
        ParagraphSpacingOption.SMALL -> "\n\n"
        ParagraphSpacingOption.MEDIUM -> "\n\n\n"
        ParagraphSpacingOption.LARGE -> "\n\n\n\n"
    }
    val surface = remember(fullText, chunks, paragraphSeparator) {
        buildReaderSurface(fullText = fullText, chunks = chunks, paragraphSeparator = paragraphSeparator)
    }
    val safeChunkIndex = currentChunkIndex.coerceIn(0, (surface.chunkRanges.lastIndex).coerceAtLeast(0))
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val highlightFg = MaterialTheme.colorScheme.onSurface
    val highlighted = remember(surface.text, surface.chunkRanges, safeChunkIndex, highlightBg, highlightFg) {
        buildAnnotatedString {
            append(surface.text)
            surface.chunkRanges.getOrNull(safeChunkIndex)?.let { range ->
                if (range.first < range.last && range.last <= surface.text.length) {
                    addStyle(
                        style = SpanStyle(
                            background = highlightBg,
                            color = highlightFg,
                        ),
                        start = range.first,
                        end = range.last,
                    )
                }
            }
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
            Text(
                text = highlighted,
                modifier = Modifier
                    .widthIn(max = readingMaxWidthDp.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                style = readingTextStyle,
            )
        }
    }
}

private data class ReaderSurfaceModel(
    val text: String,
    val chunkRanges: List<IntRange>,
)

private fun buildReaderSurface(
    fullText: String?,
    chunks: List<PlaybackChunk>,
    paragraphSeparator: String,
): ReaderSurfaceModel {
    val trimmedFullText = fullText?.trim().orEmpty()
    if (trimmedFullText.isNotEmpty()) {
        val ranges = chunks.mapNotNull { chunk ->
            val start = chunk.startChar.coerceIn(0, trimmedFullText.length)
            val endExclusive = chunk.endChar.coerceIn(start, trimmedFullText.length)
            if (start < endExclusive) start until endExclusive else null
        }
        if (ranges.isNotEmpty()) {
            return ReaderSurfaceModel(text = trimmedFullText, chunkRanges = ranges)
        }
        return ReaderSurfaceModel(
            text = trimmedFullText,
            chunkRanges = listOf(0 until trimmedFullText.length),
        )
    }

    // TODO: when the player always has canonical full text available, remove this chunk-concatenation fallback.
    if (chunks.isEmpty()) {
        return ReaderSurfaceModel(text = "No readable text available.", chunkRanges = emptyList())
    }

    val sb = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    chunks.forEachIndexed { index, chunk ->
        if (index > 0) sb.append(paragraphSeparator)
        val start = sb.length
        sb.append(chunk.text)
        val endExclusive = sb.length
        if (start < endExclusive) {
            ranges += start until endExclusive
        }
    }
    return ReaderSurfaceModel(text = sb.toString(), chunkRanges = ranges)
}
