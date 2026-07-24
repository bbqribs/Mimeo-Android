package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.PlaybackQueueItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreLibraryViewSnapshotTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    private fun items(vararg ids: Int) = ids.map { id ->
        PlaybackQueueItem(itemId = id, title = "Item $id", url = "https://example.com/$id")
    }

    @Test
    fun snapshotRoundTripsPerView() = runBlocking {
        store.saveLibraryViewSnapshot("INBOX", items(1, 2), savedAtMs = 5_000L)
        store.saveLibraryViewSnapshot("FAVORITES", items(9), savedAtMs = 6_000L)

        val inbox = store.loadLibraryViewSnapshot("INBOX")
        assertNotNull(inbox)
        assertEquals(listOf(1, 2), inbox!!.items.map { it.itemId })
        assertEquals(5_000L, inbox.savedAtMs)

        val favorites = store.loadLibraryViewSnapshot("FAVORITES")
        assertEquals(listOf(9), favorites!!.items.map { it.itemId })
    }

    @Test
    fun unknownViewHasNoSnapshot() = runBlocking {
        assertNull(store.loadLibraryViewSnapshot("ARCHIVED"))
    }

    @Test
    fun resaveReplacesWholesale_soRemovedItemsCannotResurrect() = runBlocking {
        store.saveLibraryViewSnapshot("INBOX", items(1, 2, 3))
        store.saveLibraryViewSnapshot("INBOX", items(2))

        val inbox = store.loadLibraryViewSnapshot("INBOX")
        assertEquals("stale rows must not survive a fresh save", listOf(2), inbox!!.items.map { it.itemId })
    }

    @Test
    fun lastSuccessfulSyncRoundTrips() = runBlocking {
        assertNull(store.lastSuccessfulSyncAtMsFlow.first())
        store.saveLastSuccessfulSyncAt(1_234_567L)
        assertEquals(1_234_567L, store.lastSuccessfulSyncAtMsFlow.first())
    }

    @Test
    fun accountClearWipesSnapshotsAndSyncStamp() = runBlocking {
        store.saveLibraryViewSnapshot("INBOX", items(1))
        store.saveLastSuccessfulSyncAt(999L)

        store.clearAccountScopedDataStoreState(clearOwner = true)

        assertNull(store.loadLibraryViewSnapshot("INBOX"))
        assertNull(store.lastSuccessfulSyncAtMsFlow.first())
    }
}
