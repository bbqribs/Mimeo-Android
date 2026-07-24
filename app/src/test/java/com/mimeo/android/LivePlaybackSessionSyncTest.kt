package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePlaybackSessionSyncTest {

    @Test
    fun startingAnEarlierQueueItemMovesTheSessionPointer() {
        // The reported desync: playing at the top of Up Next, skipping forward three
        // articles, then reopening the original from "Earlier in queue" and long-pressing
        // Play. The engine speaks the original while the session still points at the item
        // it skipped to.
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 41,
            hasStartedPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.MoveToSessionItem, sync)
    }

    @Test
    fun startingAHistoryItemRestoresItFromHistory() {
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 42,
            hasStartedPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = false,
            inHistory = true,
        )

        assertEquals(LivePlaybackSessionSync.RestoreFromHistory, sync)
    }

    @Test
    fun loadedButNotYetPlayingItemLeavesTheSessionAlone() {
        // Preview and plain-open both land here. Reclassifying the session on load is the
        // behaviour that had to be removed; only an actual playback start may re-point it.
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 41,
            hasStartedPlayback = false,
            sessionCurrentItemId = 44,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.None, sync)
    }

    @Test
    fun playingTheAlreadyCurrentItemIsANoOp() {
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 44,
            hasStartedPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.None, sync)
    }

    @Test
    fun itemOutsideTheSessionIsNotAdopted() {
        // Playing something that is in neither the queue nor History must not inject it
        // into Up Next; that stays an explicit user action (Play now / Reader promote).
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 99,
            hasStartedPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = false,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.None, sync)
    }

    @Test
    fun sessionMembershipTakesPrecedenceOverHistory() {
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 7,
            hasStartedPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = true,
            inHistory = true,
        )

        assertEquals(LivePlaybackSessionSync.MoveToSessionItem, sync)
    }

    @Test
    fun emptyEngineDoesNotDisturbTheSession() {
        // A cleared engine (id 0) emits alongside a live session on startup and after
        // applyAuthoritativeUpNext clears playback.
        assertEquals(
            LivePlaybackSessionSync.None,
            classifyLivePlaybackSessionSync(
                engineItemId = 0,
                hasStartedPlayback = true,
                sessionCurrentItemId = 44,
                inSessionItems = true,
                inHistory = false,
            ),
        )
        assertEquals(
            LivePlaybackSessionSync.None,
            classifyLivePlaybackSessionSync(
                engineItemId = -3,
                hasStartedPlayback = true,
                sessionCurrentItemId = null,
                inSessionItems = false,
                inHistory = false,
            ),
        )
    }

    @Test
    fun noSessionCurrentStillAdoptsASessionMember() {
        // A session whose currentIndex never resolved (-1) still projects Up Next; a
        // playback start should install the pointer rather than leave it unset.
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 41,
            hasStartedPlayback = true,
            sessionCurrentItemId = null,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.MoveToSessionItem, sync)
    }
}
