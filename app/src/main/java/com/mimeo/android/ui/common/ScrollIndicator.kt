package com.mimeo.android.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private class FloatRef(var value: Float = 0f)

internal data class VerticalScrollThumbGeometry(
    val topPx: Float,
    val heightPx: Float,
    val travelPx: Float,
)

internal fun verticalScrollThumbLeftPx(
    viewportWidthPx: Float,
    thumbWidthPx: Float,
    endPaddingPx: Float,
): Float {
    val safeViewportWidthPx = viewportWidthPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeThumbWidthPx = thumbWidthPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeEndPaddingPx = endPaddingPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    return (safeViewportWidthPx - safeThumbWidthPx - safeEndPaddingPx).coerceAtLeast(0f)
}

internal fun verticalScrollThumbGeometry(
    viewportHeightPx: Float,
    maxScrollValue: Int,
    scrollValue: Int,
    minThumbHeightPx: Float,
): VerticalScrollThumbGeometry? {
    if (viewportHeightPx <= 0f || !viewportHeightPx.isFinite() || maxScrollValue <= 0) return null

    val safeMinThumbHeightPx = minThumbHeightPx
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, viewportHeightPx)
        ?: 0f
    val contentHeightPx = viewportHeightPx + maxScrollValue.toFloat()
    val thumbHeightPx = (viewportHeightPx * viewportHeightPx / contentHeightPx)
        .coerceAtLeast(safeMinThumbHeightPx)
        .coerceAtMost(viewportHeightPx)
    val travelPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val fraction = scrollValue.coerceIn(0, maxScrollValue).toFloat() / maxScrollValue.toFloat()
    return VerticalScrollThumbGeometry(
        topPx = fraction * travelPx,
        heightPx = thumbHeightPx,
        travelPx = travelPx,
    )
}

internal fun scrollValueForThumbDrag(
    pointerYPx: Float,
    grabFraction: Float,
    viewportHeightPx: Float,
    maxScrollValue: Int,
    minThumbHeightPx: Float,
): Int {
    val geometry = verticalScrollThumbGeometry(
        viewportHeightPx = viewportHeightPx,
        maxScrollValue = maxScrollValue,
        scrollValue = 0,
        minThumbHeightPx = minThumbHeightPx,
    ) ?: return 0
    if (geometry.travelPx <= 0f) return 0

    val safePointerY = pointerYPx.takeIf { it.isFinite() } ?: 0f
    val thumbTopPx = (
        safePointerY - grabFraction.coerceIn(0f, 1f) * geometry.heightPx
        ).coerceIn(0f, geometry.travelPx)
    return (thumbTopPx / geometry.travelPx * maxScrollValue.toFloat())
        .roundToInt()
        .coerceIn(0, maxScrollValue)
}

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

/**
 * Draws the same narrow indicator as [passiveVerticalScrollIndicator], while allowing a
 * drag that begins on the visible thumb to navigate a [ScrollState]. The wider hit gutter is
 * transparent and only claims a vertical drag whose initial down is within the thumb's vertical
 * bounds. Taps on the thumb or elsewhere on the track remain available to underlying row controls.
 */
fun Modifier.draggableVerticalScrollIndicator(
    scrollState: ScrollState,
    color: Color,
    enabled: Boolean = true,
    onDragStateChange: (Boolean) -> Unit = {},
    minThumbHeight: Dp = 40.dp,
    thumbWidth: Dp = 3.dp,
    endPadding: Dp = 2.dp,
    touchGutterWidth: Dp = 24.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val currentOnDragStateChange = rememberUpdatedState(onDragStateChange)
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val endPaddingPx = with(density) { endPadding.toPx() }
    val touchGutterWidthPx = with(density) { touchGutterWidth.toPx() }

    pointerInput(
        scrollState,
        enabled,
        minThumbHeightPx,
        touchGutterWidthPx,
    ) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val geometry = verticalScrollThumbGeometry(
                viewportHeightPx = size.height.toFloat(),
                maxScrollValue = scrollState.maxValue,
                scrollValue = scrollState.value,
                minThumbHeightPx = minThumbHeightPx,
            ) ?: return@awaitEachGesture
            val gutterEndX = size.width.toFloat()
            val gutterStartX = (gutterEndX - touchGutterWidthPx).coerceAtLeast(0f)
            val downOnThumb = down.position.x in gutterStartX..gutterEndX &&
                down.position.y in geometry.topPx..(geometry.topPx + geometry.heightPx)
            if (!downOnThumb) return@awaitEachGesture

            val grabFraction = ((down.position.y - geometry.topPx) / geometry.heightPx)
                .coerceIn(0f, 1f)
            var accumulatedX = 0f
            var accumulatedY = 0f
            var draggingThumb = false
            try {
                while (true) {
                    val change = awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == down.id }
                        ?: break
                    if (change.changedToUpIgnoreConsumed()) break

                    val delta = change.position - change.previousPosition
                    accumulatedX += delta.x
                    accumulatedY += delta.y
                    if (!draggingThumb) {
                        val crossedVerticalSlop = abs(accumulatedY) > viewConfiguration.touchSlop &&
                            abs(accumulatedY) >= abs(accumulatedX)
                        if (!crossedVerticalSlop) {
                            if (abs(accumulatedX) > viewConfiguration.touchSlop) break
                            continue
                        }
                        draggingThumb = true
                        currentOnDragStateChange.value(true)
                    }

                    val targetValue = scrollValueForThumbDrag(
                        pointerYPx = change.position.y,
                        grabFraction = grabFraction,
                        viewportHeightPx = size.height.toFloat(),
                        maxScrollValue = scrollState.maxValue,
                        minThumbHeightPx = minThumbHeightPx,
                    )
                    val scrollDelta = targetValue - scrollState.value
                    if (scrollDelta != 0) scrollState.dispatchRawDelta(scrollDelta.toFloat())
                    change.consume()
                }
            } finally {
                if (draggingThumb) currentOnDragStateChange.value(false)
            }
        }
    }.drawWithContent {
        drawContent()
        val geometry = verticalScrollThumbGeometry(
            viewportHeightPx = size.height,
            maxScrollValue = scrollState.maxValue,
            scrollValue = scrollState.value,
            minThumbHeightPx = minThumbHeightPx,
        ) ?: return@drawWithContent

        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = verticalScrollThumbLeftPx(size.width, thumbWidthPx, endPaddingPx),
                y = geometry.topPx,
            ),
            size = Size(width = thumbWidthPx, height = geometry.heightPx),
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
                x = (size.width + rightInset.value - thumbWidthPx - endPaddingPx).coerceAtLeast(0f),
                y = thumbY,
            ),
            size = Size(width = thumbWidthPx, height = thumbHeightPx),
            cornerRadius = CornerRadius(x = thumbWidthPx / 2f, y = thumbWidthPx / 2f),
        )
    }
}
