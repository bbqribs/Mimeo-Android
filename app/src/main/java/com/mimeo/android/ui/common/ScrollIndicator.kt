package com.mimeo.android.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.passiveVerticalScrollIndicator(
    scrollState: ScrollState,
    color: Color,
    minThumbHeight: Dp = 40.dp,
    thumbWidth: Dp = 3.dp,
    endPadding: Dp = 2.dp,
): Modifier = composed {
    drawWithContent {
        drawContent()
        val max = scrollState.maxValue
        if (max <= 0) return@drawWithContent

        val minThumbHeightPx = minThumbHeight.toPx()
        val thumbWidthPx = thumbWidth.toPx()
        val endPaddingPx = endPadding.toPx()
        val fraction = scrollState.value.toFloat() / max.toFloat()
        val thumbHeightPx = (size.height * size.height / (size.height + max))
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(size.height)
        val trackHeightPx = (size.height - thumbHeightPx).coerceAtLeast(0f)
        val thumbY = fraction * trackHeightPx

        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = (size.width - thumbWidthPx - endPaddingPx).coerceAtLeast(0f),
                y = thumbY,
            ),
            size = Size(width = thumbWidthPx, height = thumbHeightPx),
            cornerRadius = CornerRadius(x = thumbWidthPx / 2f, y = thumbWidthPx / 2f),
        )
    }
}

fun Modifier.passiveVerticalScrollIndicator(
    listState: LazyListState,
    color: Color,
    minThumbHeight: Dp = 40.dp,
    thumbWidth: Dp = 3.dp,
    endPadding: Dp = 2.dp,
): Modifier = composed {
    drawWithContent {
        drawContent()
        val layoutInfo = listState.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItemsCount <= 0 || visibleItems.isEmpty()) return@drawWithContent

        val averageItemHeightPx = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
        val estimatedContentHeightPx = (totalItemsCount * averageItemHeightPx).coerceAtLeast(size.height)
        val estimatedScrollPx = (
            listState.firstVisibleItemIndex * averageItemHeightPx +
                listState.firstVisibleItemScrollOffset.toFloat()
            ).coerceAtLeast(0f)
        val maxScrollPx = (estimatedContentHeightPx - size.height).coerceAtLeast(0f)
        if (maxScrollPx <= 0f) return@drawWithContent

        val minThumbHeightPx = minThumbHeight.toPx()
        val thumbWidthPx = thumbWidth.toPx()
        val endPaddingPx = endPadding.toPx()
        val fraction = (estimatedScrollPx / maxScrollPx).coerceIn(0f, 1f)
        val thumbHeightPx = (size.height * size.height / estimatedContentHeightPx)
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(size.height)
        val trackHeightPx = (size.height - thumbHeightPx).coerceAtLeast(0f)
        val thumbY = fraction * trackHeightPx

        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = (size.width - thumbWidthPx - endPaddingPx).coerceAtLeast(0f),
                y = thumbY,
            ),
            size = Size(width = thumbWidthPx, height = thumbHeightPx),
            cornerRadius = CornerRadius(x = thumbWidthPx / 2f, y = thumbWidthPx / 2f),
        )
    }
}
