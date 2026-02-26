package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSelectionTest {
    @Test
    fun encodeSmartQueueAsSentinel() {
        assertEquals(SMART_QUEUE_PLAYLIST_SENTINEL, encodeSelectedPlaylistId(null))
        assertEquals(SMART_QUEUE_PLAYLIST_SENTINEL, encodeSelectedPlaylistId(0))
        assertEquals(SMART_QUEUE_PLAYLIST_SENTINEL, encodeSelectedPlaylistId(-7))
    }

    @Test
    fun decodeSentinelAsSmartQueue() {
        assertNull(decodeSelectedPlaylistId(null))
        assertNull(decodeSelectedPlaylistId(SMART_QUEUE_PLAYLIST_SENTINEL))
        assertNull(decodeSelectedPlaylistId(0))
    }

    @Test
    fun roundTripPlaylistId() {
        val playlistId = 42
        val encoded = encodeSelectedPlaylistId(playlistId)
        val decoded = decodeSelectedPlaylistId(encoded)
        assertEquals(playlistId, decoded)
    }
}
