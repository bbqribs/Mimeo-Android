package com.mimeo.android.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.ShareResultNotifications
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the shared retry-outcome application used identically by the individual and bulk
 * ViewModel retry paths and the background worker, against a real DataStore-backed store.
 */
@RunWith(RobolectricTestRunner::class)
class PendingSaveRetryOutcomeApplicationTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore
    private lateinit var notifications: ShareResultNotifications

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        notifications = ShareResultNotifications(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    private suspend fun park(url: String, autoRetryEligible: Boolean = true) {
        store.enqueuePendingManualSave(
            source = PendingSaveSource.SHARE,
            type = PendingManualSaveType.URL,
            urlInput = url,
            titleInput = null,
            bodyInput = null,
            destinationPlaylistId = null,
            lastFailureMessage = "Couldn't reach server",
            autoRetryEligible = autoRetryEligible,
            incrementRetryCount = false,
        )
    }

    @Test
    fun savedWithoutItemId_doesNotRemoveRow_inSharedRetryPath() = runBlocking {
        park("https://example.com/dup")
        val item = store.pendingManualSavesFlow.first().single()

        val stillRetryable = applyParkedRetryOutcome(
            settingsStore = store,
            notifications = notifications,
            item = item,
            result = ShareSaveResult.Saved(itemId = null, destinationName = "Smart Queue"),
        )

        assertFalse(stillRetryable)
        val rows = store.pendingManualSavesFlow.first()
        assertEquals("Row must be retained, not removed", 1, rows.size)
        assertEquals(SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE, rows.single().lastFailureMessage)
        assertNull(rows.single().resolvedItemId)
    }

    @Test
    fun savedWithItemId_resolvesRow() = runBlocking {
        park("https://example.com/ok")
        val item = store.pendingManualSavesFlow.first().single()

        val stillRetryable = applyParkedRetryOutcome(
            settingsStore = store,
            notifications = notifications,
            item = item,
            result = ShareSaveResult.Saved(itemId = 123, destinationName = "Smart Queue"),
        )

        assertFalse(stillRetryable)
        assertEquals(123, store.pendingManualSavesFlow.first().single().resolvedItemId)
    }

    @Test
    fun transientFailure_keepsRowRetryable() = runBlocking {
        park("https://example.com/net")
        val item = store.pendingManualSavesFlow.first().single()

        val stillRetryable = applyParkedRetryOutcome(
            settingsStore = store,
            notifications = notifications,
            item = item,
            result = ShareSaveResult.NetworkError,
        )

        assertTrue(stillRetryable)
        val row = store.pendingManualSavesFlow.first().single()
        assertTrue(row.autoRetryEligible)
    }

    @Test
    fun terminalNonSavableResult_removesRow() = runBlocking {
        park("not-a-url")
        val item = store.pendingManualSavesFlow.first().single()

        val stillRetryable = applyParkedRetryOutcome(
            settingsStore = store,
            notifications = notifications,
            item = item,
            result = ShareSaveResult.NoValidUrl,
        )

        assertFalse(stillRetryable)
        assertTrue(store.pendingManualSavesFlow.first().isEmpty())
    }
}
