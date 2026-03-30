package com.mimeo.android

import com.mimeo.android.model.PendingItemAction
import com.mimeo.android.model.PendingItemActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingItemActionCoalescingTest {

    @Test
    fun coalescesRepeatedFavoriteTogglesToFinalState() {
        val pending = listOf(
            PendingItemAction(id = 1, itemId = 10, actionType = PendingItemActionType.SET_FAVORITE, favorited = true),
            PendingItemAction(id = 2, itemId = 10, actionType = PendingItemActionType.SET_FAVORITE, favorited = false),
            PendingItemAction(id = 3, itemId = 10, actionType = PendingItemActionType.SET_FAVORITE, favorited = true),
        )

        val coalesced = coalescePendingItemActions(pending)

        assertEquals(1, coalesced.size)
        assertEquals(10, coalesced.first().action.itemId)
        assertEquals(PendingItemActionType.SET_FAVORITE, coalesced.first().action.actionType)
        assertEquals(true, coalesced.first().action.favorited)
        assertEquals(listOf(1L, 2L, 3L), coalesced.first().sourceIds)
    }

    @Test
    fun coalescesArchiveFamilyToLastActionPerItem() {
        val pending = listOf(
            PendingItemAction(id = 1, itemId = 20, actionType = PendingItemActionType.ARCHIVE),
            PendingItemAction(id = 2, itemId = 20, actionType = PendingItemActionType.UNARCHIVE),
            PendingItemAction(id = 3, itemId = 21, actionType = PendingItemActionType.ARCHIVE),
            PendingItemAction(id = 4, itemId = 20, actionType = PendingItemActionType.ARCHIVE),
        )

        val coalesced = coalescePendingItemActions(pending)

        assertEquals(2, coalesced.size)
        assertEquals(21, coalesced[0].action.itemId)
        assertEquals(PendingItemActionType.ARCHIVE, coalesced[0].action.actionType)
        assertEquals(20, coalesced[1].action.itemId)
        assertEquals(PendingItemActionType.ARCHIVE, coalesced[1].action.actionType)
        assertEquals(listOf(1L, 2L, 4L), coalesced[1].sourceIds)
    }
}
