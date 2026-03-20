package com.mimeo.android.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.ConnectionMode
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full-path instrumented test for [AutoDownloadWorker] using [MockWebServer].
 *
 * Verifies the complete download pipeline:
 *   credentials present → worker runs → HTTP GET /items/{id}/text →
 *   response parsed → cache row written → Result.success()
 *
 * Unlike [AutoDownloadWorkerTest] (which tests structural paths with no credentials),
 * this class actually exercises the network fetch and DB write.
 *
 * Credential lifecycle:
 *   @Before  — writes mock-server URL + a test token into SettingsStore
 *   @After   — clears credentials (writes blanks) so other tests see no-credentials state
 */
@RunWith(AndroidJUnit4::class)
class AutoDownloadWorkerIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getInstance(context)
        server = MockWebServer()
        server.start()

        // Point the app at our mock server so the worker fetches from it.
        SettingsStore(context).saveSignedInSession(
            baseUrl = server.url("/").toString().trimEnd('/'),
            connectionMode = ConnectionMode.LOCAL,
            apiToken = "test-token",
        )
    }

    @After
    fun tearDown() = runBlocking {
        server.shutdown()
        // Blank credentials so subsequent tests see the "no credentials" state.
        SettingsStore(context).saveSignedInSession(
            baseUrl = "",
            connectionMode = ConnectionMode.LOCAL,
            apiToken = "",
        )
        // Remove any cache rows written during this test class.
        database.cachedItemDao().deleteByItemId(ITEM_ID)
    }

    // -----------------------------------------------------------------------------------------
    // Happy path: worker fetches item, writes cache row, returns success
    // -----------------------------------------------------------------------------------------

    @Test
    fun workerFetchesItemAndWritesCacheRow() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(itemTextResponseJson(ITEM_ID)),
        )

        val worker = TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setInputData(workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to intArrayOf(ITEM_ID)))
            .build()

        val result = worker.startWork().get()

        assertEquals("worker should return success after downloading", Result.success(), result)

        val cached = database.cachedItemDao().findByItemId(ITEM_ID)
        assertNotNull("item $ITEM_ID should be present in cached_items after worker completes", cached)
        assertEquals(ITEM_ID, cached?.itemId)
    }

    // -----------------------------------------------------------------------------------------
    // Server returns 404: worker treats it as terminal success (no retry)
    // -----------------------------------------------------------------------------------------

    @Test
    fun workerReturnsSuccessOn404() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"detail\": \"Not found\"}"),
        )

        val worker = TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setInputData(workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to intArrayOf(ITEM_ID)))
            .build()

        val result = worker.startWork().get()

        assertEquals("404 should be treated as terminal success (no retry)", Result.success(), result)
    }

    // -----------------------------------------------------------------------------------------
    // Server returns 409 No active content: worker succeeds and persists no-content ID
    // -----------------------------------------------------------------------------------------

    @Test
    fun workerReturnsSuccessOnNoActiveContent() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("{\"detail\": \"No active content version\"}"),
        )

        val worker = TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setInputData(workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to intArrayOf(ITEM_ID)))
            .build()

        val result = worker.startWork().get()

        // 409-no-active-content is non-retryable; worker should succeed (not retry).
        assertEquals("409 no-active-content should be terminal success", Result.success(), result)
    }

    // -----------------------------------------------------------------------------------------
    // Server returns 500: worker should signal retry (if attempts remain)
    // -----------------------------------------------------------------------------------------

    @Test
    fun workerSignalsRetryOn500() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"detail\": \"Internal server error\"}"),
        )

        // runAttemptCount defaults to 0 in TestListenableWorkerBuilder, so attempts remain.
        val worker = TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setInputData(workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to intArrayOf(ITEM_ID)))
            .build()

        val result = worker.startWork().get()

        assertEquals("500 within attempt limit should signal retry", Result.retry(), result)
    }

    @Test
    fun workerReturnsSuccessOn500WhenAttemptsExhausted() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"detail\": \"Internal server error\"}"),
        )

        // Simulate the worker being on its final attempt (runAttemptCount = MAX_ATTEMPTS - 1).
        val worker = TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setInputData(workDataOf(AutoDownloadWorker.KEY_ITEM_IDS to intArrayOf(ITEM_ID)))
            .setRunAttemptCount(AutoDownloadWorker.MAX_ATTEMPTS - 1)
            .build()

        val result = worker.startWork().get()

        assertEquals("500 at max attempts should give up and return success", Result.success(), result)
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private fun itemTextResponseJson(itemId: Int): String = """
        {
            "item_id": $itemId,
            "title": "Integration Test Article",
            "url": "https://example.com/article-$itemId",
            "host": "example.com",
            "status": "processed",
            "active_content_version_id": $itemId,
            "word_count": 6,
            "text": "Hello world this is a test.",
            "paragraphs": ["Hello world this is a test."]
        }
    """.trimIndent()

    companion object {
        private const val ITEM_ID = 999
    }
}
