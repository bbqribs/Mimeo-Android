package com.mimeo.android.ui.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOpenSessionOwnershipTest {

    @Test
    fun startsSessionWhenNoPlaybackOwnerExists() {
        assertTrue(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 42,
                sessionCurrentItemId = -1,
                sessionCurrentProgressPercent = null,
                sessionCurrentChunkIndex = 0,
                sessionCurrentOffsetInChunkChars = 0,
                playbackHasStartedCurrentItem = false,
                playbackActive = false,
            ),
        )
    }

    @Test
    fun preservesSessionOwnerWhenDifferentItemAlreadyOwnsPlayback() {
        assertFalse(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 99,
                sessionCurrentItemId = 42,
                sessionCurrentProgressPercent = 12,
                sessionCurrentChunkIndex = 1,
                sessionCurrentOffsetInChunkChars = 5,
                playbackHasStartedCurrentItem = true,
                playbackActive = false,
            ),
        )
    }

    @Test
    fun allowsSessionStartWhenTappedItemIsCurrentOwner() {
        assertTrue(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 42,
                sessionCurrentItemId = 42,
                sessionCurrentProgressPercent = 12,
                sessionCurrentChunkIndex = 1,
                sessionCurrentOffsetInChunkChars = 5,
                playbackHasStartedCurrentItem = true,
                playbackActive = false,
            ),
        )
    }

    @Test
    fun preservesSessionOwnerWhenPausedContextIsRestoredFromSession() {
        assertFalse(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 88,
                sessionCurrentItemId = 42,
                sessionCurrentProgressPercent = 23,
                sessionCurrentChunkIndex = 0,
                sessionCurrentOffsetInChunkChars = 0,
                playbackHasStartedCurrentItem = false,
                playbackActive = false,
            ),
        )
    }
}
