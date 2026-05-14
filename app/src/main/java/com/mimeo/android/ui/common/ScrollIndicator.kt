package com.mimeo.android.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

private class FloatRef(var value: Float = 0f)

fun Modifier.passiveVerticalScrollIndicator(
    scrollState: ScrollState,
    color: Color,
    minThumbHeight: Dp = 40.dp,
    thumbWidth: Dp = 3.dp,
    endPadding: Dp = 2.dp,
): Modifier = composed {
    val rootView = LocalView.current
    val rightInset = remember { FloatRef() }
    onGloballyPositioned { coords ->
        rightInset.value = rootView.width - coords.boundsInRoot().right
    }.drawWithContent {
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
                x = (size.width + rightInset.value - thumbWidthPx - endPaddingPx).coerceAtLeast(0f),
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
    val rootView = LocalView.current
    val rightInset = remember { FloatRef() }
    onGloballyPositioned { coords ->
        rightInset.value = rootView.width - coords.boundsInRoot().right
    }.drawWithContent {
        drawContent()
        val layoutInfo = listState.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItemsCount <= 0 || visibleItems.isEmpty()) return@drawWithContent

        val firstVisible = visibleItems.first()
        val averageItemHeightPx = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
        val estimatedContentHeightPx = (totalItemsCount * averageItemHeightPx).coerceAtLeast(size.height)
        val estimatedScrollPx =
            (firstVisible.index * averageItemHeightPx + firstVisible.offset.absoluteValue)
                .coerceAtLeast(0f)
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
                x = (size.width + rightInset.value - thumbWidthPx - endPaddingPx).coerceAtLeast(0f),
                y = thumbY,
            ),
            size = Size(width = thumbWidthPx, height = thumbHeightPx),
            cornerRadius = CornerRadius(x = thumbWidthPx / 2f, y = thumbWidthPx / 2f),
        )
    }
}
