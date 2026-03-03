package com.mimeo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mimeo.android.R

@Stable
class LocusPlayerBarState {
    var isVisible by mutableStateOf(false)
        private set
    private var canMoveBackward by mutableStateOf(false)
    private var canMoveForward by mutableStateOf(false)
    private var canPlay by mutableStateOf(false)
    private var canMarkDone by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var onPreviousSegment: () -> Unit by mutableStateOf({})
    private var onPlayPause: () -> Unit by mutableStateOf({})
    private var onNextSegment: () -> Unit by mutableStateOf({})
    private var onPreviousItem: () -> Unit by mutableStateOf({})
    private var onMarkDone: () -> Unit by mutableStateOf({})
    private var onNextItem: () -> Unit by mutableStateOf({})

    fun update(
        canMoveBackward: Boolean,
        canMoveForward: Boolean,
        canPlay: Boolean,
        canMarkDone: Boolean,
        isPlaying: Boolean,
        onPreviousSegment: () -> Unit,
        onPlayPause: () -> Unit,
        onNextSegment: () -> Unit,
        onPreviousItem: () -> Unit,
        onMarkDone: () -> Unit,
        onNextItem: () -> Unit,
    ) {
        isVisible = true
        this.canMoveBackward = canMoveBackward
        this.canMoveForward = canMoveForward
        this.canPlay = canPlay
        this.canMarkDone = canMarkDone
        this.isPlaying = isPlaying
        this.onPreviousSegment = onPreviousSegment
        this.onPlayPause = onPlayPause
        this.onNextSegment = onNextSegment
        this.onPreviousItem = onPreviousItem
        this.onMarkDone = onMarkDone
        this.onNextItem = onNextItem
    }

    fun clear() {
        isVisible = false
    }

    @Composable
    fun Render(modifier: Modifier = Modifier) {
        if (!isVisible) return
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onMarkDone, enabled = canMarkDone) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_check_circle_24),
                        contentDescription = "Mark done",
                    )
                }
                IconButton(onClick = onPreviousItem) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_previous_24),
                        contentDescription = "Previous item",
                    )
                }
                IconButton(onClick = onPreviousSegment, enabled = canMoveBackward) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_fast_rewind_24),
                        contentDescription = "Previous segment",
                    )
                }
                IconButton(onClick = onPlayPause, enabled = canPlay) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.msr_pause_24 else R.drawable.msr_play_arrow_24,
                        ),
                        contentDescription = if (isPlaying) "Pause playback" else "Play",
                    )
                }
                IconButton(onClick = onNextSegment, enabled = canMoveForward) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_fast_forward_24),
                        contentDescription = "Next segment",
                    )
                }
                IconButton(onClick = onNextItem) {
                    Icon(
                        painter = painterResource(id = R.drawable.msr_skip_next_24),
                        contentDescription = "Next item",
                    )
                }
            }
        }
    }
}

@Composable
fun rememberLocusPlayerBarState(): LocusPlayerBarState {
    return remember { LocusPlayerBarState() }
}
