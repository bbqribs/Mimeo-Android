package com.mimeo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePlaybackTimerTest {
    @Test
    fun pausedTimeIsExcludedAndActiveItemChangeResetsTheThresholdWindow() {
        var now = 0L
        val timer = ActivePlaybackTimer { now }

        timer.update(itemId = 1, isPlaying = true)
        now += 20_000L
        timer.update(itemId = 1, isPlaying = false)
        now += 90_000L
        assertEquals(20_000L, timer.elapsedFor(1))

        timer.update(itemId = 1, isPlaying = true)
        now += 10_000L
        assertEquals(30_000L, timer.elapsedFor(1))
        timer.resetForActiveItem(itemId = 2)
        assertEquals(0L, timer.elapsedFor(1))
        timer.update(itemId = 2, isPlaying = true)
        now += 5_000L
        assertEquals(5_000L, timer.elapsedFor(2))
    }
}
