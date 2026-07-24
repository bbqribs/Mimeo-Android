package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            committedToPlayback = true,
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
            committedToPlayback = true,
            sessionCurrentItemId = 44,
            inSessionItems = false,
            inHistory = true,
        )

        assertEquals(LivePlaybackSessionSync.RestoreFromHistory, sync)
    }

    @Test
    fun loadedButUncommittedItemLeavesTheSessionAlone() {
        // Preview and plain-open both land here. Reclassifying the session on load is the
        // behaviour that had to be removed; only a commitment to play may re-point it.
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 41,
            committedToPlayback = false,
            sessionCurrentItemId = 44,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.None, sync)
    }

    @Test
    fun openWithPlayIntentCommitsBeforeAnySoundIsProduced() {
        // The pointer must move at openItem(autoPlay = true), not when speech begins.
        // Waiting until hasStartedPlaybackForCurrentItem flips re-points the session
        // mid-handoff, after autoPlayAfterLoad is consumed, which re-keys the Reader's load
        // effect outside its preserve-content window: blank + spinner, playback stopped.
        assertTrue(
            engineCommittedToPlayback(
                autoPlayAfterLoad = true,
                isSpeaking = false,
                isAutoPlaying = false,
                hasStartedPlaybackForCurrentItem = false,
            ),
        )
    }

    @Test
    fun commitmentHasNoGapAcrossTheLoadToPlayHandoff() {
        // maybeAutoPlayAfterLoad clears autoPlayAfterLoad immediately before speaking, so
        // the predicate must stay true on the isSpeaking/isAutoPlaying side of that switch —
        // otherwise the key flickers and the pointer moves twice.
        assertTrue(
            engineCommittedToPlayback(
                autoPlayAfterLoad = false,
                isSpeaking = true,
                isAutoPlaying = true,
                hasStartedPlaybackForCurrentItem = false,
            ),
        )
        // Paused after playing stays committed: the engine still owns this item.
        assertTrue(
            engineCommittedToPlayback(
                autoPlayAfterLoad = false,
                isSpeaking = false,
                isAutoPlaying = false,
                hasStartedPlaybackForCurrentItem = true,
            ),
        )
    }

    @Test
    fun plainOpenIsNotACommitment() {
        // Opening an item to read it (autoPlayAfterLoad = false) must leave Up Next alone.
        assertFalse(
            engineCommittedToPlayback(
                autoPlayAfterLoad = false,
                isSpeaking = false,
                isAutoPlaying = false,
                hasStartedPlaybackForCurrentItem = false,
            ),
        )
    }

    @Test
    fun playingTheAlreadyCurrentItemIsANoOp() {
        val sync = classifyLivePlaybackSessionSync(
            engineItemId = 44,
            committedToPlayback = true,
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
            committedToPlayback = true,
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
            committedToPlayback = true,
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
                committedToPlayback = true,
                sessionCurrentItemId = 44,
                inSessionItems = true,
                inHistory = false,
            ),
        )
        assertEquals(
            LivePlaybackSessionSync.None,
            classifyLivePlaybackSessionSync(
                engineItemId = -3,
                committedToPlayback = true,
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
            committedToPlayback = true,
            sessionCurrentItemId = null,
            inSessionItems = true,
            inHistory = false,
        )

        assertEquals(LivePlaybackSessionSync.MoveToSessionItem, sync)
    }
}
