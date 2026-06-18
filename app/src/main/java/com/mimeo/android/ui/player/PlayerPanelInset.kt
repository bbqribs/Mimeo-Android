package com.mimeo.android.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlayerControlsMode

/**
 * Single source of truth for the vertical space the bottom player panel occupies in the
 * app shell, including the shell's bottom-clearance strip below the mini player.
 *
 * The shell measures the rendered player panel (mini player + clearance) and writes the
 * result here; screens consume it (via the threaded `jumpPillBottomClearance` /
 * `snapBottomClearance` value) to reserve scrollable bottom space and to anchor floating
 * jump pills just above the panel. Measuring the rendered panel avoids brittle per-device
 * or per-mode hard-coded heights and automatically tracks collapse/expand/hide changes.
 */
@Stable
class PlayerPanelInsetState {
    var occupiedHeight: Dp by mutableStateOf(0.dp)
        private set

    /** Record the measured panel height; ignores no-op updates to avoid recomposition churn. */
    fun update(height: Dp) {
        val clamped = height.coerceAtLeast(0.dp)
        if (clamped != occupiedHeight) {
            occupiedHeight = clamped
        }
    }

    /** Reset the measured height (e.g. when the panel is hidden / no active playback). */
    fun clear() {
        if (occupiedHeight != 0.dp) {
            occupiedHeight = 0.dp
        }
    }
}

/**
 * First-frame estimate for the player-panel occupied height, used before the shell has
 * measured the rendered panel (and as a stable value in tests). Returns 0 when the panel
 * is not visible so hidden / no-playback states never add bottom padding.
 *
 * The per-mode dp values are intentionally coarse fall-backs only; the measured value from
 * [PlayerPanelInsetState] supersedes them as soon as the panel lays out.
 */
fun estimatedPlayerPanelOccupiedHeight(
    showMiniPlayer: Boolean,
    controlsMode: PlayerControlsMode,
    shellBottomClearance: Dp,
): Dp {
    if (!showMiniPlayer) return 0.dp
    val panel = when (controlsMode) {
        PlayerControlsMode.FULL -> 96.dp
        PlayerControlsMode.MINIMAL -> 72.dp
        PlayerControlsMode.NUB -> 1.dp
    }
    return panel + shellBottomClearance
}

/**
 * Resolves the effective player-panel inset the shell threads to screens:
 *  - 0 when the panel is hidden (no excess blank space),
 *  - the measured height once it is available,
 *  - otherwise the first-frame estimate.
 */
fun resolvePlayerPanelInset(
    showMiniPlayer: Boolean,
    measuredOccupiedHeight: Dp,
    estimatedOccupiedHeight: Dp,
): Dp = when {
    !showMiniPlayer -> 0.dp
    measuredOccupiedHeight > 0.dp -> measuredOccupiedHeight
    else -> estimatedOccupiedHeight
}
