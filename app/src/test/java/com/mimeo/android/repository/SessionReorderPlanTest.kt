package com.mimeo.android.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionReorderPlanTest {

    @Test
    fun movesCurrentItemPointerWhenCurrentItemIsReordered() {
        val plan = computeSessionReorderPlan(
            itemCount = 5,
            currentIndex = 2,
            fromIndex = 2,
            toIndex = 4,
        )

        assertEquals(2, plan?.fromIndex)
        assertEquals(4, plan?.toIndex)
        assertEquals(4, plan?.currentIndex)
    }

    @Test
    fun shiftsCurrentIndexBackwardWhenItemBeforeCurrentMovesAfterIt() {
        val plan = computeSessionReorderPlan(
            itemCount = 6,
            currentIndex = 3,
            fromIndex = 1,
            toIndex = 5,
        )

        assertEquals(2, plan?.currentIndex)
    }

    @Test
    fun shiftsCurrentIndexForwardWhenItemAfterCurrentMovesBeforeIt() {
        val plan = computeSessionReorderPlan(
            itemCount = 6,
            currentIndex = 2,
            fromIndex = 5,
            toIndex = 1,
        )

        assertEquals(3, plan?.currentIndex)
    }

    @Test
    fun returnsNullWhenMoveNormalizesToNoOp() {
        val plan = computeSessionReorderPlan(
            itemCount = 4,
            currentIndex = 1,
            fromIndex = -9,
            toIndex = -1,
        )

        assertNull(plan)
    }

    @Test
    fun clampsOutOfRangeIndexes() {
        val plan = computeSessionReorderPlan(
            itemCount = 4,
            currentIndex = 8,
            fromIndex = 9,
            toIndex = -2,
        )

        assertEquals(3, plan?.fromIndex)
        assertEquals(0, plan?.toIndex)
        assertEquals(0, plan?.currentIndex)
    }

    @Test
    fun clearUpcomingKeepsHistoryAndActiveItemOnly() {
        val keepCount = computeClearUpcomingKeepCount(
            itemCount = 6,
            currentIndex = 2,
        )

        assertEquals(3, keepCount)
    }

    @Test
    fun clearUpcomingClampsCurrentIndexBeforeKeepingActiveItem() {
        val keepCount = computeClearUpcomingKeepCount(
            itemCount = 4,
            currentIndex = 99,
        )

        assertEquals(4, keepCount)
    }
}
