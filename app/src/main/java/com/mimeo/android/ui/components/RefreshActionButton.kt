package com.mimeo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mimeo.android.R

enum class RefreshActionVisualState {
    Idle,
    Refreshing,
    Success,
    Failure,
}

@Composable
fun RefreshActionButton(
    state: RefreshActionVisualState,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinDegrees by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refreshSpinDegrees",
    )
    val tint by animateColorAsState(
        targetValue = when (state) {
            RefreshActionVisualState.Success -> MaterialTheme.colorScheme.primary
            RefreshActionVisualState.Refreshing -> MaterialTheme.colorScheme.primary
            RefreshActionVisualState.Failure -> MaterialTheme.colorScheme.error
            RefreshActionVisualState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "refreshTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (state == RefreshActionVisualState.Success) 1.08f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "refreshScale",
    )

    IconButton(
        onClick = onClick,
        enabled = enabled && state != RefreshActionVisualState.Refreshing,
        modifier = modifier,
    ) {
        val iconRes = when (state) {
            RefreshActionVisualState.Success -> R.drawable.msr_check_circle_24
            RefreshActionVisualState.Failure -> R.drawable.msr_error_circle_24
            else -> R.drawable.msr_refresh_24
        }
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer(
                    rotationZ = if (state == RefreshActionVisualState.Refreshing) spinDegrees else 0f,
                    scaleX = scale,
                    scaleY = scale,
                ),
        )
    }
}
