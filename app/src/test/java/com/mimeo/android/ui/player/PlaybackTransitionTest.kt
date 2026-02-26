package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransitionTest {
    @Test
    fun nearEndCommitTriggersOnlyOnThresholdCrossing() {
        assertFalse(shouldForceNearEndCommit(previousPercent = 20, currentPercent = 40))
        assertTrue(shouldForceNearEndCommit(previousPercent = 97, currentPercent = 98))
        assertFalse(shouldForceNearEndCommit(previousPercent = 98, currentPercent = 99))
        assertFalse(shouldForceNearEndCommit(previousPercent = 100, currentPercent = 100))
    }

    @Test
    fun doneAdvancesChunkAndResetsOffset() {
        val result = applyDoneTransition(
            event = PlaybackDoneEvent(
                utteranceId = "utt-1",
                itemId = 101,
                chunkIndex = 2,
            ),
            currentItemId = 101,
            currentPosition = PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 57),
            chunkCount = 6,
            lastHandledUtteranceId = null,
        )

        assertTrue(result.shouldHandle)
        assertTrue(result.shouldPlayNextChunk)
        assertFalse(result.reachedEnd)
        assertEquals(3, result.nextPosition.chunkIndex)
        assertEquals(0, result.nextPosition.offsetInChunkChars)
        assertEquals("utt-1", result.handledUtteranceId)
    }

    @Test
    fun duplicateDoneIsIdempotent() {
        val first = applyDoneTransition(
            event = PlaybackDoneEvent(
                utteranceId = "utt-1",
                itemId = 101,
                chunkIndex = 1,
            ),
            currentItemId = 101,
            currentPosition = PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 12),
            chunkCount = 4,
            lastHandledUtteranceId = null,
        )
        assertTrue(first.shouldHandle)

        val duplicate = applyDoneTransition(
            event = PlaybackDoneEvent(
                utteranceId = "utt-1",
                itemId = 101,
                chunkIndex = 1,
            ),
            currentItemId = 101,
            currentPosition = PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 12),
            chunkCount = 4,
            lastHandledUtteranceId = first.handledUtteranceId,
        )
        assertFalse(duplicate.shouldHandle)
        assertFalse(duplicate.shouldPlayNextChunk)
        assertFalse(duplicate.reachedEnd)
        assertEquals(1, duplicate.nextPosition.chunkIndex)
        assertEquals(12, duplicate.nextPosition.offsetInChunkChars)
    }
}
