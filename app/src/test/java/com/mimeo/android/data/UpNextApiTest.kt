package com.mimeo.android.data

import com.mimeo.android.model.UpNextSessionWriteRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpNextApiTest {
    private fun client() = ApiClient(OkHttpClient.Builder().followRedirects(false).build())

    private val populatedSession = """
        {
          "version": 4,
          "items": [{
            "item_id": 22, "position": 0, "title": "Article", "url": "https://example.com/a",
            "host": "example.com", "status": "ready", "active_content_version_id": 9,
            "strategy_used": "readability_http", "word_count": 321,
            "estimated_listen_minutes": 3, "has_active_content": true,
            "resume_read_percent": 12, "last_read_percent": 40,
            "progress_percent": 12, "furthest_percent": 40,
            "last_opened_at": null, "created_at": "2026-07-17T10:00:00Z",
            "archived_at": "2026-07-17T11:00:00Z", "is_archived": true, "is_muted": true
          }],
          "current_item_id": 22, "seed_source_kind": "playlist",
          "seed_source_label": "Reading list", "seeded_at": "2026-07-17T10:00:00Z",
          "updated_at": "2026-07-17T11:00:00Z", "dirty_since_seed": false
        }
    """.trimIndent()

    @Test
    fun readDecodesAbsentAndPopulatedSessions() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":null}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            assertNull(client().getUpNextSession(server.url("/").toString(), "token"))
            val session = client().getUpNextSession(server.url("/").toString(), "token")!!
            assertEquals(4L, session.version)
            assertEquals(listOf(22), session.items.map { it.itemId })
            assertEquals(22, session.currentItemId)
            assertEquals("Reading list", session.seedSourceLabel)
            assertTrue(session.items.single().isArchived)
            assertTrue(session.items.single().isMuted)
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/up-next/session", request.path)
                assertEquals("Bearer token", request.getHeader("Authorization"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun createAndReplacementSendExactExpectedVersion() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            val api = client()
            api.putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(null, listOf(22), 22, "playlist", "Reading list"),
            )
            api.putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(4, listOf(22), 22, "playlist", "Reading list"),
            )
            val create = server.takeRequest()
            val replace = server.takeRequest()
            assertEquals("PUT", create.method)
            val createBody = create.body.readUtf8()
            assertTrue(createBody.contains("\"expected_version\":null"))
            assertFalse(createBody.contains("history", ignoreCase = true))
            assertTrue(replace.body.readUtf8().contains("\"expected_version\":4"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun clearSendsObservedVersionInDeleteBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            client().clearUpNextSession(server.url("/").toString(), "token", 4)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/up-next/session", request.path)
            assertEquals("{\"expected_version\":4}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun conflictDecodesAuthoritativeCurrentSessionWithoutBecomingApiFailure() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"up_next_version_conflict","message":"refresh"},"current_session":$populatedSession}""",
            ),
        )
        server.start()
        try {
            client().putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(3, listOf(22), 22, "custom", "Android Up Next"),
            )
            fail("Expected version conflict")
        } catch (error: UpNextVersionConflictException) {
            assertEquals(4L, error.currentSession?.version)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun authenticationFailureRemainsDistinctFromVersionConflict() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))
        server.start()
        try {
            client().getUpNextSession(server.url("/").toString(), "stale-token")
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(401, error.statusCode)
        } finally {
            server.shutdown()
        }
    }
}
