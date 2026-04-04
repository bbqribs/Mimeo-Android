package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRowStateMarkersTest {

    @Test
    fun playingMarker_usesPlaybackOwnerWhenActivelyPlaying() {
        val markers = resolveQueueRowStateMarkers(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 42,
            isSpeaking = true,
            isAutoPlaying = false,
            autoPlayAfterLoad = false,
        )

        assertEquals(42, markers.playingItemId)
        assertNull(markers.readyResumeItemId)
    }

    @Test
    fun readyMarker_showsPlaybackOwnerWhenPaused() {
        val markers = resolveQueueRowStateMarkers(
            engineCurrentItemId = 42,
            sessionCurrentItemId = 42,
            isSpeaking = false,
            isAutoPlaying = false,
            autoPlayAfterLoad = false,
        )

        assertNull(markers.playingItemId)
        assertEquals(42, markers.readyResumeItemId)
    }

    @Test
    fun pausedState_fallsBackToSessionOwnerWhenEngineOwnerMissing() {
        val markers = resolveQueueRowStateMarkers(
            engineCurrentItemId = -1,
            sessionCurrentItemId = 77,
            isSpeaking = false,
            isAutoPlaying = false,
            autoPlayAfterLoad = false,
        )

        assertNull(markers.playingItemId)
        assertEquals(77, markers.readyResumeItemId)
    }

    @Test
    fun activePlayback_prefersEngineOwnerOverStaleSessionOwner() {
        val markers = resolveQueueRowStateMarkers(
            engineCurrentItemId = 55,
            sessionCurrentItemId = 44,
            isSpeaking = true,
            isAutoPlaying = false,
            autoPlayAfterLoad = false,
        )

        assertEquals(55, markers.playingItemId)
        assertNull(markers.readyResumeItemId)
    }
}

