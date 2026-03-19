package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueItemPresentationCopyTest {

    @Test
    fun progressStateUnavailableWhenNoActiveContentAndNotCached() {
        val label = queueProgressStateLabel(
            progress = 0,
            isDone = false,
            cached = false,
            noActiveContent = true,
        )

        assertEquals("Unavailable", label)
    }

    @Test
    fun progressStateDoneWhenCompleted() {
        val label = queueProgressStateLabel(
            progress = 100,
            isDone = true,
            cached = false,
            noActiveContent = false,
        )

        assertEquals("Done", label)
    }

    @Test
    fun progressStateUnreadAtZero() {
        val label = queueProgressStateLabel(
            progress = 0,
            isDone = false,
            cached = false,
            noActiveContent = false,
        )

        assertEquals("Unread", label)
    }

    @Test
    fun tapHintVariesByAvailability() {
        assertEquals(
            "Tap opens details. Offline text not available.",
            queueTapHintLabel(cached = false, noActiveContent = true),
        )
        assertEquals(
            "Tap opens reader/player at saved progress.",
            queueTapHintLabel(cached = true, noActiveContent = false),
        )
        assertEquals(
            "Tap opens reader/player. Use menu for offline download.",
            queueTapHintLabel(cached = false, noActiveContent = false),
        )
    }

    @Test
    fun captureStrategyHumanizedWhenPresent() {
        assertEquals("Capture: Full Content", queueCaptureStrategyLabel("full_content"))
        assertEquals("Capture: Reader", queueCaptureStrategyLabel("reader"))
    }

    @Test
    fun captureStrategyNullWhenMissing() {
        assertNull(queueCaptureStrategyLabel(null))
        assertNull(queueCaptureStrategyLabel("   "))
    }
}

