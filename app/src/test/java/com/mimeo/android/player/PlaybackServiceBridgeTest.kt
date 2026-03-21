package com.mimeo.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackServiceBridgeTest {
    @Test
    fun clear_removes_registered_callbacks_and_snapshot_provider() {
        PlaybackServiceBridge.onPlay = {}
        PlaybackServiceBridge.onPause = {}
        PlaybackServiceBridge.onTogglePlayPause = {}
        PlaybackServiceBridge.snapshotProvider = { PlaybackServiceSnapshot(itemId = 1, title = "A", isPlaying = true) }

        PlaybackServiceBridge.clear()

        assertNull(PlaybackServiceBridge.onPlay)
        assertNull(PlaybackServiceBridge.onPause)
        assertNull(PlaybackServiceBridge.onTogglePlayPause)
        assertNull(PlaybackServiceBridge.snapshotProvider)
    }

    @Test
    fun snapshot_provider_returns_current_snapshot() {
        PlaybackServiceBridge.snapshotProvider = { PlaybackServiceSnapshot(itemId = 22, title = "Title", isPlaying = false) }

        val snapshot = PlaybackServiceBridge.snapshotProvider?.invoke()

        assertNotNull(snapshot)
        assertEquals(22, snapshot?.itemId)
        assertEquals("Title", snapshot?.title)
        assertEquals(false, snapshot?.isPlaying)
        PlaybackServiceBridge.clear()
    }
}

