package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineReducerTest {

    @Test
    fun `open item resets live playback flags and position`() {
        val previous = PlaybackEngineState(
            currentItemId = 120,
            openIntent = PlaybackOpenIntent.ManualOpen,
            reloadNonce = 7,
            currentPosition = PlaybackPosition(chunkIndex = 3, offsetInChunkChars = 44),
            isSpeaking = true,
            isAutoPlaying = true,
            autoPlayAfterLoad = false,
            hasStartedPlaybackForCurrentItem = true,
        )

        val reduced = reduceOpenItemState(
            previous = previous,
            itemId = 321,
            intent = PlaybackOpenIntent.AutoContinue,
            autoPlayAfterLoad = true,
        )

        assertEquals(321, reduced.currentItemId)
        assertEquals(PlaybackOpenIntent.AutoContinue, reduced.openIntent)
        assertEquals(7, reduced.reloadNonce)
        assertEquals(PlaybackPosition(), reduced.currentPosition)
        assertFalse(reduced.isSpeaking)
        assertFalse(reduced.isAutoPlaying)
        assertTrue(reduced.autoPlayAfterLoad)
        assertFalse(reduced.hasStartedPlaybackForCurrentItem)
    }

    @Test
    fun `open same item increments reload nonce`() {
        val previous = PlaybackEngineState(
            currentItemId = 120,
            openIntent = PlaybackOpenIntent.ManualOpen,
            reloadNonce = 3,
        )

        val reduced = reduceOpenItemState(
            previous = previous,
            itemId = 120,
            intent = PlaybackOpenIntent.Replay,
            autoPlayAfterLoad = false,
        )

        assertEquals(4, reduced.reloadNonce)
    }

    @Test
    fun `reload increments nonce and clears autoplay flag`() {
        val current = PlaybackEngineState(
            currentItemId = 908,
            openIntent = PlaybackOpenIntent.ManualOpen,
            reloadNonce = 10,
            currentPosition = PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 9),
            isSpeaking = true,
            isAutoPlaying = true,
            autoPlayAfterLoad = true,
            hasStartedPlaybackForCurrentItem = true,
        )

        val reduced = reduceReloadItemState(
            current = current,
            intent = PlaybackOpenIntent.ManualOpen,
        )

        assertEquals(11, reduced.reloadNonce)
        assertEquals(PlaybackPosition(), reduced.currentPosition)
        assertFalse(reduced.isSpeaking)
        assertFalse(reduced.isAutoPlaying)
        assertFalse(reduced.autoPlayAfterLoad)
        assertFalse(reduced.hasStartedPlaybackForCurrentItem)
    }
}

