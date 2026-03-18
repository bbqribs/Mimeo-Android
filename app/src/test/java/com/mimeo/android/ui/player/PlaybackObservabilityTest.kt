package com.mimeo.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackObservabilityTest {

    @Test
    fun manualStartSourceUsesQueueProgressWhenKnownProgressPresent() {
        val source = resolveOpenStartSource(
            openIntent = PlaybackOpenIntent.ManualOpen,
            knownProgress = 42,
            hasChunks = true,
        )

        assertEquals("manual:queue_progress_percent", source)
    }

    @Test
    fun autoContinueStartSourceAlwaysUsesBeginning() {
        val source = resolveOpenStartSource(
            openIntent = PlaybackOpenIntent.AutoContinue,
            knownProgress = 88,
            hasChunks = true,
        )

        assertEquals("autocontinue:start_of_item", source)
    }

    @Test
    fun observabilityLinesExposeExpectedHandoffAndSeedFields() {
        val lines = playbackObservabilityLines(
            PlaybackObservabilityUiState(
                currentItemId = 123,
                requestedItemId = 456,
                openIntent = PlaybackOpenIntent.ManualOpen,
                startSource = "manual:queue_progress_percent",
                knownProgress = 34,
                seededChunk = 2,
                seededOffset = 80,
                handoffPending = true,
                handoffSettled = false,
                autoPath = false,
            ),
        )

        assertEquals(5, lines.size)
        assertTrue(lines[0].contains("current=123"))
        assertTrue(lines[0].contains("requested=456"))
        assertTrue(lines[1].contains("open_intent=ManualOpen"))
        assertTrue(lines[2].contains("start_source=manual:queue_progress_percent"))
        assertTrue(lines[3].contains("chunk=2"))
        assertTrue(lines[3].contains("offset=80"))
        assertTrue(lines[4].contains("pending=true"))
        assertTrue(lines[4].contains("settled=false"))
    }
}
