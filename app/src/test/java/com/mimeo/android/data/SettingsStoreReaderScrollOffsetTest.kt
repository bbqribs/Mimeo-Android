package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.ConnectionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreReaderScrollOffsetTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking {
            store.clearAllSettingsForTesting()
            store.saveTokenOnly("")
        }
    }

    @Test
    fun readerOffsetsPersistPerItemAndClampNegativeValues() = runBlocking {
        store.saveReaderScrollOffset(itemId = 100, offset = 640)
        store.saveReaderScrollOffset(itemId = 101, offset = -20)

        assertEquals(
            mapOf(100 to 640, 101 to 0),
            store.readerScrollOffsetByItemFlow.first(),
        )

        store.saveReaderScrollOffset(itemId = 100, offset = 720)

        assertEquals(720, store.readerScrollOffsetByItemFlow.first()[100])
    }

    @Test
    fun readerOffsetRestoresThroughANewStoreInstance() = runBlocking {
        store.saveReaderScrollOffset(itemId = 100, offset = 640)

        val restoredStore = SettingsStore(context)

        assertEquals(640, restoredStore.readerScrollOffsetByItemFlow.first()[100])
    }

    @Test
    fun signOutClearsReaderOffsets() = runBlocking {
        store.saveReaderScrollOffset(itemId = 100, offset = 640)

        store.clearReaderScrollOffsets()

        assertEquals(emptyMap<Int, Int>(), store.readerScrollOffsetByItemFlow.first())
    }

    @Test
    fun successfulSignInCannotExposePriorAccountReaderOffsets() = runBlocking {
        store.saveReaderScrollOffset(itemId = 100, offset = 640)

        store.saveSignedInSession(
            baseUrl = "https://example.test",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "replacement-token",
        )

        assertEquals(emptyMap<Int, Int>(), store.readerScrollOffsetByItemFlow.first())
    }

    @Test
    fun serverScopedClearRemovesReaderOffsets() = runBlocking {
        store.saveReaderScrollOffset(itemId = 100, offset = 640)

        store.clearServerScopedDataStoreState()

        assertEquals(emptyMap<Int, Int>(), store.readerScrollOffsetByItemFlow.first())
    }
}
