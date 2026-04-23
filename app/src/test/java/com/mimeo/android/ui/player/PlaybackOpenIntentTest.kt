package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOpenIntentTest {

    @Test
    fun manualOpenUsesPercentSeedWhenSavedPositionIsZero() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 65,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            positionForPercent = { PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50), seeded)
    }

    @Test
    fun manualOpenUsesQueueProgressWhenCachedPositionWouldDisagree() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 25,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            positionForPercent = { percent ->
                if (percent == 25) {
                    PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 10)
                } else {
                    PlaybackPosition(chunkIndex = 9, offsetInChunkChars = 999)
                }
            },
        )

        assertEquals(PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 10), seeded)
    }

    @Test
    fun manualOpenFallsBackToBeginningWhenQueueProgressIsUnknown() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 0,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            positionForPercent = { PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }

    @Test
    fun autoContinueAlwaysStartsFromBeginning() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 80,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.AutoContinue,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40),
            positionForPercent = { PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }

    @Test
    fun replayAlwaysStartsFromBeginning() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 100,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.Replay,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40),
            positionForPercent = { PlaybackPosition(chunkIndex = 7, offsetInChunkChars = 40) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }

    @Test
    fun manualOpenPrefersSavedPlaybackPointerOverQueuePercent() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 25,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = 8, offsetInChunkChars = 12),
            positionForPercent = { PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 10) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 8, offsetInChunkChars = 12), seeded)
    }

    @Test
    fun manualOpenNormalizesNegativeSavedPlaybackPointer() {
        val seeded = resolveSeededPlaybackPosition(
            knownProgress = 0,
            hasChunks = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            savedPlaybackPosition = PlaybackPosition(chunkIndex = -5, offsetInChunkChars = -10),
            positionForPercent = { PlaybackPosition(chunkIndex = 6, offsetInChunkChars = 50) },
        )

        assertEquals(PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0), seeded)
    }
}
