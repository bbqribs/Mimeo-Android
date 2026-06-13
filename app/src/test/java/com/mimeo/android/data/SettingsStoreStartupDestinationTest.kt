package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.StartupDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreStartupDestinationTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    @Test
    fun appSettings_defaultStartupDestinationIsUpNext() {
        assertEquals(StartupDestination.UP_NEXT, AppSettings().startupDestination)
    }

    @Test
    fun emptyDataStore_readsUpNextDefault() = runBlocking {
        val settings = store.settingsFlow.first()
        assertEquals(StartupDestination.UP_NEXT, settings.startupDestination)
    }

    @Test
    fun saveStartupDestination_inbox_roundTrips() = runBlocking {
        store.saveStartupDestination(StartupDestination.INBOX)
        val settings = store.settingsFlow.first()
        assertEquals(StartupDestination.INBOX, settings.startupDestination)
    }

    @Test
    fun saveStartupDestination_smartQueue_roundTrips() = runBlocking {
        store.saveStartupDestination(StartupDestination.SMART_QUEUE)
        val settings = store.settingsFlow.first()
        assertEquals(StartupDestination.SMART_QUEUE, settings.startupDestination)
    }

    @Test
    fun invalidStoredStartupDestination_fallsBackToUpNext() {
        assertEquals(
            StartupDestination.UP_NEXT,
            store.parseStartupDestination("not-a-destination"),
        )
    }
}
