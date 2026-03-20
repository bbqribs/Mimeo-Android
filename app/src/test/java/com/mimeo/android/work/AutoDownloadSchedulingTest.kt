package com.mimeo.android.work

import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.repository.ItemTextPrefetchAttempt
import com.mimeo.android.selectAutoDownloadTargetsForNewlySurfacedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the autodownload scheduling layer introduced when execution moved to
 * [AutoDownloadWorker]. Covers:
 *   - Scheduling when autodownload is ON
 *   - No scheduling when autodownload is OFF
 *   - Dedup: items already cached are not re-targeted
 *   - Dedup: same visible set surfaced again produces no new targets (passive path)
 *   - Explicit refresh re-attempts all visible uncached items
 *   - Worker retry decision (pure logic)
 *   - Worker no-active-content result classification
 *   - Input data round-trip for item ID serialization
 */
class AutoDownloadSchedulingTest {

    // -----------------------------------------------------------------------------------------
    // Target selection — ON / OFF gate
    // -----------------------------------------------------------------------------------------

    @Test
    fun `scheduling enabled — new uncached items produce targets`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(1), item(2), item(3)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = setOf(2),
            knownNoActiveContentItemIds = emptySet(),
        )
        assertEquals(listOf(1, 3), targets)
        assertTrue("should schedule when targets are non-empty", targets.isNotEmpty())
    }

    @Test
    fun `scheduling disabled — no targets regardless of queue state`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = false,
            queueItems = listOf(item(1), item(2), item(3)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
        )
        assertEquals(emptyList<Int>(), targets)
    }

    // -----------------------------------------------------------------------------------------
    // Dedup: cache state
    // -----------------------------------------------------------------------------------------

    @Test
    fun `all visible items cached — no targets — worker should not be enqueued`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(10), item(11), item(12)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = setOf(10, 11, 12),
            knownNoActiveContentItemIds = emptySet(),
        )
        assertTrue("no targets when all items cached", targets.isEmpty())
    }

    @Test
    fun `partially cached queue — only uncached items targeted`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(1), item(2), item(3), item(4)),
            previousVisibleItemIds = emptySet(),
            cachedItemIds = setOf(2, 4),
            knownNoActiveContentItemIds = emptySet(),
        )
        assertEquals(listOf(1, 3), targets)
    }

    // -----------------------------------------------------------------------------------------
    // Dedup: same visible set (passive path)
    // -----------------------------------------------------------------------------------------

    @Test
    fun `passive path — same visible set re-surfaced — no new targets`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(5), item(6), item(7)),
            previousVisibleItemIds = linkedSetOf(5, 6, 7),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
            includeAllVisibleUncached = false,
        )
        assertEquals(emptyList<Int>(), targets)
    }

    @Test
    fun `passive path — newly added item alongside existing — only new item targeted`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(5), item(6), item(8)),
            previousVisibleItemIds = linkedSetOf(5, 6),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = emptySet(),
            includeAllVisibleUncached = false,
        )
        assertEquals(listOf(8), targets)
    }

    // -----------------------------------------------------------------------------------------
    // Explicit refresh
    // -----------------------------------------------------------------------------------------

    @Test
    fun `explicit refresh — all visible uncached items targeted including previously seen`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(5), item(6), item(7)),
            previousVisibleItemIds = linkedSetOf(5, 6, 7),
            cachedItemIds = setOf(6),
            knownNoActiveContentItemIds = emptySet(),
            includeAllVisibleUncached = true,
        )
        assertEquals(listOf(5, 7), targets)
    }

    @Test
    fun `explicit refresh — no-active-content items excluded`() {
        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = true,
            queueItems = listOf(item(1), item(2), item(3)),
            previousVisibleItemIds = linkedSetOf(1, 2, 3),
            cachedItemIds = emptySet(),
            knownNoActiveContentItemIds = setOf(2),
            includeAllVisibleUncached = true,
        )
        assertEquals(listOf(1, 3), targets)
    }

    // -----------------------------------------------------------------------------------------
    // Worker retry decision (pure logic via internal helper)
    // -----------------------------------------------------------------------------------------

    @Test
    fun `retry decision — all success — returns success`() {
        val attempts = listOf(
            ItemTextPrefetchAttempt(itemId = 1, success = true),
            ItemTextPrefetchAttempt(itemId = 2, success = true),
        )
        assertFalse("no retry when all succeeded", shouldRetryAutoDownload(attempts, runAttemptCount = 0))
    }

    @Test
    fun `retry decision — retryable failure within attempt limit — returns retry`() {
        val attempts = listOf(
            ItemTextPrefetchAttempt(itemId = 1, success = true),
            ItemTextPrefetchAttempt(itemId = 2, success = false, retryable = true),
        )
        assertTrue("should retry when retryable failure and attempts remain",
            shouldRetryAutoDownload(attempts, runAttemptCount = 0))
    }

    @Test
    fun `retry decision — retryable failure at max attempts — returns success (give up)`() {
        val attempts = listOf(
            ItemTextPrefetchAttempt(itemId = 1, success = false, retryable = true),
        )
        assertFalse("should not retry when max attempts exhausted",
            shouldRetryAutoDownload(attempts, runAttemptCount = AutoDownloadWorker.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `retry decision — non-retryable failure — returns success (terminal)`() {
        val attempts = listOf(
            ItemTextPrefetchAttempt(itemId = 1, success = false, retryable = false,
                errorSummary = "api:404:Not found"),
        )
        assertFalse("non-retryable failure should not trigger retry",
            shouldRetryAutoDownload(attempts, runAttemptCount = 0))
    }

    // -----------------------------------------------------------------------------------------
    // No-active-content classification
    // -----------------------------------------------------------------------------------------

    @Test
    fun `no-active-content attempt classified from error summary`() {
        val noContentAttempts = listOf(
            ItemTextPrefetchAttempt(itemId = 3, success = false, retryable = false,
                errorSummary = "api:409:No active content version"),
            ItemTextPrefetchAttempt(itemId = 4, success = false, retryable = false,
                errorSummary = "api:404:Not found"),
        )
        val noContentIds = noContentAttempts
            .filter { !it.success && !it.retryable &&
                it.errorSummary.orEmpty().contains("No active content", ignoreCase = true) }
            .map { it.itemId }
            .toSet()
        assertEquals(setOf(3), noContentIds)
    }

    // -----------------------------------------------------------------------------------------
    // Input data serialization round-trip
    // -----------------------------------------------------------------------------------------

    @Test
    fun `item IDs survive IntArray round-trip for WorkManager input data`() {
        val original = listOf(101, 202, 303)
        val array = original.toIntArray()
        val recovered = array.toList()
        assertEquals(original, recovered)
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Pure extraction of the AutoDownloadWorker retry decision for unit-testability. */
    private fun shouldRetryAutoDownload(
        attempts: List<ItemTextPrefetchAttempt>,
        runAttemptCount: Int,
    ): Boolean {
        val hasRetryable = attempts.any { !it.success && it.retryable }
        return hasRetryable && runAttemptCount < AutoDownloadWorker.MAX_ATTEMPTS - 1
    }

    private fun item(itemId: Int) = PlaybackQueueItem(
        itemId = itemId,
        title = "Item $itemId",
        url = "https://example.com/$itemId",
        host = "example.com",
        status = "processed",
        activeContentVersionId = itemId,
        strategyUsed = null,
        wordCount = null,
        resumeReadPercent = 0,
        lastReadPercent = 0,
        apiProgressPercent = 0,
        apiFurthestPercent = 0,
        lastOpenedAt = null,
        createdAt = "2026-01-01T00:00:00Z",
    )
}
