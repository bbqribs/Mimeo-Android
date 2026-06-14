package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPromoteRouteTest {

    @Test
    fun earlierQueueOrUpcomingItemRoutesToSessionItem() {
        val route = classifyReaderPromoteRoute(
            itemId = 41,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(ReaderPromoteRoute.SessionItem, route)
    }

    @Test
    fun historyItemRoutesToHistoryItem() {
        // Long-press Play on a history item: it lives in historyItems, not
        // session.items, so it must be pulled out of history -- not treated as a
        // queue item (which would no-op and diverge engine vs session pointer).
        val route = classifyReaderPromoteRoute(
            itemId = 42,
            inSessionItems = false,
            inHistory = true,
        )

        assertEquals(ReaderPromoteRoute.HistoryItem, route)
    }

    @Test
    fun itemNotInSessionRoutesToExternalItem() {
        val route = classifyReaderPromoteRoute(
            itemId = 99,
            inSessionItems = false,
            inHistory = false,
        )

        assertEquals(ReaderPromoteRoute.ExternalItem, route)
    }

    @Test
    fun sessionMembershipTakesPrecedenceOverHistory() {
        val route = classifyReaderPromoteRoute(
            itemId = 7,
            inSessionItems = true,
            inHistory = true,
        )

        assertEquals(ReaderPromoteRoute.SessionItem, route)
    }

    @Test
    fun unresolvedItemRoutesToNone() {
        assertEquals(
            ReaderPromoteRoute.None,
            classifyReaderPromoteRoute(itemId = 0, inSessionItems = true, inHistory = true),
        )
        assertEquals(
            ReaderPromoteRoute.None,
            classifyReaderPromoteRoute(itemId = -3, inSessionItems = false, inHistory = false),
        )
    }
}
