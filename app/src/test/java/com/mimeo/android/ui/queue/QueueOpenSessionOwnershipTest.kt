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
                playbackCurrentItemId = -1,
                playbackHasStartedCurrentItem = false,
            ),
        )
    }

    @Test
    fun preservesSessionOwnerWhenDifferentItemAlreadyOwnsPlayback() {
        assertFalse(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 99,
                playbackCurrentItemId = 42,
                playbackHasStartedCurrentItem = true,
            ),
        )
    }

    @Test
    fun allowsSessionStartWhenTappedItemIsCurrentOwner() {
        assertTrue(
            shouldStartNewSessionOnQueueOpen(
                tappedItemId = 42,
                playbackCurrentItemId = 42,
                playbackHasStartedCurrentItem = true,
            ),
        )
    }
}
