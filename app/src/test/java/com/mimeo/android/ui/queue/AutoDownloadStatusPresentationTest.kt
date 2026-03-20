package com.mimeo.android.ui.queue

import com.mimeo.android.model.AutoDownloadDiagnostics
import com.mimeo.android.model.AutoDownloadWorkerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadStatusPresentationTest {

    @Test
    fun `status lines include core autodownload state and skip reasons`() {
        val lines = autoDownloadStatusLines(
            AutoDownloadDiagnostics(
                autoDownloadEnabled = true,
                queueItemCount = 50,
                offlineReadyCount = 12,
                knownNoActiveCount = 3,
                candidateCount = 50,
                queuedCount = 8,
                skippedCachedCount = 12,
                skippedNoActiveCount = 3,
                includeAllVisibleUncached = true,
                workerState = AutoDownloadWorkerState.RUNNING,
                attemptedCount = 8,
                successCount = 5,
                retryableFailureCount = 2,
                terminalFailureCount = 1,
            ),
        )

        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("Auto-download: On"))
        assertTrue(lines[0].contains("Offline-ready: 12/50"))
        assertTrue(lines[0].contains("Known unavailable: 3"))
        assertTrue(lines[1].contains("Worker: in progress"))
        assertTrue(lines[1].contains("queued 8/50"))
        assertTrue(lines[1].contains("refresh-all"))
        assertTrue(lines[2].contains("cached=12"))
        assertTrue(lines[2].contains("no-active=3"))
        assertTrue(lines[2].contains("attempted=8"))
    }

    @Test
    fun `worker state labels are user-facing and stable`() {
        assertEquals("queued", autoDownloadWorkerStateLabel(AutoDownloadWorkerState.QUEUED))
        assertEquals("retry pending", autoDownloadWorkerStateLabel(AutoDownloadWorkerState.RETRY_PENDING))
        assertEquals("skipped (no token)", autoDownloadWorkerStateLabel(AutoDownloadWorkerState.SKIPPED_NO_TOKEN))
    }
}
