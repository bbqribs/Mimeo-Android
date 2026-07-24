package com.mimeo.android.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Now Playing pointer moving *backwards* — replaying an item that has drifted into
 * "Earlier in queue" — is the shape T-AND-NOWPLAYING-POINTER-DESYNC-1 depends on. The
 * forward cases are covered by the auto-advance paths; these pin the backward ones.
 */
class SessionIndexMovePlanBackwardTest {

    private val queue = listOf(41, 42, 43, 44)

    @Test
    fun replayingAnEarlierItemMovesThePointerBackAndKeepsTheOrder() {
        // Played 41, skipped to 44; now start 41 again. 42/43/44 stay where they are.
        val plan = computeSessionIndexMovePlan(
            itemIds = queue,
            currentIndex = 3,
            targetIndex = 0,
            historyItemIds = emptyList(),
            priorActiveToHistory = false,
        )

        assertEquals(queue, plan?.itemIds)
        assertEquals(0, plan?.currentIndex)
        assertEquals(emptyList<Int>(), plan?.historyItemIds)
    }

    @Test
    fun displacedActiveItemGoesToHistoryWhenItWasMeaningfullyPlayed() {
        val plan = computeSessionIndexMovePlan(
            itemIds = queue,
            currentIndex = 3,
            targetIndex = 0,
            historyItemIds = listOf(40),
            priorActiveToHistory = true,
        )

        // 44 leaves the queue for History; the backward target keeps its index because
        // removing an item *after* the target cannot shift it.
        assertEquals(listOf(41, 42, 43), plan?.itemIds)
        assertEquals(0, plan?.currentIndex)
        assertEquals(listOf(44, 40), plan?.historyItemIds)
    }

    @Test
    fun forwardMoveToHistoryStillShiftsTheTargetDown() {
        // Guards the index adjustment the backward case must not apply.
        val plan = computeSessionIndexMovePlan(
            itemIds = queue,
            currentIndex = 0,
            targetIndex = 3,
            historyItemIds = emptyList(),
            priorActiveToHistory = true,
        )

        assertEquals(listOf(42, 43, 44), plan?.itemIds)
        assertEquals(2, plan?.currentIndex)
        assertEquals(listOf(41), plan?.historyItemIds)
    }

    @Test
    fun replayingTheAlreadyActiveItemLeavesTheSessionUntouched() {
        val plan = computeSessionIndexMovePlan(
            itemIds = queue,
            currentIndex = 3,
            targetIndex = 3,
            historyItemIds = listOf(40),
            priorActiveToHistory = true,
        )

        assertEquals(queue, plan?.itemIds)
        assertEquals(3, plan?.currentIndex)
        assertEquals(listOf(40), plan?.historyItemIds)
    }

    @Test
    fun unresolvedPointerAdoptsTheTargetWithoutTouchingHistory() {
        val plan = computeSessionIndexMovePlan(
            itemIds = queue,
            currentIndex = -1,
            targetIndex = 0,
            historyItemIds = listOf(40),
            priorActiveToHistory = true,
        )

        assertEquals(queue, plan?.itemIds)
        assertEquals(0, plan?.currentIndex)
        assertEquals(listOf(40), plan?.historyItemIds)
    }
}
