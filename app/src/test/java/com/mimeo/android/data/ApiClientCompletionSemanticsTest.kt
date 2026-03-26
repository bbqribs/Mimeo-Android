package com.mimeo.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientCompletionSemanticsTest {
    @Test
    fun markDoneUsesWebDoneRoute() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.markItemDone(server.url("/").toString(), "token", 42, autoArchive = false)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/items/42/done?auto_archive=0", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun resetDoneUsesWebResetRoute() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.resetItemDone(server.url("/").toString(), "token", 42)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/items/42/reset", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun moveToBinUsesDeleteItemRoute() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.moveItemToBin(server.url("/").toString(), "token", 42)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/items/42", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun setFavoriteUsesPutFavoriteRoute() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.setFavoriteState(
                baseUrl = server.url("/").toString(),
                token = "token",
                itemId = 42,
                favorited = true,
            )

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertEquals("PUT", request.method)
            assertEquals("/items/42/favorite", request.path)
            assertTrue(body.contains("\"favorited\":true"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun createManualTextItemSendsSourceMetadataWhenProvided() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"url":"https://example.com","host":"example.com"}"""),
        )
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.createManualTextItem(
                baseUrl = server.url("/").toString(),
                token = "token",
                url = "https://example.com",
                text = "Quoted text",
                title = "Excerpt",
                source = ManualTextSourcePayload(
                    sourceType = "web",
                    sourceLabel = "example.com",
                    sourceUrl = "https://example.com",
                    captureKind = "shared_excerpt",
                    sourceAppPackage = "com.android.chrome",
                ),
            )

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertEquals("/items/manual-text", request.path)
            assertTrue(body.contains("\"source_type\":\"web\""))
            assertTrue(body.contains("\"source_label\":\"example.com\""))
            assertTrue(body.contains("\"source_url\":\"https://example.com\""))
            assertTrue(body.contains("\"capture_kind\":\"shared_excerpt\""))
            assertTrue(body.contains("\"source_app_package\":\"com.android.chrome\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun createManualTextItemRetriesWithoutSourceWhenRejected() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":"extra fields not permitted"}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"url":"https://example.com","host":"example.com"}"""),
        )
        server.start()
        try {
            val client = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())
            client.createManualTextItem(
                baseUrl = server.url("/").toString(),
                token = "token",
                url = "https://example.com",
                text = "Quoted text",
                title = "Excerpt",
                source = ManualTextSourcePayload(
                    sourceType = "web",
                    sourceLabel = "example.com",
                    sourceUrl = "https://example.com",
                    captureKind = "shared_excerpt",
                    sourceAppPackage = null,
                ),
            )

            val firstBody = server.takeRequest().body.readUtf8()
            val secondBody = server.takeRequest().body.readUtf8()
            assertTrue(firstBody.contains("\"source\""))
            assertTrue(!secondBody.contains("\"source\""))
        } finally {
            server.shutdown()
        }
    }
}
