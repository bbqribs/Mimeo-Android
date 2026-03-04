package com.mimeo.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
}
