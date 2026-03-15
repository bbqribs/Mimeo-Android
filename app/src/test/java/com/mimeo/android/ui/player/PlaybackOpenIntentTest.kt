package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOpenIntentTest {

    @Test
    fun manualOpenUsesPercentSeedWhenSavedPositionIsZero() {
        val seeded = resolveSeededPlaybackPosition(
            saved = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            knownProgress = 65,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            positionForPercent = { PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50), seeded)
    }

    @Test
    fun manualOpenPreservesExactSavedPosition() {
        val seeded = resolveSeededPlaybackPosition(
            saved = PlaybackPosition(chunkIndex = 3, offsetInChunkChars = 120),
            knownProgress = 65,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            positionForPercent = { PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 3, offsetInChunkChars = 120), seeded)
    }

    @Test
    fun autoContinueAlwaysStartsFromBeginning() {
        val seeded = resolveSeededPlaybackPosition(
            saved = PlaybackPosition(chunkIndex = 4, offsetInChunkChars = 300),
            knownProgress = 80,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.AutoContinue,
            positionForPercent = { PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }

    @Test
    fun replayAlwaysStartsFromBeginning() {
        val seeded = resolveSeededPlaybackPosition(
            saved = PlaybackPosition(chunkIndex = 4, offsetInChunkChars = 300),
            knownProgress = 100,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.Replay,
            positionForPercent = { PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }
}
