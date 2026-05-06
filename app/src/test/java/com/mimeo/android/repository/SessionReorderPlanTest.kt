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

    @Test
    fun sessionSectionsKeepHistoryAndEarlierMutuallyExclusive() {
        val sections = computeNowPlayingSessionSections(
            items = listOf(1, 2, 3),
            currentIndex = 1,
            historyItems = listOf(9, 1),
            itemId = { it },
        )

        assertEquals(listOf(9), sections.history)
        assertEquals(listOf(1), sections.earlierInQueue)
        assertEquals(2, sections.active)
        assertEquals(listOf(3), sections.upNext)
    }

    @Test
    fun jumpToUpcomingMovesMeaningfulPriorActiveToHistory() {
        val plan = computeSessionIndexMovePlan(
            itemIds = listOf(10, 20, 30, 40, 50),
            currentIndex = 0,
            targetIndex = 3,
            historyItemIds = emptyList(),
            priorActiveToHistory = true,
        )

        val actual = plan!!
        assertEquals(listOf(20, 30, 40, 50), actual.itemIds)
        assertEquals(2, actual.currentIndex)
        assertEquals(listOf(10), actual.historyItemIds)
        assertEquals(listOf(40, 50), actual.itemIds.drop(actual.currentIndex))
    }

    @Test
    fun jumpToUpcomingKeepsBriefPriorActiveInEarlierQueue() {
        val plan = computeSessionIndexMovePlan(
            itemIds = listOf(10, 20, 30, 40, 50),
            currentIndex = 0,
            targetIndex = 3,
            historyItemIds = emptyList(),
            priorActiveToHistory = false,
        )

        assertEquals(listOf(10, 20, 30, 40, 50), plan?.itemIds)
        assertEquals(3, plan?.currentIndex)
        assertEquals(emptyList<Int>(), plan?.historyItemIds)
    }

    @Test
    fun previousWalksEarlierQueueBeforeHistory() {
        val plan = computePreviousSessionPlan(
            itemIds = listOf(20, 30, 40, 50),
            currentIndex = 2,
            historyItemIds = listOf(10),
        )

        assertEquals(listOf(20, 30, 40, 50), plan?.itemIds)
        assertEquals(1, plan?.currentIndex)
        assertEquals(listOf(10), plan?.historyItemIds)
    }

    @Test
    fun previousContinuesIntoHistoryMostRecentFirst() {
        val plan = computePreviousSessionPlan(
            itemIds = listOf(20, 30, 40),
            currentIndex = 0,
            historyItemIds = listOf(10, 5),
        )

        assertEquals(listOf(10, 20, 30, 40), plan?.itemIds)
        assertEquals(0, plan?.currentIndex)
        assertEquals(listOf(5), plan?.historyItemIds)
    }
}
