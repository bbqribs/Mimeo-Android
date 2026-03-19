package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueItemPresentationCopyTest {

    @Test
    fun progressIconUsesErrorForUnavailable() {
        val iconRes = queueProgressIconRes(
            progress = 0,
            isDone = false,
            noActiveContent = true,
        )

        assertEquals(com.mimeo.android.R.drawable.msr_error_circle_24, iconRes)
    }

    @Test
    fun progressIconUsesCheckForDone() {
        val iconRes = queueProgressIconRes(
            progress = 100,
            isDone = true,
            noActiveContent = false,
        )

        assertEquals(com.mimeo.android.R.drawable.msr_check_circle_24, iconRes)
    }

    @Test
    fun progressIconUsesClosedBookForUnread() {
        val iconRes = queueProgressIconRes(
            progress = 0,
            isDone = false,
            noActiveContent = false,
        )

        assertEquals(com.mimeo.android.R.drawable.ic_book_closed_24, iconRes)
    }

    @Test
    fun progressIconUsesOpenBookForInProgress() {
        val iconRes = queueProgressIconRes(
            progress = 34,
            isDone = false,
            noActiveContent = false,
        )

        assertEquals(com.mimeo.android.R.drawable.ic_book_open_24, iconRes)
    }

    @Test
    fun progressIconDescriptionMatchesState() {
        assertEquals(
            "Not available offline",
            queueProgressIconDescription(progress = 0, isDone = false, noActiveContent = true),
        )
        assertEquals(
            "Done",
            queueProgressIconDescription(progress = 100, isDone = true, noActiveContent = false),
        )
        assertEquals(
            "Unread",
            queueProgressIconDescription(progress = 0, isDone = false, noActiveContent = false),
        )
        assertEquals(
            "In progress",
            queueProgressIconDescription(progress = 22, isDone = false, noActiveContent = false),
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

    @Test
    fun sourceMetadataLineIncludesCaptureOnlyWhenEnabled() {
        assertEquals(
            "example.com",
            queueSourceMetadataLine(
                source = "example.com",
                captureStrategyLabel = "Capture: Reader",
                showQueueCaptureMetadata = false,
            ),
        )
        assertEquals(
            "example.com  •  Capture: Reader",
            queueSourceMetadataLine(
                source = "example.com",
                captureStrategyLabel = "Capture: Reader",
                showQueueCaptureMetadata = true,
            ),
        )
    }
}
