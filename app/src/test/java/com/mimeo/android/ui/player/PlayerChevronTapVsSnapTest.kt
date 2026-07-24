package com.mimeo.android.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chevron stacks a horizontal-drag detector and a tap detector on one box. Once a
 * gesture crosses touch slop the drag detector consumes moves and cancels the tap detector,
 * so a tap that drifts a few pixels used to fire nothing at all — neither a tap (cancelled)
 * nor a snap (below threshold). These pin the drift band as taps.
 */
class PlayerChevronTapVsSnapTest {

    @Test
    fun smallDriftIsATapNotASnap() {
        // A thumb on a small chip routinely drifts past touch slop without meaning to drag.
        assertTrue(chevronDragEndIsTap(0f))
        assertTrue(chevronDragEndIsTap(9f))
        assertTrue(chevronDragEndIsTap(-9f))
        assertTrue(chevronDragEndIsTap(31.9f))
        assertFalse(chevronDragIsSnap(31.9f))
    }

    @Test
    fun deliberateDragIsASnapNotATap() {
        assertTrue(chevronDragIsSnap(CHEVRON_SNAP_MIN_DRAG_PX))
        assertTrue(chevronDragIsSnap(-CHEVRON_SNAP_MIN_DRAG_PX))
        assertTrue(chevronDragIsSnap(140f))
        assertFalse(chevronDragEndIsTap(140f))
        assertFalse(chevronDragEndIsTap(-140f))
    }

    @Test
    fun tapAndSnapArePartitioned() {
        // Exactly one handler must fire for any drag total; never both, never neither.
        listOf(-200f, -32f, -31f, 0f, 5f, 31f, 32f, 200f).forEach { delta ->
            assertTrue(
                "delta=$delta must be exactly one of tap/snap",
                chevronDragEndIsTap(delta) != chevronDragIsSnap(delta),
            )
        }
    }
}
