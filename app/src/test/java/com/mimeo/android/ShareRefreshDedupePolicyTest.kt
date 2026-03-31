package com.mimeo.android

import com.mimeo.android.share.ShareRefreshEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRefreshDedupePolicyTest {
    private val sourceStatusChanged = "status_changed"
    private val sourceReadyFollowup = "ready_followup"
    private val sourceAutodownloadFinished = "autodownload_finished"

    @Test
    fun `key uses playlist and item when item is present`() {
        val key = resolveShareRefreshBurstKey(
            ShareRefreshEvent(
                playlistId = 12,
                itemId = 88,
                source = sourceStatusChanged,
            ),
        )

        assertEquals("playlist:12:item:88", key)
    }

    @Test
    fun `item-keyed events coalesce across source labels`() {
        val statusKey = resolveShareRefreshBurstKey(
            ShareRefreshEvent(
                playlistId = 5,
                itemId = 41,
                source = sourceStatusChanged,
            ),
        )
        val readyKey = resolveShareRefreshBurstKey(
            ShareRefreshEvent(
                playlistId = 5,
                itemId = 41,
                source = sourceReadyFollowup,
            ),
        )

        assertEquals(statusKey, readyKey)
    }

    @Test
    fun `source key includes source when item id is missing`() {
        val key = resolveShareRefreshBurstKey(
            ShareRefreshEvent(
                playlistId = null,
                itemId = null,
                source = sourceAutodownloadFinished,
            ),
        )

        assertEquals("playlist:smart:source:autodownload_finished", key)
    }

    @Test
    fun `skip when within dedupe window`() {
        assertTrue(
            shouldSkipShareRefreshBurst(
                lastExecutedAtMs = 1_000L,
                nowMs = 2_000L,
                dedupeWindowMs = 1_500L,
            ),
        )
    }

    @Test
    fun `do not skip when dedupe window has passed`() {
        assertFalse(
            shouldSkipShareRefreshBurst(
                lastExecutedAtMs = 1_000L,
                nowMs = 3_000L,
                dedupeWindowMs = 1_500L,
            ),
        )
    }
}
