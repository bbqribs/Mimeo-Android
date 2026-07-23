package com.mimeo.android.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full-path instrumented test for [PendingSaveRetryWorker] using [MockWebServer], mirroring
 * [AutoDownloadWorkerIntegrationTest]. Exercises the real parked-save flush pipeline:
 * parked row → shared save coordinator → HTTP POST /items → outcome applied to the store.
 */
@RunWith(AndroidJUnit4::class)
class PendingSaveRetryWorkerIntegrationTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var store: SettingsStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        store.clearAllSettingsForTesting()
        server = MockWebServer()
        server.start()
        store.saveSignedInSession(
            baseUrl = server.url("/").toString().trimEnd('/'),
            connectionMode = ConnectionMode.LOCAL,
            apiToken = "test-token",
        )
    }

    @After
    fun tearDown() = runBlocking {
        server.shutdown()
        store.clearAllSettingsForTesting()
    }

    private suspend fun parkUrlSave(url: String) {
        store.enqueuePendingManualSave(
            source = PendingSaveSource.SHARE,
            type = PendingManualSaveType.URL,
            urlInput = url,
            titleInput = null,
            bodyInput = null,
            destinationPlaylistId = null,
            lastFailureMessage = "Couldn't reach server",
            autoRetryEligible = true,
            incrementRetryCount = false,
        )
    }

    private fun currentBaseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun buildWorker(parkedBaseUrl: String, runAttemptCount: Int = 0): PendingSaveRetryWorker =
        TestListenableWorkerBuilder<PendingSaveRetryWorker>(context)
            .setInputData(workDataOf(PendingSaveRetryWorker.KEY_PARKED_BASE_URL to parkedBaseUrl))
            .setRunAttemptCount(runAttemptCount)
            .build()

    /** Responds 201 to POST /items and a benign 200 to any background GET (status polls etc.). */
    private fun dispatch(createResponse: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path.startsWith("/items") && !path.contains("manual-text") ->
                        createResponse()
                    else -> MockResponse().setResponseCode(200).setBody("{}")
                }
            }
        }
    }

    @Test
    fun constraintSatisfied_executesRetry_andResolvesRow() = runBlocking {
        parkUrlSave("https://example.com/retry-me")
        dispatch {
            MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\": 4242, \"url\": \"https://example.com/retry-me\", \"title\": \"T\"}")
        }

        val result = buildWorker(parkedBaseUrl = currentBaseUrl()).doWork()

        assertEquals(Result.success(), result)
        assertEquals(4242, store.pendingManualSavesFlow.first().single().resolvedItemId)
    }

    @Test
    fun serverError_keepsRowAndSignalsRetry() = runBlocking {
        parkUrlSave("https://example.com/flaky")
        dispatch { MockResponse().setResponseCode(500).setBody("{\"detail\": \"boom\"}") }

        val result = buildWorker(parkedBaseUrl = currentBaseUrl(), runAttemptCount = 0).doWork()

        assertEquals("attempts remain → retry", Result.retry(), result)
        val row = store.pendingManualSavesFlow.first().single()
        assertNull(row.resolvedItemId)
        assertTrue(row.autoRetryEligible)
    }

    @Test
    fun serverError_atMaxAttempts_givesUpButRetainsRow() = runBlocking {
        parkUrlSave("https://example.com/flaky2")
        dispatch { MockResponse().setResponseCode(500).setBody("{\"detail\": \"boom\"}") }

        val result = buildWorker(
            parkedBaseUrl = currentBaseUrl(),
            runAttemptCount = PendingSaveRetryWorker.MAX_ATTEMPTS - 1,
        ).doWork()

        assertEquals("exhausted attempts → success (bounded, no infinite retry)", Result.success(), result)
        // Row is retained with its failure state rather than lost.
        assertEquals(1, store.pendingManualSavesFlow.first().size)
    }

    @Test
    fun changedEndpoint_noOps_withoutContactingServer() = runBlocking {
        parkUrlSave("https://example.com/other-account")
        dispatch { MockResponse().setResponseCode(201).setBody("{\"id\": 1, \"url\": \"x\"}") }

        val result = buildWorker(parkedBaseUrl = "https://old-endpoint.example").doWork()

        assertEquals(Result.success(), result)
        assertEquals(0, server.requestCount)
        // Untouched — no cross-account submission.
        assertNull(store.pendingManualSavesFlow.first().single().resolvedItemId)
    }
}
