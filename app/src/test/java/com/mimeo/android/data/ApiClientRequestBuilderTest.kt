package com.mimeo.android.data

import com.mimeo.android.model.BlueskyScannerPreferencesPatch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T-AND-APICLIENT-REQUEST-BUILDER-1 — request-shape contract for the shared
 * authenticated request helpers.
 *
 * These lock the things the consolidation could silently change: bearer header,
 * per-endpoint `Accept` opt-in (some endpoints send it, some never have), verb,
 * path and query encoding, JSON vs zero-length bodies, and the unchanged
 * error/empty-response semantics. They deliberately assert on observed HTTP,
 * not on which private helper produced it.
 */
class ApiClientRequestBuilderTest {

    private fun client() = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())

    private inline fun <T> withServer(vararg responses: MockResponse, block: (MockWebServer) -> T): T {
        val server = MockWebServer()
        responses.forEach { server.enqueue(it) }
        server.start()
        return try {
            block(server)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun authenticatedGetSendsBearerAndNoAcceptHeaderOnEndpointsThatNeverSentOne() = runBlocking {
        withServer(MockResponse().setResponseCode(200).setBody("[]")) { server ->
            val items = client().getTrashedItems(server.url("/").toString(), "device-token", limit = 25)
            assertEquals(emptyList<Any>(), items)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/items?trashed=true&limit=25", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertNull(request.getHeader("Accept"))
        }
    }

    @Test
    fun smartPlaylistGetStillSendsAcceptJson() = runBlocking {
        withServer(MockResponse().setResponseCode(200).setBody("[]")) { server ->
            val playlists = client().getSmartPlaylists(server.url("/").toString(), "device-token")
            assertEquals(emptyList<Any>(), playlists)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/smart-playlists", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertEquals("application/json", request.getHeader("Accept"))
        }
    }

    @Test
    fun postJsonSendsJsonMediaTypeAndDecodesResponse() = runBlocking {
        withServer(
            MockResponse().setResponseCode(200).setBody("""{"id":3,"name":"Reading","kind":"manual","entries":[]}"""),
        ) { server ->
            val created = client().createPlaylist(server.url("/").toString(), "device-token", "Reading")
            assertEquals(3, created.id)
            assertEquals("Reading", created.name)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/playlists", request.path)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("""{"name":"Reading"}""", request.body.readUtf8())
            assertNull(request.getHeader("Accept"))
        }
    }

    @Test
    fun putJsonAcceptsEmptySuccessResponse() = runBlocking {
        withServer(MockResponse().setResponseCode(204)) { server ->
            client().reorderPlaylistEntries(server.url("/").toString(), "device-token", 7, listOf(11, 12))

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/playlists/7/entries/reorder", request.path)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals(
                """[{"entry_id":11,"position":0.0},{"entry_id":12,"position":1.0}]""",
                request.body.readUtf8(),
            )
        }
    }

    @Test
    fun patchJsonUsesPatchVerbNotThePayloadParameter() = runBlocking {
        // Guards the `patch` payload parameter shadowing Request.Builder.patch().
        withServer(
            MockResponse().setResponseCode(200)
                .setBody("""{"max_age_hours":24,"max_posts":30,"max_links":20}"""),
        ) { server ->
            val prefs = client().patchBlueskyPreferences(
                baseUrl = server.url("/").toString(),
                token = "device-token",
                patch = BlueskyScannerPreferencesPatch(maxPosts = 30),
            )
            assertEquals(30, prefs.maxPosts)

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/bluesky/preferences", request.path)
            assertEquals("application/json", request.getHeader("Accept"))
            assertEquals("""{"max_posts":30}""", request.body.readUtf8())
        }
    }

    @Test
    fun deleteSendsNoBodyAndKeepsItsPerEndpointAcceptHeader() = runBlocking {
        withServer(
            MockResponse().setResponseCode(204),
            MockResponse().setResponseCode(204),
        ) { server ->
            val baseUrl = server.url("/").toString()
            client().deleteSmartPlaylist(baseUrl, "device-token", 4)
            client().deletePlaylist(baseUrl, "device-token", 9)

            val smartDelete = server.takeRequest()
            assertEquals("DELETE", smartDelete.method)
            assertEquals("/smart-playlists/4", smartDelete.path)
            assertEquals("application/json", smartDelete.getHeader("Accept"))
            assertEquals(0L, smartDelete.bodySize)

            val manualDelete = server.takeRequest()
            assertEquals("DELETE", manualDelete.method)
            assertEquals("/playlists/9", manualDelete.path)
            assertNull(manualDelete.getHeader("Accept"))
        }
    }

    @Test
    fun payloadFreeActionPostSendsZeroLengthBodyWithoutContentType() = runBlocking {
        withServer(
            MockResponse().setResponseCode(200),
            MockResponse().setResponseCode(204),
        ) { server ->
            val baseUrl = server.url("/").toString()
            client().markItemDone(baseUrl, "device-token", 42, autoArchive = true)
            client().restoreItemFromBin(baseUrl, "device-token", 42)

            val done = server.takeRequest()
            assertEquals("POST", done.method)
            assertEquals("/items/42/done?auto_archive=1", done.path)
            assertNull(done.getHeader("Content-Type"))
            assertEquals("0", done.getHeader("Content-Length"))
            assertEquals(0L, done.bodySize)

            val restore = server.takeRequest()
            assertEquals("POST", restore.method)
            assertEquals("/items/42/restore", restore.path)
            assertNull(restore.getHeader("Content-Type"))
            assertEquals("0", restore.getHeader("Content-Length"))
        }
    }

    @Test
    fun listViewQueryParametersStayEncoded() = runBlocking {
        withServer(MockResponse().setResponseCode(200).setBody("[]")) { server ->
            client().getItemsByView(
                baseUrl = server.url("/").toString(),
                token = "device-token",
                view = ApiClient.ItemsView.ARCHIVED,
                limit = 250,
                sort = "created",
                dir = "desc",
                q = "rust & go",
            )

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            val url = requireNotNull(request.requestUrl)
            assertEquals("/items", url.encodedPath)
            assertEquals("archived", url.queryParameter("view"))
            // limit is clamped to the backend maximum before encoding.
            assertEquals("100", url.queryParameter("limit"))
            assertEquals("created", url.queryParameter("sort"))
            assertEquals("desc", url.queryParameter("dir"))
            assertEquals("rust & go", url.queryParameter("q"))
            val path = requireNotNull(request.path)
            assertTrue(path, !path.contains(" "))
        }
    }

    @Test
    fun tokenMintingRequestStaysUnauthenticated() = runBlocking {
        withServer(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"t0k3n","id":7,"name":"Pixel 8","scope":"read_write","created_at":"2026-07-25T00:00:00+00:00"}""",
            ),
        ) { server ->
            val response = client().postAuthToken(
                baseUrl = server.url("/").toString(),
                username = "brendan",
                password = "hunter2",
                deviceName = "Pixel 8",
            )
            assertEquals("t0k3n", response.token)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/auth/token", request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals(
                """{"username":"brendan","password":"hunter2","device_name":"Pixel 8"}""",
                request.body.readUtf8(),
            )
        }
    }

    @Test
    fun errorResponseStillSurfacesStatusCodeAndBody() = runBlocking {
        withServer(MockResponse().setResponseCode(404).setBody("""{"detail":"Not found"}""")) { server ->
            try {
                client().getSmartPlaylist(server.url("/").toString(), "device-token", 404)
                fail("Expected ApiException")
            } catch (error: ApiException) {
                assertEquals(404, error.statusCode)
                assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Not found"))
            }
        }
    }

    @Test
    fun unauthorizedIsLeftToTheCallerRatherThanHandledInTheClient() = runBlocking {
        withServer(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}""")) { server ->
            try {
                client().getItemText(server.url("/").toString(), "stale-token", 42)
                fail("Expected ApiException")
            } catch (error: ApiException) {
                assertEquals(401, error.statusCode)
            }
            // Exactly one attempt: no retry, no refresh, no endpoint fallback.
            assertEquals(1, server.requestCount)
            assertEquals("/items/42/text", server.takeRequest().path)
        }
    }
}
