package com.mimeo.android.ui.player

import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.ui.common.jumpPillBottomPadding
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPanelInsetTest {

    @Test
    fun estimateIsZeroWhenPlayerHidden() {
        assertEquals(
            0.dp,
            estimatedPlayerPanelOccupiedHeight(
                showMiniPlayer = false,
                controlsMode = PlayerControlsMode.FULL,
                shellBottomClearance = 12.dp,
            ),
        )
    }

    @Test
    fun estimateIncludesShellClearancePerMode() {
        assertEquals(
            108.dp,
            estimatedPlayerPanelOccupiedHeight(
                showMiniPlayer = true,
                controlsMode = PlayerControlsMode.FULL,
                shellBottomClearance = 12.dp,
            ),
        )
        assertEquals(
            84.dp,
            estimatedPlayerPanelOccupiedHeight(
                showMiniPlayer = true,
                controlsMode = PlayerControlsMode.MINIMAL,
                shellBottomClearance = 12.dp,
            ),
        )
        assertEquals(
            13.dp,
            estimatedPlayerPanelOccupiedHeight(
                showMiniPlayer = true,
                controlsMode = PlayerControlsMode.NUB,
                shellBottomClearance = 12.dp,
            ),
        )
    }

    @Test
    fun resolveReturnsZeroWhenHiddenEvenWithStaleMeasurement() {
        assertEquals(
            0.dp,
            resolvePlayerPanelInset(
                showMiniPlayer = false,
                measuredOccupiedHeight = 120.dp,
                estimatedOccupiedHeight = 108.dp,
            ),
        )
    }

    @Test
    fun resolvePrefersMeasuredHeightOverEstimateWhenAvailable() {
        assertEquals(
            120.dp,
            resolvePlayerPanelInset(
                showMiniPlayer = true,
                measuredOccupiedHeight = 120.dp,
                estimatedOccupiedHeight = 108.dp,
            ),
        )
    }

    @Test
    fun resolveFallsBackToEstimateBeforeMeasurementLands() {
        assertEquals(
            108.dp,
            resolvePlayerPanelInset(
                showMiniPlayer = true,
                measuredOccupiedHeight = 0.dp,
                estimatedOccupiedHeight = 108.dp,
            ),
        )
    }

    @Test
    fun stateHolderPropagatesMeasuredHeightAndClears() {
        val state = PlayerPanelInsetState()
        assertEquals(0.dp, state.occupiedHeight)

        state.update(104.dp)
        assertEquals(104.dp, state.occupiedHeight)

        // Negative measurements are coerced to zero.
        state.update((-5).dp)
        assertEquals(0.dp, state.occupiedHeight)

        state.update(72.dp)
        state.clear()
        assertEquals(0.dp, state.occupiedHeight)
    }

    @Test
    fun jumpPillSitsSmallGapAbovePanel() {
        // Lower edge of the pill clears the measured panel by the fixed gap.
        assertEquals(110.dp, jumpPillBottomPadding(108.dp))
        // No player -> pill rests just above the screen bottom by only the gap.
        assertEquals(2.dp, jumpPillBottomPadding(0.dp))
    }
}
