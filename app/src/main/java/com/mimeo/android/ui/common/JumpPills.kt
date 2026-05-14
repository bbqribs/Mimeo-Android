package com.mimeo.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

@Composable
fun JumpPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val shape = if (isV1) mShapes.pill else RoundedCornerShape(18.dp)
    val bgColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isV1) mColors.accentOn else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .background(bgColor, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
        )
    }
}

private val JumpPillPanelGap = 2.dp

fun jumpPillBottomPadding(baseBottomClearance: Dp): Dp = baseBottomClearance + JumpPillPanelGap

fun shouldShowJumpToTopLazy(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    indexThreshold: Int = 1,
    offsetThresholdPx: Int = 160,
): Boolean {
    return firstVisibleItemIndex > indexThreshold || firstVisibleItemScrollOffset > offsetThresholdPx
}

fun shouldShowJumpToTopScroll(
    scrollValuePx: Int,
    thresholdPx: Int = 220,
): Boolean {
    return scrollValuePx > thresholdPx
}
