package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cache eviction PR A — guards which batch actions evict cached article text.
 * "archive"/"bin" remove an item from active rotation and must drop its blob;
 * "unarchive"/"restore" must NOT evict so the next open re-caches fresh content.
 */
class CacheEvictingBatchActionsTest {

    @Test
    fun archiveAndBinEvict() {
        assertTrue("archive" in CACHE_EVICTING_BATCH_ACTIONS)
        assertTrue("bin" in CACHE_EVICTING_BATCH_ACTIONS)
    }

    @Test
    fun unarchiveAndRestoreDoNotEvict() {
        assertFalse("unarchive" in CACHE_EVICTING_BATCH_ACTIONS)
        assertFalse("restore" in CACHE_EVICTING_BATCH_ACTIONS)
    }

    @Test
    fun evictingSetIsExactlyArchiveAndBin() {
        assertTrue(CACHE_EVICTING_BATCH_ACTIONS == setOf("archive", "bin"))
    }
}
