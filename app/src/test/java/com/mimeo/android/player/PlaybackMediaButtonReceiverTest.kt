package com.mimeo.android.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMediaButtonReceiverTest {
    @Test
    fun mediaPlayPauseAndHeadsetKeysTogglePlaybackOnKeyDown() {
        val toggleAction = PlaybackService.ACTION_TOGGLE_PLAY_PAUSE

        assertEquals(
            toggleAction,
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
            ),
        )
        assertEquals(
            toggleAction,
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
            ),
        )
        assertEquals(
            toggleAction,
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            ),
        )
        assertEquals(
            toggleAction,
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
            ),
        )
    }

    @Test
    fun mediaButtonIgnoresKeyUpAndUnsupportedKeys() {
        assertNull(
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            ),
        )
        assertNull(
            playbackServiceActionForMediaButtonEvent(
                keyAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
            ),
        )
    }
}
