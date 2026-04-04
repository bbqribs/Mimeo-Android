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
                playbackActive = true,
            ),
        )
    }

    @Test
    fun allowsSessionStartWhenTappedItemIsCurrentOwner() {
        assertTrue(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 42,
                sessionCurrentItemId = 42,
                playbackActive = false,
            ),
        )
    }

    @Test
    fun retargetsSessionOwnerWhenPlaybackIsPaused() {
        assertTrue(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 88,
                sessionCurrentItemId = 42,
                playbackActive = false,
            ),
        )
    }
}
