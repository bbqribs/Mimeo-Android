package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreQueueSnapshotExpiryTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
        SettingsStore.queueSnapshotMaxAgeMsOverride = null
    }

    @After
    fun tearDown() {
        SettingsStore.queueSnapshotMaxAgeMsOverride = null
    }

    private fun queue(vararg itemIds: Int): PlaybackQueueResponse = PlaybackQueueResponse(
        count = itemIds.size,
        items = itemIds.map { id ->
            PlaybackQueueItem(itemId = id, title = "Item $id", url = "https://example.com/$id")
        },
    )

    @Test
    fun freshSnapshot_isRenderedAsLive() = runBlocking {
        val now = 10_000_000L
        store.saveQueueSnapshot(selectedPlaylistId = null, queue = queue(1, 2), savedAtMs = now)
        val loaded = store.loadQueueSnapshot(selectedPlaylistId = null, nowMs = now + 60_000L)
        assertNotNull(loaded)
        assertEquals(listOf(1, 2), loaded!!.items.map { it.itemId })
    }

    @Test
    fun snapshotOlderThanThreshold_isNotRenderedAsLive() = runBlocking {
        val savedAt = 10_000_000L
        store.saveQueueSnapshot(selectedPlaylistId = null, queue = queue(1, 2, 3), savedAtMs = savedAt)
        // 25 h later — beyond the 24 h staleness threshold.
        val loaded = store.loadQueueSnapshot(
            selectedPlaylistId = null,
            nowMs = savedAt + SettingsStore.QUEUE_SNAPSHOT_MAX_AGE_MS + 60_000L,
        )
        assertNull("Stale snapshots must not present potentially-trashed rows as live", loaded)
    }

    @Test
    fun loweredDebugThreshold_marksRecentSnapshotStale() = runBlocking {
        // Mirrors the physical-acceptance debug-threshold mechanism.
        SettingsStore.queueSnapshotMaxAgeMsOverride = 1_000L
        val savedAt = 10_000_000L
        store.saveQueueSnapshot(selectedPlaylistId = null, queue = queue(9), savedAtMs = savedAt)
        assertNull(store.loadQueueSnapshot(selectedPlaylistId = null, nowMs = savedAt + 5_000L))
        assertNotNull(store.loadQueueSnapshot(selectedPlaylistId = null, nowMs = savedAt + 500L))
    }

    @Test
    fun isQueueSnapshotStale_pureBoundary() {
        val max = SettingsStore.QUEUE_SNAPSHOT_MAX_AGE_MS
        assertFalse(SettingsStore.isQueueSnapshotStale(savedAtMs = 1_000L, nowMs = 1_000L, maxAgeMs = max))
        assertFalse(SettingsStore.isQueueSnapshotStale(savedAtMs = 1_000L, nowMs = 1_000L + max - 1, maxAgeMs = max))
        assertTrue(SettingsStore.isQueueSnapshotStale(savedAtMs = 1_000L, nowMs = 1_000L + max, maxAgeMs = max))
        // A missing/zero timestamp is treated as stale.
        assertTrue(SettingsStore.isQueueSnapshotStale(savedAtMs = 0L, nowMs = 1_000L, maxAgeMs = max))
    }
}
