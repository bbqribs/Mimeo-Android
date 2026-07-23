package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.share.SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStorePendingManualSaveTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    private suspend fun enqueueUrl(url: String, message: String = "Saving..."): Long =
        store.enqueuePendingManualSave(
            source = PendingSaveSource.SHARE,
            type = PendingManualSaveType.URL,
            urlInput = url,
            titleInput = null,
            bodyInput = null,
            destinationPlaylistId = null,
            lastFailureMessage = message,
            autoRetryEligible = false,
            incrementRetryCount = false,
        )

    @Test
    fun enqueue_returnsRowId_andReusesItForMatchingRow() = runBlocking {
        val firstId = enqueueUrl("https://example.com/a")
        assertTrue(firstId > 0L)
        // Re-parking the same save updates the same row and returns the same id.
        val sameId = enqueueUrl("https://example.com/a", message = "Couldn't reach server")
        assertEquals(firstId, sameId)
        val rows = store.pendingManualSavesFlow.first()
        assertEquals(1, rows.size)
        assertEquals("Couldn't reach server", rows.single().lastFailureMessage)

        val secondId = enqueueUrl("https://example.com/b")
        assertEquals(2, store.pendingManualSavesFlow.first().size)
        assertTrue(secondId != firstId)
    }

    @Test
    fun updatePendingManualSaveStatus_preservesRow_forSavedWithoutItemId() = runBlocking {
        val id = enqueueUrl("https://example.com/dup")
        // Simulates the Saved(itemId = null) path: mark unresolved rather than delete.
        store.updatePendingManualSaveStatus(
            itemId = id,
            statusMessage = SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE,
            autoRetryEligible = false,
        )
        val rows = store.pendingManualSavesFlow.first()
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(SAVED_ITEM_NOT_YET_VISIBLE_MESSAGE, row.lastFailureMessage)
        assertNull(row.resolvedItemId)
    }

    @Test
    fun markResolved_setsResolvedItemId() = runBlocking {
        val id = enqueueUrl("https://example.com/c")
        store.markPendingManualSaveResolved(itemId = id, resolvedItemId = 77, statusMessage = "Processing...")
        val row = store.pendingManualSavesFlow.first().single()
        assertEquals(77, row.resolvedItemId)
        assertEquals("Processing...", row.lastFailureMessage)
    }

    @Test
    fun removePendingManualSave_dropsRow() = runBlocking {
        val id = enqueueUrl("https://example.com/d")
        assertNotNull(store.pendingManualSavesFlow.first().singleOrNull())
        store.removePendingManualSave(id)
        assertTrue(store.pendingManualSavesFlow.first().isEmpty())
    }
}
