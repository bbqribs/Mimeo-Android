package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the engine-vs-session precedence for the current playback owner and the
 * archive/bin cleanup decisions derived from it. See [resolvePlaybackOwnerItemId],
 * [archivePlaybackCleanupDecision], and [binPlaybackCleanupDecision].
 *
 * Precedence matrix (engine wins when it owns an item, i.e. id > 0):
 *
 * | engine id | session id | owner   |
 * |-----------|------------|---------|
 * | 42        | 42         | 42      | engine agrees with session
 * | 42        | 7          | 42      | engine differs from session
 * | 42        | 99         | 42      | engine stays put while session moves
 * | 0         | 7          | 7       | engine absent -> session owns
 * | 0         | null       | null    | engine absent, no session
 * | -1        | 7          | 7       | non-positive engine id is "absent"
 */
class PlaybackOwnerResolutionTest {

    @Test
    fun engineOwnsWhenItAgreesWithSession() {
        assertEquals(42, resolvePlaybackOwnerItemId(engineCurrentItemId = 42, sessionCurrentItemId = 42))
    }

    @Test
    fun engineOwnsWhenItDiffersFromSession() {
        assertEquals(42, resolvePlaybackOwnerItemId(engineCurrentItemId = 42, sessionCurrentItemId = 7))
    }

    @Test
    fun engineKeepsOwnershipWhileSessionCurrentChanges() {
        // Engine item is unchanged; the session current moves underneath it.
        val owner1 = resolvePlaybackOwnerItemId(engineCurrentItemId = 42, sessionCurrentItemId = 7)
        val owner2 = resolvePlaybackOwnerItemId(engineCurrentItemId = 42, sessionCurrentItemId = 99)
        assertEquals(42, owner1)
        assertEquals(42, owner2)
    }

    @Test
    fun sessionOwnsWhenEngineIsAbsent() {
        assertEquals(7, resolvePlaybackOwnerItemId(engineCurrentItemId = 0, sessionCurrentItemId = 7))
    }

    @Test
    fun nonPositiveEngineIdIsTreatedAsAbsent() {
        assertEquals(7, resolvePlaybackOwnerItemId(engineCurrentItemId = -1, sessionCurrentItemId = 7))
    }

    @Test
    fun noOwnerWhenEngineAbsentAndNoSession() {
        assertNull(resolvePlaybackOwnerItemId(engineCurrentItemId = 0, sessionCurrentItemId = null))
    }

    // --- archive cleanup ---

    @Test
    fun archiveOfEngineOwnedCurrentItemClearsSessionWhenIdle() {
        val decision = archivePlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 42,
            isItemActivelyPlaying = false,
        )
        assertTrue(decision.affectsCurrentOwner)
        assertFalse(decision.deferCleanup)
        assertTrue(decision.clearSessionNow)
    }

    @Test
    fun archiveOfEngineOwnedCurrentItemDefersCleanupWhileActivelyPlaying() {
        val decision = archivePlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 42,
            isItemActivelyPlaying = true,
        )
        assertTrue(decision.affectsCurrentOwner)
        assertTrue(decision.deferCleanup)
        // Deferred: do not clear the session now so playback can finish.
        assertFalse(decision.clearSessionNow)
    }

    @Test
    fun archiveOfSessionCurrentItemWhenEngineAbsentClearsSession() {
        val decision = archivePlaybackCleanupDecision(
            engineCurrentItemId = 0,
            sessionCurrentItemId = 7,
            itemId = 7,
            isItemActivelyPlaying = false,
        )
        assertTrue(decision.affectsCurrentOwner)
        assertFalse(decision.deferCleanup)
        assertTrue(decision.clearSessionNow)
    }

    @Test
    fun archiveOfNonOwnerItemLeavesSessionAlone() {
        // Engine owns 42; archiving the session-current 7 must not touch the session,
        // because the engine takes precedence as the owner.
        val decision = archivePlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 7,
            isItemActivelyPlaying = false,
        )
        assertFalse(decision.affectsCurrentOwner)
        assertFalse(decision.deferCleanup)
        assertFalse(decision.clearSessionNow)
    }

    @Test
    fun archiveActivePlayingIsIgnoredForNonOwner() {
        // isItemActivelyPlaying only defers when the item is actually the owner.
        val decision = archivePlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 7,
            isItemActivelyPlaying = true,
        )
        assertFalse(decision.affectsCurrentOwner)
        assertFalse(decision.deferCleanup)
        assertFalse(decision.clearSessionNow)
    }

    // --- bin cleanup ---

    @Test
    fun binOfEngineOwnedCurrentItemClearsSessionImmediately() {
        val decision = binPlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 42,
        )
        assertTrue(decision.affectsCurrentOwner)
        // Bin never defers.
        assertFalse(decision.deferCleanup)
        assertTrue(decision.clearSessionNow)
    }

    @Test
    fun binOfSessionCurrentItemWhenEngineAbsentClearsSession() {
        val decision = binPlaybackCleanupDecision(
            engineCurrentItemId = 0,
            sessionCurrentItemId = 7,
            itemId = 7,
        )
        assertTrue(decision.affectsCurrentOwner)
        assertFalse(decision.deferCleanup)
        assertTrue(decision.clearSessionNow)
    }

    @Test
    fun binOfNonOwnerItemLeavesSessionAlone() {
        val decision = binPlaybackCleanupDecision(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 7,
            itemId = 7,
        )
        assertFalse(decision.affectsCurrentOwner)
        assertFalse(decision.clearSessionNow)
    }
}
