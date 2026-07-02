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
 * Height of the only visible element of the NUB-mode player dock: the thin progress line
 * pinned to the bottom of the (otherwise transparent) transport row. Matches the
 * `PlayerProgressLine` height in PlayerScreen.
 */
val NUB_VISIBLE_PANEL_HEIGHT = 3.dp

/**
 * First-frame estimate for the player-panel occupied height, used before the shell has
 * measured the rendered panel (and as the authoritative value for NUB mode and in tests).
 * Returns 0 when the panel is not visible so hidden / no-playback states never add bottom
 * padding.
 *
 * The FULL / MINIMAL dp values are intentionally coarse fall-backs only; the measured value
 * from [PlayerPanelInsetState] supersedes them as soon as the panel lays out. NUB is special:
 * its transport row is transparent except for the bottom progress line, so the *measured*
 * column would overshoot — the visible occupied height is only the progress line plus the
 * shell clearance, which is what this returns.
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
        PlayerControlsMode.NUB -> NUB_VISIBLE_PANEL_HEIGHT
    }
    return panel + shellBottomClearance
}

/**
 * Resolves the effective player-panel inset the shell threads to screens:
 *  - 0 when the panel is hidden (no excess blank space),
 *  - the analytic visible height for NUB mode (the measured column would overshoot because
 *    most of the NUB transport row is transparent),
 *  - the measured height once it is available,
 *  - otherwise the first-frame estimate.
 */
fun resolvePlayerPanelInset(
    showMiniPlayer: Boolean,
    controlsMode: PlayerControlsMode,
    measuredOccupiedHeight: Dp,
    estimatedOccupiedHeight: Dp,
): Dp = when {
    !showMiniPlayer -> 0.dp
    controlsMode == PlayerControlsMode.NUB -> estimatedOccupiedHeight
    measuredOccupiedHeight > 0.dp -> measuredOccupiedHeight
    else -> estimatedOccupiedHeight
}
