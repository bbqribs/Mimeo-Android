package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The single-owner rule for playback-to-session promotion. Exactly one mechanism may move
 * the Now Playing pointer per route; overlapping claims are what let the engine, the Reader
 * and the Up Next projection describe different items.
 */
class ReaderPlaySessionOwnerTest {

    @Test
    fun anUpcomingOrEarlierQueueItemIsOwnedByTheEngineCommitReconciler() {
        // Playing an item already in the session performs no session write of its own: the
        // open commits the engine, and the reconciler follows that commitment.
        assertEquals(
            ReaderPlaySessionOwner.EngineCommitReconciler,
            resolveReaderPlaySessionOwner(ReaderPromoteRoute.SessionItem, hasSession = true),
        )
    }

    @Test
    fun aHistoryItemIsOwnedByTheEngineCommitReconciler() {
        assertEquals(
            ReaderPlaySessionOwner.EngineCommitReconciler,
            resolveReaderPlaySessionOwner(ReaderPromoteRoute.HistoryItem, hasSession = true),
        )
    }

    @Test
    fun anItemOutsideTheSessionRequiresExplicitAdoption() {
        // The reconciler classifies external items as None on purpose, so nothing adopts
        // them unless an explicit Play Now does.
        assertEquals(
            ReaderPlaySessionOwner.ExplicitAdoption,
            resolveReaderPlaySessionOwner(ReaderPromoteRoute.ExternalItem, hasSession = true),
        )
    }

    @Test
    fun playingWithNoSessionSeedsNothing() {
        // There is nothing to promote into, and an explicit play must not turn itself into a
        // one-item Up Next. Seeding stays with startNowPlayingSession / auto-continue.
        assertEquals(
            ReaderPlaySessionOwner.NoSessionMutation,
            resolveReaderPlaySessionOwner(ReaderPromoteRoute.ExternalItem, hasSession = false),
        )
    }

    @Test
    fun anUnresolvedItemMutatesNothing() {
        assertEquals(
            ReaderPlaySessionOwner.NoSessionMutation,
            resolveReaderPlaySessionOwner(ReaderPromoteRoute.None, hasSession = true),
        )
    }

    @Test
    fun playingAnItemOutsideTheSessionIsNeverOwnedByBothMechanisms() {
        // Guards the invariant end to end: whatever the location classifier says, the
        // reconciler and the explicit adoption path never both claim the same item.
        val locations = listOf(
            Triple(41, true, false),
            Triple(42, false, true),
            Triple(99, false, false),
            Triple(0, false, false),
        )
        locations.forEach { (itemId, inSession, inHistory) ->
            val route = classifyReaderPromoteRoute(itemId, inSession, inHistory)
            val owner = resolveReaderPlaySessionOwner(route, hasSession = true)
            val reconcilerClaims = classifyLivePlaybackSessionSync(
                engineItemId = itemId,
                committedToPlayback = true,
                sessionCurrentItemId = 44,
                inSessionItems = inSession,
                inHistory = inHistory,
            ) != LivePlaybackSessionSync.None
            val adoptionClaims = owner == ReaderPlaySessionOwner.ExplicitAdoption
            assertEquals(
                "item $itemId must have exactly one session-pointer owner",
                1,
                listOf(reconcilerClaims, adoptionClaims).count { it } +
                    if (owner == ReaderPlaySessionOwner.NoSessionMutation) 1 else 0,
            )
        }
    }

    @Test
    fun failedExternalAdoptionIsReportedRatherThanClaimedAsSuccess() {
        // Playback still starts; the session is simply left describing what it did before.
        // The user must not be left believing the item joined Up Next.
        assertNotNull(readerExternalAdoptionMessage(adopted = false))
    }

    @Test
    fun successfulExternalAdoptionSaysNothingExtra() {
        assertNull(readerExternalAdoptionMessage(adopted = true))
    }
}
