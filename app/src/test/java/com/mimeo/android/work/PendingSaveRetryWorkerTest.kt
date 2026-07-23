package com.mimeo.android.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingSaveRetryWorkerTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    private fun buildWorker(parkedBaseUrl: String?): PendingSaveRetryWorker {
        val data = if (parkedBaseUrl == null) {
            workDataOf()
        } else {
            workDataOf(PendingSaveRetryWorker.KEY_PARKED_BASE_URL to parkedBaseUrl)
        }
        return TestListenableWorkerBuilder<PendingSaveRetryWorker>(context)
            .setInputData(data)
            .build()
    }

    @Test
    fun noCredentials_noOpsWithSuccess() = runBlocking {
        val result = buildWorker(parkedBaseUrl = "https://a.example").doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun accountOrEndpointChanged_noOps_withoutTouchingParkedRows() = runBlocking {
        store.setBaseUrlForTesting("https://current.example")
        store.saveTokenOnly("token-123")
        // A save parked under a different endpoint.
        store.enqueuePendingManualSave(
            source = PendingSaveSource.SHARE,
            type = PendingManualSaveType.URL,
            urlInput = "https://example.com/parked",
            titleInput = null,
            bodyInput = null,
            destinationPlaylistId = null,
            lastFailureMessage = "Couldn't reach server",
            autoRetryEligible = true,
            incrementRetryCount = false,
        )

        val result = buildWorker(parkedBaseUrl = "https://old-endpoint.example").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // The row is untouched — no retry attempted under the changed account/endpoint.
        val row = store.pendingManualSavesFlow.first().single()
        assertEquals("Couldn't reach server", row.lastFailureMessage)
        assertTrue(row.autoRetryEligible)
    }

    @Test
    fun noParkedRows_underMatchingEndpoint_succeedsWithoutReschedule() = runBlocking {
        store.setBaseUrlForTesting("https://current.example")
        store.saveTokenOnly("token-123")

        val result = buildWorker(parkedBaseUrl = "https://current.example").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun maxAttempts_isBounded() {
        assertEquals(5, PendingSaveRetryWorker.MAX_ATTEMPTS)
    }
}
