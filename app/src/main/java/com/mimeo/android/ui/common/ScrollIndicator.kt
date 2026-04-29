package com.mimeo.android.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
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
): Modifier {
    return this.drawWithContent {
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
            topLeft = Offset(x = size.width - thumbWidthPx - endPaddingPx, y = thumbY),
            size = Size(width = thumbWidthPx, height = thumbHeightPx),
            cornerRadius = CornerRadius(x = thumbWidthPx / 2f, y = thumbWidthPx / 2f),
        )
    }
}
